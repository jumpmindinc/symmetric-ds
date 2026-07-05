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
    private final long startTimeMs;
    private transient String cachedEventType;

    public ClusterPeerStatusMessage(String eventType, String serverId, String clusterPartitionId, String version) {
        this(eventType, serverId, clusterPartitionId, version, System.currentTimeMillis(), 0L);
    }

    /**
     * @param startTimeMs
     *            when the sending JVM's cluster peer listener started, constant for that JVM's entire run (unlike timestamp, which is the send time of this
     *            specific message). Used to deterministically decide which of two unclustered peers sharing a database is the more recent duplicate. Callers
     *            that don't track a real start time may pass 0.
     */
    public ClusterPeerStatusMessage(String eventType, String serverId, String clusterPartitionId, String version, long startTimeMs) {
        this(eventType, serverId, clusterPartitionId, version, System.currentTimeMillis(), startTimeMs);
    }

    private ClusterPeerStatusMessage(String eventType, String serverId, String clusterPartitionId, String version, long timestamp, long startTimeMs) {
        super(serverId, clusterPartitionId, version, timestamp, eventType);
        this.startTimeMs = startTimeMs;
        this.cachedEventType = eventType;
        markDecrypted();
    }

    @Override
    protected void parsePayload(String plainPayload) {
        cachedEventType = plainPayload;
    }

    @Override
    public String getEventType() {
        ensureDecrypted();
        return cachedEventType;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }
}
