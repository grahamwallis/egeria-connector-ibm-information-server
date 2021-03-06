/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.ibm.igc.repositoryconnector.mapping.entities;

import org.odpi.egeria.connectors.ibm.igc.clientlibrary.IGCRestClient;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.IGCVersionEnum;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.common.Reference;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.search.IGCSearchCondition;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.search.IGCSearchConditionSet;
import org.odpi.egeria.connectors.ibm.igc.auditlog.IGCOMRSErrorCode;
import org.odpi.egeria.connectors.ibm.igc.repositoryconnector.IGCOMRSRepositoryConnector;
import org.odpi.egeria.connectors.ibm.igc.repositoryconnector.mapping.EntityMappingInstance;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyCategory;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstancePropertyValue;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.FunctionNotSupportedException;

/**
 * Defines the common mappings to the OMRS "GovernanceDefinition" entity.
 */
public class GovernanceDefinition_Mapper extends ReferenceableMapper {

    private static class Singleton {
        private static final GovernanceDefinition_Mapper INSTANCE = new GovernanceDefinition_Mapper();
    }
    public static GovernanceDefinition_Mapper getInstance(IGCVersionEnum version) {
        return Singleton.INSTANCE;
    }

    private GovernanceDefinition_Mapper() {

        // Start by calling the superclass's constructor to initialise the Mapper
        super(
                "",
                "",
                "GovernanceDefinition"
        );

    }

    protected GovernanceDefinition_Mapper(String igcAssetTypeName,
                                          String igcAssetTypeDisplayName,
                                          String omrsEntityTypeName) {
        super(
                igcAssetTypeName,
                igcAssetTypeDisplayName,
                omrsEntityTypeName
        );

        // The list of properties that should be mapped
        addSimplePropertyMapping("name", "title");
        addSimplePropertyMapping("short_description", "summary");
        addSimplePropertyMapping("long_description", "description");
        addComplexIgcProperty("parent_policy");
        addComplexOmrsProperty("domain");
        addLiteralPropertyMapping("scope", null);
        addLiteralPropertyMapping("priority", null);
        addLiteralPropertyMapping("implications", null);
        addLiteralPropertyMapping("outcomes", null);
        addLiteralPropertyMapping("results", null);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected InstanceProperties complexPropertyMappings(EntityMappingInstance entityMap,
                                                         InstanceProperties instanceProperties) {

        instanceProperties = super.complexPropertyMappings(entityMap, instanceProperties);

        final String methodName = "complexPropertyMappings";

        IGCOMRSRepositoryConnector igcomrsRepositoryConnector = entityMap.getRepositoryConnector();
        IGCRestClient igcRestClient = igcomrsRepositoryConnector.getIGCRestClient();
        Reference igcEntity = entityMap.getIgcEntity();

        OMRSRepositoryHelper repositoryHelper = igcomrsRepositoryConnector.getRepositoryHelper();
        String repositoryName = igcomrsRepositoryConnector.getRepositoryName();

        // setup the OMRS 'domain' property
        Reference parentPolicy = (Reference) igcRestClient.getPropertyByName(igcEntity, "parent_policy");
        if (parentPolicy != null) {
            instanceProperties = repositoryHelper.addStringPropertyToInstance(
                    repositoryName,
                    instanceProperties,
                    "domain",
                    parentPolicy.getName(),
                    methodName
            );
        }

        return instanceProperties;

    }

    /**
     * Handle the search for 'domain' by searching against 'parent_policy' of the object in IGC.
     *
     * @param repositoryHelper the repository helper
     * @param repositoryName name of the repository
     * @param igcRestClient connectivity to an IGC environment
     * @param igcSearchConditionSet the set of search criteria to which to add
     * @param igcPropertyName the IGC property name (or COMPLEX_MAPPING_SENTINEL) to search
     * @param omrsPropertyName the OMRS property name (or COMPLEX_MAPPING_SENTINEL) to search
     * @param value the value for which to search
     * @throws FunctionNotSupportedException when a regular expression is used for the search which is not supported
     */
    @Override
    public void addComplexPropertySearchCriteria(OMRSRepositoryHelper repositoryHelper,
                                                 String repositoryName,
                                                 IGCRestClient igcRestClient,
                                                 IGCSearchConditionSet igcSearchConditionSet,
                                                 String igcPropertyName,
                                                 String omrsPropertyName,
                                                 InstancePropertyValue value) throws FunctionNotSupportedException {

        super.addComplexPropertySearchCriteria(repositoryHelper, repositoryName, igcRestClient, igcSearchConditionSet, igcPropertyName, omrsPropertyName, value);

        final String methodName = "addComplexPropertySearchCriteria";

        // Only need to add a condition of we are after the 'fileType' and have been provided a String
        if (omrsPropertyName.equals("domain") && value.getInstancePropertyCategory().equals(InstancePropertyCategory.PRIMITIVE)) {
            String domain = value.valueAsString();
            String searchableDomain = repositoryHelper.getUnqualifiedLiteralString(domain);
            if (repositoryHelper.isExactMatchRegex(domain)) {
                IGCSearchCondition exact = new IGCSearchCondition("parent_policy.name", "=", searchableDomain);
                igcSearchConditionSet.addCondition(exact);
            } else if (repositoryHelper.isEndsWithRegex(domain)) {
                IGCSearchCondition endsWith = new IGCSearchCondition("parent_policy.name", "like %{0}", searchableDomain);
                igcSearchConditionSet.addCondition(endsWith);
            } else if (repositoryHelper.isStartsWithRegex(domain)) {
                IGCSearchCondition startsWith = new IGCSearchCondition("parent_policy.name", "like {0}%", searchableDomain);
                igcSearchConditionSet.addCondition(startsWith);
            } else if (repositoryHelper.isContainsRegex(domain)) {
                IGCSearchCondition contains = new IGCSearchCondition("parent_policy.name", "like %{0}%", searchableDomain);
                igcSearchConditionSet.addCondition(contains);
            } else {
                IGCOMRSErrorCode errorCode = IGCOMRSErrorCode.REGEX_NOT_IMPLEMENTED;
                String errorMessage = errorCode.getErrorMessageId() + errorCode.getFormattedErrorMessage(
                        repositoryName,
                        domain);
                throw new FunctionNotSupportedException(errorCode.getHTTPErrorCode(),
                        this.getClass().getName(),
                        methodName,
                        errorMessage,
                        errorCode.getSystemAction(),
                        errorCode.getUserAction());
            }
        }

    }

}
