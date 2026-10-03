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
package org.jumpmind.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.jumpmind.log.LogThrottle.ThrottledLogMessageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class ThrottledLoggerTest {
    private static final long START_TIME = 1000;
    private static final long INFO_INTERVAL_MS = 30000;
    private static final long DEBUG_INTERVAL_MS = 5000;
    Logger log;
    ThrottledLogger throttledLogger;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        throttledLogger = new ThrottledLogger(log, new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, true));
    }

    @Test
    void testInfoWritesNothingBeforeInterval() {
        throttledLogger.info(START_TIME + INFO_INTERVAL_MS, "message {}", 1);
        verifyNoMoreInteractions(log);
    }

    @Test
    void testInfoWritesMessageAfterInterval() {
        throttledLogger.info(START_TIME + INFO_INTERVAL_MS + 1, "message {} {}", 1, 2);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(log).info(anyString(), arguments.capture());
        assertEquals(Arrays.<Object> asList(1, 2), Arrays.asList(arguments.getValue()));
        verifyNoMoreInteractions(log);
    }

    @Test
    void testInfoWritesOncePerInterval() {
        long firstTime = START_TIME + INFO_INTERVAL_MS + 1;
        throttledLogger.info(firstTime, "message {}", 1);
        throttledLogger.info(firstTime + INFO_INTERVAL_MS, "message {}", 2);
        verify(log, times(1)).info(anyString(), any(Object[].class));
        throttledLogger.info(firstTime + INFO_INTERVAL_MS + 1, "message {}", 3);
        verify(log, times(2)).info(anyString(), any(Object[].class));
    }

    @Test
    void testDebugWritesMessageAfterDebugIntervalBeforeInfoInterval() {
        throttledLogger.debug(START_TIME + DEBUG_INTERVAL_MS + 1, "message {}", 1);
        verify(log).debug(anyString(), any(Object[].class));
        verifyNoMoreInteractions(log);
    }

    @Test
    void testDebugWritesNothingWhenInfoIsDue() {
        throttledLogger.debug(START_TIME + INFO_INTERVAL_MS + 1, "message {}", 1);
        verifyNoMoreInteractions(log);
    }

    @Test
    void testDebugWritesNothingBeforeDebugInterval() {
        throttledLogger.debug(START_TIME + DEBUG_INTERVAL_MS, "message {}", 1);
        verifyNoMoreInteractions(log);
    }

    @Test
    void testGetLevelDelegatesToThrottle() {
        assertEquals(ThrottledLogMessageLevel.NONE, throttledLogger.getLevel(START_TIME));
        assertEquals(ThrottledLogMessageLevel.DEBUG, throttledLogger.getLevel(START_TIME + DEBUG_INTERVAL_MS + 1));
        assertEquals(ThrottledLogMessageLevel.INFO, throttledLogger.getLevel(START_TIME + INFO_INTERVAL_MS + 1));
    }

    @Test
    void testForInfoOnlyNeverWritesDebug() {
        ThrottledLogger infoOnlyLogger = ThrottledLogger.forInfoOnly(log, START_TIME, INFO_INTERVAL_MS);
        infoOnlyLogger.debug(START_TIME + INFO_INTERVAL_MS, "message {}", 1);
        assertEquals(false, infoOnlyLogger.isInfoDue(START_TIME + INFO_INTERVAL_MS));
        assertEquals(true, infoOnlyLogger.isInfoDue(START_TIME + INFO_INTERVAL_MS + 1));
        verifyNoMoreInteractions(log);
    }

    @Test
    void testIsDebugDueDelegatesToThrottle() {
        assertEquals(false, throttledLogger.isDebugDue(START_TIME + DEBUG_INTERVAL_MS));
        assertEquals(true, throttledLogger.isDebugDue(START_TIME + DEBUG_INTERVAL_MS + 1));
        assertEquals(true, throttledLogger.isDebugDue(START_TIME + INFO_INTERVAL_MS + 1));
    }

    @Test
    void testInfoOrDebugWritesNothingBeforeDebugInterval() {
        throttledLogger.infoOrDebug(START_TIME + DEBUG_INTERVAL_MS, "message {}", 1);
        verify(log).isTraceEnabled();
        verifyNoMoreInteractions(log);
    }

    @Test
    void testInfoOrDebugWritesDebugBetweenIntervals() {
        throttledLogger.infoOrDebug(START_TIME + DEBUG_INTERVAL_MS + 1, "message {}", 1);
        verify(log).debug(anyString(), any(Object[].class));
        verifyNoMoreInteractions(log);
    }

    @Test
    void testInfoOrDebugWritesInfoAfterInfoInterval() {
        throttledLogger.infoOrDebug(START_TIME + INFO_INTERVAL_MS + 1, "message {}", 1);
        verify(log).info(anyString(), any(Object[].class));
        verifyNoMoreInteractions(log);
    }

    @Test
    void testInfoOrDebugRestartsIntervalsAfterWriting() {
        long debugTime = START_TIME + DEBUG_INTERVAL_MS + 1;
        throttledLogger.infoOrDebug(debugTime, "message {}", 1);
        throttledLogger.infoOrDebug(debugTime + DEBUG_INTERVAL_MS, "message {}", 2);
        verify(log, times(1)).debug(anyString(), any(Object[].class));
        throttledLogger.infoOrDebug(debugTime + DEBUG_INTERVAL_MS + 1, "message {}", 3);
        verify(log, times(2)).debug(anyString(), any(Object[].class));
    }

    @Test
    void testGetLevelReturnsTraceInsteadOfNoneWhenTraceEnabled() {
        when(log.isTraceEnabled()).thenReturn(true);
        assertEquals(ThrottledLogMessageLevel.TRACE, throttledLogger.getLevel(START_TIME));
    }

    @Test
    void testGetLevelKeepsDebugAndInfoWhenTraceEnabled() {
        when(log.isTraceEnabled()).thenReturn(true);
        assertEquals(ThrottledLogMessageLevel.DEBUG, throttledLogger.getLevel(START_TIME + DEBUG_INTERVAL_MS + 1));
        assertEquals(ThrottledLogMessageLevel.INFO, throttledLogger.getLevel(START_TIME + INFO_INTERVAL_MS + 1));
    }

    @Test
    void testIsDebugDueBypassesThrottleWhenTraceEnabled() {
        when(log.isTraceEnabled()).thenReturn(true);
        assertEquals(true, throttledLogger.isDebugDue(START_TIME));
    }

    @Test
    void testInfoOrDebugWritesTraceBeforeDebugIntervalWhenTraceEnabled() {
        when(log.isTraceEnabled()).thenReturn(true);
        throttledLogger.infoOrDebug(START_TIME, "message {}", 1);
        verify(log).isTraceEnabled();
        verify(log).trace(anyString(), any(Object[].class));
        verifyNoMoreInteractions(log);
    }

    @Test
    void testInfoOrDebugTraceDoesNotRestartIntervals() {
        when(log.isTraceEnabled()).thenReturn(true);
        throttledLogger.infoOrDebug(START_TIME, "message {}", 1);
        assertEquals(ThrottledLogMessageLevel.DEBUG, throttledLogger.getLevel(START_TIME + DEBUG_INTERVAL_MS + 1));
    }

    @Test
    void testInfoOnlyLoggerBypassesThrottleForDebugWhenTraceEnabled() {
        when(log.isTraceEnabled()).thenReturn(true);
        ThrottledLogger infoOnlyLogger = ThrottledLogger.forInfoOnly(log, START_TIME, INFO_INTERVAL_MS);
        assertEquals(true, infoOnlyLogger.isDebugDue(START_TIME));
    }
}
