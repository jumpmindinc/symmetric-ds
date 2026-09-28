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
package org.jumpmind.symmetric.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathResolutionExceptionTest {
    @Test
    void testConstructor_withNoArguments() {
        assertNull(new PathResolutionException().getMessage());
    }

    @Test
    void testConstructor_withMessage() {
        assertEquals("no common path", new PathResolutionException("no common path").getMessage());
    }

    @Test
    void testConstructor_withCause() {
        IllegalStateException cause = new IllegalStateException("bad state");
        assertSame(cause, new PathResolutionException(cause).getCause());
    }

    @Test
    void testConstructor_withMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("bad state");
        PathResolutionException exception = new PathResolutionException("no common path", cause);
        assertEquals("no common path", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testIsRuntimeException() {
        assertTrue(RuntimeException.class.isInstance(new PathResolutionException()));
    }
}
