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
 * software distributed under the LICENSE is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.cache;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.InitialSettings;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.RegionSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the Apache Commons JCS configuration properties for {@link JcsTcpCacheCoordinator}. Always configures the mandatory PEER_REGION/ENGINE_REGION regions
 * in addition to any caller-supplied regions, and rejects region names that collide with each other or with the mandatory names.
 */
final class JcsPropertiesBuilder {
    private static final Logger log = LoggerFactory.getLogger(JcsPropertiesBuilder.class);
    static final String PEER_REGION = "SYM_CLUSTER_PEERS";
    static final String ENGINE_REGION = "SYM_CLUSTER_ENGINES";
    private static final String JCS_REGION_SYNC_LATERAL_TCP = "LATERAL_TCP"; // Sync mode specific to this class
    private static final int DEFAULT_MAX_OBJECTS = 1000;
    private static final int DEFAULT_MAX_LIFE_SECONDS = -1;
    private static final boolean DEFAULT_USE_MEMORY_SHRINKER = false;
    private static final int DEFAULT_SHRINKER_INTERVAL_SECONDS = 30;
    private static final IClusterCacheCoordinator.RemovalType DEFAULT_REMOVAL_TYPE = IClusterCacheCoordinator.RemovalType.LRU;
    private static final String JCS_CONFIG_GLOBAL_PREFIX = "jcs.default";
    private static final String JCS_CONFIG_REGION_PREFIX = "jcs.region";
    private static final String JCS_CONFIG_AUX_PREFIX = "jcs.auxiliary";

    private JcsPropertiesBuilder() {
    }

    /**
     * Builds the full set of JCS configuration properties: core lateral TCP/UDP discovery settings plus the mandatory regions merged with any caller-supplied
     * regions.
     *
     * @throws IllegalArgumentException
     *             if a region name in regionSettings collides with another region name or with a mandatory region name
     */
    static Properties build(InitialSettings initialSettings, Set<RegionSettings> regionSettings) {
        Properties props = buildJcsCoreProperties(initialSettings);
        props.putAll(buildRegionalProperties(withMandatoryRegions(regionSettings)));
        log.debug("Built JCS properties: {}", props);
        return props;
    }

    /**
     * Merges the caller-supplied regions with the mandatory PEER_REGION/ENGINE_REGION, which are always configured with default settings. Rejects duplicate
     * region names, including caller-supplied regions that reuse a mandatory region name, since those are not caller-configurable.
     */
    private static Set<RegionSettings> withMandatoryRegions(Set<RegionSettings> regionSettings) {
        Set<String> regionNames = new HashSet<>(Set.of(PEER_REGION, ENGINE_REGION));
        for (RegionSettings settings : regionSettings) {
            if (!regionNames.add(settings.regionName())) {
                throw new IllegalArgumentException("Duplicate region name: " + settings.regionName());
            }
        }
        Set<RegionSettings> allRegionSettings = new LinkedHashSet<>();
        allRegionSettings.add(defaultRegionSettings(PEER_REGION));
        allRegionSettings.add(defaultRegionSettings(ENGINE_REGION));
        allRegionSettings.addAll(regionSettings);
        return allRegionSettings;
    }

    private static RegionSettings defaultRegionSettings(String regionName) {
        return new RegionSettings(regionName, DEFAULT_MAX_OBJECTS, DEFAULT_MAX_LIFE_SECONDS, DEFAULT_USE_MEMORY_SHRINKER,
                DEFAULT_SHRINKER_INTERVAL_SECONDS, DEFAULT_REMOVAL_TYPE);
    }

    /**
     * Prepares the core (non-region) configuration properties for Apache JCS's CompositeCacheManager: the lateral TCP auxiliary cache and its UDP discovery
     * settings.
     *
     * JCS's own UDP discovery timers (UDPDiscoveryAttributes.sendDelaySec/maxIdleTimeSec) are not exposed through TCPLateralCacheAttributes and so cannot be
     * set here; JCS 3.2.1 also never reads sendDelaySec (its passive broadcast runs on a hardcoded 15s interval) and always constructs UDPDiscoveryAttributes
     * with its own defaults. This is not load-bearing for SymmetricDS: peer liveness is decided by our own heartbeat cadence and staleness threshold (see
     * ParameterConstants.CLUSTER_PEER_HEARTBEAT_MS, CLUSTER_PEER_OBSOLETE_MS, and detectIfPeerIsStale), which run independently of JCS's UDP discovery of
     * lateral TCP peers.
     */
    private static Properties buildJcsCoreProperties(InitialSettings initialSettings) {
        Properties props = new Properties();
        props.setProperty(JCS_CONFIG_GLOBAL_PREFIX, "");
        String auxPrefix = JCS_CONFIG_AUX_PREFIX + ".LATERAL_TCP";
        props.setProperty(auxPrefix, "org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.LateralTCPCacheFactory");
        props.setProperty(auxPrefix + ".attributes", "org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.TCPLateralCacheAttributes");
        props.setProperty(auxPrefix + ".attributes.TcpListenerPort", String.valueOf(initialSettings.port()));
        props.setProperty(auxPrefix + ".attributes.UdpDiscoveryEnabled", "true");
        props.setProperty(auxPrefix + ".attributes.AllowGet", "false");
        props.setProperty(auxPrefix + ".attributes.Receive", "true");
        return props;
    }

    /**
     * Prepares the per-region configuration properties, enriching each region with its lateral TCP mode and sizing/expiration/eviction settings. A negative
     * maxLifeSeconds leaves the region's elements eternal (no age-based expiration), matching JCS's own default. Disk and remote auxiliaries are explicitly
     * disabled at both the region and element level since only the LATERAL_TCP auxiliary is ever configured; lateral is explicitly enabled to match it.
     */
    private static Properties buildRegionalProperties(Set<RegionSettings> regionSettings) {
        Properties props = new Properties();
        for (RegionSettings settings : regionSettings) {
            String regionPrefix = JCS_CONFIG_REGION_PREFIX + "." + settings.regionName();
            props.setProperty(regionPrefix, JCS_REGION_SYNC_LATERAL_TCP);
            props.setProperty(regionPrefix + ".cacheattributes.MaxObjects", String.valueOf(settings.maxObjects()));
            props.setProperty(regionPrefix + ".cacheattributes.UseLateral", "true");
            props.setProperty(regionPrefix + ".cacheattributes.UseRemote", "false");
            props.setProperty(regionPrefix + ".cacheattributes.UseDisk", "false");
            props.setProperty(regionPrefix + ".cacheattributes.UseMemoryShrinker", String.valueOf(settings.useMemoryShrinker()));
            props.setProperty(regionPrefix + ".cacheattributes.ShrinkerIntervalSeconds", String.valueOf(settings.shrinkerIntervalSeconds()));
            props.setProperty(regionPrefix + ".cacheattributes.MemoryCacheName", settings.removalType().toMemoryCacheName());
            props.setProperty(regionPrefix + ".elementattributes.IsEternal", String.valueOf(settings.maxLifeSeconds() < 0));
            props.setProperty(regionPrefix + ".elementattributes.MaxLife", String.valueOf(settings.maxLifeSeconds()));
            props.setProperty(regionPrefix + ".elementattributes.IsLateral", "true");
            props.setProperty(regionPrefix + ".elementattributes.IsRemote", "false");
            props.setProperty(regionPrefix + ".elementattributes.IsSpool", "false");
        }
        return props;
    }
}
