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
import static org.mockito.Mockito.mock;

import java.util.List;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRouterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class DataGapDetectorSummaryLoggingTest {
    Logger detectorLogger;
    Level originalLevel;
    ListAppender<ILoggingEvent> appender;
    long startTime;
    DataGapFastDetector detector;

    @BeforeEach
    void setUp() {
        detectorLogger = (Logger) LoggerFactory.getLogger(DataGapFastDetector.class.getName());
        originalLevel = detectorLogger.getLevel();
        detectorLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<ILoggingEvent>();
        appender.start();
        detectorLogger.addAppender(appender);
        startTime = System.currentTimeMillis();
        detector = new DataGapFastDetector(mock(IDataService.class), mock(IParameterService.class), mock(IContextService.class),
                mock(ISymmetricDialect.class), mock(IRouterService.class), mock(IStatisticManager.class), mock(INodeService.class),
                mock(IClusterService.class));
        detector.getDetectionSummary().recordDetection(100, 3);
    }

    @AfterEach
    void tearDown() {
        detectorLogger.detachAppender(appender);
        detectorLogger.setLevel(originalLevel);
    }

    @Test
    void testNothingLoggedBeforeDebugThreshold() {
        detector.logGapDetectionSummary(startTime);
        assertEquals(0, appender.list.size());
    }

    @Test
    void testDebugLoggedAfterDebugThreshold() {
        detector.logGapDetectionSummary(startTime + Constants.LONG_OPERATION_DEBUG_THRESHOLD + 1000);
        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size());
        assertEquals(Level.DEBUG, events.get(0).getLevel());
        assertEquals("Gap detection took 100 ms, 0 gaps added and 0 gaps deleted since the last summary", events.get(0).getFormattedMessage());
    }

    @Test
    void testInfoLoggedAfterInfoThreshold() {
        detector.logGapDetectionSummary(startTime + Constants.LONG_OPERATION_THRESHOLD + 1000);
        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size());
        assertEquals(Level.INFO, events.get(0).getLevel());
        assertEquals(true, events.get(0).getFormattedMessage().startsWith("Gap detection summary for the last "));
    }

    @Test
    void testInfoKeepsSummaryCounters() {
        detector.logGapDetectionSummary(startTime + Constants.LONG_OPERATION_THRESHOLD + 1000);
        assertEquals(1, detector.getDetectionSummary().getCycleCount());
    }

    @Test
    void testDebugKeepsSummaryCounters() {
        detector.logGapDetectionSummary(startTime + Constants.LONG_OPERATION_DEBUG_THRESHOLD + 1000);
        assertEquals(1, detector.getDetectionSummary().getCycleCount());
    }

    @Test
    void testNothingLoggedRightAfterInfo() {
        long infoTime = startTime + Constants.LONG_OPERATION_THRESHOLD + 1000;
        detector.logGapDetectionSummary(infoTime);
        detector.logGapDetectionSummary(infoTime + 1);
        assertEquals(1, appender.list.size());
    }
}
