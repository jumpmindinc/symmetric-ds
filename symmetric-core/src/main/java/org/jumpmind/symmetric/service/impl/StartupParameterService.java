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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.model.StartupParameter;
import org.jumpmind.symmetric.model.StartupParameter.Source;
import org.jumpmind.symmetric.model.StartupParameter.Type;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.jumpmind.symmetric.util.TypedPropertiesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidates resolution of parameters needed before a database connection exists. Wraps the existing precedence logic in
 * {@link org.jumpmind.symmetric.util.TypedPropertiesFactory} (files < environment variables < JVM system properties) rather than replacing it, and records
 * which layer won for each parameter so that resolution isn't silently re-derived by multiple classes.
 */
public class StartupParameterService implements IStartupParameterService {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * "options" isn't a real system property; it's the value of the SYM_OPTIONS environment variable (set by setenv/setenv.bat) collected as a single composite
     * key by {@link TypedProperties#collectFrom}. By default it embeds "-Djavax.net.ssl.keyStorePassword=...", so it must be treated as sensitive.
     */
    private static final String SYM_OPTIONS_PARAMETER_KEY = "options";
    private static final Set<String> SENSITIVE_SYSTEM_PROPERTY_KEYS = Set.of(
            SecurityConstants.SYSPROP_KEYSTORE_PASSWORD,
            SecurityConstants.SYSPROP_TRUSTSTORE_PASSWORD,
            SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED,
            SecurityConstants.ALIAS_SYM_SECRET_KEY,
            SYM_OPTIONS_PARAMETER_KEY);
    private static final Map<String, String> ENV_VAR_NAMES_BY_PARAMETER = reverse(ServerConstants.JVM_IMPORT_ENV_VARS);
    private final Map<String, StartupParameter> parameters = new ConcurrentHashMap<>();
    private final Map<String, Source> knownFileSources;
    private final Map<String, ParameterMetaData> parameterMetaData;
    private ITypedPropertiesFactory propertiesFactory;
    private TypedProperties mergedProperties;
    private TypedProperties jvmProperties;
    private Map<String, String> environmentVariables;

    public StartupParameterService(TypedProperties mergedProperties, TypedProperties jvmProperties,
            Map<String, String> environmentVariables, Map<String, Source> knownFileSources) {
        this(mergedProperties, jvmProperties, environmentVariables, knownFileSources, Map.of());
    }

    public StartupParameterService(TypedProperties mergedProperties, TypedProperties jvmProperties,
            Map<String, String> environmentVariables, Map<String, Source> knownFileSources,
            Map<String, ParameterMetaData> supplementalParameterMetaData) {
        this.mergedProperties = mergedProperties;
        this.jvmProperties = jvmProperties;
        this.environmentVariables = environmentVariables;
        this.knownFileSources = knownFileSources;
        this.parameterMetaData = mergeParameterMetaData(supplementalParameterMetaData);
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(this.mergedProperties);
        resolveAll();
    }

    public StartupParameterService(TypedProperties mergedProperties) {
        this(mergedProperties, new TypedProperties(System.getProperties()), System.getenv(), Map.of());
    }

    public StartupParameterService(ITypedPropertiesFactory propertiesFactory, Map<String, Source> knownFileSources,
            Map<String, ParameterMetaData> supplementalParameterMetaData) {
        this(propertiesFactory.reload(), new TypedProperties(System.getProperties()), System.getenv(), knownFileSources,
                supplementalParameterMetaData);
        this.propertiesFactory = propertiesFactory;
    }

    private static Map<String, ParameterMetaData> mergeParameterMetaData(Map<String, ParameterMetaData> supplementalParameterMetaData) {
        if (supplementalParameterMetaData.isEmpty()) {
            return ParameterConstants.getParameterMetaData();
        }
        Map<String, ParameterMetaData> mergedParameterMetaData = new HashMap<>(ParameterConstants.getParameterMetaData());
        mergedParameterMetaData.putAll(supplementalParameterMetaData);
        return mergedParameterMetaData;
    }

    private static Map<String, String> reverse(Map<String, String> envVarNameToParameter) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : envVarNameToParameter.entrySet()) {
            result.put(entry.getValue(), entry.getKey());
        }
        return result;
    }

    private void resolveAll() {
        for (String key : mergedProperties.stringPropertyNames()) {
            StartupParameter parameter = resolve(key);
            if (isTrackable(parameter)) {
                parameters.put(key, parameter);
            }
        }
    }

    private boolean isTrackable(StartupParameter parameter) {
        ParameterMetaData metaData = parameterMetaData.get(parameter.name());
        boolean isRuntimeParameter = metaData != null && metaData.isDatabaseOverridable();
        return !isRuntimeParameter || parameter.source() != Source.DEFAULT;
    }

    private StartupParameter resolve(String key) {
        String rawValue = resolveRawValue(key);
        ParameterMetaData metaData = parameterMetaData.get(key);
        String defaultValue = metaData != null ? substituteTokensInDefault(key, metaData.getDefaultValue()) : null;
        Type type = inferType(metaData, Objects.toString(rawValue, defaultValue));
        Source source = determineSource(key, rawValue, defaultValue);
        return new StartupParameter(key, type, rawValue, defaultValue, source);
    }

    private String resolveRawValue(String key) {
        String rawValue = mergedProperties.getProperty(key);
        if (rawValue != null) {
            return rawValue;
        }
        rawValue = jvmProperties.getProperty(key);
        if (rawValue != null) {
            return rawValue;
        }
        return environmentVariables.get(findEnvVarName(key));
    }

    private String substituteTokensInDefault(String key, String defaultValue) {
        if (defaultValue == null || !defaultValue.contains("$(")) {
            return defaultValue;
        }
        TypedProperties copiedProperties = mergedProperties.copy();
        copiedProperties.setProperty(key, defaultValue);
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(copiedProperties);
        return copiedProperties.getProperty(key);
    }

    private Source determineSource(String key, String rawValue, String defaultValue) {
        if (rawValue == null) {
            return Source.DEFAULT;
        }
        if (rawValue.equals(jvmProperties.getProperty(key))) {
            return Source.JVM_SYSTEM_PROPERTY;
        }
        String envVarName = findEnvVarName(key);
        if (rawValue.equals(environmentVariables.get(envVarName))) {
            return Source.ENVIRONMENT_VARIABLE;
        }
        if (rawValue.equals(defaultValue)) {
            return Source.DEFAULT;
        }
        Source knownSource = knownFileSources.get(key);
        return knownSource != null ? knownSource : Source.SYMMETRIC_PROPERTIES_FILE;
    }

    private String findEnvVarName(String key) {
        String explicit = ENV_VAR_NAMES_BY_PARAMETER.get(key);
        if (explicit != null) {
            return explicit;
        }
        return ServerConstants.SYM_ENV_PREFIX + key.toUpperCase().replace('.', '_');
    }

    private Type inferType(ParameterMetaData metaData, String value) {
        if (metaData != null) {
            if (metaData.isBooleanType()) {
                return Type.BOOLEAN;
            }
            if (metaData.isIntType()) {
                return Type.INT;
            }
            return Type.STRING;
        }
        return inferTypeFromValue(value);
    }

    private Type inferTypeFromValue(String value) {
        if (StringUtils.isBlank(value)) {
            return Type.STRING;
        }
        String trimmedValue = value.trim();
        if (Strings.CI.equalsAny(trimmedValue, "true", "false")) {
            return Type.BOOLEAN;
        }
        if (trimmedValue.matches("-?\\d+")) {
            return Type.INT;
        }
        if (trimmedValue.matches("-?\\d*\\.\\d+")) {
            return Type.DOUBLE;
        }
        return Type.STRING;
    }

    private boolean isSensitive(String key) {
        if (SENSITIVE_SYSTEM_PROPERTY_KEYS.contains(key) || ArrayUtils.contains(ParameterConstants.REDACTED_PROPERTIES, key)) {
            return true;
        }
        ParameterMetaData metaData = parameterMetaData.get(key);
        return metaData != null && metaData.isEncryptedType();
    }

    @Override
    public String getString(String key) {
        return getString(key, null);
    }

    @Override
    public String getString(String key, String defaultValue) {
        String value = getParameter(key).asString();
        return StringUtils.defaultIfBlank(value, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            TypedProperties.logPropertiesException(log, key, value);
            return defaultValue;
        }
    }

    @Override
    public boolean is(String key, boolean defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        return value.equals("1") || Boolean.parseBoolean(value);
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            TypedProperties.logPropertiesException(log, key, value);
            return defaultValue;
        }
    }

    @Override
    public StartupParameter getParameter(String key) {
        StartupParameter parameter = parameters.get(key);
        if (parameter != null) {
            return parameter;
        }
        parameter = resolve(key);
        if (isTrackable(parameter)) {
            parameters.put(key, parameter);
        }
        return parameter;
    }

    @Override
    public Map<String, StartupParameter> getAllParameters() {
        return Collections.unmodifiableMap(new TreeMap<>(parameters));
    }

    @Override
    public TypedProperties asTypedProperties() {
        TypedProperties combinedProperties = jvmProperties.copy();
        combinedProperties.putAll(mergedProperties);
        return combinedProperties;
    }

    @Override
    public synchronized boolean refresh() {
        if (propertiesFactory == null) {
            return false;
        }
        TypedProperties reloadedProperties = propertiesFactory.reload();
        boolean isChanged = !reloadedProperties.equals(mergedProperties);
        this.mergedProperties = reloadedProperties;
        this.jvmProperties = new TypedProperties(System.getProperties());
        this.environmentVariables = System.getenv();
        parameters.clear();
        resolveAll();
        return isChanged;
    }

    @Override
    public synchronized void refreshSystemProperty(String key) {
        String value = System.getProperty(key);
        jvmProperties.setProperty(key, value);
        mergedProperties.setProperty(key, value);
        parameters.put(key, resolve(key));
    }

    @Override
    public String dumpAsText() {
        StringBuilder text = new StringBuilder("Startup Parameters:").append(System.lineSeparator());
        for (Entry<String, StartupParameter> entry : getAllParameters().entrySet()) {
            String key = entry.getKey();
            StartupParameter parameter = entry.getValue();
            boolean isSensitive = isSensitive(key);
            String value = isSensitive ? ParameterConstants.REDACTED : parameter.asString();
            String defaultValue = isSensitive ? ParameterConstants.REDACTED : parameter.defaultValue();
            text.append(key).append('=').append(value)
                    .append(" [source=").append(parameter.source())
                    .append(", default=").append(defaultValue)
                    .append(']').append(System.lineSeparator());
        }
        return text.toString();
    }
}
