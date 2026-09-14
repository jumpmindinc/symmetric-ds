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
package org.jumpmind.symmetric.util;

import java.util.Comparator;

public class CounterStatComparator implements Comparator<CounterStat> {
    protected boolean sortAscending = true;

    public CounterStatComparator() {
    }

    public CounterStatComparator(boolean sortAscending) {
        this.sortAscending = sortAscending;
    }

    @Override
    public int compare(CounterStat o1, CounterStat o2) {
        int compare = 0;
        if (o1 != null && o2 != null) {
            if (o1.getCount() > o2.getCount()) {
                compare = 1;
            } else if (o1.getCount() < o2.getCount()) {
                compare = -1;
            }
        } else if (o1 == null) {
            compare = -1;
        } else {
            compare = 1;
        }
        return sortAscending ? compare : compare * -1;
    }
}
