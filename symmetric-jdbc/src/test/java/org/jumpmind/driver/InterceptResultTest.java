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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterceptResultTest {
    private InterceptResult result;

    @BeforeEach
    void setUp() {
        result = new InterceptResult();
    }

    @Test
    void testIsIntercepted_defaultsToFalse() {
        assertFalse(result.isIntercepted());
    }

    @Test
    void testSetIntercepted_toTrue() {
        result.setIntercepted(true);
        assertTrue(result.isIntercepted());
    }

    @Test
    void testSetIntercepted_toFalse() {
        result.setIntercepted(true);
        result.setIntercepted(false);
        assertFalse(result.isIntercepted());
    }

    @Test
    void testGetInterceptResult_defaultsToNull() {
        assertNull(result.getInterceptResult());
    }

    @Test
    void testSetInterceptResult_withValue() {
        Object value = new Object();
        result.setInterceptResult(value);
        assertSame(value, result.getInterceptResult());
    }

    @Test
    void testSetInterceptResult_withNull() {
        result.setInterceptResult(new Object());
        result.setInterceptResult(null);
        assertNull(result.getInterceptResult());
    }
}
