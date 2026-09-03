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
 * <p>
 * A single JVM-wide instance ({@link #getInstance()}) holds one resolved parameter set per registered engine name, plus one under {@link #GLOBAL_ENGINE_NAME}
 * for parameters resolved before any specific engine exists. This avoids the divergence that comes from multiple independent instances each taking their own
 * snapshot of JVM system properties/environment variables at different points in time.
 */
public class StartupParameterService implements IStartupParameterService {
    protected static final Logger log = LoggerFactory.getLogger(StartupParameterService.class);
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
    private static final StartupParameterService INSTANCE = new StartupParameterService();
    private final Map<String, EngineParameters> parametersByEngine = new ConcurrentHashMap<>();
    private final TypedProperties jvmProperties = new TypedProperties(System.getProperties());
    private final Map<String, String> environmentVariables = System.getenv();

    private StartupParameterService() {
    }

    public static IStartupParameterService getInstance() {
        return INSTANCE;
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

    @Override
    public TypedProperties registerEngine(ITypedPropertiesFactory propertiesFactory, Map<String, Source> knownFileSources,
            Map<String, ParameterMetaData> supplementalParameterMetaData) {
        TypedProperties mergedProperties = propertiesFactory.reload();
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(mergedProperties);
        String engineName = mergedProperties.get(ParameterConstants.ENGINE_NAME);
        EngineParameters engineParameters = new EngineParameters(engineName, mergedProperties, knownFileSources,
                mergeParameterMetaData(supplementalParameterMetaData), propertiesFactory);
        resolveAll(engineParameters);
        parametersByEngine.put(engineName, engineParameters);
        return asTypedProperties(engineParameters);
    }

    @Override
    public void registerGlobal(TypedProperties mergedProperties, Map<String, Source> knownFileSources) {
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(mergedProperties);
        EngineParameters engineParameters = new EngineParameters(GLOBAL_ENGINE_NAME, mergedProperties, knownFileSources,
                ParameterConstants.getParameterMetaData(), null);
        resolveAll(engineParameters);
        parametersByEngine.put(GLOBAL_ENGINE_NAME, engineParameters);
    }

    @Override
    public void unregisterEngine(String engineName) {
        parametersByEngine.remove(engineName);
    }

    private void resolveAll(EngineParameters engineParameters) {
        for (String key : engineParameters.mergedProperties.stringPropertyNames()) {
            engineParameters.parameters.put(key, resolve(engineParameters, key));
        }
    }

    private StartupParameter resolve(EngineParameters engineParameters, String key) {
        String rawValue = resolveRawValue(engineParameters, key);
        ParameterMetaData metaData = engineParameters.parameterMetaData.get(key);
        String defaultValue = metaData != null ? substituteTokensInDefault(engineParameters, key, metaData.getDefaultValue()) : null;
        Type type = inferType(metaData, Objects.toString(rawValue, defaultValue));
        Source source = determineSource(engineParameters, key, rawValue, defaultValue);
        boolean isSensitive = isSensitive(key, metaData);
        return new StartupParameter(engineParameters.engineName, key, type, rawValue, defaultValue, source, isSensitive);
    }

    private String resolveRawValue(EngineParameters engineParameters, String key) {
        String rawValue = engineParameters.mergedProperties.getProperty(key);
        if (rawValue != null) {
            return rawValue;
        }
        rawValue = jvmProperties.getProperty(key);
        if (rawValue != null) {
            return rawValue;
        }
        return environmentVariables.get(findEnvVarName(key));
    }

    private String substituteTokensInDefault(EngineParameters engineParameters, String key, String defaultValue) {
        if (defaultValue == null || !defaultValue.contains("$(")) {
            return defaultValue;
        }
        TypedProperties copiedProperties = engineParameters.mergedProperties.copy();
        copiedProperties.setProperty(key, defaultValue);
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(copiedProperties);
        return copiedProperties.getProperty(key);
    }

    private Source determineSource(EngineParameters engineParameters, String key, String rawValue, String defaultValue) {
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
        Source knownSource = engineParameters.knownFileSources.get(key);
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

    private boolean isSensitive(String key, ParameterMetaData metaData) {
        if (SENSITIVE_SYSTEM_PROPERTY_KEYS.contains(key) || ArrayUtils.contains(ParameterConstants.REDACTED_PROPERTIES, key)) {
            return true;
        }
        return metaData != null && metaData.isEncryptedType();
    }

    private boolean isNoisyDefaultedDatabaseParameter(EngineParameters engineParameters, StartupParameter parameter) {
        ParameterMetaData metaData = engineParameters.parameterMetaData.get(parameter.name());
        boolean isDatabaseParameter = metaData != null && metaData.isDatabaseOverridable();
        return isDatabaseParameter && parameter.source() == Source.DEFAULT;
    }

    @Override
    public String getString(String engineName, String key) {
        return getString(engineName, key, null);
    }

    @Override
    public String getString(String engineName, String key, String defaultValue) {
        StartupParameter parameter = getParameter(engineName, key);
        String value = parameter != null ? parameter.asString() : null;
        return StringUtils.defaultIfBlank(value, defaultValue);
    }

    @Override
    public int getInt(String engineName, String key, int defaultValue) {
        String value = getString(engineName, key, null);
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
    public boolean is(String engineName, String key, boolean defaultValue) {
        String value = getString(engineName, key, null);
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        return value.equals("1") || Boolean.parseBoolean(value);
    }

    @Override
    public double getDouble(String engineName, String key, double defaultValue) {
        String value = getString(engineName, key, null);
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
    public StartupParameter getParameter(String engineName, String key) {
        EngineParameters engineParameters = parametersByEngine.get(engineName);
        if (engineParameters == null) {
            return null;
        }
        StartupParameter parameter = engineParameters.parameters.get(key);
        if (parameter != null) {
            return parameter;
        }
        parameter = resolve(engineParameters, key);
        engineParameters.parameters.put(key, parameter);
        return parameter;
    }

    @Override
    public Map<String, StartupParameter> getAllParameters(String engineName) {
        EngineParameters engineParameters = parametersByEngine.get(engineName);
        if (engineParameters == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new TreeMap<>(engineParameters.parameters));
    }

    @Override
    public TypedProperties asTypedProperties(String engineName) {
        EngineParameters engineParameters = parametersByEngine.get(engineName);
        if (engineParameters == null) {
            return jvmProperties.copy();
        }
        return asTypedProperties(engineParameters);
    }

    private TypedProperties asTypedProperties(EngineParameters engineParameters) {
        TypedProperties combinedProperties = jvmProperties.copy();
        combinedProperties.putAll(engineParameters.mergedProperties);
        return combinedProperties;
    }

    @Override
    public synchronized boolean refresh(String engineName) {
        EngineParameters engineParameters = parametersByEngine.get(engineName);
        if (engineParameters == null || engineParameters.propertiesFactory == null) {
            return false;
        }
        TypedProperties reloadedProperties = engineParameters.propertiesFactory.reload();
        TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(reloadedProperties);
        boolean isChanged = !reloadedProperties.equals(engineParameters.mergedProperties);
        engineParameters.mergedProperties = reloadedProperties;
        engineParameters.parameters.clear();
        resolveAll(engineParameters);
        return isChanged;
    }

    @Override
    public synchronized void refreshSystemProperty(String key) {
        String value = System.getProperty(key);
        jvmProperties.setProperty(key, value);
        for (EngineParameters engineParameters : parametersByEngine.values()) {
            engineParameters.mergedProperties.setProperty(key, value);
            engineParameters.parameters.put(key, resolve(engineParameters, key));
        }
    }

    @Override
    public String dumpAsText(String engineName) {
        StringBuilder text = new StringBuilder("Startup Parameters:").append(System.lineSeparator());
        EngineParameters engineParameters = parametersByEngine.get(engineName);
        if (engineParameters == null) {
            return text.toString();
        }
        for (Entry<String, StartupParameter> entry : getAllParameters(engineName).entrySet()) {
            String key = entry.getKey();
            StartupParameter parameter = entry.getValue();
            if (isNoisyDefaultedDatabaseParameter(engineParameters, parameter)) {
                continue;
            }
            String value = parameter.isSensitive() ? ParameterConstants.REDACTED : parameter.asString();
            String defaultValue = parameter.isSensitive() ? ParameterConstants.REDACTED : parameter.defaultValue();
            text.append(key).append('=').append(value)
                    .append(" [source=").append(parameter.source())
                    .append(", default=").append(defaultValue)
                    .append(']').append(System.lineSeparator());
        }
        return text.toString();
    }

    @Override
    public String getGlobalString(String key) {
        return getString(GLOBAL_ENGINE_NAME, key);
    }

    @Override
    public String getGlobalString(String key, String defaultValue) {
        return getString(GLOBAL_ENGINE_NAME, key, defaultValue);
    }

    @Override
    public int getGlobalInt(String key, int defaultValue) {
        return getInt(GLOBAL_ENGINE_NAME, key, defaultValue);
    }

    @Override
    public boolean isGlobal(String key, boolean defaultValue) {
        return is(GLOBAL_ENGINE_NAME, key, defaultValue);
    }

    @Override
    public double getGlobalDouble(String key, double defaultValue) {
        return getDouble(GLOBAL_ENGINE_NAME, key, defaultValue);
    }

    @Override
    public StartupParameter getGlobalParameter(String key) {
        return getParameter(GLOBAL_ENGINE_NAME, key);
    }

    @Override
    public Map<String, StartupParameter> getGlobalParameters() {
        return getAllParameters(GLOBAL_ENGINE_NAME);
    }

    @Override
    public TypedProperties getGlobalTypedProperties() {
        return asTypedProperties(GLOBAL_ENGINE_NAME);
    }

    @Override
    public boolean refreshGlobal() {
        return refresh(GLOBAL_ENGINE_NAME);
    }

    @Override
    public String dumpGlobalAsText() {
        return dumpAsText(GLOBAL_ENGINE_NAME);
    }

    /**
     * Everything needed to resolve one engine's (or the global bucket's) parameters, other than the JVM/environment snapshot, which is shared across every
     * engine so that a single {@link #refreshSystemProperty(String)} call keeps them all consistent with each other.
     */
    private static final class EngineParameters {
        private final String engineName;
        private final Map<String, StartupParameter> parameters = new ConcurrentHashMap<>();
        private final Map<String, Source> knownFileSources;
        private final Map<String, ParameterMetaData> parameterMetaData;
        private final ITypedPropertiesFactory propertiesFactory;
        private TypedProperties mergedProperties;

        private EngineParameters(String engineName, TypedProperties mergedProperties, Map<String, Source> knownFileSources,
                Map<String, ParameterMetaData> parameterMetaData, ITypedPropertiesFactory propertiesFactory) {
            this.engineName = engineName;
            this.mergedProperties = mergedProperties;
            this.knownFileSources = knownFileSources;
            this.parameterMetaData = parameterMetaData;
            this.propertiesFactory = propertiesFactory;
        }
    }
}
