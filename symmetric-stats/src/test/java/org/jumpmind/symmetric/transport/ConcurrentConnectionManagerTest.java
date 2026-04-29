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
package org.jumpmind.symmetric.transport;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.observability.metrics.EngineMetricsService;
import org.jumpmind.symmetric.observability.metrics.TestMetricsManagerFactory;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.Reservation;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationStatus;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationType;
import org.junit.jupiter.api.Test;

public class ConcurrentConnectionManagerTest {
    @Test
    public void testRemoveTimedOutReservations() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getEngineName()).thenReturn("test-engine");
        IParameterService parameterService = mock(IParameterService.class);
        ConcurrentConnectionManager mgr = new ConcurrentConnectionManager(parameterService, new EngineMetricsService(engine,
                TestMetricsManagerFactory.create(), false));
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

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConcurrentConnectionManager newMgr(int maxWorkers) {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getEngineName()).thenReturn("test-engine");
        IParameterService ps = mock(IParameterService.class);
        when(ps.getInt(ParameterConstants.CONCURRENT_WORKERS)).thenReturn(maxWorkers);
        when(ps.getLong(ParameterConstants.CONCURRENT_RESERVATION_TIMEOUT)).thenReturn(30_000L);
        return new ConcurrentConnectionManager(ps, new EngineMetricsService(engine, TestMetricsManagerFactory.create(), false));
    }

    // ── reserveConnection ─────────────────────────────────────────────────────

    @Test
    public void testReserveConnection_underLimit_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(2);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    public void testReserveConnection_atLimit_returnsBusy() {
        ConcurrentConnectionManager mgr = newMgr(1);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        ReservationStatus status = mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.BUSY, status);
    }

    @Test
    public void testReserveConnection_whitelistedNode_bypassesLimit() {
        ConcurrentConnectionManager mgr = newMgr(1);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false); // fills pool
        mgr.addToWhitelist("node2");
        ReservationStatus status = mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    public void testReserveConnection_existingHardReservation_returnsDuplicate() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.DUPLICATE, status);
    }

    @Test
    public void testReserveConnection_existingSoftReplacedByHard_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    @Test
    public void testReserveConnection_requiresExisting_noReservation_returnsNotFound() {
        ConcurrentConnectionManager mgr = newMgr(5);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, true);
        assertEquals(ReservationStatus.NOT_FOUND, status);
    }

    @Test
    public void testReserveConnection_requiresExisting_reservationPresent_returnsAccepted() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.SOFT, false);
        ReservationStatus status = mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, true);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    // ── releaseConnection ─────────────────────────────────────────────────────

    @Test
    public void testReleaseConnection_found_returnsTrue() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        assertTrue(mgr.releaseConnection("node1", "0", "push"));
    }

    @Test
    public void testReleaseConnection_notFound_returnsFalse() {
        ConcurrentConnectionManager mgr = newMgr(5);
        assertFalse(mgr.releaseConnection("node1", "0", "push"));
    }

    @Test
    public void testReleaseConnection_decrementedReservationCount() {
        ConcurrentConnectionManager mgr = newMgr(1);
        mgr.reserveConnection("node1", "0", "push", ReservationType.HARD, false);
        mgr.releaseConnection("node1", "0", "push");
        // After release the slot is free, so another node can be accepted
        ReservationStatus status = mgr.reserveConnection("node2", "0", "push", ReservationType.HARD, false);
        assertEquals(ReservationStatus.ACCEPTED, status);
    }

    // ── getReservationIdentifier ──────────────────────────────────────────────

    @Test
    public void testGetReservationIdentifier_defaultChannel_returnsNodeIdOnly() {
        assertEquals("node1", ConcurrentConnectionManager.getReservationIdentifier("node1", "0"));
    }

    @Test
    public void testGetReservationIdentifier_nullChannel_returnsNodeIdOnly() {
        assertEquals("node1", ConcurrentConnectionManager.getReservationIdentifier("node1", null));
    }

    @Test
    public void testGetReservationIdentifier_nonDefaultChannel_returnsNodeIdDashChannel() {
        assertEquals("node1-channel1", ConcurrentConnectionManager.getReservationIdentifier("node1", "channel1"));
    }

    // ── whitelist ─────────────────────────────────────────────────────────────

    @Test
    public void testWhitelist_addAndGet() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.addToWhitelist("node1");
        String[] list = mgr.getWhiteList();
        assertEquals(1, list.length);
        assertEquals("node1", list[0]);
    }

    @Test
    public void testWhitelist_removeNode_notInList() {
        ConcurrentConnectionManager mgr = newMgr(5);
        mgr.addToWhitelist("node1");
        mgr.removeFromWhiteList("node1");
        assertEquals(0, mgr.getWhiteList().length);
    }
}