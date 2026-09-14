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
package org.jumpmind.symmetric.route;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleRouterContext extends Context {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected NodeChannel channel;
    protected boolean encountedTransactionBoundary = false;
    protected Map<String, Long> stats = new HashMap<String, Long>();
    protected String nodeId;
    protected boolean requestGapDetection = false;
    protected int batchSizeNotToExceed;
    protected Map<String, RouterTimer> routerTimers = new HashMap<String, RouterTimer>();

    public SimpleRouterContext() {
    }

    public SimpleRouterContext(String nodeId, NodeChannel channel) {
        this.nodeId = nodeId;
        this.channel = channel;
    }

    public RouterTimer addQueryTime(String routerId, long qt) {
        RouterTimer rt = routerTimers.get(routerId);
        if (rt == null) {
            rt = new RouterTimer();
            routerTimers.put(routerId, rt);
        }
        rt.addQueryTime(qt);
        return rt;
    }

    public BinaryEncoding getBinaryEncoding() {
        return null;
    }

    public long getBatchId() {
        return -1;
    }

    public String getSourceNodeId() {
        return nodeId;
    }

    public NodeChannel getChannel() {
        return this.channel;
    }

    public Map<String, Object> getContextCache() {
        return this.context;
    }

    public void setEncountedTransactionBoundary(boolean encountedTransactionBoundary) {
        this.encountedTransactionBoundary = encountedTransactionBoundary;
    }

    public boolean isEncountedTransactionBoundary() {
        return this.encountedTransactionBoundary;
    }

    public int getBatchSizeNotToExceed() {
        return batchSizeNotToExceed;
    }

    public void setBatchSizeNotToExceed(int batchSizeNotToExceed) {
        this.batchSizeNotToExceed = batchSizeNotToExceed;
    }

    synchronized public void incrementStat(long amount, String name) {
        Long val = stats.get(name);
        if (val == null) {
            val = 0l;
        }
        val += amount;
        stats.put(name, val);
    }

    synchronized public long getStat(String name) {
        Long val = (Long) stats.get(name);
        if (val == null) {
            val = 0l;
        }
        return val;
    }

    synchronized public void logStats(Logger log, long totalTimeInMs) {
        boolean infoLevel = totalTimeInMs > Constants.LONG_OPERATION_THRESHOLD;
        if ((infoLevel && log.isInfoEnabled()) || log.isDebugEnabled()) {
            Set<String> keys = new TreeSet<String>(stats.keySet());
            StringBuilder statsPrintout = new StringBuilder(channel.getChannelId());
            for (String key : keys) {
                statsPrintout.append(", " + key + "=" + stats.get(key));
            }
            if (infoLevel) {
                log.info("Routed channel {}", statsPrintout);
            } else {
                log.debug("Routed channel {}", statsPrintout);
            }
        }
    }

    synchronized public void transferStats(SimpleRouterContext ctx) {
        Set<String> keys = new HashSet<String>(ctx.stats.keySet());
        for (String key : keys) {
            Long value = stats.get(key);
            if (value == null) {
                value = 0l;
            }
            incrementStat(value, key);
        }
    }

    public class RouterTimer {
        private long queryTime;
        private long totalQueryTime;

        public RouterTimer() {
            queryTime = 0;
            totalQueryTime = 0;
        }

        public void resetQueryTime() {
            queryTime = 0;
        }

        public long getQueryTime() {
            return queryTime;
        }

        public long getTotalQueryTime() {
            return totalQueryTime;
        }

        public void addQueryTime(long qt) {
            queryTime += qt;
            totalQueryTime += qt;
        }
    }
}
