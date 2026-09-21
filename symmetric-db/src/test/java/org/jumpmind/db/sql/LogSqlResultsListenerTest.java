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
package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LogSqlResultsListenerTest {
    private final LogSqlResultsListener listener = new LogSqlResultsListener();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LogSqlResultsListener.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void testSqlBefore_logsAtInfo() {
        listener.sqlBefore("select 1", 1);
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("Executing DDL: select 1", event.getFormattedMessage());
    }

    @Test
    void testSqlApplied_logsNothing() {
        listener.sqlApplied("select 1", 1, 1, 1);
        assertTrue(appender.list.isEmpty());
    }

    @Test
    void testSqlErrored_withDropStatementLogsAtInfo() {
        listener.sqlErrored("drop table foo", new SqlException("boom"), 1, true, false);
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("DDL failed: drop table foo", event.getFormattedMessage());
    }

    @Test
    void testSqlErrored_withSequenceCreateLogsAtInfo() {
        listener.sqlErrored("create sequence foo", new SqlException("boom"), 1, false, true);
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("DDL failed: create sequence foo", event.getFormattedMessage());
    }

    @Test
    void testSqlErrored_withNeitherLogsAtWarn() {
        listener.sqlErrored("create table foo", new SqlException("boom"), 1, false, false);
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertEquals("DDL failed: create table foo", event.getFormattedMessage());
    }
}
