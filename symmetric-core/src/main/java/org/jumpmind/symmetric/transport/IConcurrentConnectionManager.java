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

import java.util.Date;
import java.util.Map;

import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.NodeConnectionStatistics;
import org.jumpmind.symmetric.transport.ConcurrentConnectionManager.Reservation;

public interface IConcurrentConnectionManager {
    public static enum ReservationType {
        /**
         * A hard reservation is one that is expected to be released. It does not have a timeout.
         */
        HARD,
        /**
         * A soft reservation is one that will time out eventually.
         */
        SOFT
    };

    public static enum ReservationStatus {
        /**
         * Reservation was accepted
         */
        ACCEPTED,
        /**
         * Second reservation was requested before releasing first one
         */
        DUPLICATE,
        /**
         * Server is busy and there are no reservations left to hand out
         */
        BUSY,
        /**
         * Reservation was not found on this server
         */
        NOT_FOUND,
        /**
         * Server is not ready to service the request, due to either database connection issues or other limitations
         */
        NOT_READY
    };

    /**
     * @param nodeId
     * @param reservationRequest
     *            if true then hold onto reservation for the time it typically takes for a node to reconnect after the initial request. Otherwise, we know that
     *            the node has actually connected for activity.
     * @return true if the connection has been reserved and the node is meant to proceed with its current operation.
     */
    public ReservationStatus reserveConnection(String nodeId, String channelId, String poolId, ReservationType reservationRequest,
            boolean requiresExistingReservation);

    public boolean releaseConnection(String nodeId, String poolId);

    public boolean releaseConnection(String nodeId, String channelId, String poolId);

    public int getReservationCount(String poolId);

    public Map<String, Map<String, NodeConnectionStatistics>> getNodeConnectionStatisticsByPoolByNodeId();

    public Map<String, Map<String, Reservation>> getActiveReservationsByNodeByPool();

    public void addToWhitelist(String nodeId);

    public String[] getWhiteList();

    public void removeFromWhiteList(String nodeId);

    public Map<String, Date> getPullReservationsByNodeId();

    public Map<String, Date> getPushReservationsByNodeId();
}