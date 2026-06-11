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

public class ClusterPeerStateMessage extends ClusterPeerSecureMessage {
    private static final long serialVersionUID = 1L;
    private transient Type cachedType;
    private transient String cachedInstanceId;
    private transient boolean decrypted;

    public ClusterPeerStateMessage(Type type, String serverId, String instanceId, String version) {
        this(type, serverId, instanceId, version, System.currentTimeMillis());
    }

    private ClusterPeerStateMessage(Type type, String serverId, String instanceId, String version, long timestamp) {
        super(serverId, version, timestamp, type.name() + "|" + instanceId);
        this.cachedType = type;
        this.cachedInstanceId = instanceId;
        this.decrypted = true;
    }

    private void ensureDecrypted() {
        if (!decrypted) {
            String payload = decryptPayload();
            String[] parts = payload.split("\\|", 2);
            cachedType = Type.valueOf(parts[0]);
            cachedInstanceId = parts[1];
            decrypted = true;
        }
    }

    public Type getType() {
        ensureDecrypted();
        return cachedType;
    }

    public String getInstanceId() {
        ensureDecrypted();
        return cachedInstanceId;
    }
}
