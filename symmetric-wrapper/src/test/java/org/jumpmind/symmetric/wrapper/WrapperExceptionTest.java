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
package org.jumpmind.symmetric.wrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class WrapperExceptionTest {
    @Test
    void testGetters() {
        WrapperException exception = new WrapperException(Constants.RC_FAIL_EXECUTION, 5, "Failed executing server");
        assertEquals(Constants.RC_FAIL_EXECUTION, exception.getErrorCode());
        assertEquals(5, exception.getNativeErrorCode());
        assertEquals("Failed executing server", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testIsRuntimeException() {
        assertInstanceOf(RuntimeException.class, new WrapperException(Constants.RC_MUST_BE_ROOT, 0, "Must be root"));
    }

    @Test
    void testToString_withoutNativeErrorCode() {
        assertEquals("Error 14 [Must be root to install]",
                new WrapperException(Constants.RC_MUST_BE_ROOT, 0, "Must be root to install").toString());
    }

    @Test
    void testToString_withNativeErrorCode() {
        assertEquals("Error 19 [OpenService returned error 5] with native error 5",
                new WrapperException(Constants.RC_NATIVE_ERROR, 5, "OpenService returned error 5").toString());
    }

    @Test
    void testToString_withNegativeNativeErrorCode() {
        assertEquals("Error 8 [Failed executing server]",
                new WrapperException(Constants.RC_FAIL_EXECUTION, -1, "Failed executing server").toString());
    }

    @Test
    void testToString_withNullMessage() {
        assertEquals("Error 3 [null]", new WrapperException(Constants.RC_MISSING_CONFIG_FILE, 0, null).toString());
    }

    @Test
    void testConstructor_withCause_keepsOnlyTheCause() {
        IOException cause = new IOException("Permission denied");
        WrapperException exception = new WrapperException(Constants.RC_FAIL_INSTALL, 7, "Failed while writing run file", cause);
        assertSame(cause, exception.getCause());
        // Defect pinned, not endorsed: the four-argument constructor assigns only the cause, dropping errorCode, nativeErrorCode and message
        assertEquals(0, exception.getErrorCode());
        assertEquals(0, exception.getNativeErrorCode());
        assertNull(exception.getMessage());
    }

    @Test
    void testToString_withCause() {
        WrapperException exception = new WrapperException(Constants.RC_FAIL_INSTALL, 7, "Failed while writing run file",
                new IOException("Permission denied"));
        assertEquals("Error 0 [null] caused by IOException [Permission denied]", exception.toString());
    }
}
