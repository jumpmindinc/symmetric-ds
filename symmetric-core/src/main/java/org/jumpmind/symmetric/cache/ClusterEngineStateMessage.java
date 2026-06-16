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
package org.jumpmind.symmetric.cache;

/**
 * Per-engine cluster status message transmitted via the SYM_CLUSTER_ENGINES JCS region. Decoupled from JVM-level peer status so that multiple engines sharing a
 * JVM can each broadcast independent states (e.g. engine A upgrading while engine B is online).
 */
public class ClusterEngineStateMessage extends ClusterPeerSecureMessage {
    private static final long serialVersionUID = 1L;
    public static final String ENGINE_STARTING = "ENGINE_STARTING";
    public static final String ENGINE_UPGRADING_DB = "ENGINE_UPGRADING_DB";
    public static final String ENGINE_ONLINE = "ENGINE_ONLINE";
    public static final String ENGINE_OFFLINE = "ENGINE_OFFLINE";
    private transient String cachedEngineState;
    private transient String cachedEngineName;

    public ClusterEngineStateMessage(String engineState, String engineName,
            String serverId, String instanceId, String version) {
        this(engineState, engineName, serverId, instanceId, version, System.currentTimeMillis());
    }

    private ClusterEngineStateMessage(String engineState, String engineName,
            String serverId, String instanceId, String version, long timestamp) {
        super(serverId, version, timestamp, engineState + "|" + engineName);
        this.cachedEngineState = engineState;
        this.cachedEngineName = engineName;
        markDecrypted();
    }

    @Override
    protected void parsePayload(String plainPayload) {
        String[] parts = plainPayload.split("\\|", 2);
        cachedEngineState = parts[0];
        cachedEngineName = parts.length > 1 ? parts[1] : "";
    }

    @Override
    public String getEventType() {
        ensureDecrypted();
        return cachedEngineState;
    }

    public String getEngineState() {
        ensureDecrypted();
        return cachedEngineState;
    }

    public String getEngineName() {
        ensureDecrypted();
        return cachedEngineName;
    }
}
