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
import org.jumpmind.symmetric.model.StartupParameter.Source;

/**
 * Supplies startup parameter values from a store outside the properties files, environment and JVM, such as a file written by another process, and reports when
 * that store changes so {@link IStartupParameterService} can pick up the new values without a restart. Callers invoke {@link #refresh(TypedProperties)} before
 * {@link #getParameters()}.
 */
public interface IStartupParameterSource {
    /**
     * The origin recorded against every parameter this source supplies.
     */
    Source getSource();

    /**
     * Re-reads the backing store, locating it from {@code startupProperties}, the engine's properties as resolved from its files, environment variables and JVM
     * system properties. Returns true if the values this source supplies changed since the previous call. Must not throw.
     */
    boolean refresh(TypedProperties startupProperties);

    /**
     * The values as of the last {@link #refresh(TypedProperties)}, keyed by parameter name. Empty when the backing store is unavailable.
     */
    Map<String, String> getParameters();
}
