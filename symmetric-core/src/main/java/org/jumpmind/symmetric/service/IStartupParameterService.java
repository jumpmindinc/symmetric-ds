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

import java.util.Map;

import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.model.StartupParameter;
import org.jumpmind.symmetric.model.StartupParameter.Source;

/**
 * A single JVM-wide service that resolves and provides read-only, typed access to parameters needed before a database connection exists (JVM system properties,
 * environment variables, symmetric-server.properties, engine properties files), recording where each value came from so that resolution can be consolidated
 * instead of independently re-derived by multiple classes.
 * <p>
 * Parameters are partitioned by engine name, since a single JVM can host multiple engines, each with its own properties file. Parameters that are resolved
 * before any specific engine exists (e.g. {@code symmetric-server.properties}, cluster bootstrap) are stored under {@link #GLOBAL_ENGINE_NAME}. Every
 * engine-scoped method has a {@code getGlobal*}/{@code isGlobal}/{@code refreshGlobal}/{@code dumpGlobalAsText} counterpart that operates on that same global
 * bucket.
 */
public interface IStartupParameterService {
    String GLOBAL_ENGINE_NAME = "*";

    /**
     * Resolves an engine's startup parameters from its own properties file (via {@code propertiesFactory}) merged with JVM system properties and environment
     * variables, determines the engine's name from the resolved properties themselves, and stores the result under that name, replacing any prior registration
     * for the same name. Returns the resolved properties so callers that need them immediately (e.g. to register the JDBC driver or create the database
     * platform) don't need to already know the engine's name.
     */
    TypedProperties registerEngine(ITypedPropertiesFactory propertiesFactory, Map<String, Source> knownFileSources,
            Map<String, ParameterMetaData> supplementalParameterMetaData);

    /**
     * Resolves and stores process-wide startup parameters (e.g. from {@code symmetric-server.properties}) that exist before any specific engine does, under
     * {@link #GLOBAL_ENGINE_NAME}.
     */
    void registerGlobal(TypedProperties mergedProperties, Map<String, Source> knownFileSources);

    /**
     * Removes a previously registered engine's parameters, so a long-running process that stops/restarts an engine doesn't leak entries.
     */
    void unregisterEngine(String engineName);

    String getString(String engineName, String key);

    String getString(String engineName, String key, String defaultValue);

    int getInt(String engineName, String key, int defaultValue);

    boolean is(String engineName, String key, boolean defaultValue);

    double getDouble(String engineName, String key, double defaultValue);

    StartupParameter getParameter(String engineName, String key);

    Map<String, StartupParameter> getAllParameters(String engineName);

    /**
     * Migration bridge for legacy call sites that still take a {@link TypedProperties} rather than this service.
     */
    TypedProperties asTypedProperties(String engineName);

    /**
     * Re-resolves an engine's parameters from files/JVM/environment. Returns true if any previously resolved value changed.
     */
    boolean refresh(String engineName);

    /**
     * Re-resolves a single key against the live JVM system properties, across every registered engine (and the global bucket), for callers that just changed
     * one via {@code System.setProperty} after this service was already constructed (e.g. a CLI flag parsed after startup, such as {@code --storepass}). This
     * has no engine-name parameter because a JVM system property change is inherently process-wide.
     */
    void refreshSystemProperty(String key);

    /**
     * A human-readable dump of an engine's resolved parameters (name, value, source, default), with sensitive values masked. Intended for debug-level
     * diagnostic logging only. Omits a database-overridable parameter that's still sitting at its default value, since that's a database concern rather than a
     * startup concern.
     */
    String dumpAsText(String engineName);

    String getGlobalString(String key);

    String getGlobalString(String key, String defaultValue);

    int getGlobalInt(String key, int defaultValue);

    boolean isGlobal(String key, boolean defaultValue);

    double getGlobalDouble(String key, double defaultValue);

    StartupParameter getGlobalParameter(String key);

    Map<String, StartupParameter> getGlobalParameters();

    TypedProperties getGlobalTypedProperties();

    boolean refreshGlobal();

    String dumpGlobalAsText();
}
