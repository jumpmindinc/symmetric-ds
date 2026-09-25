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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DummyInterceptorTest {
    private Object wrapped;
    private DummyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        wrapped = new Object();
        interceptor = new DummyInterceptor(wrapped);
    }

    @Test
    void testPreExecute_withNoParameters() {
        InterceptResult result = interceptor.preExecute("someMethod");
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
    }

    @Test
    void testPreExecute_withParameters() {
        InterceptResult result = interceptor.preExecute("someMethod", "arg1", 2, true);
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
    }

    @Test
    void testPostExecute_withNoParameters() {
        InterceptResult result = interceptor.postExecute("someMethod", "returnValue", 100L, 150L);
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
    }

    @Test
    void testPostExecute_withParameters() {
        InterceptResult result = interceptor.postExecute("someMethod", "returnValue", 100L, 150L, "arg1", 2);
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
    }

    @Test
    void testPostExecute_withNullResult() {
        InterceptResult result = interceptor.postExecute("someMethod", null, 100L, 150L);
        assertFalse(result.isIntercepted());
        assertNull(result.getInterceptResult());
    }

    @Test
    void testGetWrapped_returnsConstructorArgument() {
        assertSame(wrapped, interceptor.getWrapped());
    }
}
