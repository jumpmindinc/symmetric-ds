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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.observability.metrics.EngineMetricsService;
import org.jumpmind.symmetric.observability.metrics.TestMetricsManagerFactory;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.Reservation;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationStatus;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationType;
import org.junit.jupiter.api.Test;

class ConcurrentConnectionManagerTest {
    @Test
    void testRemoveTimedOutReservations() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getEngineName()).thenReturn("test-engine");
        IParameterService parameterService = mock(IParameterService.class);
        ConcurrentConnectionManager mgr = new ConcurrentConnectionManager(parameterService, new EngineMetricsService(engine,
                TestMetricsManagerFactory.create(), false), null);
        Map<String, Reservation> reservations = new HashMap<String, Reservation>();
        String nodeId = "1";
        Reservation current = new ConcurrentConnectionManager.Reservation(nodeId, System.currentTimeMillis() + 10000, ReservationType.HARD);
        reservations.put(nodeId, current);
        nodeId = "2";
        current = new ConcurrentConnectionManager.Reservation(nodeId, System.currentTimeMillis() + 10000, ReservationType.HARD);
        reservations.put(nodeId, current);
        assertEquals(2, reservations.size());
        mgr.removeTimedOutReservations(reservations);
        assertEquals(2, reservations.size());
        current.timeToLiveInMs = System.currentTimeMillis() - 10000;
        mgr.removeTimedOutReservations(reservations);
        assertEquals(1, reservations.size());
    }

    private ConcurrentConnectionManager newMgr(int maxWorkers) {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getEngineName()).thenReturn("test-engine");
        IParameterService ps = mock(IParameterService.class);
        when(ps.getInt(ParameterConstants.CONCURRENT_WORKERS)).thenReturn(maxWorkers);
        when(ps.getLong(ParameterConstants.CONCURRENT_RESERVATION_TIMEOUT)).thenReturn(30_000L);
        return new ConcurrentConnectionManager(ps, new EngineMetricsService(engine, TestMetricsManagerFactory.create(), false), null);
    }

    @Test
    void testReserveConnection_underLimit_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(2);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    void testReserveConnection_atLimit_returnsBusy() {
        ConcurrentConnectionManager mgr = newMgr(1);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        ReservationStatus status = mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.BUSY, status);
    }

    @Test
    void testReserveConnection_whitelistedNode_bypassesLimit() {
        ConcurrentConnectionManager mgr = newMgr(1);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false); // fills pool
        mgr.addToWhitelist("node2");
        ReservationStatus status = mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    void testReserveConnection_existingHardReservation_returnsDuplicate() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.DUPLICATE, status);
    }

    @Test
    void testReserveConnection_existingSoftReplacedByHard_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    void testReserveConnection_requiresExisting_noReservation_returnsNotFound() {
        ConcurrentConnectionManager mgr = newMgr(5);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, true);
        assertEquals(ReservationStatus.NOT_FOUND, status);
    }

    @Test
    void testReserveConnection_requiresExisting_reservationPresent_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, true);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    void testReleaseConnection_found_returnsTrue() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertTrue(mgr.releaseConnection("node1", "0", "push"));
    }

    @Test
    void testReleaseConnection_notFound_returnsFalse() {
        ConcurrentConnectionManager mgr = newMgr(5);
        assertFalse(mgr.releaseConnection("node1", "0", "push"));
    }

    @Test
    void testReleaseConnection_decrementedReservationCount() {
        ConcurrentConnectionManager mgr = newMgr(1);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        mgr.releaseConnection("node1", "0", "push");
        // After release the slot is free, so another node can be accepted
        ReservationStatus status = mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    void testGetReservationIdentifier_defaultChannel_returnsNodeIdOnly() {
        assertEquals("node1", ConcurrentConnectionManager.getReservationIdentifier("node1", "0"));
    }

    @Test
    void testGetReservationIdentifier_nullChannel_returnsNodeIdOnly() {
        assertEquals("node1", ConcurrentConnectionManager.getReservationIdentifier("node1", null));
    }

    @Test
    void testGetReservationIdentifier_nonDefaultChannel_returnsNodeIdDashChannel() {
        assertEquals("node1-channel1", ConcurrentConnectionManager.getReservationIdentifier("node1", "channel1"));
    }

    @Test
    void testWhitelist_addAndGet() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.addToWhitelist("node1");
        String[] list = mgr.getWhiteList();
        assertEquals(1, list.length);
        assertEquals("node1", list[0]);
    }

    @Test
    void testWhitelist_removeNode_notInList() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.addToWhitelist("node1");
        mgr.removeFromWhiteList("node1");
        assertEquals(0, mgr.getWhiteList().length);
    }

    @Test
    void releaseConnection_byNodeId_found_returnsTrue() {
        ConcurrentConnectionManager mgr = newMgr(5);
        // Reserve with default channel "0" so the reservation key is just the nodeId
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        // 2-arg release uses nodeId as the direct map key
        assertTrue(mgr.releaseConnection("node1", "push"));
    }

    @Test
    void releaseConnection_byNodeId_notFound_returnsFalse() {
        ConcurrentConnectionManager mgr = newMgr(5);
        assertFalse(mgr.releaseConnection("node99", "push"));
    }

    @Test
    void getReservationCount_empty_returnsZero() {
        ConcurrentConnectionManager mgr = newMgr(5);
        assertEquals(0, mgr.getReservationCount("push"));
    }

    @Test
    void getReservationCount_afterReservation_returnsOne() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(1, mgr.getReservationCount("push"));
    }

    @Test
    void getPullReservationsByNodeId_emptyMap_returnsEmptyMap() {
        ConcurrentConnectionManager mgr = newMgr(5);
        assertTrue(mgr.getPullReservationsByNodeId().isEmpty());
    }

    @Test
    void getPushReservationsByNodeId_afterHardReservation_containsNode() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertTrue(mgr.getPushReservationsByNodeId().containsKey("node1"));
    }

    @Test
    void getNodeConnectionStatisticsByPoolByNodeId_returnsStats() {
        ConcurrentConnectionManager mgr = newMgr(5);
        assertNotNull(mgr.getNodeConnectionStatisticsByPoolByNodeId());
    }

    @Test
    void Reservation_getChannelId_returnsDefaultZero() {
        Reservation r = new Reservation("node1", System.currentTimeMillis() + 10000, ReservationType.HARD);
        assertEquals("0", r.getChannelId());
    }

    @Test
    void Reservation_withChannelId_constructor() {
        Reservation r = new Reservation("node1", "ch1", System.currentTimeMillis() + 10000, ReservationType.HARD);
        assertEquals("node1", r.getNodeId());
    }

    @Test
    void Reservation_getNodeId_returnsNodeId() {
        Reservation r = new Reservation("myNode", System.currentTimeMillis() + 10000, ReservationType.HARD);
        assertEquals("myNode", r.getNodeId());
    }

    @Test
    void Reservation_getTimeToLiveInMs_returnsValue() {
        long ttl = System.currentTimeMillis() + 10000;
        Reservation r = new Reservation("node1", ttl, ReservationType.HARD);
        assertEquals(ttl, r.getTimeToLiveInMs());
    }

    @Test
    void Reservation_getCreateTime_returnsPositive() {
        Reservation r = new Reservation("node1", System.currentTimeMillis() + 10000, ReservationType.HARD);
        assertTrue(r.getCreateTime() > 0);
    }

    @Test
    void Reservation_hashCode_usesNodeId() {
        Reservation r = new Reservation("node1", System.currentTimeMillis() + 10000, ReservationType.HARD);
        assertEquals("node1".hashCode(), r.hashCode());
    }

    @Test
    void Reservation_equals_sameNodeId_returnsTrue() {
        Reservation a = new Reservation("node1", System.currentTimeMillis() + 10000, ReservationType.HARD);
        Reservation b = new Reservation("node1", System.currentTimeMillis() + 20000, ReservationType.SOFT);
        assertEquals(a, b);
    }

    @Test
    void Reservation_equals_differentNodeId_returnsFalse() {
        Reservation a = new Reservation("node1", System.currentTimeMillis() + 10000, ReservationType.HARD);
        Reservation b = new Reservation("node2", System.currentTimeMillis() + 10000, ReservationType.HARD);
        assertNotEquals(a, b);
    }

    @Test
    void Reservation_getIdentifier_defaultChannel_returnsNodeId() {
        Reservation r = new Reservation("node1", System.currentTimeMillis() + 10000, ReservationType.HARD);
        assertEquals("node1", r.getIdentifier());
    }

    @Test
    void Reservation_getIdentifier_nonDefaultChannel() {
        // Using getReservationIdentifier directly to test non-default channel formatting
        assertEquals("node1-ch1", ConcurrentConnectionManager.getReservationIdentifier("node1", "ch1"));
    }
}