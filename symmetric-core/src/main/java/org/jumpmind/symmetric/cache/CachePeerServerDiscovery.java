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

import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.TCPLateralCacheAttributes;
import org.apache.commons.jcs3.utils.discovery.DiscoveredService;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryManager;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryService;
import org.apache.commons.jcs3.utils.discovery.behavior.IDiscoveryListener;
import org.apache.commons.jcs3.utils.serialization.StandardSerializer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database-based discovery: mode "db". Populated by AbstractSymmetricEngine.refreshClusterPeers from SYM_NODE_HOST.
 */
public class CachePeerServerDiscovery implements ICachePeerServerDiscovery {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Map<String, String> knownPeerAddresses = new ConcurrentHashMap<>();
    protected volatile DiscoveryContext context;
    private volatile UDPDiscoveryService discoveryService;

    @Override
    public void enrichJcsProperties(Properties jcsProperties, String lateralAuxAttributesPrefix) {
    }

    @Override
    public synchronized void start(DiscoveryContext context) {
        this.context = context;
    }

    @Override
    public synchronized boolean announcePeer(String serverId, String address) {
        if (StringUtils.isBlank(address)) {
            return false;
        }
        UDPDiscoveryService service = getUdpDiscoveryService();
        if (service == null) {
            log.debug("Skipping peer discovery announcement because JCS discovery is unavailable. serverId={}, address={}", serverId, address);
            return false;
        }
        String previousAddress = knownPeerAddresses.put(serverId, address);
        if (address.equals(previousAddress)) {
            return false;
        }
        if (previousAddress != null) {
            retract(service, previousAddress);
        }
        announce(service, address);
        log.info("Announced discovered peer for JCS lateral cache discovery. serverId={}, address={}, previousAddress={}", serverId, address, previousAddress);
        return true;
    }

    @Override
    public synchronized boolean retractPeer(String serverId) {
        String address = knownPeerAddresses.remove(serverId);
        if (address == null) {
            return false;
        }
        UDPDiscoveryService service = getUdpDiscoveryService();
        if (service != null) {
            retract(service, address);
        }
        return true;
    }

    @Override
    public synchronized void stop() {
        discoveryService = null;
        context = null;
        knownPeerAddresses.clear();
    }

    protected UDPDiscoveryService getUdpDiscoveryService() {
        DiscoveryContext ctx = context;
        if (ctx == null || ctx.jcsManager() == null) {
            return null;
        }
        if (discoveryService == null) {
            try {
                TCPLateralCacheAttributes defaults = new TCPLateralCacheAttributes();
                discoveryService = UDPDiscoveryManager.getInstance().getService(defaults.getUdpDiscoveryAddr(), defaults.getUdpDiscoveryPort(),
                        null, ctx.port(), 0, ctx.jcsManager(), new StandardSerializer());
            } catch (Exception ex) {
                log.warn("Unable to access JCS UDP discovery service! serverId=" + ctx.serverId(), ex);
            }
        }
        return discoveryService;
    }

    private void announce(UDPDiscoveryService service, String address) {
        DiscoveredService ds = buildInjector(address);
        for (IDiscoveryListener listener : service.getCopyOfDiscoveryListeners()) {
            listener.addDiscoveredService(ds);
        }
    }

    private void retract(UDPDiscoveryService service, String address) {
        DiscoveredService ds = buildInjector(address);
        for (IDiscoveryListener listener : service.getCopyOfDiscoveryListeners()) {
            listener.removeDiscoveredService(ds);
        }
    }

    private DiscoveredService buildInjector(String address) {
        DiscoveredService ds = new DiscoveredService();
        ds.setServiceAddress(address);
        ds.setServicePort(context.port());
        ds.setCacheNames(new ArrayList<>(context.regionNames()));
        return ds;
    }
}
