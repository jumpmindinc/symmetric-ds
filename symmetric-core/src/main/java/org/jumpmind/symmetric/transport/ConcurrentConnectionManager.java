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

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.IApplicationHealthTracker;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.IUpDownCounter;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.util.IDatabaseHealthTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @see IConcurrentConnectionManager
 */
public class ConcurrentConnectionManager implements IConcurrentConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(ConcurrentConnectionManager.class);
    protected IParameterService parameterService;
    protected Map<String, Map<String, Reservation>> activeReservationsByNodeByPool = new HashMap<String, Map<String, Reservation>>();
    protected Map<String, Map<String, NodeConnectionStatistics>> nodeConnectionStatistics = new HashMap<String, Map<String, NodeConnectionStatistics>>();
    protected Set<String> whiteList = new HashSet<String>();
    protected Map<String, Long> transportErrorTimeByNode = new HashMap<String, Long>();
    private IUpDownCounter connectionsCounter;
    private ISymDoubleGauge utilizationGauge;
    private IDatabaseHealthTracker databaseHealthTracker;

    public ConcurrentConnectionManager(IParameterService parameterService,
            IEngineMetricsService metricsService, IDatabaseHealthTracker databaseHealthTracker) {
        this.parameterService = parameterService;
        this.databaseHealthTracker = databaseHealthTracker;
        registerMetrics(metricsService);
    }

    private void registerMetrics(IEngineMetricsService metricsService) {
        if (metricsService != null) {
            connectionsCounter = metricsService.getUpDownCounter(SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS);
            utilizationGauge = metricsService.getDoubleGauge(SymMetricConstants.METRIC_ID_SERVER_CONNECTIONS_UTILIZATION);
        }
    }

    private void addConnectionAndUpdateUtilizationGauge(int connectionChange) {
        if (connectionsCounter != null) {
            connectionsCounter.add(connectionChange);
        }
        if (utilizationGauge != null) {
            utilizationGauge.setValue(calculateReservationPercentage());
        }
    }

    private synchronized double calculateReservationPercentage() {
        int maxPoolSize = parameterService.getInt(ParameterConstants.CONCURRENT_WORKERS);
        if (maxPoolSize <= 0) {
            return 0.0;
        }
        int totalReservations = 0;
        for (Map<String, Reservation> poolReservations : activeReservationsByNodeByPool.values()) {
            totalReservations += poolReservations.size();
        }
        return (totalReservations * 100.0) / maxPoolSize;
    }

    protected void logTooBusyRejection(String nodeId, String poolId) {
        getNodeConnectionStatistics(nodeId, poolId).numOfRejections++;
    }

    protected void logConnectedTimePeriod(String nodeId, long startMs, long endMs, String poolId) {
        NodeConnectionStatistics stats = getNodeConnectionStatistics(nodeId, poolId);
        stats.totalConnectionCount++;
        stats.totalConnectionTimeMs += endMs - startMs;
        stats.lastConnectionTimeMs = startMs;
    }

    private synchronized NodeConnectionStatistics getNodeConnectionStatistics(String nodeId,
            String poolId) {
        Map<String, NodeConnectionStatistics> statsMap = nodeConnectionStatistics.get(poolId);
        if (statsMap == null) {
            statsMap = new HashMap<String, NodeConnectionStatistics>();
            nodeConnectionStatistics.put(poolId, statsMap);
        }
        NodeConnectionStatistics stats = statsMap.get(nodeId);
        if (stats == null) {
            stats = new NodeConnectionStatistics();
            statsMap.put(nodeId, stats);
        }
        return stats;
    }

    @Override
    public synchronized boolean releaseConnection(String nodeId, String channelId, String poolId) {
        String reservationId = getReservationIdentifier(nodeId, channelId);
        Map<String, Reservation> reservations = getReservationMap(poolId);
        Reservation reservation = reservations.remove(reservationId);
        if (reservation != null) {
            updateReadiness(reservations);
            logConnectedTimePeriod(reservationId, reservation.createTime, System.currentTimeMillis(), poolId);
            addConnectionAndUpdateUtilizationGauge(-1);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized boolean releaseConnection(String nodeId, String poolId) {
        Map<String, Reservation> reservations = getReservationMap(poolId);
        Reservation reservation = reservations.remove(nodeId);
        if (reservation != null) {
            updateReadiness(reservations);
            logConnectedTimePeriod(nodeId, reservation.createTime, System.currentTimeMillis(), poolId);
            addConnectionAndUpdateUtilizationGauge(-1);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized void addToWhitelist(String nodeId) {
        whiteList.add(nodeId);
    }

    @Override
    public synchronized void removeFromWhiteList(String nodeId) {
        whiteList.remove(nodeId);
    }

    @Override
    public synchronized String[] getWhiteList() {
        return whiteList.toArray(new String[whiteList.size()]);
    }

    @Override
    public synchronized int getReservationCount(String poolId) {
        return getReservationMap(poolId).size();
    }

    @Override
    public ReservationStatus reserveConnection(String nodeId, String channelId, String poolId,
            ReservationType reservationRequest, boolean requiresExistingReservation) {
        // checked outside the synchronized reservation logic so a connection test never blocks other callers
        if (databaseHealthTracker != null && !databaseHealthTracker.isRuntimeDbHealthy()) {
            log.warn("Node '{}' Channel '{}' requested a {} connection, but was rejected because the runtime database is not healthy",
                    nodeId, channelId, poolId);
            return ReservationStatus.NOT_READY;
        }
        return internalReserveConnection(nodeId, channelId, poolId, reservationRequest, requiresExistingReservation);
    }

    private synchronized ReservationStatus internalReserveConnection(String nodeId, String channelId, String poolId,
            ReservationType reservationRequest, boolean requiresExistingReservation) {
        String reservationId = getReservationIdentifier(nodeId, channelId);
        log.debug("Reserving connection for {} {}", poolId, reservationId);
        Map<String, Reservation> reservations = getReservationMap(poolId);
        removeTimedOutReservations(reservations);
        Reservation existingReservation = reservations.get(reservationId);
        if (requiresExistingReservation && existingReservation == null) {
            logRejection("it was missing a reservation", nodeId, channelId, poolId);
            return ReservationStatus.NOT_FOUND;
        }
        if (existingReservation == null && !hasCapacity(reservations, nodeId)) {
            return ReservationStatus.BUSY;
        }
        if (existingReservation != null && existingReservation.getType() != ReservationType.SOFT) {
            logRejection("it already has one", nodeId, channelId, poolId);
            return ReservationStatus.DUPLICATE;
        }
        grantReservation(reservations, reservationId, nodeId, reservationRequest);
        return ReservationStatus.ACCEPTED;
    }

    private boolean hasCapacity(Map<String, Reservation> reservations, String nodeId) {
        int maxPoolSize = parameterService.getInt(ParameterConstants.CONCURRENT_WORKERS);
        return reservations.size() < maxPoolSize || whiteList.contains(nodeId);
    }

    private void grantReservation(Map<String, Reservation> reservations, String reservationId, String nodeId,
            ReservationType reservationRequest) {
        reservations.put(reservationId, new Reservation(reservationId, computeExpirationTime(reservationRequest), reservationRequest));
        transportErrorTimeByNode.remove(nodeId);
        updateReadiness(reservations);
        addConnectionAndUpdateUtilizationGauge(1);
    }

    private long computeExpirationTime(ReservationType reservationRequest) {
        long timeout = parameterService.getLong(ParameterConstants.CONCURRENT_RESERVATION_TIMEOUT);
        long expirationTime = System.currentTimeMillis() + timeout;
        if (reservationRequest != ReservationType.SOFT) {
            expirationTime += timeout; // Allow HARD reservations extra time to call releaseConnection() gracefully
        }
        return expirationTime;
    }

    private void logRejection(String reason, String nodeId, String channelId, String poolId) {
        String message = "Node '{}' Channel '{}' requested a {} connection, but was rejected because " + reason;
        if (shouldLogTransportError(nodeId)) {
            log.warn(message, nodeId, channelId, poolId);
        } else {
            log.info(message, nodeId, channelId, poolId);
        }
    }

    @Override
    public Map<String, Date> getPullReservationsByNodeId() {
        return getReservationsByNodeId("pull");
    }

    @Override
    public Map<String, Date> getPushReservationsByNodeId() {
        return getReservationsByNodeId("push");
    }

    protected Map<String, Date> getReservationsByNodeId(String urlPath) {
        Map<String, Date> byNodeId = new HashMap<String, Date>();
        Set<String> poolIds = activeReservationsByNodeByPool.keySet();
        for (String poolId : poolIds) {
            if (poolId.endsWith(urlPath)) {
                Map<String, Reservation> reservations = activeReservationsByNodeByPool.get(poolId);
                Set<String> nodeIds = new HashSet<String>(reservations.keySet());
                for (String nodeId : nodeIds) {
                    Reservation reservation = reservations.get(nodeId);
                    if (reservation != null && reservation.getType() == ReservationType.HARD) {
                        byNodeId.put(nodeId, new Date(reservation.getCreateTime()));
                    }
                }
            }
        }
        return byNodeId;
    }

    protected void removeTimedOutReservations(Map<String, Reservation> reservations) {
        long currentTime = System.currentTimeMillis();
        String[] keys = reservations.keySet().toArray(new String[reservations.size()]);
        if (keys != null) {
            for (String key : keys) {
                Reservation reservation = reservations.get(key);
                if (reservation.timeToLiveInMs < currentTime) {
                    reservations.remove(key);
                    addConnectionAndUpdateUtilizationGauge(-1);
                }
            }
            updateReadiness(reservations);
        }
    }

    private Map<String, Reservation> getReservationMap(String poolId) {
        Map<String, Reservation> reservations = activeReservationsByNodeByPool.get(poolId);
        if (reservations == null) {
            reservations = new HashMap<String, Reservation>();
            activeReservationsByNodeByPool.put(poolId, reservations);
        }
        return reservations;
    }

    public static class Reservation {
        String nodeId;
        String channelId = "0";
        long timeToLiveInMs;
        long createTime = System.currentTimeMillis();
        ReservationType type;

        public Reservation(String nodeId, long timeToLiveInMs, ReservationType type) {
            this.nodeId = nodeId;
            this.timeToLiveInMs = timeToLiveInMs;
            this.type = type;
        }

        public Reservation(String nodeId, String channelId, long timeToLiveInMs, ReservationType type) {
            this.nodeId = nodeId;
            this.timeToLiveInMs = timeToLiveInMs;
            this.type = type;
        }

        @Override
        public int hashCode() {
            return nodeId.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Reservation) {
                return nodeId.equals(((Reservation) obj).nodeId);
            } else {
                return false;
            }
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getChannelId() {
            return channelId;
        }

        public long getTimeToLiveInMs() {
            return timeToLiveInMs;
        }

        public long getCreateTime() {
            return createTime;
        }

        public ReservationType getType() {
            return type;
        }

        public String getIdentifier() {
            return ConcurrentConnectionManager.getReservationIdentifier(getNodeId(), getChannelId());
        }
    }

    public static String getReservationIdentifier(String nodeId, String channelId) {
        return channelId == null || channelId.equals("0") ? nodeId : nodeId + "-" + channelId;
    }

    @Override
    public Map<String, Map<String, NodeConnectionStatistics>> getNodeConnectionStatisticsByPoolByNodeId() {
        return this.nodeConnectionStatistics;
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

    public static class NodeConnectionStatistics {
        int numOfRejections;
        long totalConnectionCount;
        long totalConnectionTimeMs;
        long lastConnectionTimeMs;

        public int getNumOfRejections() {
            return numOfRejections;
        }

        public long getTotalConnectionCount() {
            return totalConnectionCount;
        }

        public long getTotalConnectionTimeMs() {
            return totalConnectionTimeMs;
        }

        public long getLastConnectionTimeMs() {
            return lastConnectionTimeMs;
        }
    }

    @Override
    public Map<String, Map<String, Reservation>> getActiveReservationsByNodeByPool() {
        return activeReservationsByNodeByPool;
    }

    private void updateReadiness(Map<String, Reservation> reservations) {
        IApplicationHealthTracker tracker = ApplicationHealthTracker.getTracker();
        if (tracker != null) {
            int maxPoolSize = parameterService.getInt(ParameterConstants.CONCURRENT_WORKERS);
            boolean engineReady = reservations.size() < maxPoolSize;
            tracker.setEngineReadiness(parameterService.getEngineName(), engineReady);
            log.debug("Engine readiness = {}, reservations = {}, max = {}", engineReady, reservations.size(), maxPoolSize);
        }
    }
}