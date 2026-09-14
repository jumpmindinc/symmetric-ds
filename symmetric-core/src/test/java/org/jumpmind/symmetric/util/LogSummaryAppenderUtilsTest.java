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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.jumpmind.util.LogSummary;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

class LogSummaryAppenderUtilsTest {
    @Test
    void testGetLogSummaryWarningsReturnsEmptyWhenNone() {
        List<LogSummary> warnings = LogSummaryAppenderUtils.getLogSummaryWarnings("nonexistent-engine");
        assertNotNull(warnings);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void testGetLogSummaryErrorsReturnsEmptyWhenNone() {
        List<LogSummary> errors = LogSummaryAppenderUtils.getLogSummaryErrors("nonexistent-engine");
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void testGetLevelReturnsNonNull() {
        Level level = LogSummaryAppenderUtils.getLevel("org.jumpmind");
        assertNotNull(level);
    }

    @Test
    void testGetRootLevelReturnsNonNull() {
        Level level = LogSummaryAppenderUtils.getRootLevel();
        assertNotNull(level);
    }

    @Test
    void testClearAllLogSummariesLeavesListEmpty() {
        LogSummaryAppenderUtils.clearAllLogSummaries("any-engine");
        assertTrue(LogSummaryAppenderUtils.getLogSummaryErrors("any-engine").isEmpty());
        assertTrue(LogSummaryAppenderUtils.getLogSummaryWarnings("any-engine").isEmpty());
    }

    @Test
    void testSetAndGetLevel() {
        LogSummaryAppenderUtils.setLevel("org.jumpmind.test.utils", Level.DEBUG);
        Level result = LogSummaryAppenderUtils.getLevel("org.jumpmind.test.utils");
        assertNotNull(result);
    }

    @Test
    void testIsDefaultLogLayoutPatternReturnsFalseWithNoFileAppender() {
        assertTrue(!LogSummaryAppenderUtils.isDefaultLogLayoutPattern() || LogSummaryAppenderUtils.getLogFile() != null);
    }

    @Test
    void testAddProtectedLoggerPreventsRaisingLevelAboveMinimum() {
        String loggerName = "org.jumpmind.test.protected.utils";
        LogSummaryAppenderUtils.setLevel(loggerName, Level.WARN);
        LogSummaryAppenderUtils.addProtectedLogger(loggerName, Level.WARN);
        Level beforeBlock = LogSummaryAppenderUtils.getLevel(loggerName);
        LogSummaryAppenderUtils.setLevel(loggerName, Level.ERROR);
        assertEquals(beforeBlock, LogSummaryAppenderUtils.getLevel(loggerName));
        LogSummaryAppenderUtils.setLevel(loggerName, Level.INFO);
    }
}
