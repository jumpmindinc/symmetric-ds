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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataGapDetectionSummaryTest {
    private static final long CREATION_TIME = 1000;
    private static final long LOG_TIME = 31001;
    DataGapDetectionSummary summary;

    @BeforeEach
    void setUp() {
        summary = new DataGapDetectionSummary(CREATION_TIME);
    }

    @Test
    void testInfoMessageReportsRecordedFigures() {
        summary.recordDetection(120, 4);
        summary.recordGapChanges(2, 1);
        summary.recordLastDataId(500);
        summary.recordFullAnalysis(2000);
        assertEquals("Gap detection summary for the last 30001 ms: 1 cycles, average detection time 120 ms, maximum detection time 120 ms, "
                + "4 open gaps, 2 gaps added, 1 gaps deleted, last data ID 500",
                summary.getInfoMessage(LOG_TIME));
    }

    @Test
    void testInfoMessageReportsAverageAndMaximumDetectionTime() {
        summary.recordDetection(100, 3);
        summary.recordDetection(300, 5);
        assertEquals("Gap detection summary for the last 30001 ms: 2 cycles, average detection time 200 ms, maximum detection time 300 ms, "
                + "5 open gaps, 0 gaps added, 0 gaps deleted, last data ID none",
                summary.getInfoMessage(LOG_TIME));
    }

    @Test
    void testDebugMessageReportsLastDetectionAndGapChanges() {
        summary.recordDetection(100, 3);
        summary.recordDetection(120, 4);
        summary.recordGapChanges(2, 1);
        summary.recordGapChanges(1, 1);
        assertEquals("Gap detection took 120 ms, 3 gaps added and 2 gaps deleted since the last summary", summary.getDebugMessage());
    }

    @Test
    void testResetCountersClearsCycleFigures() {
        summary.recordDetection(120, 4);
        summary.recordGapChanges(2, 1);
        summary.resetCounters(LOG_TIME);
        assertEquals(0, summary.getCycleCount());
        assertEquals(0L, summary.getGapsAdded());
        assertEquals(0L, summary.getGapsDeleted());
    }

    @Test
    void testResetCountersRestartsElapsedTime() {
        summary.recordDetection(120, 4);
        summary.resetCounters(LOG_TIME);
        summary.recordDetection(80, 4);
        assertEquals("Gap detection summary for the last 5000 ms: 1 cycles, average detection time 80 ms, maximum detection time 80 ms, "
                + "4 open gaps, 0 gaps added, 0 gaps deleted, last data ID none",
                summary.getInfoMessage(LOG_TIME + 5000));
    }

    @Test
    void testLastDataIdOpenGapsAndFullAnalysisSurviveReset() {
        summary.recordDetection(120, 4);
        summary.recordLastDataId(500);
        summary.recordFullAnalysis(2000);
        summary.resetCounters(LOG_TIME);
        assertEquals(500L, summary.getLastDataId());
        assertEquals(2000L, summary.getLastFullAnalysisTime());
        assertEquals(4, summary.getOpenGapCount());
    }
}
