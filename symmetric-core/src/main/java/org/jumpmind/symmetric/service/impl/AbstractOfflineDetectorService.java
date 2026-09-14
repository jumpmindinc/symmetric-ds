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

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.exception.HttpException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.IOfflineClientListener;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.model.RemoteNodeStatus.Status;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IOfflineDetectorService;
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
import org.jumpmind.util.ExceptionUtils;

/**
 * Abstract service that provides help methods for detecting offline status.
 */
public abstract class AbstractOfflineDetectorService extends AbstractService implements IOfflineDetectorService {
    protected IExtensionService extensionService;
    private Map<String, Long> transportErrorTimeByNode = new HashMap<String, Long>();

    public AbstractOfflineDetectorService() {
    }

    public AbstractOfflineDetectorService(IParameterService parameterService,
            ISymmetricDialect symmetricDialect, IExtensionService extensionService) {
        super(parameterService, symmetricDialect);
        this.extensionService = extensionService;
    }

    protected void fireOnline(Node remoteNode, RemoteNodeStatus status) {
        transportErrorTimeByNode.remove(remoteNode.getNodeId());
        List<IOfflineClientListener> offlineListeners = extensionService.getExtensionPointList(IOfflineClientListener.class);
        if (offlineListeners != null) {
            for (IOfflineClientListener listener : offlineListeners) {
                listener.online(remoteNode);
            }
        }
    }

    protected void fireOffline(Exception exception, Node remoteNode, RemoteNodeStatus status) {
        String syncUrl = remoteNode.getSyncUrl() == null ? parameterService.getRegistrationUrl() : remoteNode.getSyncUrl();
        if (isOffline(exception)) {
            logTransportMessage(remoteNode, "Could not communicate with {} at {} because exception {}", remoteNode, syncUrl, getExceptionMessage(exception));
            status.setStatus(Status.OFFLINE);
        } else if (isServiceUnavailable(exception)) {
            ServiceUnavailableException e = (ServiceUnavailableException) exception;
            logTransportMessage(remoteNode, "Remote node {} at {} was unavailable {}", remoteNode, syncUrl, e.getMessage() == null ? ""
                    : ": " + e.getMessage());
            status.setStatus(Status.OFFLINE);
        } else if (isServiceNotReady(exception)) {
            logTransportMessage(remoteNode, "Remote node {} at {} service not ready", remoteNode, syncUrl);
            status.setStatus(Status.OFFLINE);
        } else if (isBusy(exception)) {
            logTransportMessage(remoteNode, "Remote node {} at {} was busy", remoteNode, syncUrl);
            status.setStatus(Status.BUSY);
        } else if (isDuplicateConnection(exception)) {
            logTransportMessage(remoteNode, "Remote node {} at {} already processing a connection from this node", remoteNode, syncUrl);
            status.setStatus(Status.BUSY);
        } else if (isNoReservation(exception)) {
            log.warn("Missing reservation during push with {}", new Object[] { remoteNode });
            status.setStatus(Status.BUSY);
        } else if (isNotAuthenticated(exception)) {
            log.warn("Authorization denied from {} at {}", new Object[] { remoteNode, syncUrl });
            status.setStatus(Status.NOT_AUTHORIZED);
        } else if (isAuthenticationExpired(exception)) {
            log.info("Session expired, so will re-authenticate");
            status.setStatus(Status.NOT_AUTHORIZED);
        } else if (isSyncDisabled(exception)) {
            log.warn("Sync was not enabled for {} at {}", new Object[] { remoteNode, syncUrl });
            status.setStatus(Status.SYNC_DISABLED);
        } else if (isRegistrationNotOpen(exception)) {
            log.warn("Registration was not open at {} {}", new Object[] { remoteNode, syncUrl });
            status.setStatus(Status.REGISTRATION_REQUIRED);
        } else if (isRegistrationRequired(exception)) {
            log.warn("Registration is needed before communicating with {} at {}", new Object[] { remoteNode, syncUrl });
            status.setStatus(Status.REGISTRATION_REQUIRED);
        } else if (isRegistrationPending(exception)) {
            log.info("Registration is still pending");
            status.setStatus(Status.REGISTRATION_REQUIRED);
        } else if (isInitialLoadPending(exception)) {
            log.info("Initial load is pending for node {}", new Object[] { remoteNode });
            status.setStatus(Status.INITIAL_LOAD_PENDING);
        } else if (getHttpException(exception) != null) {
            HttpException http = getHttpException(exception);
            logTransportMessage(remoteNode, "Could not communicate with {} at {} because it returned HTTP code {} and exception {}",
                    remoteNode, syncUrl, http.getCode(), getExceptionMessage(exception));
        } else {
            log.warn(String.format("Could not communicate with node '%s' at %s because of unexpected error", remoteNode, syncUrl), exception);
            status.setStatus(Status.UNKNOWN_ERROR);
        }
        List<IOfflineClientListener> offlineListeners = extensionService.getExtensionPointList(IOfflineClientListener.class);
        if (offlineListeners != null) {
            for (IOfflineClientListener listener : offlineListeners) {
                if (isOffline(exception)) {
                    listener.offline(remoteNode);
                } else if (isBusy(exception)) {
                    listener.busy(remoteNode);
                } else if (isNotAuthenticated(exception)) {
                    listener.notAuthenticated(remoteNode);
                } else if (isSyncDisabled(exception)) {
                    listener.syncDisabled(remoteNode);
                } else if (isRegistrationRequired(exception)) {
                    listener.registrationRequired(remoteNode);
                } else {
                    listener.unknownError(remoteNode, exception);
                }
            }
        }
    }

    protected String getExceptionMessage(Exception e) {
        return e.getClass().getName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    protected void logTransportMessage(Node remoteNode, String message, Object... args) {
        if (shouldLogTransportError(remoteNode.getNodeId())) {
            log.warn(message, args);
        } else {
            log.info(message, args);
        }
    }

    protected boolean shouldLogTransportError(String nodeId) {
        long maxErrorMillis = parameterService.getLong(ParameterConstants.TRANSPORT_MAX_ERROR_MILLIS, 300000);
        Long errorTime = transportErrorTimeByNode.get(nodeId);
        if (errorTime == null) {
            errorTime = System.currentTimeMillis();
            transportErrorTimeByNode.put(nodeId, errorTime);
        }
        return System.currentTimeMillis() - errorTime >= maxErrorMillis;
    }

    protected boolean isOffline(Exception ex) {
        return is(ex, SocketException.class, ConnectException.class, SocketTimeoutException.class, UnknownHostException.class);
    }

    protected boolean isNotAuthenticated(Exception ex) {
        return is(ex, AuthenticationException.class);
    }

    protected boolean isAuthenticationExpired(Exception ex) {
        return is(ex, AuthenticationExpiredException.class);
    }

    protected boolean isBusy(Exception ex) {
        return is(ex, ConnectionRejectedException.class);
    }

    protected boolean isDuplicateConnection(Exception ex) {
        return is(ex, ConnectionDuplicateException.class);
    }

    protected boolean isServiceUnavailable(Exception ex) {
        return is(ex, ServiceUnavailableException.class);
    }

    protected boolean isServiceNotReady(Exception ex) {
        return is(ex, ServiceNotReadyException.class);
    }

    protected boolean isSyncDisabled(Exception ex) {
        return is(ex, SyncDisabledException.class);
    }

    protected boolean isRegistrationRequired(Exception ex) {
        return is(ex, RegistrationRequiredException.class);
    }

    protected boolean isRegistrationPending(Exception ex) {
        return is(ex, RegistrationPendingException.class);
    }

    protected boolean isInitialLoadPending(Exception ex) {
        return is(ex, RegistrationPendingException.class, InitialLoadPendingException.class);
    }

    protected boolean isRegistrationNotOpen(Exception ex) {
        return is(ex, RegistrationNotOpenException.class);
    }

    protected boolean isNoReservation(Exception ex) {
        return is(ex, NoReservationException.class);
    }

    protected boolean is(Exception e, Class<?>... exceptions) {
        return ExceptionUtils.is(e, exceptions);
    }

    protected HttpException getHttpException(Exception ex) {
        HttpException exception = null;
        if (ex != null) {
            Throwable cause = ExceptionUtils.getRootCause(ex);
            if (cause instanceof HttpException) {
                exception = (HttpException) cause;
            } else if (ex instanceof HttpException) {
                exception = (HttpException) ex;
            }
        }
        return exception;
    }
}