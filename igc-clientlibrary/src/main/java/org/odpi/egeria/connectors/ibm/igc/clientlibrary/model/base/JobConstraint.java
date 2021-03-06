/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.base;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.PUBLIC_ONLY;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.common.Reference;
import org.odpi.egeria.connectors.ibm.igc.clientlibrary.model.common.ItemList;

/**
 * POJO for the {@code job_constraint} asset type in IGC, displayed as '{@literal Job Constraint}' in the IGC UI.
 * <br><br>
 * (this code has been created based on out-of-the-box IGC metadata types.
 *  If modifications are needed, eg. to handle custom attributes,
 *  extending from this class in your own custom class is the best approach.)
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.EXISTING_PROPERTY, property="_type", visible=true, defaultImpl=JobConstraint.class)
@JsonAutoDetect(getterVisibility=PUBLIC_ONLY, setterVisibility=PUBLIC_ONLY, fieldVisibility=NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown=true)
@JsonTypeName("job_constraint")
public class JobConstraint extends Reference {

    @JsonProperty("expression")
    protected String expression;

    @JsonProperty("name")
    protected String name;

    @JsonProperty("native_id")
    protected String nativeId;

    @JsonProperty("of_link")
    protected MainObject ofLink;

    @JsonProperty("uses_flow_variable")
    protected ItemList<DataItem> usesFlowVariable;

    /**
     * Retrieve the {@code expression} property (displayed as '{@literal Expression}') of the object.
     * @return {@code String}
     */
    @JsonProperty("expression")
    public String getExpression() { return this.expression; }

    /**
     * Set the {@code expression} property (displayed as {@code Expression}) of the object.
     * @param expression the value to set
     */
    @JsonProperty("expression")
    public void setExpression(String expression) { this.expression = expression; }

    /**
     * Retrieve the {@code name} property (displayed as '{@literal Name}') of the object.
     * @return {@code String}
     */
    @JsonProperty("name")
    public String getTheName() { return this.name; }

    /**
     * Set the {@code name} property (displayed as {@code Name}) of the object.
     * @param name the value to set
     */
    @JsonProperty("name")
    public void setTheName(String name) { this.name = name; }

    /**
     * Retrieve the {@code native_id} property (displayed as '{@literal Native ID}') of the object.
     * @return {@code String}
     */
    @JsonProperty("native_id")
    public String getNativeId() { return this.nativeId; }

    /**
     * Set the {@code native_id} property (displayed as {@code Native ID}) of the object.
     * @param nativeId the value to set
     */
    @JsonProperty("native_id")
    public void setNativeId(String nativeId) { this.nativeId = nativeId; }

    /**
     * Retrieve the {@code of_link} property (displayed as '{@literal Link}') of the object.
     * @return {@code MainObject}
     */
    @JsonProperty("of_link")
    public MainObject getOfLink() { return this.ofLink; }

    /**
     * Set the {@code of_link} property (displayed as {@code Link}) of the object.
     * @param ofLink the value to set
     */
    @JsonProperty("of_link")
    public void setOfLink(MainObject ofLink) { this.ofLink = ofLink; }

    /**
     * Retrieve the {@code uses_flow_variable} property (displayed as '{@literal Stage Columns}') of the object.
     * @return {@code ItemList<DataItem>}
     */
    @JsonProperty("uses_flow_variable")
    public ItemList<DataItem> getUsesFlowVariable() { return this.usesFlowVariable; }

    /**
     * Set the {@code uses_flow_variable} property (displayed as {@code Stage Columns}) of the object.
     * @param usesFlowVariable the value to set
     */
    @JsonProperty("uses_flow_variable")
    public void setUsesFlowVariable(ItemList<DataItem> usesFlowVariable) { this.usesFlowVariable = usesFlowVariable; }

}
