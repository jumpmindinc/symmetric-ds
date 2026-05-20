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
package org.jumpmind.db.platform.postgresql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.jumpmind.db.sql.SqlException;
import org.junit.jupiter.api.Test;

class PostgreSqlDatabasePlatformTest {
    private final PostgreSqlDatabasePlatform platform = mock(PostgreSqlDatabasePlatform.class, CALLS_REAL_METHODS);

    @Test
    void testIsMissingCitextExtensionError_citextDirectMessage() {
        assertTrue(platform.isMissingCitextExtensionError(
                new SqlException("ERROR: type \"citext\" does not exist")));
    }

    @Test
    void testIsMissingCitextExtensionError_unrelatedError() {
        assertFalse(platform.isMissingCitextExtensionError(
                new SqlException("ERROR: relation \"users\" already exists")));
    }

    @Test
    void testIsMissingCitextExtensionError_permissionDeniedError() {
        assertFalse(platform.isMissingCitextExtensionError(
                new SqlException("ERROR: permission denied to create extension \"citext\"")));
    }

    @Test
    void testIsMissingCitextExtensionError_nullMessage() {
        assertFalse(platform.isMissingCitextExtensionError(new RuntimeException()));
    }
}
