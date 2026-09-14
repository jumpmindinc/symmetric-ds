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
package org.jumpmind.symmetric.util;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jumpmind.exception.IoException;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.CollectionUtils;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class TypedPropertiesFactory implements ITypedPropertiesFactory {
    private static final Logger log = LoggerFactory.getLogger(TypedPropertiesFactory.class);
    protected File propertiesFile;
    protected Properties properties;

    public TypedPropertiesFactory() {
    }

    @Override
    public void init(File propertiesFile, Properties properties) {
        this.propertiesFile = propertiesFile;
        this.properties = properties;
    }

    public File getPropertiesFile() {
        return propertiesFile;
    }

    @Override
    public TypedProperties reload() {
        TypedProperties fileProperties = loadPropertiesFromConfigLocations();
        mergeAndOverrideWithJvmAndEnvironmentVariables(fileProperties, true);
        return fileProperties;
    }

    public static TypedProperties getEnvironmentVariables() {
        return getEnvironmentVariables(System.getenv());
    }

    public static TypedProperties getEnvironmentVariables(Map<String, String> env) {
        TypedProperties properties = new TypedProperties();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            properties.put(entry.getKey(), entry.getValue());
        }
        return properties;
    }

    public static void mergeAndOverrideWithJvmAndEnvironmentVariables(TypedProperties fileProperties, boolean addMissingProperties) {
        mergeAndOverrideWithJvmAndEnvironmentVariables(fileProperties, addMissingProperties, getEnvironmentVariables(), new TypedProperties(System
                .getProperties()));
    }

    public static void mergeAndOverrideWithJvmAndEnvironmentVariables(TypedProperties fileProperties, boolean addMissingProperties,
            TypedProperties envProperties, TypedProperties jvmProperties) {
        TypedProperties otelEnvProperties = envProperties.renameKeysWithUnderscores(SymMetricConstants.OTEL_ENV_PREFIX);
        TypedProperties symEnvProperties = new TypedProperties();
        symEnvProperties.collectFrom(envProperties, ServerConstants.SYM_ENV_PREFIX, true);
        if (fileProperties.isEmpty() && symEnvProperties.isEmpty()) {
            throw new RuntimeException("Property files were not found");
        }
        if (addMissingProperties) {
            fileProperties.putAll(otelEnvProperties);
            fileProperties.putAll(symEnvProperties);
        } else {
            fileProperties.merge(otelEnvProperties);
            fileProperties.merge(symEnvProperties);
        }
        fileProperties.merge(jvmProperties);
        replaceSystemAndEnvironmentVariables(fileProperties);
        for (Map.Entry<String, String> override : ServerConstants.JVM_OVERRIDE_ENV_VARS.entrySet()) {
            String envVarName = override.getKey();
            envVarName = envVarName.substring(4).toLowerCase().replace('_', '.');
            String jvmPropertyName = override.getValue();
            if (symEnvProperties.containsKey(envVarName) && (addMissingProperties || fileProperties.containsKey(jvmPropertyName))) {
                fileProperties.put(jvmPropertyName, symEnvProperties.getProperty(envVarName));
            }
        }
        if (log.isDebugEnabled()) {
            otelEnvProperties.logAllKeys("OTelEnvProperties");
            symEnvProperties.logAllKeys("SymEnvProperties");
            fileProperties.logAllKeys("CombinedProperties");
        }
    }

    public static void importJvmEnvVars() {
        importJvmEnvVars(System.getenv());
    }

    public static void importJvmEnvVars(Map<String, String> env) {
        for (Map.Entry<String, String> entry : ServerConstants.JVM_IMPORT_ENV_VARS.entrySet()) {
            String value = env.get(entry.getKey());
            if (isNotBlank(value)) {
                System.setProperty(entry.getValue(), value);
            }
        }
    }

    public static final void replaceSystemAndEnvironmentVariables(Properties properties) {
        Set<Object> keys = new HashSet<Object>(properties.keySet());
        Map<String, String> env = System.getenv();
        Map<String, String> systemProperties = CollectionUtils.toMap(System.getProperties());
        for (Object object : keys) {
            String value = properties.getProperty((String) object);
            if (isNotBlank(value)) {
                value = FormatUtils.replaceTokens(value, env, true);
                value = FormatUtils.replaceTokens(value, systemProperties, true);
                if (value.contains("hostName")) {
                    value = FormatUtils.replace("hostName", AppUtils.getHostName(), value);
                }
                if (value.contains("portNumber")) {
                    value = FormatUtils.replace("portNumber", AppUtils.getPortNumber(), value);
                }
                if (value.contains("protocol")) {
                    value = FormatUtils.replace("protocol", AppUtils.getProtocol(), value);
                }
                if (value.contains("ipAddress")) {
                    value = FormatUtils.replace("ipAddress", AppUtils.getIpAddress(), value);
                }
                if (value.contains("engineName")) {
                    value = FormatUtils.replace("engineName", properties.getProperty(ParameterConstants.ENGINE_NAME, ""), value);
                }
                if (value.contains("nodeGroupId")) {
                    value = FormatUtils.replace("nodeGroupId", properties.getProperty(ParameterConstants.NODE_GROUP_ID, ""), value);
                }
                if (value.contains("externalId")) {
                    value = FormatUtils.replace("externalId", properties.getProperty(ParameterConstants.EXTERNAL_ID, ""), value);
                }
                if (value.contains("syncUrl")) {
                    value = FormatUtils.replace("syncUrl", properties.getProperty(ParameterConstants.SYNC_URL, ""), value);
                }
                if (value.contains("registrationUrl")) {
                    value = FormatUtils.replace("registrationUrl", properties.getProperty(ParameterConstants.REGISTRATION_URL, ""), value);
                }
                properties.put(object, value);
            }
        }
    }

    private TypedProperties loadPropertiesFromConfigLocations() {
        PropertiesFactoryBean factoryBean = new PropertiesFactoryBean();
        factoryBean.setIgnoreResourceNotFound(true);
        factoryBean.setLocalOverride(true);
        factoryBean.setSingleton(false);
        factoryBean.setProperties(properties);
        factoryBean.setLocations(buildLocations(propertiesFile));
        try {
            TypedProperties properties = new TypedProperties(factoryBean.getObject());
            return properties;
        } catch (FileNotFoundException e) {
            return new TypedProperties();
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    @Override
    public TypedProperties reload(File propFile) {
        TypedProperties typedProperties = new TypedProperties(propFile);
        return typedProperties;
    }

    @Override
    public TypedProperties reload(Properties properties) {
        TypedProperties typedProperties = new TypedProperties(properties);
        return typedProperties;
    }

    @Override
    public void save(Properties props, File propFile, String comments) throws IOException {
        try (FileOutputStream os = new FileOutputStream(propFile)) {
            props.store(os, comments);
        }
    }

    protected Resource[] buildLocations(File propertiesFile) {
        /*
         * System properties always override the properties found in these files. System properties are merged in the parameter service.
         */
        List<Resource> resources = new ArrayList<Resource>();
        resources.add(new ClassPathResource("/symmetric-default.properties"));
        resources.add(new ClassPathResource("/symmetric-console-default.properties"));
        resources.add(new FileSystemResource(AppUtils.getSymHome() + "/conf/symmetric.properties"));
        resources.add(new ClassPathResource("/symmetric.properties"));
        resources.add(new ClassPathResource("/symmetric-console-default.properties"));
        resources.add(new ClassPathResource("/symmetric-override.properties"));
        if (propertiesFile != null && propertiesFile.exists()) {
            resources.add(new FileSystemResource(propertiesFile.getAbsolutePath()));
        }
        return resources.toArray(new Resource[resources.size()]);
    }
}
