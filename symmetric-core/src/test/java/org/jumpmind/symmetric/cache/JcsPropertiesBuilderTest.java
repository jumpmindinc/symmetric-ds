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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.CacheCoordinatorNetworkSettings;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.RegionSettings;
import org.jumpmind.symmetric.cache.IClusterCacheCoordinator.RemovalType;
import org.junit.jupiter.api.Test;

class JcsPropertiesBuilderTest {
    private static final CacheCoordinatorNetworkSettings INITIAL_SETTINGS = new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, true, 3000L);

    @Test
    void build_containsJcsDefaultKeyWithNoTrailingPeriod() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS, Set.of());
        assertTrue(props.containsKey("jcs.default"));
        assertEquals("", props.getProperty("jcs.default"));
    }

    @Test
    void build_configuresLateralTcpAuxiliary() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS, Set.of());
        assertEquals("org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.LateralTCPCacheFactory",
                props.getProperty("jcs.auxiliary.LATERAL_TCP"));
        assertEquals("org.apache.commons.jcs3.auxiliary.lateral.socket.tcp.TCPLateralCacheAttributes",
                props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes"));
    }

    @Test
    void build_setsTcpListenerPortFromInitialSettings() {
        Properties props = JcsPropertiesBuilder.build(new CacheCoordinatorNetworkSettings("server1", "inst1", 5150, true, 3000L), Set.of());
        assertEquals("5150", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.TcpListenerPort"));
    }

    @Test
    void build_enablesUdpDiscovery() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS, Set.of());
        assertEquals("true", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.UdpDiscoveryEnabled"));
    }

    @Test
    void build_disallowsGet_allowsReceive() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS, Set.of());
        assertEquals("false", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.AllowGet"));
        assertEquals("true", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.Receive"));
    }

    @Test
    void build_setsDisconnectionTuningOnTheDefinedAuxiliary() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS, Set.of());
        // heartbeat 3000 -> delivery budget 1500 -> socket timeout capped at the 1000 ms ceiling.
        assertEquals("1000", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.SocketTimeOut"));
        assertEquals("1000", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.OpenTimeOut"));
        assertEquals("0", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.ZombieQueueMaxSize"));
        // Guard against auxiliary-name drift: these must attach to the auxiliary that is actually defined, not a stray name JCS would ignore.
        assertNull(props.getProperty("jcs.auxiliary.LTCP.attributes.ZombieQueueMaxSize"));
    }

    @Test
    void build_shortHeartbeat_shrinksSocketTimeoutsToTheDeliveryBudget() {
        // heartbeat 1000 -> delivery budget 500 -> socket timeouts follow the budget rather than the 1000 ms ceiling, so a dead-peer put still fails in budget.
        Properties props = JcsPropertiesBuilder.build(new CacheCoordinatorNetworkSettings("server1", "inst1", 1101, true, 1000L), Set.of());
        assertEquals("500", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.SocketTimeOut"));
        assertEquals("500", props.getProperty("jcs.auxiliary.LATERAL_TCP.attributes.OpenTimeOut"));
    }

    @Test
    void networkSettings_deriveTimeoutsFromHeartbeat_acrossFloorCeilingAndHalving() {
        assertEquals(1500L, new CacheCoordinatorNetworkSettings("s", "i", 1101, true, 3000L).deliveryTimeoutMs());
        assertEquals(1000L, new CacheCoordinatorNetworkSettings("s", "i", 1101, true, 3000L).socketTimeoutMs());
        assertEquals(500L, new CacheCoordinatorNetworkSettings("s", "i", 1101, true, 1000L).socketTimeoutMs());
        // Below 2x the floor, the delivery budget floors at 250 ms and the socket timeout follows it.
        assertEquals(250L, new CacheCoordinatorNetworkSettings("s", "i", 1101, true, 200L).deliveryTimeoutMs());
        assertEquals(250L, new CacheCoordinatorNetworkSettings("s", "i", 1101, true, 200L).socketTimeoutMs());
        // Above 2x the ceiling, the socket timeout stays capped at 1000 ms even though the budget is larger.
        assertEquals(1000L, new CacheCoordinatorNetworkSettings("s", "i", 1101, true, 10000L).socketTimeoutMs());
    }

    @Test
    void build_noRegionSettings_configuresBothMandatoryRegionsWithNoTrailingPeriod() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS, Set.of());
        assertEquals("LATERAL_TCP", props.getProperty("jcs.region.SYM_CLUSTER_PEERS"));
        assertEquals("LATERAL_TCP", props.getProperty("jcs.region.SYM_CLUSTER_ENGINES"));
    }

    @Test
    void build_mandatoryRegions_useDefaultSizingAndAreEternal() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS, Set.of());
        assertEquals("1000", props.getProperty("jcs.region.SYM_CLUSTER_PEERS.cacheattributes.MaxObjects"));
        assertEquals("true", props.getProperty("jcs.region.SYM_CLUSTER_PEERS.elementattributes.IsEternal"));
        assertEquals("-1", props.getProperty("jcs.region.SYM_CLUSTER_PEERS.elementattributes.MaxLife"));
    }

    @Test
    void build_customRegion_isConfiguredAlongsideMandatoryRegions() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS,
                Set.of(new RegionSettings("CUSTOM_REGION", 50, 30, false, 30, RemovalType.LRU)));
        assertEquals("LATERAL_TCP", props.getProperty("jcs.region.CUSTOM_REGION"));
        assertEquals("50", props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.MaxObjects"));
        assertTrue(props.containsKey("jcs.region.SYM_CLUSTER_PEERS"));
        assertTrue(props.containsKey("jcs.region.SYM_CLUSTER_ENGINES"));
    }

    @Test
    void build_regionNameDuplicatesMandatoryRegion_throws() {
        Set<RegionSettings> regionSettings = Set.of(new RegionSettings("SYM_CLUSTER_PEERS", 50, 30, false, 30, RemovalType.LRU));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JcsPropertiesBuilder.build(INITIAL_SETTINGS, regionSettings));
        assertTrue(ex.getMessage().contains("SYM_CLUSTER_PEERS"));
    }

    @Test
    void build_duplicateCustomRegionNames_throws() {
        Set<RegionSettings> regionSettings = new HashSet<>(List.of(
                new RegionSettings("CUSTOM_REGION", 50, 30, false, 30, RemovalType.LRU),
                new RegionSettings("CUSTOM_REGION", 100, 60, false, 30, RemovalType.LRU)));
        assertThrows(IllegalArgumentException.class, () -> JcsPropertiesBuilder.build(INITIAL_SETTINGS, regionSettings));
    }

    @Test
    void build_regionSizingAndMaxLife_reflectRegionSettings() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS,
                Set.of(new RegionSettings("CUSTOM_REGION", 500, 60, false, 30, RemovalType.LRU)));
        assertEquals("500", props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.MaxObjects"));
        assertEquals("false", props.getProperty("jcs.region.CUSTOM_REGION.elementattributes.IsEternal"));
        assertEquals("60", props.getProperty("jcs.region.CUSTOM_REGION.elementattributes.MaxLife"));
    }

    @Test
    void build_negativeMaxLifeSeconds_marksElementsEternal() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS,
                Set.of(new RegionSettings("CUSTOM_REGION", 500, -1, false, 30, RemovalType.LRU)));
        assertEquals("true", props.getProperty("jcs.region.CUSTOM_REGION.elementattributes.IsEternal"));
    }

    @Test
    void build_disablesDiskAndRemote_enablesLateral_atCacheLevel() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS,
                Set.of(new RegionSettings("CUSTOM_REGION", 500, 60, false, 30, RemovalType.LRU)));
        assertEquals("false", props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.UseDisk"));
        assertEquals("false", props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.UseRemote"));
        assertEquals("true", props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.UseLateral"));
    }

    @Test
    void build_disablesSpoolAndRemote_enablesLateral_atElementLevel() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS,
                Set.of(new RegionSettings("CUSTOM_REGION", 500, 60, false, 30, RemovalType.LRU)));
        assertEquals("false", props.getProperty("jcs.region.CUSTOM_REGION.elementattributes.IsSpool"));
        assertEquals("false", props.getProperty("jcs.region.CUSTOM_REGION.elementattributes.IsRemote"));
        assertEquals("true", props.getProperty("jcs.region.CUSTOM_REGION.elementattributes.IsLateral"));
    }

    @Test
    void build_configuresMemoryShrinkerAndInterval() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS,
                Set.of(new RegionSettings("CUSTOM_REGION", 500, 60, true, 45, RemovalType.LRU)));
        assertEquals("true", props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.UseMemoryShrinker"));
        assertEquals("45", props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.ShrinkerIntervalSeconds"));
    }

    @Test
    void build_memoryShrinkerDisabledByDefault() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS,
                Set.of(new RegionSettings("CUSTOM_REGION", 500, 60, false, 45, RemovalType.LRU)));
        assertEquals("false", props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.UseMemoryShrinker"));
    }

    @Test
    void build_lruRemovalType_mapsToRealJcsMemoryCacheClass() {
        Properties props = JcsPropertiesBuilder.build(INITIAL_SETTINGS,
                Set.of(new RegionSettings("CUSTOM_REGION", 500, 60, false, 30, RemovalType.LRU)));
        assertEquals("org.apache.commons.jcs3.engine.memory.lru.LRUMemoryCache",
                props.getProperty("jcs.region.CUSTOM_REGION.cacheattributes.MemoryCacheName"));
    }

    @Test
    void build_lfuRemovalType_throwsUnsupported() {
        Set<RegionSettings> regionSettings = Set.of(new RegionSettings("CUSTOM_REGION", 500, 60, false, 30, RemovalType.LFU));
        assertThrows(UnsupportedOperationException.class, () -> JcsPropertiesBuilder.build(INITIAL_SETTINGS, regionSettings));
    }

    @Test
    void build_arcRemovalType_throwsUnsupported() {
        Set<RegionSettings> regionSettings = Set.of(new RegionSettings("CUSTOM_REGION", 500, 60, false, 30, RemovalType.ARC));
        assertThrows(UnsupportedOperationException.class, () -> JcsPropertiesBuilder.build(INITIAL_SETTINGS, regionSettings));
    }
}
