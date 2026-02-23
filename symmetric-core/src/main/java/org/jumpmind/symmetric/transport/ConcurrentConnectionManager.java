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

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
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

    public ConcurrentConnectionManager(IParameterService parameterService,
            IStatisticManager statisticManager) {
        this.parameterService = parameterService;
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
            logConnectedTimePeriod(reservationId, reservation.createTime, System.currentTimeMillis(), poolId);
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
            logConnectedTimePeriod(nodeId, reservation.createTime, System.currentTimeMillis(),
                    poolId);
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
    public synchronized ReservationStatus reserveConnection(String nodeId, String channelId, String poolId,
            ReservationType reservationRequest, boolean requiresExistingReservation) {
        String reservationId = getReservationIdentifier(nodeId, channelId);
        log.debug("Reserving connection for {} {}", poolId, reservationId);
        Map<String, Reservation> reservations = getReservationMap(poolId);
        int maxPoolSize = parameterService.getInt(ParameterConstants.CONCURRENT_WORKERS);
        long timeout = parameterService.getLong(ParameterConstants.CONCURRENT_RESERVATION_TIMEOUT);
        removeTimedOutReservations(reservations);
        Reservation existingReservation = reservations.get(reservationId);
        if (requiresExistingReservation && existingReservation == null) {
            String message = "Node '{}' Channel '{}' requested a {} connection, but was rejected because it was missing a reservation";
            if (shouldLogTransportError(nodeId)) {
                log.warn(message, nodeId, channelId, poolId);
            } else {
                log.info(message, nodeId, channelId, poolId);
            }
            return ReservationStatus.NOT_FOUND;
        } else if (reservations.size() < maxPoolSize || existingReservation != null || whiteList.contains(nodeId)) {
            if (existingReservation == null || existingReservation.getType() == ReservationType.SOFT) {
                long reservationExpiration = System.currentTimeMillis() + timeout;
                if (reservationRequest != ReservationType.SOFT) {
                    reservationExpiration += timeout; // Allow HARD reservations extra time to call releaseConnection() gracefully
                }
                reservations.put(reservationId, new Reservation(reservationId, reservationExpiration, reservationRequest));
                transportErrorTimeByNode.remove(nodeId);
                return ReservationStatus.ACCEPTED;
            } else {
                String message = "Node '{}' Channel '{}' requested a {} connection, but was rejected because it already has one";
                if (shouldLogTransportError(nodeId)) {
                    log.warn(message, nodeId, channelId, poolId);
                } else {
                    log.info(message, nodeId, channelId, poolId);
                }
                return ReservationStatus.DUPLICATE;
            }
        } else {
            return ReservationStatus.BUSY;
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
                }
            }
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
}