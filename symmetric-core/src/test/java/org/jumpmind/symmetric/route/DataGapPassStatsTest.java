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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DataGapPassStatsTest {
    @Test
    void testNewStatsAreZero() {
        DataGapPassStats stats = new DataGapPassStats();
        assertEquals(0, stats.getIdsFound());
        assertEquals(0L, stats.getRangeChecked());
        assertEquals(0, stats.getGapsAdded());
        assertEquals(0, stats.getGapsDeleted());
        assertEquals(0, stats.getGapsExpireChecked());
    }

    @Test
    void testAddIdsFoundAccumulates() {
        DataGapPassStats stats = new DataGapPassStats();
        stats.addIdsFound(3);
        stats.addIdsFound(4);
        assertEquals(7, stats.getIdsFound());
    }

    @Test
    void testAddRangeCheckedAccumulatesBeyondIntRange() {
        DataGapPassStats stats = new DataGapPassStats();
        stats.addRangeChecked(Integer.MAX_VALUE);
        stats.addRangeChecked(Integer.MAX_VALUE);
        assertEquals(2L * Integer.MAX_VALUE, stats.getRangeChecked());
    }

    @Test
    void testIncrementsCountIndependently() {
        DataGapPassStats stats = new DataGapPassStats();
        stats.incrementGapsAdded();
        stats.incrementGapsAdded();
        stats.incrementGapsDeleted();
        stats.incrementGapsExpireChecked();
        stats.incrementGapsExpireChecked();
        stats.incrementGapsExpireChecked();
        assertEquals(2, stats.getGapsAdded());
        assertEquals(1, stats.getGapsDeleted());
        assertEquals(3, stats.getGapsExpireChecked());
    }
}
