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
package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class DataTruncationExceptionTest {
    @Test
    void testConstructor_withNoArgs() {
        DataTruncationException ex = new DataTruncationException();
        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testConstructor_withMessageAndCause() {
        Throwable cause = new RuntimeException("root");
        DataTruncationException ex = new DataTruncationException("boom", cause);
        assertEquals("boom", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void testConstructor_withMessage() {
        DataTruncationException ex = new DataTruncationException("boom");
        assertEquals("boom", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testConstructor_withCause() {
        Throwable cause = new RuntimeException("root");
        DataTruncationException ex = new DataTruncationException(cause);
        assertSame(cause, ex.getCause());
    }
}
