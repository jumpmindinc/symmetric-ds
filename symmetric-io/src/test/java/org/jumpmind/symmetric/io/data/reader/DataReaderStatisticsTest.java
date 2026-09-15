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
package org.jumpmind.symmetric.io.data.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.util.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataReaderStatisticsTest {
    private DataReaderStatistics statistics;

    @BeforeEach
    void setUp() {
        statistics = new DataReaderStatistics();
    }

    @Test
    void testIsStatistics() {
        assertTrue(Statistics.class.isInstance(statistics));
    }

    @Test
    void testGet_withUnrecordedKeyReturnsZero() {
        assertEquals(0, statistics.get(DataReaderStatistics.READ_BYTE_COUNT));
    }

    @Test
    void testIncrement_accumulatesByteCount() {
        statistics.increment(DataReaderStatistics.READ_BYTE_COUNT, 10);
        statistics.increment(DataReaderStatistics.READ_BYTE_COUNT, 5);
        assertEquals(15, statistics.get(DataReaderStatistics.READ_BYTE_COUNT));
    }

    @Test
    void testIncrement_withoutAmountAddsOne() {
        statistics.increment(DataReaderStatistics.READ_RECORD_COUNT);
        assertEquals(1, statistics.get(DataReaderStatistics.READ_RECORD_COUNT));
    }

    @Test
    void testSet_replacesValue() {
        statistics.set(DataReaderStatistics.LOAD_ID, 99);
        assertEquals(99, statistics.get(DataReaderStatistics.LOAD_ID));
    }

    @Test
    void testKeysAreDistinct() {
        statistics.set(DataReaderStatistics.DATA_INSERT_ROW_COUNT, 3);
        statistics.set(DataReaderStatistics.DATA_UPDATE_ROW_COUNT, 7);
        assertEquals(3, statistics.get(DataReaderStatistics.DATA_INSERT_ROW_COUNT));
        assertEquals(7, statistics.get(DataReaderStatistics.DATA_UPDATE_ROW_COUNT));
    }
}
