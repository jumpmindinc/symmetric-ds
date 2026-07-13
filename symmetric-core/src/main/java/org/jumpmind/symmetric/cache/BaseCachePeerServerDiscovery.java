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

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.jcs3.auxiliary.AuxiliaryCache;
import org.apache.commons.jcs3.auxiliary.lateral.LateralCacheNoWait;
import org.apache.commons.jcs3.auxiliary.lateral.LateralCacheNoWaitFacade;
import org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.LateralTCPCacheFactory;
import org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.TCPLateralCacheAttributes;
import org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.behavior.ITCPLateralCacheAttributes;
import org.apache.commons.jcs3.engine.control.CompositeCache;
import org.apache.commons.jcs3.engine.control.CompositeCacheManager;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryManager;
import org.apache.commons.jcs3.utils.discovery.UDPDiscoveryService;
import org.apache.commons.jcs3.utils.serialization.StandardSerializer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared base for cache-peer-server discovery mechanisms: wires a discovered peer's address directly into the running JCS lateral TCP cache's live connection
 * set (see {@link #addPeerConnection}), bypassing JCS's own UDP-discovery-listener plumbing entirely (that plumbing is only ever registered by
 * {@code LateralTCPCacheFactory.createDiscoveryService} when {@code UdpDiscoveryEnabled=true}, which SymmetricDS never sets — see JcsPropertiesBuilder).
 * Mode-specific subclasses (e.g. {@code NodeHostCachePeerServerDiscovery}) decide how/when {@link #announcePeer(String, String)} gets called; this class
 * handles the actual JCS wiring once it does.
 */
public class BaseCachePeerServerDiscovery implements ICachePeerServerDiscovery {
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
        DiscoveryContext ctx = context;
        if (ctx == null || ctx.jcsManager() == null) {
            log.debug("Skipping peer discovery announcement because JCS discovery is not started. serverId={}, address={}", serverId, address);
            return false;
        }
        String previousAddress = knownPeerAddresses.put(serverId, address);
        if (address.equals(previousAddress)) {
            return false;
        }
        if (previousAddress != null) {
            removePeerConnection(ctx, previousAddress);
        }
        addPeerConnection(ctx, address);
        log.info("Announced discovered peer for JCS lateral cache discovery. serverId={}, address={}, previousAddress={}", serverId, address, previousAddress);
        return true;
    }

    @Override
    public synchronized boolean retractPeer(String serverId) {
        String address = knownPeerAddresses.remove(serverId);
        if (address == null) {
            return false;
        }
        DiscoveryContext ctx = context;
        if (ctx != null) {
            removePeerConnection(ctx, address);
        }
        return true;
    }

    @Override
    public synchronized void stop() {
        discoveryService = null;
        context = null;
        knownPeerAddresses.clear();
    }

    public UDPDiscoveryService getUdpDiscoveryService() {
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

    /**
     * Adds a new outbound lateral TCP connection to {@code address} for every configured region, unless one already exists. Clones the region's currently
     * configured {@link ITCPLateralCacheAttributes}, points the clone at the peer's address, builds a {@link LateralCacheNoWait} from it via the region's
     * registered {@link LateralTCPCacheFactory}, and adds it directly to the running {@link LateralCacheNoWaitFacade} — exactly what
     * {@code LateralTCPDiscoveryListener.addDiscoveredService} does internally when UDP discovery is enabled, done here ourselves instead.
     */
    private void addPeerConnection(DiscoveryContext ctx, String address) {
        String tcpServer = toTcpServer(ctx, address);
        for (String regionName : ctx.regionNames()) {
            try {
                addPeerConnectionForRegion(ctx.jcsManager(), regionName, tcpServer);
            } catch (Exception ex) {
                log.warn("Failed to add lateral TCP peer connection. region=" + regionName + ", address=" + address, ex);
            }
        }
    }

    private void removePeerConnection(DiscoveryContext ctx, String address) {
        String tcpServer = toTcpServer(ctx, address);
        for (String regionName : ctx.regionNames()) {
            try {
                removePeerConnectionForRegion(ctx.jcsManager(), regionName, tcpServer);
            } catch (Exception ex) {
                log.warn("Failed to remove lateral TCP peer connection. region=" + regionName + ", address=" + address, ex);
            }
        }
    }

    /**
     * Peer addresses reported by callers (e.g. {@code AbstractSymmetricEngine.refreshClusterPeers}, which passes just SYM_NODE_HOST's bare IP address) don't
     * necessarily include a port, but JCS's {@code TcpServer} attribute requires "host:port". All cluster nodes share the same configured lateral TCP listener
     * port ({@link DiscoveryContext#port()} for this node), so an address with no port already present is assumed to listen on that same port.
     */
    private static String toTcpServer(DiscoveryContext ctx, String address) {
        return address.indexOf(':') >= 0 ? address : address + ":" + ctx.port();
    }

    @SuppressWarnings("unchecked")
    private <K, V> void addPeerConnectionForRegion(CompositeCacheManager jcsManager, String regionName, String tcpServer) throws Exception {
        CompositeCache<K, V> cache = jcsManager.getCache(regionName);
        LateralCacheNoWaitFacade<K, V> facade = getLateralFacade(cache);
        if (facade == null || facade.containsNoWait(tcpServer)) {
            return;
        }
        ITCPLateralCacheAttributes lca = (ITCPLateralCacheAttributes) facade.getAuxiliaryCacheAttributes().clone();
        lca.setTcpServer(tcpServer);
        LateralTCPCacheFactory factory = (LateralTCPCacheFactory) jcsManager.registryFacGet(JcsPropertiesBuilder.LATERAL_TCP_AUX_NAME);
        LateralCacheNoWait<K, V> noWait = factory.createCacheNoWait(lca, null, new StandardSerializer());
        factory.monitorCache(noWait);
        facade.addNoWait(noWait);
    }

    private <K, V> void removePeerConnectionForRegion(CompositeCacheManager jcsManager, String regionName, String tcpServer) {
        CompositeCache<K, V> cache = jcsManager.getCache(regionName);
        LateralCacheNoWaitFacade<K, V> facade = getLateralFacade(cache);
        if (facade != null) {
            facade.removeNoWait(tcpServer);
        }
    }

    @SuppressWarnings("unchecked")
    private <K, V> LateralCacheNoWaitFacade<K, V> getLateralFacade(CompositeCache<K, V> cache) {
        for (AuxiliaryCache<K, V> aux : cache.getAuxCacheList()) {
            if (aux instanceof LateralCacheNoWaitFacade) {
                return (LateralCacheNoWaitFacade<K, V>) aux;
            }
        }
        return null;
    }
}
