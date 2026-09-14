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
package org.jumpmind.symmetric.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.model.DatabaseParameter;
import org.jumpmind.symmetric.model.Node;

/**
 * Get and set application wide configuration information.
 */
public interface IParameterService {
    public BigDecimal getDecimal(String key);

    public BigDecimal getDecimal(String key, BigDecimal defaulVal);

    public boolean is(String key);

    public boolean is(String key, boolean defaultVal);

    public int getInt(String key);

    public int getInt(String key, int defaultVal);

    public long getLong(String key);

    public long getLong(String key, long defaultVal);

    public String getString(String key);

    public String getString(String key, String defaultVal);

    public void saveParameter(String key, Object paramValue, String lastUpdateBy);

    public void saveParameter(String externalId, String nodeGroupId, String key, Object paramValue, String lastUpdateBy);

    public void saveParameters(String externalId, String nodeGroupId, Map<String, Object> parameters, String lastUpdateBy);

    public void deleteParameter(String key);

    public void deleteParameter(String externalId, String nodeGroupId, String key);

    public void deleteParameterWithUpdate(String externalId, String nodeGroupId, String key);

    public void deleteAllParameters();

    public void rereadParameters();

    public Date getLastTimeParameterWereCached();

    public List<DatabaseParameter> getDatabaseParametersForAll();

    public List<DatabaseParameter> getDatabaseParametersFor(String paramKey);

    public TypedProperties getDatabaseParameters(String externalId, String nodeGroupId);

    public List<DatabaseParameter> getOfflineNodeParameters();

    public TypedProperties getAllParameters();

    public boolean isRegistrationServer();

    public boolean isRemoteNodeRegistrationServer(Node remoteNode);

    public boolean refreshFromDatabase();

    /**
     * Get the group id for this instance
     */
    public String getNodeGroupId();

    /**
     * Get the external id for this instance
     */
    public String getExternalId();

    public Map<String, String> getReplacementValues();

    /**
     * Provide the url used to register at to get initial configuration information
     */
    public String getRegistrationUrl();

    /**
     * Provide information about the URL used to contact this symmetric instance
     */
    public String getSyncUrl();

    public String getTablePrefix();

    public String getEngineName();

    public String getTempDirectory();

    public boolean hasDatabaseBeenSetup();
}