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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.junit.jupiter.api.Test;

class DmlStatementOptionsTest {
    @Test
    void testConstructor_withDmlTypeAndTableName() {
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        assertEquals(DmlType.INSERT, options.getDmlType());
        assertEquals("my_table", options.getTableName());
        assertNull(options.getColumns());
        assertNotNull(options.getDatabaseInfo());
        assertNull(options.getCatalogName());
        assertNull(options.getSchemaName());
        assertNull(options.getKeys());
        assertFalse(options.useQuotedIdentifiers());
        assertFalse(options.isNamedParameters());
    }

    @Test
    void testConstructor_withDmlTypeAndTable() {
        Table table = new Table("cat1", "schema1", "my_table", new String[] { "id", "name" },
                new String[] { "id" });
        DmlStatementOptions options = new DmlStatementOptions(DmlType.UPDATE, table);
        assertEquals(DmlType.UPDATE, options.getDmlType());
        assertEquals("my_table", options.getTableName());
        assertEquals("cat1", options.getCatalogName());
        assertEquals("schema1", options.getSchemaName());
        assertArrayEquals(table.getColumns(), options.getColumns());
        assertArrayEquals(table.getPrimaryKeyColumns(), options.getKeys());
    }

    @Test
    void testDatabaseInfo_setsDatabaseInfoAndReturnsThis() {
        DatabaseInfo databaseInfo = new DatabaseInfo();
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        DmlStatementOptions result = options.databaseInfo(databaseInfo);
        assertSame(options, result);
        assertSame(databaseInfo, options.getDatabaseInfo());
    }

    @Test
    void testCatalogName_setsCatalogName() {
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.catalogName("cat1");
        assertEquals("cat1", options.getCatalogName());
    }

    @Test
    void testSchemaName_setsSchemaName() {
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.schemaName("schema1");
        assertEquals("schema1", options.getSchemaName());
    }

    @Test
    void testColumns_setsColumns() {
        Column[] columns = new Column[] { new Column("id") };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.columns(columns);
        assertSame(columns, options.getColumns());
    }

    @Test
    void testKeys_setsKeys() {
        Column[] keys = new Column[] { new Column("id", true) };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.keys(keys);
        assertSame(keys, options.getKeys());
    }

    @Test
    void testNullKeyValues_setsNullKeyValues() {
        boolean[] nullKeyValues = new boolean[] { true, false };
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.nullKeyValues(nullKeyValues);
        assertSame(nullKeyValues, options.getNullKeyValues());
    }

    @Test
    void testQuotedIdentifiers_setsUseQuotedIdentifiers() {
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.quotedIdentifiers(true);
        assertTrue(options.useQuotedIdentifiers());
    }

    @Test
    void testNamedParameters_setsNamedParameters() {
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.namedParameters(true);
        assertTrue(options.isNamedParameters());
    }

    @Test
    void testTextColumnExpression_setsTextColumnExpression() {
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.textColumnExpression("to_char(?)");
        assertEquals("to_char(?)", options.getTextColumnExpression());
    }

    @Test
    void testDmlType_setsDmlType() {
        DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, "my_table");
        options.dmlType(DmlType.DELETE);
        assertEquals(DmlType.DELETE, options.getDmlType());
    }
}
