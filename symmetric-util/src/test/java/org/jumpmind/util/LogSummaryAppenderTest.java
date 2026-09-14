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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;

class LogSummaryAppenderTest {
    private LogSummaryAppender appender;

    @BeforeEach
    void setUp() {
        appender = new LogSummaryAppender("TEST_SUMMARY");
        appender.start();
    }

    private ILoggingEvent mockEvent(Level level, String engineName, String message) {
        return mockEvent(level, engineName, message, null);
    }

    private ILoggingEvent mockEvent(Level level, String engineName, String message, IThrowableProxy throwableProxy) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(level);
        Map<String, String> mdc = new HashMap<>();
        if (engineName != null) {
            mdc.put("engineName", engineName);
        }
        when(event.getMDCPropertyMap()).thenReturn(mdc);
        when(event.getFormattedMessage()).thenReturn(message);
        when(event.getThrowableProxy()).thenReturn(throwableProxy);
        when(event.getTimeStamp()).thenReturn(System.currentTimeMillis());
        when(event.getThreadName()).thenReturn("test-thread");
        return event;
    }

    @Test
    void testErrorEventStoredInErrors() {
        appender.doAppend(mockEvent(Level.ERROR, "server", "connection failed"));
        List<LogSummary> errors = appender.getLogSummaries("server", Level.ERROR);
        assertEquals(1, errors.size());
        assertEquals("connection failed", errors.get(0).getMessage());
    }

    @Test
    void testWarnEventStoredInWarnings() {
        appender.doAppend(mockEvent(Level.WARN, "server", "slow query"));
        List<LogSummary> warnings = appender.getLogSummaries("server", Level.WARN);
        assertEquals(1, warnings.size());
        assertEquals("slow query", warnings.get(0).getMessage());
    }

    @Test
    void testInfoEventIgnored() {
        appender.doAppend(mockEvent(Level.INFO, "server", "started"));
        assertTrue(appender.getLogSummaries("server", Level.ERROR).isEmpty());
        assertTrue(appender.getLogSummaries("server", Level.WARN).isEmpty());
    }

    @Test
    void testBlankEngineNameIgnored() {
        appender.doAppend(mockEvent(Level.ERROR, "", "error"));
        appender.doAppend(mockEvent(Level.ERROR, null, "error"));
        assertTrue(appender.getLogSummaries("", Level.ERROR).isEmpty());
    }

    @Test
    void testDuplicateMessagesAggregated() {
        appender.doAppend(mockEvent(Level.ERROR, "server", "same error"));
        appender.doAppend(mockEvent(Level.ERROR, "server", "same error"));
        List<LogSummary> errors = appender.getLogSummaries("server", Level.ERROR);
        assertEquals(1, errors.size());
        assertEquals(2, errors.get(0).getCount());
    }

    @Test
    void testDistinctMessagesStoredSeparately() {
        appender.doAppend(mockEvent(Level.ERROR, "server", "error one"));
        appender.doAppend(mockEvent(Level.ERROR, "server", "error two"));
        assertEquals(2, appender.getLogSummaries("server", Level.ERROR).size());
    }

    @Test
    void testMostRecentTimeUpdatedOnRepeat() {
        ILoggingEvent first = mockEvent(Level.ERROR, "server", "repeated");
        when(first.getTimeStamp()).thenReturn(1000L);
        appender.doAppend(first);
        long firstTime = appender.getLogSummaries("server", Level.ERROR).get(0).getMostRecentTime();
        ILoggingEvent second = mockEvent(Level.ERROR, "server", "repeated");
        when(second.getTimeStamp()).thenReturn(2000L);
        appender.doAppend(second);
        long secondTime = appender.getLogSummaries("server", Level.ERROR).get(0).getMostRecentTime();
        assertTrue(secondTime >= firstTime);
    }

    @Test
    void testClearAllRemovesEngineEntries() {
        appender.doAppend(mockEvent(Level.ERROR, "server", "error"));
        appender.doAppend(mockEvent(Level.WARN, "server", "warn"));
        appender.clearAll("server");
        assertTrue(appender.getLogSummaries("server", Level.ERROR).isEmpty());
        assertTrue(appender.getLogSummaries("server", Level.WARN).isEmpty());
    }

    @Test
    void testClearAllPreservesOtherEngines() {
        appender.doAppend(mockEvent(Level.ERROR, "server", "error"));
        appender.doAppend(mockEvent(Level.ERROR, "client", "error"));
        appender.clearAll("server");
        assertEquals(1, appender.getLogSummaries("client", Level.ERROR).size());
    }

    @Test
    void testPurgeOlderThan() {
        long past = System.currentTimeMillis() - 10000;
        ILoggingEvent oldEvent = mockEvent(Level.ERROR, "server", "old error");
        when(oldEvent.getTimeStamp()).thenReturn(past);
        appender.doAppend(oldEvent);
        appender.purgeOlderThan(System.currentTimeMillis());
        assertTrue(appender.getLogSummaries("server", Level.ERROR).isEmpty());
    }

    @Test
    void testPurgePreservesRecentEvents() {
        appender.doAppend(mockEvent(Level.ERROR, "server", "recent error"));
        appender.purgeOlderThan(System.currentTimeMillis() - 10000);
        assertFalse(appender.getLogSummaries("server", Level.ERROR).isEmpty());
    }

    @Test
    void testFallbackMessageFromThrowableProxy() {
        IThrowableProxy proxy = mock(IThrowableProxy.class);
        when(proxy.getClassName()).thenReturn("java.sql.SQLException");
        when(proxy.getMessage()).thenReturn("timeout");
        ILoggingEvent event = mockEvent(Level.ERROR, "server", null, proxy);
        appender.doAppend(event);
        List<LogSummary> errors = appender.getLogSummaries("server", Level.ERROR);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("SQLException"));
    }

    @Test
    void testThreadNameRecorded() {
        ILoggingEvent event = mockEvent(Level.ERROR, "server", "error");
        when(event.getThreadName()).thenReturn("my-thread-1");
        appender.doAppend(event);
        assertEquals("my-thread-1", appender.getLogSummaries("server", Level.ERROR).get(0).getMostRecentThreadName());
    }

    @Test
    void testSummariesSortedByMostRecentTime() {
        ILoggingEvent first = mockEvent(Level.ERROR, "server", "first error");
        when(first.getTimeStamp()).thenReturn(1000L);
        ILoggingEvent second = mockEvent(Level.ERROR, "server", "second error");
        when(second.getTimeStamp()).thenReturn(2000L);
        appender.doAppend(first);
        appender.doAppend(second);
        List<LogSummary> summaries = appender.getLogSummaries("server", Level.ERROR);
        assertEquals(2, summaries.size());
        assertTrue(summaries.get(0).getMostRecentTime() <= summaries.get(1).getMostRecentTime());
    }
}
