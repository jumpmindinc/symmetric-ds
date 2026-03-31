/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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

import java.io.File;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.jumpmind.symmetric.util.TypedPropertiesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractParameterService {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected IExtensionService extensionService;
    protected TypedProperties parameters;
    private long cacheTimeoutInMs = 0;
    private long lastTimeParameterWereCached;
    protected Properties systemProperties;
    protected boolean databaseHasBeenInitialized = false;
    protected boolean databaseHasBeenSetup = false;
    protected String externalId = null;
    protected String engineName = null;
    protected String nodeGroupId = null;
    protected String syncUrl = null;
    protected String registrationUrl = null;

    public AbstractParameterService() {
        this.systemProperties = (Properties) System.getProperties().clone();
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultVal) {
        String val = getString(key);
        if (val != null) {
            try {
                return new BigDecimal(val);
            } catch (NumberFormatException ex) {
                TypedProperties.logPropertiesException(log, key, val);
            }
        }
        return defaultVal;
    }

    public BigDecimal getDecimal(String key) {
        return getDecimal(key, BigDecimal.ZERO);
    }

    public boolean is(String key) {
        return is(key, false);
    }

    public boolean is(String key, boolean defaultVal) {
        String val = getString(key);
        if (val != null) {
            val = val.trim();
            if (val.equals("1")) {
                return true;
            } else {
                return Boolean.parseBoolean(val);
            }
        } else {
            return defaultVal;
        }
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultVal) {
        String val = getString(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ex) {
                TypedProperties.logPropertiesException(log, key, val);
            }
        }
        return defaultVal;
    }

    public long getLong(String key) {
        return getLong(key, 0);
    }

    public long getLong(String key, long defaultVal) {
        String val = getString(key);
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException ex) {
                TypedProperties.logPropertiesException(log, key, val);
            }
        }
        return defaultVal;
    }

    public String getString(String key, String defaultVal) {
        String value = getParameters().get(key, defaultVal);
        return StringUtils.isBlank(value) ? defaultVal : value;
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getTempDirectory() {
        String engineName = this.getEngineName();
        String tmpDirBase = getString("java.io.tmpdir", System.getProperty("java.io.tmpdir"));
        if (StringUtils.trimToNull(engineName) == null) {
            return tmpDirBase;
        } else {
            return tmpDirBase + File.separator + engineName;
        }
    }

    protected abstract TypedProperties rereadApplicationParameters();

    public synchronized void rereadParameters() {
        lastTimeParameterWereCached = 0;
        getParameters();
    }

    protected synchronized TypedProperties getParameters() {
        long timeoutTime = System.currentTimeMillis() - cacheTimeoutInMs;
        // see if the parameters have timed out
        if (parameters == null || (cacheTimeoutInMs > 0 && lastTimeParameterWereCached < timeoutTime)) {
            try {
                parameters = rereadApplicationParameters();
                TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(parameters);
                lastTimeParameterWereCached = System.currentTimeMillis();
                cacheTimeoutInMs = getInt(ParameterConstants.PARAMETER_REFRESH_PERIOD_IN_MS);
            } catch (SqlException ex) {
                if (parameters != null) {
                    log.warn("Could not read database parameters.  We will try again later", ex);
                } else {
                    log.error("Could not read database parameters and they have not yet been initialized");
                    throw ex;
                }
                throw ex;
            }
        }
        return parameters;
    }

    public TypedProperties getAllParameters() {
        return getParameters();
    }

    public Date getLastTimeParameterWereCached() {
        return new Date(lastTimeParameterWereCached);
    }

    public String getExternalId() {
        if (externalId == null) {
            String value = getString(ParameterConstants.EXTERNAL_ID);
            value = SymmetricUtils.substituteScripts(value, getReplacementValues());
            externalId = value;
            if (log.isDebugEnabled()) {
                log.debug("External Id eval results in: {}", externalId);
            }
        }
        return externalId;
    }

    public String getSyncUrl() {
        if (syncUrl == null) {
            String value = getString(ParameterConstants.SYNC_URL);
            value = SymmetricUtils.substituteScripts(value, getReplacementValues());
            if (value != null) {
                value = value.trim().replaceAll("[/\\\\]+$", "");
            }
            syncUrl = value;
            if (log.isDebugEnabled()) {
                log.debug("Sync URL eval results in: {}", syncUrl);
            }
        }
        return syncUrl;
    }

    public String getNodeGroupId() {
        if (nodeGroupId == null) {
            String value = getString(ParameterConstants.NODE_GROUP_ID);
            value = SymmetricUtils.substituteScripts(value, getReplacementValues());
            nodeGroupId = value;
            if (log.isDebugEnabled()) {
                log.debug("Node Group Id eval results in: {}", nodeGroupId);
            }
        }
        return nodeGroupId;
    }

    public String getRegistrationUrl() {
        if (registrationUrl == null) {
            String value = getString(ParameterConstants.REGISTRATION_URL);
            value = SymmetricUtils.substituteScripts(value, getReplacementValues());
            if (value != null) {
                value = value.trim();
            }
            registrationUrl = value;
            if (log.isDebugEnabled()) {
                log.debug("Registration URL eval results in: {}", registrationUrl);
            }
        }
        return registrationUrl;
    }

    public String getEngineName() {
        if (engineName == null) {
            String value = getString(ParameterConstants.ENGINE_NAME, "SymmetricDS");
            value = SymmetricUtils.substituteScripts(value, getReplacementValues());
            engineName = value;
            if (log.isDebugEnabled()) {
                log.debug("Engine Name eval results in: {}", engineName);
            }
        }
        return engineName;
    }

    public Map<String, String> getReplacementValues() {
        Map<String, String> replacementValues = new HashMap<String, String>(2);
        replacementValues.put("nodeGroupId", nodeGroupId);
        replacementValues.put("externalId", externalId);
        replacementValues.put("engineName", engineName);
        replacementValues.put("syncUrl", syncUrl);
        replacementValues.put("registrationUrl", registrationUrl);
        return replacementValues;
    }

    public synchronized void setDatabaseHasBeenInitialized(boolean databaseHasBeenInitialized) {
        if (this.databaseHasBeenInitialized != databaseHasBeenInitialized) {
            this.databaseHasBeenInitialized = databaseHasBeenInitialized;
            this.parameters = null;
        }
    }

    public synchronized void setDatabaseHasBeenSetup(boolean databaseHasBeenSetup) {
        this.databaseHasBeenSetup = databaseHasBeenSetup;
    }

    public synchronized boolean hasDatabaseBeenSetup() {
        return databaseHasBeenSetup;
    }

    abstract public TypedProperties getDatabaseParameters(String externalId, String nodeGroupId);

    protected synchronized TypedProperties rereadDatabaseParameters(Properties p) {
        if (databaseHasBeenInitialized) {
            TypedProperties properties = getDatabaseParameters(ParameterConstants.ALL,
                    ParameterConstants.ALL);
            properties.putAll(getDatabaseParameters(ParameterConstants.ALL,
                    p.getProperty(ParameterConstants.NODE_GROUP_ID)));
            properties.putAll(getDatabaseParameters(
                    p.getProperty(ParameterConstants.EXTERNAL_ID),
                    p.getProperty(ParameterConstants.NODE_GROUP_ID)));
            return properties;
        } else {
            return new TypedProperties();
        }
    }

    public void setExtensionService(IExtensionService extensionService) {
        this.extensionService = extensionService;
    }
}