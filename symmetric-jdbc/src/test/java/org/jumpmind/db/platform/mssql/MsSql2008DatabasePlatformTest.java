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
package org.jumpmind.db.platform.mssql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.ISqlTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MsSql2008DatabasePlatformTest {
    private static final String CATALOG_NAME = "dbCatalog";
    private static final String SCHEMA_NAME = "dbSchema";
    private static final String TABLE_NAME = "dbTable";
    private MsSql2008DatabasePlatform platform;
    private ISqlTemplate sqlTemplateDirty;
    private MsSql2008DdlBuilder ddlBuilder;

    @BeforeEach
    void setUp() {
        platform = mock(MsSql2008DatabasePlatform.class);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        ddlBuilder = new MsSql2008DdlBuilder();
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(platform.getDatabaseInfo()).thenReturn(ddlBuilder.getDatabaseInfo());
        when(platform.getEstimatedRowCount(org.mockito.ArgumentMatchers.any(Table.class))).thenCallRealMethod();
    }

    @Test
    @DisplayName("getEstimatedRowCount with catalog, schema, and default delimited mode quotes all identifiers")
    void testEstimatedRowCountWithCatalogAndSchema() {
        Table table = new Table(TABLE_NAME);
        table.setCatalog(CATALOG_NAME);
        table.setSchema(SCHEMA_NAME);
        when(sqlTemplateDirty.queryForLong(anyString(), anyString())).thenReturn(42L);
        long result = platform.getEstimatedRowCount(table);
        assertEquals(42L, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> paramCaptor = ArgumentCaptor.forClass(String.class);
        verify(sqlTemplateDirty).queryForLong(sqlCaptor.capture(), paramCaptor.capture());
        assertEquals("SELECT ISNULL(SUM(rows), -1) FROM \"" + CATALOG_NAME + "\".sys.partitions " // Catalog is significant for the sys.partitions view
                + "WHERE object_id = OBJECT_ID(?) AND index_id IN (0,1)", sqlCaptor.getValue());
        assertEquals("\"" + CATALOG_NAME + "\".\"" + SCHEMA_NAME + "\".\"" + TABLE_NAME + "\"", paramCaptor.getValue());
    }

    @Test
    @DisplayName("getEstimatedRowCount without catalog omits catalog prefix from sys.partitions")
    void testEstimatedRowCountWithoutCatalog() {
        Table table = new Table(TABLE_NAME);
        table.setSchema(SCHEMA_NAME);
        when(sqlTemplateDirty.queryForLong(anyString(), anyString())).thenReturn(100L);
        long result = platform.getEstimatedRowCount(table);
        assertEquals(100L, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> paramCaptor = ArgumentCaptor.forClass(String.class);
        verify(sqlTemplateDirty).queryForLong(sqlCaptor.capture(), paramCaptor.capture());
        assertEquals("SELECT ISNULL(SUM(rows), -1) FROM sys.partitions "
                + "WHERE object_id = OBJECT_ID(?) AND index_id IN (0,1)", sqlCaptor.getValue());
        assertEquals("\"" + SCHEMA_NAME + "\".\"" + TABLE_NAME + "\"", paramCaptor.getValue());
    }

    @Test
    @DisplayName("getEstimatedRowCount without catalog or schema uses just quoted table name")
    void testEstimatedRowCountTableOnly() {
        Table table = new Table(TABLE_NAME);
        when(sqlTemplateDirty.queryForLong(anyString(), anyString())).thenReturn(0L);
        long result = platform.getEstimatedRowCount(table);
        assertEquals(0L, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> paramCaptor = ArgumentCaptor.forClass(String.class);
        verify(sqlTemplateDirty).queryForLong(sqlCaptor.capture(), paramCaptor.capture());
        assertEquals("SELECT ISNULL(SUM(rows), -1) FROM sys.partitions "
                + "WHERE object_id = OBJECT_ID(?) AND index_id IN (0,1)", sqlCaptor.getValue());
        assertEquals("\"" + TABLE_NAME + "\"", paramCaptor.getValue());
    }

    @Test
    @DisplayName("getEstimatedRowCount with delimited mode off produces unquoted names")
    void testEstimatedRowCountUnquoted() {
        Table table = new Table(TABLE_NAME);
        table.setCatalog(CATALOG_NAME);
        table.setSchema(SCHEMA_NAME);
        ddlBuilder.setDelimitedIdentifierModeOn(false);
        when(sqlTemplateDirty.queryForLong(anyString(), anyString())).thenReturn(7L);
        long result = platform.getEstimatedRowCount(table);
        assertEquals(7L, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> paramCaptor = ArgumentCaptor.forClass(String.class);
        verify(sqlTemplateDirty).queryForLong(sqlCaptor.capture(), paramCaptor.capture());
        assertEquals("SELECT ISNULL(SUM(rows), -1) FROM " + CATALOG_NAME + ".sys.partitions "
                + "WHERE object_id = OBJECT_ID(?) AND index_id IN (0,1)", sqlCaptor.getValue());
        assertEquals(CATALOG_NAME + "." + SCHEMA_NAME + "." + TABLE_NAME, paramCaptor.getValue());
    }

    @Test
    @DisplayName("getEstimatedRowCount returns -1 when table not found")
    void testEstimatedRowCountTableNotFound() {
        Table table = new Table(TABLE_NAME + "_non_existant");
        when(sqlTemplateDirty.queryForLong(anyString(), anyString())).thenReturn(-1L);
        long result = platform.getEstimatedRowCount(table);
        assertEquals(-1L, result);
    }
}
