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
package org.jumpmind.symmetric;

import java.util.Map;

public interface IApplicationHealthTracker {
    boolean isAlive();

    void setAlive(boolean alive);

    public Map<String, Boolean> getReadinessMap();

    void setEngineReadiness(String engineName, boolean ready);

    void stopTrackingEngine(String engineName);

    boolean isEngineReady(String engineName);

    boolean isReady();

    /**
     * Marks this JVM and all its tracked engines as not ready, for use immediately before process termination so health checks (e.g. a Kubernetes readiness
     * probe) see the failure right away rather than waiting for the process to actually die.
     */
    void onShutdown();
}
