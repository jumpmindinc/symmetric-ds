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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelStatsByPeriodMap extends AbstractStatsByPeriodMap<Map<String, ChannelStats>, ChannelStats> {
    private static final long serialVersionUID = 1L;

    public ChannelStatsByPeriodMap(Date start, Date end, List<ChannelStats> list,
            int periodSizeInMinutes) {
        super(start, end, list, periodSizeInMinutes);
    }

    @Override
    protected void add(Date periodStart, ChannelStats stat) {
        Map<String, ChannelStats> map = get(periodStart);
        if (map == null) {
            map = new HashMap<String, ChannelStats>();
            put(periodStart, map);
        }
        ChannelStats existing = map.get(stat.getChannelId());
        if (existing == null) {
            map.put(stat.getChannelId(), stat);
        } else {
            existing.add(stat);
        }
    }

    @Override
    protected void addBlank(Date periodStart) {
        put(periodStart, new HashMap<String, ChannelStats>());
    }
}
