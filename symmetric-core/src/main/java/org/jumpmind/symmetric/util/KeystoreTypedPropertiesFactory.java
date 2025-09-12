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
package org.jumpmind.symmetric.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.Strings;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeystoreTypedPropertiesFactory extends TypedPropertiesFactory {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private List<String> keystoreParametersList;
    private Map<String, ParameterMetaData> parameterMetaData = ParameterConstants.getParameterMetaData();
    private ISecurityService securityService = SecurityServiceFactory.create();
    private String engineName;

    public void init(File propertiesFile, Properties properties) {
        super.init(propertiesFile, properties);
        String keystoreParameters = System.getProperty(SystemConstants.SYSPROP_KEYSTORE_PARAMETERS);
        if (keystoreParameters != null && keystoreParameters.length() > 0) {
            keystoreParametersList = Arrays.asList(keystoreParameters.split(","));
        }
        if (keystoreParametersList != null) {
            for (String param : keystoreParametersList) {
                ParameterMetaData metaData = parameterMetaData.get(param.trim());
                if (metaData != null && metaData.isDatabaseOverridable()) {
                    log.error("Parameter {} is set up as a keystore parameter, but only startup parameters can be configured that way");
                    throw new IllegalStateException("Parameter " + param
                            + " is set up as a keystore parameter, but only startup parameters can be configured that way.");
                }
            }
        }
    }

    @Override
    public TypedProperties reload() {
        TypedProperties typedProperties = super.reload();
        if (typedProperties.containsKey(ParameterConstants.ENGINE_NAME)) {
            this.engineName = typedProperties.get(ParameterConstants.ENGINE_NAME);
        }
        return getKeystoreValues(typedProperties);
    }

    @Override
    public TypedProperties reload(File propFile) {
        TypedProperties typedProperties = super.reload(propFile);
        if (typedProperties.containsKey(ParameterConstants.ENGINE_NAME)) {
            this.engineName = typedProperties.get(ParameterConstants.ENGINE_NAME);
        }
        return getKeystoreValues(typedProperties);
    }

    @Override
    public TypedProperties reload(Properties properties) {
        TypedProperties typedProperties = super.reload(properties);
        if (typedProperties.containsKey(ParameterConstants.ENGINE_NAME)) {
            this.engineName = typedProperties.get(ParameterConstants.ENGINE_NAME).toString();
        }
        return typedProperties;
    }

    @Override
    public void save(Properties properties, File propFile, String comments) throws IOException {
        for (Object key : properties.keySet()) {
            String keyString = key == null ? "" : key.toString();
            if (isParameterSavedInKeystore(keyString)) {
                Object value = properties.get(key);
                String valueString = value == null ? "" : value.toString();
                String keystoreKey = getKeystoreParameterKey(keyString);
                if (valueString.length() > 0) {
                    try {
                        securityService.setKeystoreEntry(keystoreKey, valueString);
                        valueString = SecurityConstants.PREFIX_KEYSTORE_STORAGE + keystoreKey;
                        properties.put(key, valueString);
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    try {
                        securityService.deleteKeystoreEntry(keystoreKey);
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        super.save(properties, propFile, comments);
    }

    private TypedProperties getKeystoreValues(TypedProperties typedProperties) {
        for (Object key : typedProperties.keySet()) {
            String keyString = key == null ? "" : key.toString();
            if (isParameterSavedInKeystore(keyString)) {
                Object value = typedProperties.get(key);
                String valueString = value == null ? "" : value.toString();
                if (Strings.CS.startsWith(valueString, SecurityConstants.PREFIX_KEYSTORE_STORAGE)) {
                    String keystoreKey = getKeystoreParameterKey(keyString);
                    try {
                        valueString = securityService.getKeystoreEntry(keystoreKey);
                        typedProperties.put(key, valueString);
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        return typedProperties;
    }

    private boolean isParameterSavedInKeystore(String key) {
        if (keystoreParametersList != null) {
            if (keystoreParametersList.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String getKeystoreParameterKey(String key) {
        return (engineName != null ? engineName + "." : "") + key;
    }
}
