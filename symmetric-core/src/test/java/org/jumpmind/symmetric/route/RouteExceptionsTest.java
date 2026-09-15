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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteExceptionsTest {
    @Test
    void testCommonBatchCollisionException_withNoArguments() {
        assertNull(new CommonBatchCollisionException().getMessage());
    }

    @Test
    void testCommonBatchCollisionException_withMessage() {
        assertEquals("batch 5 collided", new CommonBatchCollisionException("batch 5 collided").getMessage());
    }

    @Test
    void testCommonBatchCollisionException_isRuntimeException() {
        assertTrue(RuntimeException.class.isInstance(new CommonBatchCollisionException()));
    }

    @Test
    void testDelayRoutingException_withNoArguments() {
        assertNull(new DelayRoutingException().getMessage());
    }

    @Test
    void testDelayRoutingException_withMessage() {
        assertEquals("gap detected", new DelayRoutingException("gap detected").getMessage());
    }

    @Test
    void testDelayRoutingException_withCause() {
        IllegalStateException cause = new IllegalStateException("bad state");
        assertSame(cause, new DelayRoutingException(cause).getCause());
    }

    @Test
    void testDelayRoutingException_withMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("bad state");
        DelayRoutingException exception = new DelayRoutingException("gap detected", cause);
        assertEquals("gap detected", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testDelayRoutingException_isRuntimeException() {
        assertTrue(RuntimeException.class.isInstance(new DelayRoutingException()));
    }
}
