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
package org.jumpmind.symmetric.statistic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.lang3.time.DateUtils;

abstract public class AbstractStatsByPeriodMap<T, M extends AbstractNodeHostStats> extends TreeMap<Date, T> {
    private static final long serialVersionUID = 1L;

    public AbstractStatsByPeriodMap(Date start, Date end, List<M> list, int periodSizeInMinutes) {
        Iterator<M> stats = list.iterator();
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(start);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        startCal.set(Calendar.MINUTE, round(startCal.get(Calendar.MINUTE)));
        Date periodStart = startCal.getTime();
        Date periodEnd = DateUtils.addMinutes(periodStart, periodSizeInMinutes);
        M stat = null;
        while (periodStart.before(end)) {
            if (stat == null && stats.hasNext()) {
                stat = stats.next();
            }
            if (stat != null
                    && (periodStart.equals(stat.getStartTime()) || periodStart.before(stat
                            .getStartTime())) && periodEnd.after(stat.getStartTime())) {
                add(periodStart, stat);
                stat = null;
            } else {
                if (stat != null && stat.getStartTime().before(periodStart)) {
                    stat = null;
                }
                if (!containsKey(periodStart)) {
                    addBlank(periodStart);
                }
                periodStart = periodEnd;
                periodEnd = DateUtils.addMinutes(periodStart, periodSizeInMinutes);
            }
        }
    }

    abstract protected void addBlank(Date periodStart);

    abstract protected void add(Date periodStart, M stat);

    protected int round(int value) {
        return 5 * new BigDecimal((double) value / 5d).setScale(2, RoundingMode.HALF_DOWN)
                .intValue();
    }
}
