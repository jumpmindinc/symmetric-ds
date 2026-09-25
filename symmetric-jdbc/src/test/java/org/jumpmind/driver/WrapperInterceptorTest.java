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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;

class WrapperInterceptorTest {
    @Test
    void testCreateInterceptor_withConfiguredInterceptorClass() {
        Object wrapped = new Object();
        TypedProperties properties = new TypedProperties();
        properties.put(wrapped.getClass().getName() + ".interceptor", "org.jumpmind.driver.StatementInterceptor");
        WrapperInterceptor interceptor = WrapperInterceptor.createInterceptor(wrapped, properties);
        assertInstanceOf(StatementInterceptor.class, interceptor);
        assertSame(wrapped, interceptor.getWrapped());
    }

    @Test
    void testCreateInterceptor_withInvalidInterceptorClassName_throwsRuntimeException() {
        Object wrapped = new Object();
        TypedProperties properties = new TypedProperties();
        properties.put(wrapped.getClass().getName() + ".interceptor", "com.bogus.NoSuchInterceptor");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> WrapperInterceptor.createInterceptor(wrapped, properties));
        assertTrue(ex.getMessage().contains("Failed to load and instantiate interceptor class"));
        assertNotNull(ex.getCause());
    }

    @Test
    void testCreateInterceptor_withPreparedStatementWrapper_returnsStatementInterceptor() {
        PreparedStatementWrapper wrapped = mock(PreparedStatementWrapper.class);
        WrapperInterceptor interceptor = WrapperInterceptor.createInterceptor(wrapped, new TypedProperties());
        assertInstanceOf(StatementInterceptor.class, interceptor);
        assertSame(wrapped, interceptor.getWrapped());
    }

    @Test
    void testCreateInterceptor_withStatementWrapper_returnsStatementInterceptor() {
        StatementWrapper wrapped = mock(StatementWrapper.class);
        WrapperInterceptor interceptor = WrapperInterceptor.createInterceptor(wrapped, new TypedProperties());
        assertInstanceOf(StatementInterceptor.class, interceptor);
        assertSame(wrapped, interceptor.getWrapped());
    }

    @Test
    void testCreateInterceptor_withPlainObject_returnsDummyInterceptor() {
        Object wrapped = new Object();
        WrapperInterceptor interceptor = WrapperInterceptor.createInterceptor(wrapped, new TypedProperties());
        assertInstanceOf(DummyInterceptor.class, interceptor);
        assertSame(wrapped, interceptor.getWrapped());
    }

    @Test
    void testCreateInterceptor_withNullProperties_doesNotThrow() {
        Object wrapped = new Object();
        WrapperInterceptor interceptor = assertDoesNotThrow(() -> WrapperInterceptor.createInterceptor(wrapped, null));
        assertInstanceOf(DummyInterceptor.class, interceptor);
        assertSame(wrapped, interceptor.getWrapped());
    }

    @Test
    void testGetWrapped_returnsConstructorArgument() {
        Object wrapped = new Object();
        WrapperInterceptor interceptor = new DummyInterceptor(wrapped);
        assertSame(wrapped, interceptor.getWrapped());
    }
}
