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

import java.util.Properties;

/**
 * Pluggable peer-server discovery for the JCS lateral cache transport. An implementation decides how peers become reachable: by contributing JCS properties
 * (enrichJcsProperties), by running an external discovery protocol started in start(), and/or by accepting externally-supplied addresses through announcePeer
 * (e.g. SYM_NODE_HOST rows).
 */
public interface ICachePeerServerDiscovery {
    /**
     * Contribute or override JCS lateral-auxiliary properties this mechanism needs, before the CompositeCacheManager is configured.
     *
     * @param jcsProperties
     *            the properties being assembled; mutate in place
     * @param lateralAuxAttributesPrefix
     *            key prefix for lateral-aux attributes, e.g. "jcs.auxiliary.LATERAL_TCP.attributes"
     */
    void enrichJcsProperties(Properties jcsProperties, String lateralAuxAttributesPrefix);

    /** Called once after JCS is configured and caches exist. May launch a background discovery protocol that feeds announcePeer(...). */
    void start(DiscoveryContext context);

    /** Registers/refreshes a peer's address for lateral discovery. Returns true if this changed the registration (new peer or changed address). */
    boolean announcePeer(String serverId, String address);

    /** Retracts any registration previously made for this serverId. Returns true if something was retracted. */
    boolean retractPeer(String serverId);

    /** Releases resources / stops the protocol. */
    void stop();
}
