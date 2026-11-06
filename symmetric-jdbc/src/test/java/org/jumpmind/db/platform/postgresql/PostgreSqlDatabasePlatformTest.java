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
package org.jumpmind.db.platform.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.junit.jupiter.api.Test;

class PostgreSqlDatabasePlatformTest {
    private final PostgreSqlDatabasePlatform platform = mock(PostgreSqlDatabasePlatform.class, CALLS_REAL_METHODS);

    @Test
    void testGetClassName() {
        assertEquals(PostgreSqlDatabasePlatform.class.getName(), platform.getClassName());
    }

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

    @Test
    void testGetDefaultCatalog_returnsCurrentDatabase() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.queryForObject("select current_database()", String.class))
                .thenReturn("test_database");
        String catalog = platform.getDefaultCatalog();
        assertNotNull(catalog);
        assertEquals("test_database", catalog);
        verify(sqlTemplate).queryForObject("select current_database()", String.class);
    }

    @Test
    void testGetDefaultCatalog_cachesCatalogValue() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.queryForObject("select current_database()", String.class))
                .thenReturn("test_database");
        String catalog1 = platform.getDefaultCatalog();
        String catalog2 = platform.getDefaultCatalog();
        assertEquals("test_database", catalog1);
        assertEquals("test_database", catalog2);
        verify(sqlTemplate, times(1)).queryForObject(anyString(), eq(String.class));
    }

    private static Column arrayColumn(boolean required) {
        Column column = new Column("item_code");
        column.setMappedTypeCode(Types.ARRAY);
        column.setJdbcTypeName("_int8");
        column.setRequired(required);
        return column;
    }

    @Test
    void testCreateArray_blankValueForRequiredColumnFailsWithColumnName() {
        Column requiredColumn = arrayColumn(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> platform.createArray(requiredColumn, ""));
        assertTrue(ex.getMessage().contains("item_code"));
        assertTrue(ex.getMessage().contains("Blank value for required array column"));
    }

    @Test
    void testCreateArray_whitespaceValueForRequiredColumnFails() {
        Column requiredColumn = arrayColumn(true);
        assertThrows(IllegalArgumentException.class, () -> platform.createArray(requiredColumn, "  "));
    }

    @Test
    void testCreateArray_blankValueForOptionalColumnReturnsNull() {
        assertNull(platform.createArray(arrayColumn(false), ""));
    }

    @Test
    void testCreateArray_nonBlankValueBindsArrayLiteral() throws SQLException {
        Array array = platform.createArray(arrayColumn(true), "{12}");
        assertNotNull(array);
        assertEquals("{12}", array.toString());
        assertEquals("int8", array.getBaseTypeName());
        assertEquals(Types.INTEGER, array.getBaseType());
    }

    @Test
    void testCreateArray_emptyArrayLiteralForRequiredColumnBinds() {
        Array array = platform.createArray(arrayColumn(true), "{}");
        assertEquals("{}", array.toString());
    }
}
