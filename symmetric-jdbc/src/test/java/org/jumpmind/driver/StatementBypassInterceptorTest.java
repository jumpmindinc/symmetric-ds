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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class StatementBypassInterceptorTest {
    private PreparedStatementWrapper psMock;
    private StatementBypassInterceptor interceptor;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        psMock = mock(PreparedStatementWrapper.class);
        interceptor = new StatementBypassInterceptor(psMock, new TypedProperties());
        logger = (Logger) LoggerFactory.getLogger(StatementBypassInterceptor.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void testPreparedStatementPreExecute_withGetUpdateCount() {
        InterceptResult result = interceptor.preparedStatementPreExecute(psMock, "getUpdateCount", new Object[0]);
        assertTrue(result.isIntercepted());
        assertEquals(Integer.valueOf(1), result.getInterceptResult());
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    void testPreparedStatementPreExecute_withExecuteInsertBypassed() {
        when(psMock.getStatement()).thenReturn("insert into orders (id) values (1)");
        InterceptResult result = interceptor.preparedStatementPreExecute(psMock, "execute", new Object[0]);
        assertTrue(result.isIntercepted());
        assertEquals(Boolean.FALSE, result.getInterceptResult());
        assertEquals("PreparedStatement.execute *BYPASSED* insert into orders (id) values (1)", lastLogMessage());
    }

    @Test
    void testPreparedStatementPreExecute_withExecuteUpdateInsertBypassed() {
        when(psMock.getStatement()).thenReturn("insert into orders (id) values (1)");
        InterceptResult result = interceptor.preparedStatementPreExecute(psMock, "executeUpdate", new Object[0]);
        assertTrue(result.isIntercepted());
        assertEquals(Integer.valueOf(1), result.getInterceptResult());
        assertEquals("PreparedStatement.executeUpdate *BYPASSED* insert into orders (id) values (1)", lastLogMessage());
    }

    @Test
    void testPreparedStatementPreExecute_withExecuteQueryInsertBypassed() {
        when(psMock.getStatement()).thenReturn("insert into orders (id) values (1)");
        InterceptResult result = interceptor.preparedStatementPreExecute(psMock, "executeQuery", new Object[0]);
        assertTrue(result.isIntercepted());
        assertNull(result.getInterceptResult());
        assertEquals("PreparedStatement.executeQuery *BYPASSED* insert into orders (id) values (1)", lastLogMessage());
    }

    @Test
    void testPreparedStatementPreExecute_withExecuteUpdateStatementBypassed() {
        when(psMock.getStatement()).thenReturn("update orders set qty = 1");
        InterceptResult result = interceptor.preparedStatementPreExecute(psMock, "execute", new Object[0]);
        assertTrue(result.isIntercepted());
        assertEquals(Boolean.FALSE, result.getInterceptResult());
        assertEquals("PreparedStatement.execute *BYPASSED* update orders set qty = 1", lastLogMessage());
    }

    @Test
    void testPreparedStatementPreExecute_withSymTableNotBypassed() {
        when(psMock.getStatement()).thenReturn("update sym_node set node_id = 1");
        InterceptResult result = interceptor.preparedStatementPreExecute(psMock, "execute", new Object[0]);
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    void testPreparedStatementPreExecute_withSelectNotBypassed() {
        when(psMock.getStatement()).thenReturn("select id from orders");
        InterceptResult result = interceptor.preparedStatementPreExecute(psMock, "executeQuery", new Object[0]);
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    void testPreparedStatementPreExecute_withNonExecuteMethodFallsToSuper() {
        InterceptResult result = interceptor.preparedStatementPreExecute(psMock, "close", new Object[0]);
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    void testPreparedStatementExecute_isNoOpAndDoesNotLog() {
        interceptor.preparedStatementExecute("executeUpdate", 5, "insert into orders (id) values (1)");
        assertTrue(listAppender.list.isEmpty());
    }

    private String lastLogMessage() {
        return listAppender.list.get(listAppender.list.size() - 1).getFormattedMessage();
    }
}
