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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BulkSqlExceptionTest {
    @Test
    void testBuildMessage_withFailedRows() {
        int[] failedRows = { 2, 5 };
        String message = BulkSqlException.buildMessage("INSERT", "insert into foo values (?)", failedRows);
        assertEquals("The INSERT bulk operation of: insert into foo values (?) failed. The rows that failed were: [2, 5]", message);
    }

    @Test
    void testBuildMessage_withEmptyFailedRows() {
        int[] failedRows = {};
        String message = BulkSqlException.buildMessage("UPDATE", "update foo set bar = ?", failedRows);
        assertEquals("The UPDATE bulk operation of: update foo set bar = ? failed. The rows that failed were: []", message);
    }

    @Test
    void testGetFailedRows() {
        int[] failedRows = { 1, 3, 7 };
        BulkSqlException ex = new BulkSqlException(failedRows, "INSERT", "insert into foo values (?)");
        assertArrayEquals(failedRows, ex.getFailedRows());
    }

    @Test
    void testConstructor_setsMessageFromFailedRows() {
        int[] failedRows = { 4 };
        BulkSqlException ex = new BulkSqlException(failedRows, "DELETE", "delete from foo");
        assertEquals("The DELETE bulk operation of: delete from foo failed. The rows that failed were: [4]", ex.getMessage());
    }
}
