/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.service.jmx;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.service.IParameterService;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "The management interface for node parameters")
public class ParameterManagementService implements IBuiltInExtensionPoint, ISymmetricEngineAware {
    private IParameterService parameterService;

    public ParameterManagementService() {
    }

    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.parameterService = engine.getParameterService();
    }

    @ManagedOperation(description = "Reload supported parameters from file or database")
    public void rereadParameters() {
        this.parameterService.rereadParameters();
    }

    @ManagedOperation(description = "Update a parameter for this node only")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "key", description = "The name of the parameter"),
            @ManagedOperationParameter(name = "value", description = "The value for the parameter") })
    public void updateParameter(String key, String value) {
        this.parameterService.saveParameter(key, value, "jmx");
    }

    @ManagedOperation(description = "Update a parameter for all nodes")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "key", description = "The name of the parameter"),
            @ManagedOperationParameter(name = "value", description = "The value for the parameter") })
    public void updateParameterForAll(String key, String value) {
        this.parameterService.saveParameter(ParameterConstants.ALL, ParameterConstants.ALL, key,
                value, "jmx");
    }

    @ManagedOperation(description = "Update a parameter for all nodes in a group")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "nodeGroup", description = "The name of the node group"),
            @ManagedOperationParameter(name = "key", description = "The name of the parameter"),
            @ManagedOperationParameter(name = "value", description = "The value for the parameter") })
    public void updateParameterForNodeGroup(String nodeGroup, String key, String value) {
        this.parameterService.saveParameter(ParameterConstants.ALL, nodeGroup, key, value, "jmx");
    }

    @ManagedOperation(description = "Update a parameter for a specific node")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "externalId", description = "The name of the external id of node"),
            @ManagedOperationParameter(name = "nodeGroup", description = "The name of the node group"),
            @ManagedOperationParameter(name = "key", description = "The name of the parameter"),
            @ManagedOperationParameter(name = "value", description = "The value for the parameter") })
    public void updateParameterForNode(String externalId, String nodeGroup, String key, String value) {
        this.parameterService.saveParameter(externalId, nodeGroup, key, value, "jmx");
    }

    @ManagedAttribute(description = "The parameters configured for this SymmetricDS instance")
    public String getParametersList() {
        StringBuilder buffer = new StringBuilder();
        TypedProperties properties = parameterService.getAllParameters();
        buffer.append("<pre>");
        for (Object key : properties.keySet()) {
            buffer.append(key).append("=").append(properties.get(key)).append("\n");
        }
        buffer.append("</pre>");
        return buffer.toString();
    }
}