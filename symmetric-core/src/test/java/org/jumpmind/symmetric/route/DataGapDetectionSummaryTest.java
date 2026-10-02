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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.jumpmind.symmetric.common.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class DataGapDetectionSummaryTest {
    private static final long CREATION_TIME = 1000;
    private static final long INTERVAL_END_TIME = CREATION_TIME + Constants.LONG_OPERATION_THRESHOLD + 1;
    Logger log;
    DataGapDetectionSummary summary;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        summary = new DataGapDetectionSummary(log, CREATION_TIME);
    }

    @Test
    void testLogsDebugBeforeInterval() {
        summary.recordDetection(120, 4);
        summary.recordGapChanges(2, 1);
        summary.logSummary(CREATION_TIME + Constants.LONG_OPERATION_THRESHOLD);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(log).debug(anyString(), arguments.capture());
        assertEquals(Arrays.<Object> asList(120L, 2L, 1L), Arrays.asList(arguments.getValue()));
        verifyNoMoreInteractions(log);
    }

    @Test
    void testLogsInfoAfterInterval() {
        summary.recordDetection(120, 4);
        summary.recordGapChanges(2, 1);
        summary.recordLastDataId(500);
        summary.recordFullAnalysis(2000);
        summary.logSummary(INTERVAL_END_TIME);
        assertEquals(Arrays.<Object> asList(Constants.LONG_OPERATION_THRESHOLD + 1, 1, 120L, 120L, 4, 2L, 1L, "at " + new Date(2000), "500"),
                captureInfoArguments());
        verifyNoMoreInteractions(log);
    }

    @Test
    void testInfoReportsAverageAndMaximumDetectionTime() {
        summary.recordDetection(100, 3);
        summary.recordDetection(300, 5);
        summary.logSummary(INTERVAL_END_TIME);
        List<Object> arguments = captureInfoArguments();
        assertEquals(2, arguments.get(1));
        assertEquals(200L, arguments.get(2));
        assertEquals(300L, arguments.get(3));
        assertEquals(5, arguments.get(4));
    }

    @Test
    void testInfoReportsNeverAndNoneWhenNothingRecorded() {
        summary.recordDetection(10, 0);
        summary.logSummary(INTERVAL_END_TIME);
        List<Object> arguments = captureInfoArguments();
        assertEquals("never", arguments.get(7));
        assertEquals("none", arguments.get(8));
    }

    @Test
    void testCountersResetAfterInfo() {
        summary.recordDetection(120, 4);
        summary.recordGapChanges(2, 1);
        summary.logSummary(INTERVAL_END_TIME);
        assertEquals(0, summary.getCycleCount());
        assertEquals(0L, summary.getGapsAdded());
        assertEquals(0L, summary.getGapsDeleted());
    }

    @Test
    void testLastDataIdAndFullAnalysisSurviveReset() {
        summary.recordDetection(120, 4);
        summary.recordLastDataId(500);
        summary.recordFullAnalysis(2000);
        summary.logSummary(INTERVAL_END_TIME);
        assertEquals(500L, summary.getLastDataId());
        assertEquals(2000L, summary.getLastFullAnalysisTime());
        assertEquals(4, summary.getOpenGapCount());
    }

    @Test
    void testNextInfoWaitsForNewInterval() {
        summary.recordDetection(120, 4);
        summary.logSummary(INTERVAL_END_TIME);
        summary.recordDetection(80, 4);
        summary.logSummary(INTERVAL_END_TIME + Constants.LONG_OPERATION_THRESHOLD);
        verify(log).info(anyString(), any(Object[].class));
        summary.logSummary(INTERVAL_END_TIME + Constants.LONG_OPERATION_THRESHOLD + 1);
        verify(log, times(2)).info(anyString(), any(Object[].class));
    }

    private List<Object> captureInfoArguments() {
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(log).info(anyString(), arguments.capture());
        return Arrays.asList(arguments.getValue());
    }
}
