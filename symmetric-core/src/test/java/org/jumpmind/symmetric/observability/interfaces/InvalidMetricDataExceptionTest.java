/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.observability.interfaces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class InvalidMetricDataExceptionTest {
    // ── message-only constructor ──────────────────────────────────────────────

    @Test
    void messageConstructor_isRuntimeException() {
        InvalidMetricDataException ex = new InvalidMetricDataException("test message");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void messageConstructor_messageIsSet() {
        InvalidMetricDataException ex = new InvalidMetricDataException("test message");
        assertEquals("test message", ex.getMessage());
    }

    @Test
    void messageConstructor_causeIsNull() {
        InvalidMetricDataException ex = new InvalidMetricDataException("test message");
        assertEquals(null, ex.getCause());
    }

    // ── message-and-cause constructor ─────────────────────────────────────────

    @Test
    void messageCauseConstructor_messageIsSet() {
        Throwable cause = new RuntimeException("root cause");
        InvalidMetricDataException ex = new InvalidMetricDataException("wrapper message", cause);
        assertEquals("wrapper message", ex.getMessage());
    }

    @Test
    void messageCauseConstructor_causeIsSet() {
        Throwable cause = new RuntimeException("root cause");
        InvalidMetricDataException ex = new InvalidMetricDataException("wrapper message", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void messageCauseConstructor_isRuntimeException() {
        Throwable cause = new RuntimeException("root cause");
        InvalidMetricDataException ex = new InvalidMetricDataException("wrapper message", cause);
        assertInstanceOf(RuntimeException.class, ex);
    }
}
