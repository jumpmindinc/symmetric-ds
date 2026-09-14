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
package org.jumpmind.symmetric.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.model.DatabaseParameter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IParameterService;

public class MockParameterService extends AbstractParameterService implements IParameterService {
    private Properties properties = new Properties();

    public MockParameterService() {
    }

    public MockParameterService(String... parameters) {
        if (parameters != null) {
            String key = null;
            for (int i = 0; i < parameters.length; i++) {
                if (i % 2 != 0) {
                    properties.setProperty(key, parameters[i]);
                } else {
                    key = parameters[0];
                }
            }
        }
    }

    public boolean refreshFromDatabase() {
        return false;
    }

    public MockParameterService(Properties properties) {
        this.properties = properties;
    }

    public boolean isRegistrationServer() {
        return false;
    }

    public void saveParameter(String key, Object paramValue, String lastUpdateBy) {
    }

    public void saveParameter(String externalId, String nodeGroupId, String key, Object paramValue, String lastUpdateBy) {
    }

    public void saveParameters(String externalId, String nodeGroupId, Map<String, Object> parameters, String lastUpdateBy) {
    }

    public void deleteParameter(String externalId, String nodeGroupId, String key) {
    }

    public void deleteAllParameters() {
    }

    public List<DatabaseParameter> getDatabaseParametersForAll() {
        return null;
    }

    public List<DatabaseParameter> getDatabaseParametersFor(String paramKey) {
        return null;
    }

    public TypedProperties getDatabaseParametersByNodeGroupId(String nodeGroupId) {
        return null;
    }

    public List<DatabaseParameter> getOfflineNodeParameters() {
        return null;
    }

    public String getTablePrefix() {
        return "sym";
    }

    @Override
    protected TypedProperties rereadApplicationParameters() {
        return new TypedProperties(properties);
    }

    @Override
    public void deleteParameterWithUpdate(String externalId,
            String nodeGroupId, String key) {
    }

    @Override
    public TypedProperties getDatabaseParameters(String externalId, String nodeGroupId) {
        return null;
    }

    @Override
    public void deleteParameter(String key) {
    }

    @Override
    public boolean isRemoteNodeRegistrationServer(Node remoteNode) {
        return getRegistrationUrl().equalsIgnoreCase(remoteNode.getSyncUrl());
    }
}
