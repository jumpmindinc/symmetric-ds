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

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.model.StartupParameter;

/**
 * Resolves and provides read-only, typed access to parameters needed before a database connection exists (JVM system properties, environment variables,
 * symmetric-server.properties, engine properties files), recording where each value came from so that resolution can be consolidated instead of independently
 * re-derived by multiple classes.
 */
public interface IStartupParameterService {
    String getString(String key);

    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    boolean is(String key, boolean defaultValue);

    double getDouble(String key, double defaultValue);

    StartupParameter getParameter(String key);

    Map<String, StartupParameter> getAllParameters();

    /**
     * Migration bridge for legacy call sites that still take a {@link TypedProperties} rather than this service.
     */
    TypedProperties asTypedProperties();

    /**
     * Re-resolves from files/JVM/environment. Returns true if any previously resolved value changed.
     */
    boolean refresh();

    /**
     * Re-resolves a single key against the live JVM system properties, for callers that just changed one via {@code System.setProperty} after this service was
     * already constructed (e.g. a CLI flag parsed after startup, such as {@code --storepass}).
     */
    void refreshSystemProperty(String key);

    /**
     * A human-readable dump of every resolved parameter (name, value, source, default), with sensitive values masked. Intended for debug-level diagnostic
     * logging only.
     */
    String dumpAsText();
}
