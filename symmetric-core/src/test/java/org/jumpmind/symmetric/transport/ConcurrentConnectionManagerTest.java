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
package org.jumpmind.symmetric.transport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.NodeConnectionStatistics;
import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.Reservation;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationResult;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationStatus;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationType;
import org.jumpmind.symmetric.util.IDatabaseHealthTracker;
import org.junit.jupiter.api.Test;

class ConcurrentConnectionManagerTest {
    private IParameterService mockPs(int maxWorkers) {
        IParameterService ps = mock(IParameterService.class);
        when(ps.getInt(ParameterConstants.CONCURRENT_WORKERS)).thenReturn(maxWorkers);
        when(ps.getLong(ParameterConstants.CONCURRENT_RESERVATION_TIMEOUT)).thenReturn(30_000L);
        when(ps.getLong(anyString(), anyLong())).thenReturn(300_000L);
        return ps;
    }

    private ConcurrentConnectionManager newMgr(int maxWorkers) {
        return new ConcurrentConnectionManager(mockPs(maxWorkers), null, null);
    }

    private ConcurrentConnectionManager newMgrWithMetrics(int maxWorkers, IEngineMetricsService metricsService) {
        return new ConcurrentConnectionManager(mockPs(maxWorkers), metricsService, null);
    }

    @Test
    void metrics_reserveConnection_invokesCounterAndGauge() {
        IUpDownCounter counter = mock(IUpDownCounter.class);
        ISymDoubleGauge gauge = mock(ISymDoubleGauge.class);
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        when(metricsService.getUpDownCounter(anyString())).thenReturn(counter);
        when(metricsService.getDoubleGauge(anyString())).thenReturn(gauge);
        ConcurrentConnectionManager mgr = newMgrWithMetrics(5, metricsService);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        verify(counter).add(1L);
        verify(gauge).setValue(anyDouble());
    }

    @Test
    void metrics_softThenHardReservationThenRelease_countsOneReservation() {
        IUpDownCounter counter = mock(IUpDownCounter.class);
        ConcurrentConnectionManager mgr = newMgrWithMetrics(5, metricsServiceWith(counter));
        mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, true);
        mgr.releaseConnection("node1", "0", "push");
        verify(counter, times(1)).add(1L);
        verify(counter, times(1)).add(-1L);
        verify(counter, never()).add(0L);
    }

    @Test
    void metrics_renewedSoftReservation_countsOneReservation() {
        IUpDownCounter counter = mock(IUpDownCounter.class);
        ConcurrentConnectionManager mgr = newMgrWithMetrics(5, metricsServiceWith(counter));
        mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false);
        mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false);
        verify(counter, times(1)).add(1L);
    }

    private static IEngineMetricsService metricsServiceWith(IUpDownCounter counter) {
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        when(metricsService.getUpDownCounter(anyString())).thenReturn(counter);
        when(metricsService.getDoubleGauge(anyString())).thenReturn(mock(ISymDoubleGauge.class));
        return metricsService;
    }

    @Test
    void metrics_zeroMaxPoolSize_setsGaugeToZero() {
        IUpDownCounter counter = mock(IUpDownCounter.class);
        ISymDoubleGauge gauge = mock(ISymDoubleGauge.class);
        IEngineMetricsService metricsService = mock(IEngineMetricsService.class);
        when(metricsService.getUpDownCounter(anyString())).thenReturn(counter);
        when(metricsService.getDoubleGauge(anyString())).thenReturn(gauge);
        ConcurrentConnectionManager mgr = newMgrWithMetrics(0, metricsService);
        mgr.addToWhitelist("node1");
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        verify(gauge).setValue(0.0);
    }

    @Test
    void logTooBusyRejection_incrementsNumOfRejections() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.logTooBusyRejection("node1", "push");
        NodeConnectionStatistics stats = mgr.getNodeConnectionStatisticsByPoolByNodeId()
                .get("push").get("node1");
        assertEquals(1, stats.getNumOfRejections());
    }

    @Test
    void reserveConnection_requiresExisting_notFound_firstCall_returnsNotFound() {
        ConcurrentConnectionManager mgr = newMgr(5);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, true);
        assertEquals(ReservationStatus.NOT_FOUND, status);
    }

    @Test
    void reserveConnection_duplicateHardReservation_firstCall_returnsDuplicate() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.DUPLICATE, status);
    }

    @Test
    void reserveConnection_runtimeDbUnhealthy_returnsNotReadyWithoutReservation() {
        IDatabaseHealthTracker databaseHealthTracker = mock(IDatabaseHealthTracker.class);
        when(databaseHealthTracker.isRuntimeDbHealthy()).thenReturn(false);
        ConcurrentConnectionManager mgr = new ConcurrentConnectionManager(mockPs(5), null, databaseHealthTracker);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.NOT_READY, status);
        assertEquals(0, mgr.getReservationCount("push"));
    }

    @Test
    void reserveConnection_runtimeDbHealthy_returnsAccepted() {
        IDatabaseHealthTracker databaseHealthTracker = mock(IDatabaseHealthTracker.class);
        when(databaseHealthTracker.isRuntimeDbHealthy()).thenReturn(true);
        ConcurrentConnectionManager mgr = new ConcurrentConnectionManager(mockPs(5), null, databaseHealthTracker);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    void reserveConnection_nullHealthTracker_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(5);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    void reserveConnection_whitelistedNodeAtCapacity_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(1);
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false));
        assertEquals(ReservationStatus.BUSY, mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false));
        mgr.addToWhitelist("node2");
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false));
    }

    @Test
    void reserveConnection_softReservationUpgradedByHard_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(5);
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false));
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false));
        Reservation reservation = mgr.getActiveReservationsByNodeByPool().get("push").get("node1");
        assertEquals(ReservationType.HARD, reservation.getType());
    }

    @Test
    void reserveConnection_requiresExistingWithExistingReservation_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(5);
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false));
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, true));
    }

    @Test
    void reserveConnection_hardReservationGetsDoubleTimeout() {
        long[] currentTimeMs = { 100_000 };
        ConcurrentConnectionManager mgr = new ConcurrentConnectionManager(mockPs(5), null, null, () -> currentTimeMs[0]);
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("softNode", "0", "push", ReservationType.SOFT, false));
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("hardNode", "0", "push", ReservationType.HARD, false));
        Map<String, Reservation> reservations = mgr.getActiveReservationsByNodeByPool().get("push");
        assertEquals(100_000 + 30_000, reservations.get("softNode").timeToLiveInMs);
        assertEquals(100_000 + 60_000, reservations.get("hardNode").timeToLiveInMs);
    }

    @Test
    void reserveConnection_expiredReservationRemoved_freesCapacity() {
        long[] currentTimeMs = { 100_000 };
        ConcurrentConnectionManager mgr = new ConcurrentConnectionManager(mockPs(1), null, null, () -> currentTimeMs[0]);
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false));
        assertEquals(ReservationStatus.BUSY, mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false));
        currentTimeMs[0] = 100_000 + 30_000 + 1;
        assertEquals(ReservationStatus.ACCEPTED, mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false));
    }

    @Test
    void Reservation_equals_nonReservationObject_returnsFalse() {
        Reservation r = new Reservation("node1", System.currentTimeMillis() + 10_000, ReservationType.HARD);
        assertNotNull(r);
        assertNotEquals("string", r);
    }

    @Test
    void nodeConnectionStatistics_getters_returnValues() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        mgr.releaseConnection("node1", "0", "push");
        Map<String, NodeConnectionStatistics> poolStats = mgr.getNodeConnectionStatisticsByPoolByNodeId().get("push");
        assertNotNull(poolStats);
        NodeConnectionStatistics stats = poolStats.get("node1");
        assertNotNull(stats);
        assertEquals(0, stats.getNumOfRejections());
        assertEquals(1L, stats.getTotalConnectionCount());
        assertTrue(stats.getTotalConnectionTimeMs() >= 0L);
        assertTrue(stats.getLastConnectionTimeMs() > 0L);
    }

    @Test
    void getActiveReservationsByNodeByPool_returnsActiveReservations() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        Map<String, Map<String, Reservation>> byPool = mgr.getActiveReservationsByNodeByPool();
        assertNotNull(byPool);
        assertTrue(byPool.containsKey("push"));
        assertTrue(byPool.get("push").containsKey("node1"));
    }

    @Test
    void testReserveConnection_returnsGrantedReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        ReservationResult result = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false);
        assertEquals(ReservationStatus.ACCEPTED, result.status());
        assertEquals(ReservationType.SOFT, result.reservation().getType());
        assertSame(mgr.getActiveReservationsByNodeByPool().get("push").get("node1-default"), result.reservation());
    }

    @Test
    void testReserveConnection_hardRequest_returnsHardReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        ReservationResult result = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.HARD, false);
        assertEquals(ReservationType.HARD, result.reservation().getType());
    }

    @Test
    void testReserveConnection_upgradeToHard_returnsNewReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        Reservation softReservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        ReservationResult result = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.HARD, true);
        assertNotSame(softReservation, result.reservation());
        assertSame(mgr.getActiveReservationsByNodeByPool().get("push").get("node1-default"), result.reservation());
    }

    @Test
    void testReserveConnection_renewal_returnsNewReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        Reservation originalReservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        ReservationResult result = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false);
        assertNotSame(originalReservation, result.reservation());
        assertSame(mgr.getActiveReservationsByNodeByPool().get("push").get("node1-default"), result.reservation());
    }

    @Test
    void testReserveConnection_busy_returnsNoReservation() {
        ConcurrentConnectionManager mgr = newMgr(1);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        ReservationResult result = mgr.reserveConnectionWithResult("node2", "0", "push", ReservationType.SOFT, false);
        assertEquals(ReservationStatus.BUSY, result.status());
        assertNull(result.reservation());
    }

    @Test
    void testReserveConnection_duplicate_returnsNoReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        ReservationResult result = mgr.reserveConnectionWithResult("node1", "0", "push", ReservationType.SOFT, false);
        assertEquals(ReservationStatus.DUPLICATE, result.status());
        assertNull(result.reservation());
    }

    @Test
    void testReserveConnection_missingRequiredReservation_returnsNoReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        ReservationResult result = mgr.reserveConnectionWithResult("node1", "0", "push", ReservationType.HARD, true);
        assertEquals(ReservationStatus.NOT_FOUND, result.status());
        assertNull(result.reservation());
    }

    @Test
    void testReserveConnection_runtimeDbUnhealthy_returnsNoReservation() {
        IDatabaseHealthTracker databaseHealthTracker = mock(IDatabaseHealthTracker.class);
        when(databaseHealthTracker.isRuntimeDbHealthy()).thenReturn(false);
        ConcurrentConnectionManager mgr = new ConcurrentConnectionManager(mockPs(5), null, databaseHealthTracker);
        ReservationResult result = mgr.reserveConnectionWithResult("node1", "0", "push", ReservationType.SOFT, false);
        assertEquals(ReservationStatus.NOT_READY, result.status());
        assertNull(result.reservation());
    }

    @Test
    void testReleaseSoftReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        Reservation reservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        assertTrue(mgr.releaseSoftReservation("node1", "default", "push", reservation));
        assertEquals(0, mgr.getReservationCount("push"));
        assertEquals(1L, mgr.getNodeConnectionStatisticsByPoolByNodeId().get("push").get("node1-default").getTotalConnectionCount());
    }

    @Test
    void testReleaseSoftReservation_afterUpgradeToHard_keepsReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        Reservation softReservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        Reservation hardReservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.HARD, true).reservation();
        assertFalse(mgr.releaseSoftReservation("node1", "default", "push", softReservation));
        assertSame(hardReservation, mgr.getActiveReservationsByNodeByPool().get("push").get("node1-default"));
    }

    @Test
    void testReleaseSoftReservation_afterRenewal_keepsReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        Reservation originalReservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        Reservation renewedReservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        assertFalse(mgr.releaseSoftReservation("node1", "default", "push", originalReservation));
        assertSame(renewedReservation, mgr.getActiveReservationsByNodeByPool().get("push").get("node1-default"));
    }

    @Test
    void testReleaseSoftReservation_whenTwoConnectionsRenewSameReservation_releasesOnlyTheCurrentOne() {
        ConcurrentConnectionManager mgr = newMgr(5);
        Reservation firstConnectionReservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        Reservation secondConnectionReservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        assertFalse(mgr.releaseSoftReservation("node1", "default", "push", firstConnectionReservation));
        assertEquals(1, mgr.getReservationCount("push"));
        assertTrue(mgr.releaseSoftReservation("node1", "default", "push", secondConnectionReservation));
        assertEquals(0, mgr.getReservationCount("push"));
    }

    @Test
    void testReleaseSoftReservation_withHardReservation_keepsReservation() {
        ConcurrentConnectionManager mgr = newMgr(5);
        Reservation reservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.HARD, false).reservation();
        assertFalse(mgr.releaseSoftReservation("node1", "default", "push", reservation));
        assertEquals(1, mgr.getReservationCount("push"));
    }

    @Test
    void testReleaseSoftReservation_whenAlreadyReleased_returnsFalse() {
        ConcurrentConnectionManager mgr = newMgr(5);
        Reservation reservation = mgr.reserveConnectionWithResult("node1", "default", "push", ReservationType.SOFT, false).reservation();
        mgr.releaseConnection("node1", "default", "push");
        assertFalse(mgr.releaseSoftReservation("node1", "default", "push", reservation));
    }

    @Test
    void testReleaseSoftReservation_withNullReservation_returnsFalse() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "default", "push", ReservationType.SOFT, false);
        assertFalse(mgr.releaseSoftReservation("node1", "default", "push", null));
        assertEquals(1, mgr.getReservationCount("push"));
    }
}
