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

public class ClusterPeerStatusMessage extends ClusterPeerSecureMessage {
    private static final long serialVersionUID = 1L;
    public static final String EVENT_PEER_JOINING = "PEER_JOINING";
    public static final String EVENT_PEER_HEARTBEAT = "PEER_HEARTBEAT";
    public static final String EVENT_PEER_LEAVING = "PEER_LEAVING";
    public static final String EVENT_PEER_INITIALIZING = "PEER_INITIALIZING";
    public static final String EVENT_PEER_UPGRADING_DB = "PEER_UPGRADING_DB";
    private transient String cachedEventType;
    private transient String cachedClusterPartitionId;

    public ClusterPeerStatusMessage(String eventType, String serverId, String clusterPartitionId, String version) {
        this(eventType, serverId, clusterPartitionId, version, System.currentTimeMillis());
    }

    private ClusterPeerStatusMessage(String eventType, String serverId, String clusterPartitionId, String version, long timestamp) {
        super(serverId, version, timestamp, eventType + "|" + clusterPartitionId);
        this.cachedEventType = eventType;
        this.cachedClusterPartitionId = clusterPartitionId;
        markDecrypted();
    }

    @Override
    protected void parsePayload(String plainPayload) {
        String[] parts = plainPayload.split("\\|", 2);
        cachedEventType = parts[0];
        cachedClusterPartitionId = parts[1];
    }

    @Override
    public String getEventType() {
        ensureDecrypted();
        return cachedEventType;
    }

    public String getClusterPartitionId() {
        ensureDecrypted();
        return cachedClusterPartitionId;
    }
}
