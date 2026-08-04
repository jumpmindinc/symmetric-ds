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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class NodeConcurrencyInterceptorTest {
    private static final String NODE_ID = "node1";
    private static final String IDENTITY_NODE_ID = "corp-000";
    private static final String PUSH_URI = "/sync/corp-000/push";
    private IConcurrentConnectionManager concurrentConnectionManager;
    private IConfigurationService configurationService;
    private INodeService nodeService;
    private IStatisticManager statisticManager;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private NodeConcurrencyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        concurrentConnectionManager = mock(IConcurrentConnectionManager.class);
        configurationService = mock(IConfigurationService.class);
        nodeService = mock(INodeService.class);
        statisticManager = mock(IStatisticManager.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        interceptor = new NodeConcurrencyInterceptor(concurrentConnectionManager, configurationService, nodeService, statisticManager);
        when(request.getRequestURI()).thenReturn(PUSH_URI);
        when(request.getContextPath()).thenReturn("");
        when(request.getServletPath()).thenReturn("");
        when(request.getMethod()).thenReturn("PUT");
        when(request.getParameter(WebConstants.NODE_ID)).thenReturn(NODE_ID);
        when(nodeService.findIdentityNodeId()).thenReturn(IDENTITY_NODE_ID);
        when(response.isCommitted()).thenReturn(false);
    }

    private void stubReservationStatus(ReservationStatus status) {
        when(concurrentConnectionManager.reserveConnection(any(), any(), any(), any(), anyBoolean())).thenReturn(status);
    }

    @Test
    void before_reservationNotReady_sendsServiceNotReadyAndCountsRejection() throws Exception {
        stubReservationStatus(ReservationStatus.NOT_READY);
        assertFalse(interceptor.before(request, response));
        verify(response).sendError(WebConstants.SC_SERVICE_NOT_READY, null);
        verify(statisticManager).incrementNodesRejected(1);
    }

    @Test
    void before_reservationBusy_sendsServiceBusy() throws Exception {
        stubReservationStatus(ReservationStatus.BUSY);
        assertFalse(interceptor.before(request, response));
        verify(response).sendError(WebConstants.SC_SERVICE_BUSY, null);
        verify(statisticManager).incrementNodesRejected(1);
    }

    @Test
    void before_reservationNotFound_sendsNoReservation() throws Exception {
        stubReservationStatus(ReservationStatus.NOT_FOUND);
        assertFalse(interceptor.before(request, response));
        verify(response).sendError(WebConstants.SC_NO_RESERVATION, null);
        verify(statisticManager).incrementNodesRejected(1);
    }

    @Test
    void before_reservationDuplicate_sendsAlreadyConnected() throws Exception {
        stubReservationStatus(ReservationStatus.DUPLICATE);
        assertFalse(interceptor.before(request, response));
        verify(response).sendError(WebConstants.SC_ALREADY_CONNECTED, null);
        verify(statisticManager).incrementNodesRejected(1);
    }

    @Test
    void before_reservationAccepted_setsSuspendIgnoreHeadersAndProceeds() throws Exception {
        stubReservationStatus(ReservationStatus.ACCEPTED);
        NodeChannels nodeChannels = mock(NodeChannels.class);
        when(nodeChannels.getSuspendChannelsAsString(anyString())).thenReturn("");
        when(nodeChannels.getIgnoreChannelsAsString(anyString())).thenReturn("");
        when(configurationService.getSuspendIgnoreChannelLists(NODE_ID)).thenReturn(nodeChannels);
        assertTrue(interceptor.before(request, response));
        verify(response).setHeader(WebConstants.SUSPENDED_CHANNELS, "");
        verify(response).setHeader(WebConstants.IGNORED_CHANNELS, "");
    }
}
