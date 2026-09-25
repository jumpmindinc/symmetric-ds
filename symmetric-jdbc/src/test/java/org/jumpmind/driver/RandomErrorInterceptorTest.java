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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import org.jumpmind.db.sql.SqlException;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RandomErrorInterceptorTest {
    private static final int SHOULD_THROW_TRIALS = 20000;
    private PreparedStatementWrapper psMock;
    private TypedProperties properties;

    @BeforeEach
    void setUp() {
        psMock = mock(PreparedStatementWrapper.class);
        properties = new TypedProperties();
        resetCounter("okCounter");
        resetCounter("errorCounter");
    }

    @AfterEach
    void tearDown() {
        resetCounter("okCounter");
        resetCounter("errorCounter");
    }

    @Test
    void testPreparedStatementPreExecute_belowThreshold() {
        RandomErrorInterceptor interceptor = new RandomErrorInterceptor(psMock, properties);
        InterceptResult result = assertDoesNotThrow(() -> interceptor.preparedStatementPreExecute(psMock, "executeUpdate", new Object[0]));
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
        assertEquals(1, readCounter("okCounter"));
    }

    @Test
    void testPreparedStatementPreExecute_throwsWhenShouldThrowError() {
        RandomErrorInterceptor interceptor = spy(new RandomErrorInterceptor(psMock, properties));
        doReturn(true).when(interceptor).shouldThrowError();
        SqlException exception = assertThrows(SqlException.class,
                () -> interceptor.preparedStatementPreExecute(psMock, "executeUpdate", new Object[0]));
        assertTrue(exception.getMessage().contains("MOCK/RANDOM ERROR"));
    }

    @Test
    void testPreparedStatementExecute_isNoOp() {
        RandomErrorInterceptor interceptor = new RandomErrorInterceptor(psMock, properties);
        assertDoesNotThrow(() -> interceptor.preparedStatementExecute("executeUpdate", 5, "select 1"));
    }

    @Test
    void testShouldThrowError_belowThreshold() {
        setCounter("okCounter", 500);
        RandomErrorInterceptor interceptor = new RandomErrorInterceptor(psMock, properties);
        for (int i = 0; i < 100; i++) {
            assertFalse(interceptor.shouldThrowError());
        }
    }

    @Test
    void testShouldThrowError_atThreshold() {
        setCounter("okCounter", 1000);
        RandomErrorInterceptor interceptor = new RandomErrorInterceptor(psMock, properties);
        boolean sawTrue = false;
        boolean sawFalse = false;
        for (int i = 0; i < SHOULD_THROW_TRIALS && !(sawTrue && sawFalse); i++) {
            if (interceptor.shouldThrowError()) {
                sawTrue = true;
            } else {
                sawFalse = true;
            }
        }
        assertTrue(sawTrue);
        assertTrue(sawFalse);
    }

    private void setCounter(String fieldName, int value) {
        try {
            Field field = RandomErrorInterceptor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            ((AtomicInteger) field.get(null)).set(value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void resetCounter(String fieldName) {
        setCounter(fieldName, 0);
    }

    private int readCounter(String fieldName) {
        try {
            Field field = RandomErrorInterceptor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return ((AtomicInteger) field.get(null)).get();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
