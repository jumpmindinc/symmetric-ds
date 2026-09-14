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
package org.jumpmind.symmetric.cache;

public class ClusterServerStatusMessage extends ClusterPlainMessage {
    private static final long serialVersionUID = 1L;
    public static final String EVENT_PEER_JOINING = "PEER_JOINING";
    public static final String EVENT_PEER_HEARTBEAT = "PEER_HEARTBEAT";
    public static final String EVENT_PEER_LEAVING = "PEER_LEAVING";
    public static final String EVENT_PEER_INITIALIZING = "PEER_INITIALIZING";
    public static final String EVENT_PEER_DISCOVERY = "PEER_DISCOVERY";
    private final long startTimeMs;
    private transient String serverStatus;

    public ClusterServerStatusMessage(ClusterPeerServerState status, String serverId, String clusterPartitionId, long startTimeMs) {
        this(status.getValue(), serverId, clusterPartitionId, startTimeMs, System.currentTimeMillis());
    }

    public ClusterServerStatusMessage(String status, String serverId, String clusterPartitionId, long startTimeMs) {
        this(status, serverId, clusterPartitionId, startTimeMs, System.currentTimeMillis());
    }

    /**
     * Used by ClusterMessageConverter when reconstructing a message from a received/cached secure envelope, passing the envelope's own timestamp so staleness
     * reflects when the peer actually sent it, not when this JVM happened to decode it.
     */
    ClusterServerStatusMessage(String status, String serverId, String clusterPartitionId, long startTimeMs, long timestamp) {
        super(serverId, clusterPartitionId, timestamp);
        this.startTimeMs = startTimeMs;
        this.serverStatus = status;
    }

    @Override
    public String getEventType() {
        return serverStatus;
    }

    public String getStatus() {
        return serverStatus;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }
}
