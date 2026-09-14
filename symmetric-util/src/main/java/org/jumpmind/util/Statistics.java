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
package org.jumpmind.util;

import java.util.HashMap;
import java.util.Map;

public class Statistics {
    Map<String, Long> stats = new HashMap<String, Long>();
    Map<String, Long> timers = new HashMap<String, Long>();
    Map<String, Map<String, Long>> tableStats = new HashMap<String, Map<String, Long>>();

    public void increment(String category) {
        increment(category, 1);
    }

    public long get(String category) {
        Long value = stats.get(category);
        if (value != null) {
            return value;
        } else {
            return 0l;
        }
    }

    public void set(String category, long value) {
        stats.put(category, value);
    }

    public boolean contains(String category) {
        return stats.containsKey(category);
    }

    public void increment(String category, long increment) {
        Long value = stats.get(category);
        if (value == null) {
            value = increment;
        } else {
            value = value + increment;
        }
        stats.put(category, value);
    }

    public void incrementTableStats(String tableName, String dmlType, long increment) {
        Map<String, Long> tableStatMap = tableStats.get(tableName);
        if (tableStatMap == null) {
            tableStatMap = new HashMap<String, Long>();
        }
        Long value = tableStatMap.get(dmlType);
        if (value == null) {
            value = increment;
        } else {
            value = value + increment;
        }
        tableStatMap.put(dmlType, value);
        tableStats.put(tableName, tableStatMap);
    }

    public Map<String, Map<String, Long>> getTableStats() {
        return this.tableStats;
    }

    public void startTimer(String category) {
        timers.put(category, System.currentTimeMillis());
    }

    public long stopTimer(String category) {
        long time = 0;
        Long startTime = timers.get(category);
        if (startTime != null) {
            time = System.currentTimeMillis() - startTime;
            increment(category, time);
        }
        timers.remove(category);
        return time;
    }

    @Override
    public String toString() {
        return stats.toString();
    }
}
