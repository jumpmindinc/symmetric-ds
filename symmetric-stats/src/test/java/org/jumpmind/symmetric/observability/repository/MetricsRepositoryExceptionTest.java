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
package org.jumpmind.symmetric.observability.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.jumpmind.symmetric.SymmetricException;
import org.junit.jupiter.api.Test;

class MetricsRepositoryExceptionTest {
    @Test
    void constructor_message_setsMessage() {
        MetricsRepositoryException ex = new MetricsRepositoryException("test message");
        assertEquals("test message", ex.getMessage());
    }

    @Test
    void constructor_message_isSymmetricException() {
        MetricsRepositoryException ex = new MetricsRepositoryException("msg");
        assertInstanceOf(SymmetricException.class, ex);
    }

    @Test
    void constructor_messageAndCause_setsMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        MetricsRepositoryException ex = new MetricsRepositoryException("wrapped", cause);
        assertEquals("wrapped", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void constructor_cause_setsCause() {
        Throwable cause = new IllegalStateException("underlying");
        MetricsRepositoryException ex = new MetricsRepositoryException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        MetricsRepositoryException ex = new MetricsRepositoryException("x");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
