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
import org.jumpmind.symmetric.observability.metrics.EngineMetricsService;
import org.jumpmind.symmetric.observability.metrics.TestMetricsManagerFactory;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.MockStatisticManager;
import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.Reservation;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationType;
import org.junit.jupiter.api.Test;

public class ConcurrentConnectionManagerUnitTest {
    @Test
    public void testRemoveTimedOutReservations() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getEngineName()).thenReturn("test-engine");
        IParameterService parameterService = mock(IParameterService.class);
        ConcurrentConnectionManager mgr = new ConcurrentConnectionManager(parameterService, new MockStatisticManager(), new EngineMetricsService(engine, TestMetricsManagerFactory.create(), false));
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
}