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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.Reservation;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationResult;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationStatus;
import org.jumpmind.symmetric.transport.IConcurrentConnectionManager.ReservationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class NodeConcurrencyInterceptorTest {
    private static final String NODE_ID = "node1";
    private static final String IDENTITY_NODE_ID = "corp-000";
    private static final String PUSH_URI = "/sync/corp-000/push";
    private static final String CHANNEL_ID = "default";
    private IConcurrentConnectionManager concurrentConnectionManager;
    private IConfigurationService configurationService;
    private INodeService nodeService;
    private IStatisticManager statisticManager;
    private IParameterService parameterService;
    private IReservationConnectionCloseTracker reservationConnectionCloseTracker;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private NodeConcurrencyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        concurrentConnectionManager = mock(IConcurrentConnectionManager.class);
        configurationService = mock(IConfigurationService.class);
        nodeService = mock(INodeService.class);
        statisticManager = mock(IStatisticManager.class);
        parameterService = mock(IParameterService.class);
        reservationConnectionCloseTracker = mock(IReservationConnectionCloseTracker.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        interceptor = new NodeConcurrencyInterceptor(concurrentConnectionManager, configurationService, nodeService, statisticManager,
                parameterService, reservationConnectionCloseTracker);
        when(request.getRequestURI()).thenReturn(PUSH_URI);
        when(request.getContextPath()).thenReturn("");
        when(request.getServletPath()).thenReturn("");
        when(request.getMethod()).thenReturn("PUT");
        when(request.getParameter(WebConstants.NODE_ID)).thenReturn(NODE_ID);
        when(nodeService.findIdentityNodeId()).thenReturn(IDENTITY_NODE_ID);
        when(response.isCommitted()).thenReturn(false);
    }

    private void stubReservationStatus(ReservationStatus status) {
        stubReservationResult(status, null);
    }

    private void stubReservationResult(ReservationStatus status, Reservation reservation) {
        when(concurrentConnectionManager.reserveConnectionWithResult(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new ReservationResult(status, reservation));
        when(concurrentConnectionManager.reserveConnection(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(status);
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

    @Test
    void testBefore_headPushWithReleaseOnCloseDisabled_doesNotTrackReservation() throws Exception {
        stubAcceptedHeadPush();
        assertFalse(interceptor.before(request, response));
        verifyNoInteractions(reservationConnectionCloseTracker);
    }

    @Test
    void testBefore_headPushWithReleaseOnCloseEnabled_tracksReservation() throws Exception {
        stubAcceptedHeadPush();
        Reservation reservation = new Reservation(NODE_ID, System.currentTimeMillis() + 10_000, ReservationType.SOFT);
        stubReservationResult(ReservationStatus.ACCEPTED, reservation);
        when(parameterService.is(ParameterConstants.CONCURRENT_RESERVATION_RELEASE_ON_CLOSE, false)).thenReturn(true);
        assertFalse(interceptor.before(request, response));
        verify(reservationConnectionCloseTracker).isMarkedForRelease(request, concurrentConnectionManager, NODE_ID, CHANNEL_ID, PUSH_URI, reservation);
        verify(response, never()).sendError(eq(WebConstants.SC_SERVICE_ERROR), any());
    }

    @Test
    void testBefore_headPushWithUntrackableConnection_keepsReservation() throws Exception {
        stubAcceptedHeadPush();
        when(parameterService.is(ParameterConstants.CONCURRENT_RESERVATION_RELEASE_ON_CLOSE, false)).thenReturn(true);
        when(reservationConnectionCloseTracker.isMarkedForRelease(any(), any(), any(), any(), any(), any())).thenReturn(false);
        assertFalse(interceptor.before(request, response));
        verify(concurrentConnectionManager, never()).releaseConnection(any(), any(), any());
        verify(response, never()).sendError(eq(WebConstants.SC_SERVICE_ERROR), any());
    }

    @Test
    void testBefore_headPushWithForwardingHeader_doesNotTrackReservation() throws Exception {
        stubAcceptedHeadPush();
        when(parameterService.is(ParameterConstants.CONCURRENT_RESERVATION_RELEASE_ON_CLOSE, false)).thenReturn(true);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");
        assertFalse(interceptor.before(request, response));
        verifyNoInteractions(reservationConnectionCloseTracker);
    }

    @Test
    void testBefore_headPushWhenTrackingFails_keepsReservation() throws Exception {
        stubAcceptedHeadPush();
        when(parameterService.is(ParameterConstants.CONCURRENT_RESERVATION_RELEASE_ON_CLOSE, false)).thenReturn(true);
        when(reservationConnectionCloseTracker.isMarkedForRelease(any(), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("test"));
        assertFalse(interceptor.before(request, response));
        verify(concurrentConnectionManager, never()).releaseConnection(any(), any(), any());
        verify(response, never()).sendError(eq(WebConstants.SC_SERVICE_ERROR), any());
    }

    @Test
    void testBefore_putPushWithReleaseOnCloseEnabled_doesNotTrackReservation() throws Exception {
        stubReservationStatus(ReservationStatus.ACCEPTED);
        stubEmptySuspendIgnoreLists();
        when(parameterService.is(ParameterConstants.CONCURRENT_RESERVATION_RELEASE_ON_CLOSE, false)).thenReturn(true);
        assertTrue(interceptor.before(request, response));
        verifyNoInteractions(reservationConnectionCloseTracker);
    }

    @Test
    void testBefore_withLegacyConstructor_headPushAcceptsWithoutTracking() throws Exception {
        NodeConcurrencyInterceptor legacyInterceptor = new NodeConcurrencyInterceptor(concurrentConnectionManager, configurationService, nodeService,
                statisticManager);
        stubAcceptedHeadPush();
        assertFalse(legacyInterceptor.before(request, response));
        verify(response, never()).sendError(eq(WebConstants.SC_SERVICE_ERROR), any());
        verifyNoInteractions(reservationConnectionCloseTracker);
    }

    @Test
    void testBefore_withLegacyConstructor_putPushProceeds() throws Exception {
        NodeConcurrencyInterceptor legacyInterceptor = new NodeConcurrencyInterceptor(concurrentConnectionManager, configurationService, nodeService,
                statisticManager);
        stubReservationStatus(ReservationStatus.ACCEPTED);
        stubEmptySuspendIgnoreLists();
        assertTrue(legacyInterceptor.before(request, response));
    }

    private void stubAcceptedHeadPush() {
        when(request.getMethod()).thenReturn("HEAD");
        when(request.getHeader(WebConstants.CHANNEL_QUEUE)).thenReturn(CHANNEL_ID);
        stubReservationStatus(ReservationStatus.ACCEPTED);
        stubEmptySuspendIgnoreLists();
    }

    private void stubEmptySuspendIgnoreLists() {
        NodeChannels nodeChannels = mock(NodeChannels.class);
        when(nodeChannels.getSuspendChannelsAsString(anyString())).thenReturn("");
        when(nodeChannels.getIgnoreChannelsAsString(anyString())).thenReturn("");
        when(configurationService.getSuspendIgnoreChannelLists(NODE_ID)).thenReturn(nodeChannels);
    }

    @Test
    void before_pushStatusPost_doesNotRequireExistingReservation() throws Exception {
        when(request.getRequestURI()).thenReturn("/sync/corp-000/pushstatus/");
        when(request.getMethod()).thenReturn("POST");
        stubReservationStatus(ReservationStatus.ACCEPTED);
        interceptor.before(request, response);
        verify(concurrentConnectionManager).reserveConnection(eq(NODE_ID), any(), eq("/sync/corp-000/pushstatus/"), eq(ReservationType.HARD), eq(false));
    }

    @Test
    void before_pushPut_requiresExistingReservation() throws Exception {
        stubReservationStatus(ReservationStatus.ACCEPTED);
        interceptor.before(request, response);
        verify(concurrentConnectionManager).reserveConnection(eq(NODE_ID), any(), eq(PUSH_URI), eq(ReservationType.HARD), eq(true));
    }

    @Test
    void hasPathSegment_matchesWholeSegmentsOnly() {
        assertTrue(NodeConcurrencyInterceptor.hasPathSegment("/corp-000/push", "push"));
        assertTrue(NodeConcurrencyInterceptor.hasPathSegment("/corp-000/filesync/push", "push"));
        assertTrue(NodeConcurrencyInterceptor.hasPathSegment("/corp-000/compare/push", "push"));
        assertTrue(NodeConcurrencyInterceptor.hasPathSegment("/corp-000/filesync/push", "filesync"));
        assertFalse(NodeConcurrencyInterceptor.hasPathSegment("/corp-000/pushstatus/", "push"));
        assertFalse(NodeConcurrencyInterceptor.hasPathSegment("/pushers/pull", "push"));
    }
}
