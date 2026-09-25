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
package org.jumpmind.db.platform.mariadb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.junit.jupiter.api.Test;

class MariaDBDdlBuilderTest {
    @Test
    void testWriteGeneratedColumn_persisted_emitsPersistentKeyword() {
        MariaDBDdlBuilder ddlBuilder = new MariaDBDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        String ddl = ddlBuilder.createTable(buildTableWithComputedColumn(true));
        assertTrue(ddl.contains(" AS (a + b) PERSISTENT"), "Expected persisted generated column to be written as PERSISTENT");
        assertFalse(ddl.contains("STORED"), "MariaDB should emit PERSISTENT, not STORED");
        assertFalse(ddl.contains(" VIRTUAL"), "Persisted generated column should not be written as VIRTUAL");
    }

    @Test
    void testWriteGeneratedColumn_nonPersisted_emitsVirtualKeyword() {
        MariaDBDdlBuilder ddlBuilder = new MariaDBDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        String ddl = ddlBuilder.createTable(buildTableWithComputedColumn(false));
        assertTrue(ddl.contains(" AS (a + b) VIRTUAL"), "Expected non-persisted generated column to be written as VIRTUAL");
        assertFalse(ddl.contains("PERSISTENT"), "Non-persisted generated column should not be written as PERSISTENT");
    }

    @Test
    void testWriteGeneratedColumn_indexRetained_whenPersistedGeneratedColumnsSupported() {
        MariaDBDdlBuilder ddlBuilder = new MariaDBDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setPersistedGeneratedColumnsSupported(true);
        Table table = buildTableWithComputedColumn(true);
        NonUniqueIndex index = new NonUniqueIndex("idx_computed");
        index.addColumn(new IndexColumn("total"));
        table.addIndex(index);
        String ddl = ddlBuilder.createTable(table);
        assertTrue(ddl.contains("idx_computed"), "Expected index on persisted generated column to be created");
    }

    @Test
    void testWriteGeneratedColumn_indexSkipped_forNonPersistedColumn() {
        MariaDBDdlBuilder ddlBuilder = new MariaDBDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setPersistedGeneratedColumnsSupported(true);
        Table table = buildTableWithComputedColumn(false);
        NonUniqueIndex index = new NonUniqueIndex("idx_computed");
        index.addColumn(new IndexColumn("total"));
        table.addIndex(index);
        String ddl = ddlBuilder.createTable(table);
        assertFalse(ddl.contains("idx_computed"), "Index on a non-persisted (VIRTUAL) generated column should be skipped");
    }

    @Test
    void testWriteGeneratedColumn_indexRetained_whenNonPersistedGeneratedColumnsIndexSupported() {
        MariaDBDdlBuilder ddlBuilder = new MariaDBDdlBuilder();
        ddlBuilder.getDatabaseInfo().setGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setPersistedGeneratedColumnsSupported(true);
        ddlBuilder.getDatabaseInfo().setNonPersistedGeneratedColumnsIndexSupported(true);
        Table table = buildTableWithComputedColumn(false);
        NonUniqueIndex index = new NonUniqueIndex("idx_computed");
        index.addColumn(new IndexColumn("total"));
        table.addIndex(index);
        String ddl = ddlBuilder.createTable(table);
        assertTrue(ddl.contains("idx_computed"), "Expected index on non-persisted (VIRTUAL) generated column to be created "
                + "when the platform supports indexing non-persisted generated columns");
    }

    private Table buildTableWithComputedColumn(boolean persisted) {
        Column idCol = new Column("id", true, Types.INTEGER, 0, 0);
        Column aCol = new Column("a", false, Types.INTEGER, 0, 0);
        Column bCol = new Column("b", false, Types.INTEGER, 0, 0);
        Column computedCol = new Column("total");
        computedCol.setMappedTypeCode(Types.INTEGER);
        computedCol.setGenerated(true);
        computedCol.setPersisted(persisted);
        computedCol.setDefaultValue("(a + b)");
        computedCol.addPlatformColumn(new PlatformColumn(DatabaseNamesConstants.MARIADB, "INTEGER", null));
        return new Table("test_computed", idCol, aCol, bCol, computedCol);
    }
}
