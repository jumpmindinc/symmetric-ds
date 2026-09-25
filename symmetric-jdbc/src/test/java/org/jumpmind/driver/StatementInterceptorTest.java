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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class StatementInterceptorTest {
    private TypedProperties properties;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        properties = new TypedProperties();
        logger = (Logger) LoggerFactory.getLogger(StatementInterceptor.class);
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
    void testPreExecute_withPreparedStatementWrapperDelegates() {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(psMock, properties);
        InterceptResult preResult = interceptor.preExecute("setString", 1, "myArg");
        assertNonIntercepted(preResult);
        when(psMock.getStatement()).thenReturn("update test_table set col1 = ?");
        interceptor.preparedStatementPostExecute(psMock, "executeUpdate", null, 0, 5);
        assertEquals("PreparedStatement.executeUpdate (5ms.) update test_table set col1 = 'myArg'", lastLogMessage());
    }

    @Test
    void testPreExecute_withStatementWrapperNotIntercepted() {
        StatementWrapper statementWrapperMock = mock(StatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(statementWrapperMock, properties);
        InterceptResult preResult = interceptor.preExecute("setString", 1, "leakedArg");
        assertNonIntercepted(preResult);
        PreparedStatementWrapper psMock = preparedStatementWrapperReturning("update test_table set col1 = ?");
        interceptor.preparedStatementPostExecute(psMock, "executeUpdate", null, 0, 5);
        assertEquals("PreparedStatement.executeUpdate (5ms.) update test_table set col1 = ?", lastLogMessage());
    }

    @Test
    void testPreExecute_withPlainObjectNotIntercepted() {
        Object plainWrapped = new Object();
        StatementInterceptor interceptor = new StatementInterceptor(plainWrapped, properties);
        InterceptResult preResult = interceptor.preExecute("setString", 1, "leakedArg");
        assertNonIntercepted(preResult);
        PreparedStatementWrapper psMock = preparedStatementWrapperReturning("update test_table set col1 = ?");
        interceptor.preparedStatementPostExecute(psMock, "executeUpdate", null, 0, 5);
        assertEquals("PreparedStatement.executeUpdate (5ms.) update test_table set col1 = ?", lastLogMessage());
    }

    @Test
    void testPreparedStatementPreExecute_withSetMethodAndMultipleParams() {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(psMock, properties);
        InterceptResult preResult = interceptor.preparedStatementPreExecute(psMock, "setString", new Object[] { 1, "value1" });
        assertNonIntercepted(preResult);
        when(psMock.getStatement()).thenReturn("update test_table set col1 = ?");
        interceptor.preparedStatementPostExecute(psMock, "executeUpdate", null, 0, 5);
        assertEquals("PreparedStatement.executeUpdate (5ms.) update test_table set col1 = 'value1'", lastLogMessage());
    }

    @Test
    void testPreparedStatementPreExecute_withSetMethodAndSingleParam() {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(psMock, properties);
        InterceptResult preResult = interceptor.preparedStatementPreExecute(psMock, "setString", new Object[] { 1 });
        assertNonIntercepted(preResult);
        when(psMock.getStatement()).thenReturn("update test_table set col1 = ?");
        interceptor.preparedStatementPostExecute(psMock, "executeUpdate", null, 0, 5);
        assertEquals("PreparedStatement.executeUpdate (5ms.) update test_table set col1 = ?", lastLogMessage());
    }

    @Test
    void testPreparedStatementPreExecute_withNonSetMethod() {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(psMock, properties);
        InterceptResult preResult = interceptor.preparedStatementPreExecute(psMock, "close", new Object[] { 1, "value1" });
        assertNonIntercepted(preResult);
        when(psMock.getStatement()).thenReturn("update test_table set col1 = ?");
        interceptor.preparedStatementPostExecute(psMock, "executeUpdate", null, 0, 5);
        assertEquals("PreparedStatement.executeUpdate (5ms.) update test_table set col1 = ?", lastLogMessage());
    }

    @Test
    void testPreparedStatementPreExecute_withNullParameters() {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(psMock, properties);
        InterceptResult preResult = interceptor.preparedStatementPreExecute(psMock, "setString", null);
        assertNonIntercepted(preResult);
        when(psMock.getStatement()).thenReturn("update test_table set col1 = ?");
        interceptor.preparedStatementPostExecute(psMock, "executeUpdate", null, 0, 5);
        assertEquals("PreparedStatement.executeUpdate (5ms.) update test_table set col1 = ?", lastLogMessage());
    }

    @Test
    void testPostExecute_withPreparedStatementWrapper() {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        when(psMock.getStatement()).thenReturn("select * from sym_node");
        StatementInterceptor interceptor = new StatementInterceptor(psMock, properties);
        InterceptResult result = interceptor.postExecute("executeQuery", null, 0, 3);
        assertNonIntercepted(result);
        assertEquals("PreparedStatement.executeQuery (3ms.) select * from sym_node", lastLogMessage());
    }

    @Test
    void testPostExecute_withStatementWrapper() {
        StatementWrapper statementWrapperMock = mock(StatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(statementWrapperMock, properties);
        InterceptResult result = interceptor.postExecute("executeUpdate", null, 0, 4, "p1", "p2");
        assertNonIntercepted(result);
        assertEquals("Statement.executeUpdate (4ms.) [p1, p2]", lastLogMessage());
    }

    @Test
    void testPostExecute_withPlainObject() {
        Object plainWrapped = new Object();
        StatementInterceptor interceptor = new StatementInterceptor(plainWrapped, properties);
        InterceptResult result = interceptor.postExecute("executeUpdate", null, 0, 5);
        assertNonIntercepted(result);
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    void testPreparedStatementPostExecute_withExecuteMethod() {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        when(psMock.getStatement()).thenReturn("delete from sym_node where id = ?");
        StatementInterceptor interceptor = new StatementInterceptor(psMock, properties);
        InterceptResult result = interceptor.preparedStatementPostExecute(psMock, "executeUpdate", null, 10, 25);
        assertNonIntercepted(result);
        assertEquals("PreparedStatement.executeUpdate (15ms.) delete from sym_node where id = ?", lastLogMessage());
        assertEquals(Level.INFO, listAppender.list.get(0).getLevel());
    }

    @Test
    void testPreparedStatementPostExecute_withNonExecuteMethod() {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(psMock, properties);
        InterceptResult result = interceptor.preparedStatementPostExecute(psMock, "close", null, 0, 5);
        assertNonIntercepted(result);
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    void testStatementPostExecute_withExecuteMethod() {
        StatementWrapper statementWrapperMock = mock(StatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(statementWrapperMock, properties);
        InterceptResult result = interceptor.statementPostExecute(statementWrapperMock, "executeBatch", null, 2, 9, "x", "y");
        assertNonIntercepted(result);
        assertEquals("Statement.executeBatch (7ms.) [x, y]", lastLogMessage());
        assertEquals(Level.INFO, listAppender.list.get(0).getLevel());
    }

    @Test
    void testStatementPostExecute_withNonExecuteMethod() {
        StatementWrapper statementWrapperMock = mock(StatementWrapper.class);
        StatementInterceptor interceptor = new StatementInterceptor(statementWrapperMock, properties);
        InterceptResult result = interceptor.statementPostExecute(statementWrapperMock, "close", null, 0, 5);
        assertNonIntercepted(result);
        assertTrue(listAppender.list.isEmpty());
    }

    @Test
    void testPreparedStatementExecute_logsInfo() {
        StatementInterceptor interceptor = new StatementInterceptor(mock(PreparedStatementWrapper.class), properties);
        interceptor.preparedStatementExecute("executeQuery", 42, "select 1");
        assertEquals("PreparedStatement.executeQuery (42ms.) select 1", lastLogMessage());
        assertEquals(Level.INFO, listAppender.list.get(0).getLevel());
    }

    @Test
    void testStatementExecute_logsInfo() {
        StatementInterceptor interceptor = new StatementInterceptor(mock(StatementWrapper.class), properties);
        interceptor.statementExecute("executeBatch", 7, "p1", "p2");
        assertEquals("Statement.executeBatch (7ms.) [p1, p2]", lastLogMessage());
        assertEquals(Level.INFO, listAppender.list.get(0).getLevel());
    }

    private void assertNonIntercepted(InterceptResult result) {
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
    }

    private PreparedStatementWrapper preparedStatementWrapperReturning(String statement) {
        PreparedStatementWrapper psMock = mock(PreparedStatementWrapper.class);
        when(psMock.getStatement()).thenReturn(statement);
        return psMock;
    }

    private String lastLogMessage() {
        return listAppender.list.get(listAppender.list.size() - 1).getFormattedMessage();
    }
}
