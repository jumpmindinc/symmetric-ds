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
package org.jumpmind.db.platform.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.sql.Types;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqliteDatabasePlatformTest {
    private SqliteDatabasePlatform platform;

    @BeforeEach
    void setUp() {
        platform = mock(SqliteDatabasePlatform.class, CALLS_REAL_METHODS);
    }

    @Test
    void testGetClassName() {
        assertEquals(SqliteDatabasePlatform.class.getName(), platform.getClassName());
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.SQLITE, platform.getName());
    }

    @Test
    void testGetDefaultSchema_isNull() {
        assertNull(platform.getDefaultSchema());
    }

    @Test
    void testGetDefaultCatalog_isNull() {
        assertNull(platform.getDefaultCatalog());
    }

    @Test
    void testSupportsMultiThreadedTransactions() {
        assertFalse(platform.supportsMultiThreadedTransactions());
    }

    @Test
    void testSupportsLimitOffset() {
        assertTrue(platform.supportsLimitOffset());
    }

    @Test
    void testMassageForLimitOffset() {
        assertEquals("select * from item limit 10 offset 20", platform.massageForLimitOffset("select * from item", 10, 20));
    }

    @Test
    void testMassageForLimitOffset_stripsTrailingSemicolon() {
        assertEquals("select * from item limit 10 offset 0", platform.massageForLimitOffset("select * from item;", 10, 0));
    }

    @Test
    void testParseBigDecimal_withBlankValue() {
        assertEquals("", platform.parseBigDecimal(""));
    }

    @Test
    void testParseBigDecimal_withNumericValue() {
        assertEquals(new BigDecimal("12.50"), platform.parseBigDecimal("12.50"));
    }

    @Test
    void testParseBigInteger_withBlankValue() {
        assertEquals("", platform.parseBigInteger(""));
    }

    @Test
    void testParseBigInteger_withNumericValue() {
        assertEquals(42L, platform.parseBigInteger("42"));
    }

    @Test
    void testParseInteger_withBlankValue() {
        assertEquals("", platform.parseInteger(""));
    }

    @Test
    void testParseInteger_withNumericValue() {
        assertEquals(42, platform.parseInteger("42"));
    }

    @Test
    void testGetDateTimeStringValue_readsRawStringFromRow() {
        Row row = new Row(1);
        row.put("create_time", "2020-01-02 10:15:30");
        assertEquals("2020-01-02 10:15:30", platform.getDateTimeStringValue("create_time", Types.TIMESTAMP, row, false));
    }
}
