/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.ibm.datastage.dataengineconnector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.odpi.egeria.connectors.ibm.datastage.dataengineconnector.auditlog.DataStageErrorCode;
import org.odpi.egeria.connectors.ibm.datastage.dataengineconnector.mapping.ProcessMapping;
import org.odpi.egeria.connectors.ibm.datastage.dataengineconnector.mapping.SchemaTypeMapping;
import org.odpi.egeria.connectors.ibm.datastage.dataengineconnector.model.*;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.IGCRestClient;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.IGCVersionEnum;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.base.*;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.common.ItemList;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.search.IGCSearch;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.search.IGCSearchCondition;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.search.IGCSearchConditionSet;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.update.IGCCreate;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.update.IGCUpdate;
import org.odpi.openmetadata.accessservices.dataengine.model.SoftwareServerCapability;
import org.odpi.openmetadata.frameworks.connectors.ffdc.*;
import org.odpi.openmetadata.frameworks.connectors.properties.ConnectionProperties;
import org.odpi.openmetadata.frameworks.connectors.properties.EndpointProperties;
import org.odpi.openmetadata.openconnectors.governancedaemonconnectors.dataengineproxy.DataEngineConnectorBase;
import org.odpi.openmetadata.openconnectors.governancedaemonconnectors.dataengineproxy.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class DataStageConnector extends DataEngineConnectorBase {

    private static final Logger log = LoggerFactory.getLogger(DataStageConnector.class);

    private static final String SYNC_RULE_NAME = "Job metadata will be periodically synced through ODPi Egeria's Data Engine OMAS";
    private static final String SYNC_RULE_DESC = "GENERATED -- DO NOT UPDATE: last synced at ";

    private final SimpleDateFormat SYNC_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private IGCRestClient igcRestClient;
    private ObjectMapper objectMapper;
    private DataEngineSoftwareServerCapability dataEngine;

    private String defaultUserId;

    private DataStageCache dataStageCache;

    /**
     * Default constructor used by the OCF Connector Provider.
     */
    public DataStageConnector() {
        // Nothing to do...
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(String               connectorInstanceId,
                           ConnectionProperties connectionProperties) {
        super.initialize(connectorInstanceId, connectionProperties);
        this.objectMapper = new ObjectMapper();
        this.dataStageCache = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws ConnectorCheckedException {

        super.start();
        final String methodName = "start";

        if (log.isInfoEnabled()) { log.info("Initializing DataStageDataEngineConnector..."); }

        EndpointProperties endpointProperties = connectionProperties.getEndpoint();
        if (endpointProperties == null) {
            raiseConnectorCheckedException(DataStageErrorCode.CONNECTION_FAILURE, methodName, null, "<null>");
        } else {
            String address = endpointProperties.getAddress();
            if (address == null || address.equals("")) {
                raiseConnectorCheckedException(DataStageErrorCode.CONNECTION_FAILURE, methodName, null, address);
            } else {

                String igcUser = connectionProperties.getUserId();
                String igcPass = connectionProperties.getClearPassword();

                Map<String, Object> proxyProperties = this.connectionBean.getConfigurationProperties();
                Integer igcPage = null;
                if (proxyProperties != null) {
                    igcPage = (Integer) proxyProperties.get("pageSize");
                }

                this.defaultUserId = igcUser;

                IGCVersionEnum igcVersion;
                // Create new REST API client (opens a new session)
                this.igcRestClient = new IGCRestClient("https://" + address, igcUser, igcPass);
                if (this.igcRestClient.start()) {

                    // Set the version based on the IGC client's auto-determination of the IGC environment's version
                    igcVersion = this.igcRestClient.getIgcVersion();
                    // Set the default page size to whatever is provided as part of config parameters (default to 100)
                    if (igcPage != null) {
                        this.igcRestClient.setDefaultPageSize(igcPage);
                    } else {
                        this.igcRestClient.setDefaultPageSize(100);
                    }

                    // Create a new SoftwareServerCapability representing this Data Engine
                    SoftwareServerCapability sscDataEngine = new SoftwareServerCapability();
                    sscDataEngine.setEngineType("IBM InfoSphere DataStage");
                    sscDataEngine.setEngineVersion(igcVersion.getVersionString());
                    sscDataEngine.setQualifiedName("ibm-datastage@" + address);
                    sscDataEngine.setDisplayName(address);
                    dataEngine = new DataEngineSoftwareServerCapability(sscDataEngine, defaultUserId);

                } else {
                    raiseConnectorCheckedException(DataStageErrorCode.CONNECTION_FAILURE, methodName, null, address);
                }

            }
        }

    }

    /**
     * Free up any resources held since the connector is no longer needed.
     */
    @Override
    public void disconnect() {
        // Close the session on the IGC REST client
        this.igcRestClient.disconnect();
    }

    /**
     * Retrieve the details about the data engine to which we are connected.
     *
     * @return DataEngineSoftwareServerCapability
     */
    @Override
    public DataEngineSoftwareServerCapability getDataEngineDetails() { return dataEngine; }

    /**
     * Retrieve the date and time at which changes were last synchronized.
     *
     * @return Date
     */
    @Override
    public Date getChangesLastSynced() {
        InformationGovernanceRule jobSyncRule = getJobSyncRule();
        Date lastSync = null;
        if (jobSyncRule != null) {
            String description = jobSyncRule.getShortDescription();
            String dateString = description.substring(SYNC_RULE_DESC.length());
            try {
                lastSync = SYNC_DATE_FORMAT.parse(dateString);
            } catch (ParseException e) {
                log.error("Unable to parse date and time of last sync from rule: {} ({})", description, dateString);
            }
        }
        return lastSync;
    }

    /**
     * Persist the date and time at which changes were last successfully synchronized.
     *
     * @param time the date and time at which changes were last successfully synchronized
     */
    @Override
    public void setChangesLastSynced(Date time) {
        final String methodName = "setChangesLastSynced";
        InformationGovernanceRule exists = getJobSyncRule();
        String newDescription = SYNC_RULE_DESC + SYNC_DATE_FORMAT.format(time);
        boolean success;
        if (exists == null) {
            // Create the entry
            IGCCreate igcCreate = new IGCCreate("information_governance_rule");
            igcCreate.addProperty(DataStageConstants.NAME, SYNC_RULE_NAME);
            igcCreate.addProperty(DataStageConstants.SHORT_DESCRIPTION, newDescription);
            success = igcRestClient.create(igcCreate) != null;
        } else {
            // Update the entry
            IGCUpdate igcUpdate = new IGCUpdate(exists.getId());
            igcUpdate.addProperty(DataStageConstants.SHORT_DESCRIPTION, newDescription);
            success = igcRestClient.update(igcUpdate);
        }
        if (!success) {
            DataStageErrorCode errorCode = DataStageErrorCode.SYNC_TIME_UPDATE_FAILURE;
            String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage();
            throw new OCFRuntimeException(
                    errorCode.getHTTPErrorCode(),
                    this.getClass().getName(),
                    methodName,
                    errorMessage,
                    errorCode.getSystemAction(),
                    errorCode.getUserAction()
            );
        }
    }

    /**
     * Retrieve a list of the changed schema types between the dates and times provided.
     *
     * @param from the date and time from which to look for changes (exclusive)
     * @param to the date and time up to which to look for changes (inclusive)
     * @return {@code List<DataEngineSchemaType>}
     */
    @Override
    public List<DataEngineSchemaType> getChangedSchemaTypes(Date from, Date to) {

        if (log.isDebugEnabled()) { log.debug("Looking for changed SchemaTypes..."); }
        Map<String, DataEngineSchemaType> schemaTypeMap = new HashMap<>();

        initializeCache(from, to);

        // Iterate through each job looking for any virtual assets -- these must be created first
        for (DataStageJob job : dataStageCache.getAllJobs()) {
            for (String storeRid : job.getStoreRids()) {
                if (log.isDebugEnabled()) { log.debug(" ... considering store: {}", storeRid); }
                if (DataStageDataAsset.isVirtualAsset(storeRid) && !schemaTypeMap.containsKey(storeRid)) {
                    if (log.isDebugEnabled()) { log.debug(" ... VIRTUAL! Creating a SchemaType ..."); }
                    SchemaTypeMapping schemaTypeMapping = new SchemaTypeMapping(job, job.getStoreIdentityFromRid(storeRid), job.getFieldsForStore(storeRid));
                    DataEngineSchemaType deSchemaType = new DataEngineSchemaType(schemaTypeMapping.getSchemaType(), defaultUserId);
                    try {
                        if (log.isDebugEnabled()) { log.debug(" ... created: {}", objectMapper.writeValueAsString(deSchemaType.getSchemaType())); }
                    } catch (JsonProcessingException e) {
                        log.error("Unable to serialise to JSON: {}", deSchemaType.getSchemaType(), e);
                    }
                    schemaTypeMap.put(storeRid, deSchemaType);
                }
            }
        }

        return new ArrayList<>(schemaTypeMap.values());

    }

    /**
     * Retrieve a list of the changed port implementations between the dates and times provided.
     *
     * @param from the date and time from which to look for changes (exclusive)
     * @param to the date and time up to which to look for changes (inclusive)
     * @return {@code List<DataEnginePortImplementation>}
     */
    @Override
    public List<DataEnginePortImplementation> getChangedPortImplementations(Date from, Date to) {
        // do nothing -- port implementations will always be handled by other methods
        return Collections.emptyList();
    }

    /**
     * Retrieve a list of the changed port aliases between the dates and times provided.
     *
     * @param from the date and time from which to look for changes (exclusive)
     * @param to the date and time up to which to look for changes (inclusive)
     * @return {@code List<DataEnginePortAlias>}
     */
    @Override
    public List<DataEnginePortAlias> getChangedPortAliases(Date from, Date to) {
        // do nothing -- port aliases will always be handled by other methods
        return Collections.emptyList();
    }

    /**
     * Retrieve a list of the changed processes between the dates and times provided.
     *
     * @param from the date and time from which to look for changes (exclusive)
     * @param to the date and time up to which to look for changes (inclusive)
     * @return {@code List<DataEngineProcess>}
     */
    @Override
    public List<DataEngineProcess> getChangedProcesses(Date from, Date to) {

        initializeCache(from, to);

        List<DataEngineProcess> processes = new ArrayList<>();

        List<DataStageJob> seqList = new ArrayList<>();
        Map<String, DataEngineProcess> jobProcessByRid = new HashMap<>();
        // Translate changed jobs first, to build up appropriate PortAliases list
        for (DataStageJob detailedJob : dataStageCache.getAllJobs()) {
            if (detailedJob.getType().equals(DataStageJob.JobType.SEQUENCE)) {
                seqList.add(detailedJob);
            } else {
                processes.addAll(getProcessesForEachStage(detailedJob));
                DataEngineProcess jobProcess = getProcessForJob(detailedJob);
                if (jobProcess != null) {
                    jobProcessByRid.put(detailedJob.getJobObject().getId(), jobProcess);
                    processes.add(jobProcess);
                }
            }
        }
        // Then load sequences, re-using the PortAliases constructed for the jobs
        // TODO: this probably will NOT work for nested sequences?
        for (DataStageJob detailedSeq: seqList) {
            processes.add(getProcessForSequence(detailedSeq, jobProcessByRid));
        }

        return processes;

    }

    /**
     * Retrieve a list of the changed lineage mappings between the dates and times provided.
     *
     * @param from the date and time from which to look for changes (exclusive)
     * @param to the date and time up to which to look for changes (inclusive)
     * @return {@code List<DataEngineLineageMappings>}
     */
    @Override
    public List<DataEngineLineageMappings> getChangedLineageMappings(Date from, Date to) {
        // do nothing -- lineage mappings will always be handled by other methods
        return Collections.emptyList();
    }

    /**
     * Initialize the cache of changed job details based on the provided dates and times.
     *
     * @param from the date and time from which to cache changes (exclusive)
     * @param to the date and time up to which to cache changes (inclusive)
     */
    private void initializeCache(Date from, Date to) {
        DataStageCache forComparison = new DataStageCache(from, to);
        if (dataStageCache == null || !dataStageCache.equals(forComparison)) {
            // Initialize the cache, if it is empty, or reset it if it differs from the dates and times we've been given
            dataStageCache = forComparison;
            dataStageCache.initialize(igcRestClient);
        }
    }

    /**
     * Translate the detailed stages of the provided DataStage job into Processes.
     *
     * @param job the job for which to translate detailed stages
     * @return {@code List<DataEngineProcess>}
     */
    private List<DataEngineProcess> getProcessesForEachStage(DataStageJob job) {
        List<DataEngineProcess> processes = new ArrayList<>();
        if (log.isDebugEnabled()) { log.debug("Translating processes for each stage..."); }
        for (Stage stage : job.getAllStages()) {
            ProcessMapping processMapping = new ProcessMapping(job, stage);
            DataEngineProcess process = processMapping.getProcess();
            if (process != null) {
                try {
                    if (log.isDebugEnabled()) { log.debug(" ... process: {}", objectMapper.writeValueAsString(process)); }
                } catch (JsonProcessingException e) {
                    log.error("Unable to serialise to JSON: {}", process, e);
                }
                processes.add(process);
            }
        }
        return processes;
    }

    /**
     * Translate a single Process to represent the DataStage job itself.
     *
     * @param job the job object for which to load a process
     * @return DataEngineProcess
     */
    private DataEngineProcess getProcessForJob(DataStageJob job) {
        DataEngineProcess process = null;
        if (!job.getType().equals(DataStageJob.JobType.SEQUENCE)) {
            if (log.isDebugEnabled()) { log.debug("Load process for job..."); }
            ProcessMapping processMapping = new ProcessMapping(job);
            process = processMapping.getProcess();
            if (process != null) {
                try {
                    if (log.isDebugEnabled()) { log.debug(" ... process: {}", objectMapper.writeValueAsString(process)); }
                } catch (JsonProcessingException e) {
                    log.error("Unable to serialise to JSON: {}", process, e);
                }
            }
        }
        return process;
    }

    /**
     * Translate a single Process to represent the DataStage sequence itself.
     *
     * @param job the job object for which to load a process
     * @param jobProcessByRid a map from job RID to its detailed process definition
     * @return DataEngineProcess
     */
    private DataEngineProcess getProcessForSequence(DataStageJob job, Map<String, DataEngineProcess> jobProcessByRid) {
        DataEngineProcess process = null;
        if (job.getType().equals(DataStageJob.JobType.SEQUENCE)) {
            if (log.isDebugEnabled()) { log.debug("Load process for sequence..."); }
            ProcessMapping processMapping = new ProcessMapping(job, jobProcessByRid);
            process = processMapping.getProcess();
            if (process != null) {
                try {
                    if (log.isDebugEnabled()) { log.debug(" ... process: {}", objectMapper.writeValueAsString(process)); }
                } catch (JsonProcessingException e) {
                    log.error("Unable to serialise to JSON: {}", process, e);
                }
            }
        }
        return process;
    }

    /**
     * Retrieve the rule that represents the job sync with Data Engine OMAS.
     *
     * @return InformationGovernanceRule
     */
    private InformationGovernanceRule getJobSyncRule() {
        IGCSearch igcSearch = new IGCSearch("information_governance_rule");
        igcSearch.addProperty(DataStageConstants.SHORT_DESCRIPTION);
        IGCSearchCondition condition = new IGCSearchCondition(DataStageConstants.NAME, "=", SYNC_RULE_NAME);
        IGCSearchConditionSet conditionSet = new IGCSearchConditionSet(condition);
        igcSearch.addConditions(conditionSet);
        ItemList<InformationGovernanceRule> results = igcRestClient.search(igcSearch);
        return (results == null || results.getPaging().getNumTotal() == 0) ? null : results.getItems().get(0);
    }

    /**
     * Throws a ConnectorCheckedException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the name of the method throwing the exception
     * @param cause the underlying cause of the exception (or null if none)
     * @param params any parameters for formatting the error message
     * @throws ConnectorCheckedException always
     */
    private void raiseConnectorCheckedException(DataStageErrorCode errorCode, String methodName, Throwable cause, String ...params) throws ConnectorCheckedException {
        String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(params);
        throw new ConnectorCheckedException(
                errorCode.getHTTPErrorCode(),
                this.getClass().getName(),
                methodName,
                errorMessage,
                errorCode.getSystemAction(),
                errorCode.getUserAction(),
                cause
        );
    }

}
