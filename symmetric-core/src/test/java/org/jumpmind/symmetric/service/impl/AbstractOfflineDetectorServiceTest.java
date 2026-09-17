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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.exception.HttpException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.IOfflineClientListener;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatus.Status;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.InitialLoadPendingException;
import org.jumpmind.symmetric.service.RegistrationNotOpenException;
import org.jumpmind.symmetric.service.RegistrationPendingException;
import org.jumpmind.symmetric.service.RegistrationRequiredException;
import org.jumpmind.symmetric.transport.AuthenticationException;
import org.jumpmind.symmetric.transport.AuthenticationExpiredException;
import org.jumpmind.symmetric.transport.ConnectionDuplicateException;
import org.jumpmind.symmetric.transport.ConnectionRejectedException;
import org.jumpmind.symmetric.transport.NoReservationException;
import org.jumpmind.symmetric.transport.ServiceNotReadyException;
import org.jumpmind.symmetric.transport.ServiceUnavailableException;
import org.jumpmind.symmetric.transport.SyncDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractOfflineDetectorServiceTest {
    private IParameterService parameterService;
    private IExtensionService extensionService;
    private IOfflineClientListener listener;
    private Node remoteNode;
    private RemoteNodeStatus status;
    private TestOfflineDetectorService service;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        extensionService = mock(IExtensionService.class);
        listener = mock(IOfflineClientListener.class);
        when(extensionService.getExtensionPointList(IOfflineClientListener.class)).thenReturn(Collections.singletonList(listener));
        remoteNode = new Node("store-001", "store");
        remoteNode.setSyncUrl("http://localhost:31415/sync/store-001");
        status = new RemoteNodeStatus("store-001", "default", null);
        service = new TestOfflineDetectorService(parameterService, newSymmetricDialect(), extensionService);
    }

    @Test
    void testIsOffline_withSocketExceptions() {
        assertTrue(service.isOffline(new SocketException()));
        assertTrue(service.isOffline(new ConnectException()));
        assertTrue(service.isOffline(new SocketTimeoutException()));
        assertTrue(service.isOffline(new UnknownHostException()));
    }

    @Test
    void testIsOffline_withWrappedCause() {
        assertTrue(service.isOffline(new IOException(new ConnectException())));
    }

    @Test
    void testIsOffline_withUnrelatedException() {
        assertFalse(service.isOffline(new IllegalStateException()));
    }

    @Test
    void testIs_withNullException() {
        assertFalse(service.is(null, SocketException.class));
    }

    @Test
    void testExceptionClassification() {
        assertTrue(service.isNotAuthenticated(new AuthenticationException()));
        assertTrue(service.isAuthenticationExpired(new AuthenticationExpiredException()));
        assertTrue(service.isBusy(new ConnectionRejectedException()));
        assertTrue(service.isDuplicateConnection(new ConnectionDuplicateException()));
        assertTrue(service.isServiceUnavailable(new ServiceUnavailableException()));
        assertTrue(service.isServiceNotReady(new ServiceNotReadyException()));
        assertTrue(service.isSyncDisabled(new SyncDisabledException()));
        assertTrue(service.isRegistrationRequired(new RegistrationRequiredException()));
        assertTrue(service.isRegistrationPending(new RegistrationPendingException()));
        assertTrue(service.isRegistrationNotOpen(new RegistrationNotOpenException()));
        assertTrue(service.isNoReservation(new NoReservationException()));
    }

    @Test
    void testIsInitialLoadPending_treatsARegistrationPendingExceptionAsPendingToo() {
        assertTrue(service.isInitialLoadPending(new InitialLoadPendingException()));
        assertTrue(service.isInitialLoadPending(new RegistrationPendingException()));
    }

    @Test
    void testGetExceptionMessage_withAMessage() {
        assertEquals("java.lang.IllegalStateException: boom", service.getExceptionMessage(new IllegalStateException("boom")));
    }

    @Test
    void testGetExceptionMessage_withoutAMessage() {
        assertEquals("java.lang.IllegalStateException", service.getExceptionMessage(new IllegalStateException()));
    }

    @Test
    void testGetHttpException_withTheExceptionItself() {
        HttpException exception = new HttpException(503, "unavailable");
        assertSame(exception, service.getHttpException(exception));
    }

    @Test
    void testGetHttpException_withTheExceptionAsRootCause() {
        HttpException cause = new HttpException(401, "denied");
        assertSame(cause, service.getHttpException(new IOException(cause)));
    }

    @Test
    void testGetHttpException_withAnUnrelatedException() {
        assertNull(service.getHttpException(new IllegalStateException()));
    }

    @Test
    void testGetHttpException_withNull() {
        assertNull(service.getHttpException(null));
    }

    @Test
    void testShouldLogTransportError_onTheFirstErrorForANode() {
        when(parameterService.getLong(ParameterConstants.TRANSPORT_MAX_ERROR_MILLIS, 300000)).thenReturn(300000L);
        assertFalse(service.shouldLogTransportError("store-001"));
    }

    @Test
    void testShouldLogTransportError_onceTheThresholdHasElapsed() {
        when(parameterService.getLong(ParameterConstants.TRANSPORT_MAX_ERROR_MILLIS, 300000)).thenReturn(0L);
        assertTrue(service.shouldLogTransportError("store-001"));
    }

    @Test
    void testFireOnline_notifiesTheListeners() {
        service.fireOnline(remoteNode, status);
        verify(listener).online(remoteNode);
    }

    @Test
    void testFireOnline_clearsTheRecordedTransportErrorTime() {
        when(parameterService.getLong(ParameterConstants.TRANSPORT_MAX_ERROR_MILLIS, 300000)).thenReturn(0L);
        service.shouldLogTransportError("store-001");
        service.fireOnline(remoteNode, status);
        when(parameterService.getLong(ParameterConstants.TRANSPORT_MAX_ERROR_MILLIS, 300000)).thenReturn(300000L);
        assertFalse(service.shouldLogTransportError("store-001"));
    }

    @Test
    void testFireOffline_withAConnectionFailure() {
        service.fireOffline(new ConnectException(), remoteNode, status);
        assertEquals(Status.OFFLINE, status.getStatus());
        verify(listener).offline(remoteNode);
    }

    @Test
    void testFireOffline_withAnUnavailableService() {
        service.fireOffline(new ServiceUnavailableException(), remoteNode, status);
        assertEquals(Status.OFFLINE, status.getStatus());
    }

    @Test
    void testFireOffline_withAServiceThatIsNotReady() {
        service.fireOffline(new ServiceNotReadyException(), remoteNode, status);
        assertEquals(Status.OFFLINE, status.getStatus());
    }

    @Test
    void testFireOffline_withARejectedConnection() {
        service.fireOffline(new ConnectionRejectedException(), remoteNode, status);
        assertEquals(Status.BUSY, status.getStatus());
        verify(listener).busy(remoteNode);
    }

    @Test
    void testFireOffline_withADuplicateConnection() {
        service.fireOffline(new ConnectionDuplicateException(), remoteNode, status);
        assertEquals(Status.BUSY, status.getStatus());
    }

    @Test
    void testFireOffline_withAMissingReservation() {
        service.fireOffline(new NoReservationException(), remoteNode, status);
        assertEquals(Status.BUSY, status.getStatus());
    }

    @Test
    void testFireOffline_withAnAuthenticationFailure() {
        service.fireOffline(new AuthenticationException(), remoteNode, status);
        assertEquals(Status.NOT_AUTHORIZED, status.getStatus());
        verify(listener).notAuthenticated(remoteNode);
    }

    @Test
    void testFireOffline_withAnExpiredSession() {
        service.fireOffline(new AuthenticationExpiredException(), remoteNode, status);
        assertEquals(Status.NOT_AUTHORIZED, status.getStatus());
    }

    @Test
    void testFireOffline_withSyncDisabled() {
        service.fireOffline(new SyncDisabledException(), remoteNode, status);
        assertEquals(Status.SYNC_DISABLED, status.getStatus());
        verify(listener).syncDisabled(remoteNode);
    }

    @Test
    void testFireOffline_withRegistrationNotOpen() {
        service.fireOffline(new RegistrationNotOpenException(), remoteNode, status);
        assertEquals(Status.REGISTRATION_REQUIRED, status.getStatus());
    }

    @Test
    void testFireOffline_withRegistrationRequired() {
        service.fireOffline(new RegistrationRequiredException(), remoteNode, status);
        assertEquals(Status.REGISTRATION_REQUIRED, status.getStatus());
        verify(listener).registrationRequired(remoteNode);
    }

    @Test
    void testFireOffline_withRegistrationPending() {
        service.fireOffline(new RegistrationPendingException(), remoteNode, status);
        assertEquals(Status.REGISTRATION_REQUIRED, status.getStatus());
    }

    @Test
    void testFireOffline_withAPendingInitialLoad() {
        service.fireOffline(new InitialLoadPendingException(), remoteNode, status);
        assertEquals(Status.INITIAL_LOAD_PENDING, status.getStatus());
    }

    @Test
    void testFireOffline_withAnHttpFailureLeavesTheStatusAlone() {
        service.fireOffline(new HttpException(500, "server error"), remoteNode, status);
        assertEquals(Status.NO_DATA, status.getStatus());
    }

    @Test
    void testFireOffline_withAnUnexpectedError() {
        IllegalStateException exception = new IllegalStateException("boom");
        service.fireOffline(exception, remoteNode, status);
        assertEquals(Status.UNKNOWN_ERROR, status.getStatus());
        verify(listener).unknownError(remoteNode, exception);
    }

    @Test
    void testFireOffline_withoutASyncUrlFallsBackToTheRegistrationUrl() {
        remoteNode.setSyncUrl(null);
        when(parameterService.getRegistrationUrl()).thenReturn("http://localhost:31415/sync/corp");
        service.fireOffline(new ConnectException(), remoteNode, status);
        verify(parameterService).getRegistrationUrl();
        assertEquals(Status.OFFLINE, status.getStatus());
    }

    @Test
    void testFireOffline_withoutAnyRegisteredListeners() {
        when(extensionService.getExtensionPointList(IOfflineClientListener.class)).thenReturn(null);
        service.fireOffline(new ConnectException(), remoteNode, status);
        assertEquals(Status.OFFLINE, status.getStatus());
    }

    private ISymmetricDialect newSymmetricDialect() {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        return symmetricDialect;
    }

    private static class TestOfflineDetectorService extends AbstractOfflineDetectorService {
        TestOfflineDetectorService(IParameterService parameterService, ISymmetricDialect symmetricDialect, IExtensionService extensionService) {
            super(parameterService, symmetricDialect, extensionService);
        }
    }
}
