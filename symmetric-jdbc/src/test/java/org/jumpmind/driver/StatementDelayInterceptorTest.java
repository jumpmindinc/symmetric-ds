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
package org.jumpmind.driver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class StatementDelayInterceptorTest {
    private static final String DELAY_PROPERTY = StatementDelayInterceptor.class.getName() + ".delay";
    private Logger delayLogger;
    private ListAppender<ILoggingEvent> delayAppender;

    @BeforeEach
    void setUp() {
        delayLogger = (Logger) LoggerFactory.getLogger(StatementDelayInterceptor.class);
        delayAppender = new ListAppender<>();
        delayAppender.start();
        delayLogger.addAppender(delayAppender);
    }

    @AfterEach
    void tearDown() {
        delayLogger.detachAppender(delayAppender);
        delayAppender.stop();
    }

    @Test
    void testConstructor_withoutDelayProperty() throws ReflectiveOperationException {
        StatementDelayInterceptor interceptor = newInterceptor(null);
        assertEquals(10L, readDelay(interceptor));
    }

    @Test
    void testConstructor_withDelayProperty() throws ReflectiveOperationException {
        StatementDelayInterceptor interceptor = newInterceptor("1");
        assertEquals(1L, readDelay(interceptor));
    }

    @Test
    void testConstructor_withWhitespaceDelayProperty() throws ReflectiveOperationException {
        StatementDelayInterceptor interceptor = newInterceptor(" 5 ");
        assertEquals(5L, readDelay(interceptor));
    }

    @Test
    void testPreparedStatementExecute_withInsertLogsDelayed() {
        StatementDelayInterceptor interceptor = newInterceptor("1");
        interceptor.preparedStatementExecute("executeUpdate", 5, "insert into orders (id) values (1)");
        assertEquals("PreparedStatement.executeUpdate DELAYED (6ms.) insert into orders (id) values (1)", lastLogMessage());
    }

    @Test
    void testPreparedStatementExecute_withUpdateLogsDelayed() {
        StatementDelayInterceptor interceptor = newInterceptor("1");
        interceptor.preparedStatementExecute("executeUpdate", 5, "update orders set qty = 1");
        assertEquals("PreparedStatement.executeUpdate DELAYED (6ms.) update orders set qty = 1", lastLogMessage());
    }

    @Test
    void testPreparedStatementExecute_withUpperCaseInsertLogsDelayed() {
        StatementDelayInterceptor interceptor = newInterceptor("1");
        interceptor.preparedStatementExecute("execute", 2, "INSERT INTO ORDERS (ID) VALUES (1)");
        assertEquals("PreparedStatement.execute DELAYED (3ms.) INSERT INTO ORDERS (ID) VALUES (1)", lastLogMessage());
    }

    @Test
    void testPreparedStatementExecute_withOtherSqlDoesNotLog() {
        StatementDelayInterceptor interceptor = newInterceptor("1");
        interceptor.preparedStatementExecute("executeQuery", 5, "select * from orders");
        assertTrue(delayAppender.list.isEmpty());
    }

    @Test
    void testPreparedStatementExecute_withInterruptedThreadCompletesWithoutThrowing() {
        StatementDelayInterceptor interceptor = newInterceptor("1");
        Thread.currentThread().interrupt();
        try {
            assertDoesNotThrow(() -> interceptor.preparedStatementExecute("execute", 5, "insert into x values (1)"));
            assertEquals("PreparedStatement.execute DELAYED (6ms.) insert into x values (1)", lastLogMessage());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testStatementExecute_delegatesToSuperAndLogs() {
        Logger statementLogger = (Logger) LoggerFactory.getLogger(StatementInterceptor.class);
        ListAppender<ILoggingEvent> statementAppender = new ListAppender<>();
        statementAppender.start();
        statementLogger.addAppender(statementAppender);
        try {
            StatementDelayInterceptor interceptor = newInterceptor("1");
            interceptor.statementExecute("executeUpdate", 5, "p1");
            assertEquals(1, statementAppender.list.size());
            assertTrue(statementAppender.list.get(0).getFormattedMessage().contains("Statement.executeUpdate"));
        } finally {
            statementLogger.detachAppender(statementAppender);
            statementAppender.stop();
        }
    }

    private StatementDelayInterceptor newInterceptor(String delayValue) {
        TypedProperties properties = new TypedProperties();
        if (delayValue != null) {
            properties.setProperty(DELAY_PROPERTY, delayValue);
        }
        return new StatementDelayInterceptor(mock(PreparedStatementWrapper.class), properties);
    }

    private long readDelay(StatementDelayInterceptor interceptor) throws ReflectiveOperationException {
        Field field = StatementDelayInterceptor.class.getDeclaredField("delay");
        field.setAccessible(true);
        return field.getLong(interceptor);
    }

    private String lastLogMessage() {
        return delayAppender.list.get(delayAppender.list.size() - 1).getFormattedMessage();
    }
}
