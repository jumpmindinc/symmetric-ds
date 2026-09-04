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
package org.jumpmind.symmetric.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseUpgradeListenerTest {
    private DatabaseUpgradeListener listener;
    private ISqlTemplate sqlTemplate;
    private Database currentModel;
    private StringBuilder sqlScript;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        sqlTemplate = mock(ISqlTemplate.class);
        listener = new DatabaseUpgradeListener();
        listener.setSymmetricEngine(engine);
        currentModel = new Database();
        currentModel.addTable(new Table("sym_test_table"));
        currentModel.addTable(new Table("sym_second_table"));
        sqlScript = new StringBuilder();
    }

    @Test
    void testDropTableDueToUpgrade_TableIsNull_ReturnsFalseAndNeverCallsSqlTemplate() {
        boolean result = listener.dropTableDueToUpgrade(null, currentModel, sqlTemplate, sqlScript);
        assertFalse(result, "A null table should not be dropped");
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testDropTableDueToUpgrade_TableExists_DropsTableAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropTableDueToUpgrade(table, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "An existing table should be dropped");
        verify(sqlTemplate, times(1)).update("drop table sym_test_table");
    }

    @Test
    void testDropTableDueToUpgrade_SqlTemplateThrows_ReturnsFalse() {
        when(sqlTemplate.update(anyString())).thenThrow(new RuntimeException("table does not exist"));
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropTableDueToUpgrade(table, currentModel, sqlTemplate, sqlScript);
        assertFalse(result, "A failed drop should not propagate the exception");
    }

    @Test
    void testTruncateTableDueToUpgrade_TableExists_TruncatesAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.truncateTableDueToUpgrade(table, sqlTemplate, sqlScript);
        assertTrue(result, "An existing table should be truncated");
        verify(sqlTemplate, times(1)).update("truncate table sym_test_table");
    }

    @Test
    void testDropPrimaryKeyConstraintDueToUpgrade_TableExists_QueriesConstraintNameAndDropsIt() {
        when(sqlTemplate.queryForString(anyString())).thenReturn("sym_pk_test_table");
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropPrimaryKeyConstraintDueToUpgrade(table, sqlTemplate, sqlScript);
        assertTrue(result, "An existing table's primary key should be dropped");
        verify(sqlTemplate, times(1)).update("alter table sym_test_table drop constraint sym_pk_test_table");
    }

    @Test
    void testDropTables_AllTablesExist_ActsOnEachAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_second_table" };
        boolean result = listener.dropTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Dropping all existing tables should succeed");
        verify(sqlTemplate, times(1)).update("drop table sym_test_table");
        verify(sqlTemplate, times(1)).update("drop table sym_second_table");
    }

    @Test
    void testDropTables_OneTableMissing_SkipsItAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_missing_table" };
        boolean result = listener.dropTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing table should be skipped without affecting the overall result");
        verify(sqlTemplate, times(1)).update("drop table sym_test_table");
        verify(sqlTemplate, never()).update("drop table sym_missing_table");
    }

    @Test
    void testTruncateTables_OneTableMissing_SkipsItAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_missing_table" };
        boolean result = listener.truncateTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing table should be skipped without affecting the overall result");
        verify(sqlTemplate, times(1)).update("truncate table sym_test_table");
        verify(sqlTemplate, never()).update("truncate table sym_missing_table");
    }

    @Test
    void testTruncateTables_AllTablesExist_ActsOnEachAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_second_table" };
        boolean result = listener.truncateTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "Truncating all existing tables should succeed");
        verify(sqlTemplate, times(1)).update("truncate table sym_test_table");
        verify(sqlTemplate, times(1)).update("truncate table sym_second_table");
    }

    @Test
    void testDropPkFromTables_OneTableMissing_SkipsItAndReturnsTrue() {
        when(sqlTemplate.queryForString(anyString())).thenReturn("sym_pk_test_table");
        String[] tableNames = { "sym_test_table", "sym_missing_table" };
        boolean result = listener.dropPkFromTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing table should be skipped without affecting the overall result");
        verify(sqlTemplate, times(1)).update("alter table sym_test_table drop constraint sym_pk_test_table");
    }

    @Test
    void testDropIndexFromTable_TableIsNull_ReturnsFalseAndNeverCallsSqlTemplate() {
        boolean result = listener.dropIndexFromTable(null, "sym_idx_test", sqlTemplate, sqlScript);
        assertFalse(result, "A null table should not have an index dropped");
        verify(sqlTemplate, never()).update(anyString());
    }

    @Test
    void testDropIndexFromTable_TableExists_DropsIndexAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropIndexFromTable(table, "sym_idx_test", sqlTemplate, sqlScript);
        assertTrue(result, "An existing table's index should be dropped");
        verify(sqlTemplate, times(1)).update("drop index sym_test_table.sym_idx_test");
    }

    @Test
    void testDropIndexFromTable_SqlTemplateThrows_ReturnsFalse() {
        when(sqlTemplate.update(anyString())).thenThrow(new RuntimeException("index does not exist"));
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.dropIndexFromTable(table, "sym_idx_test", sqlTemplate, sqlScript);
        assertFalse(result, "A failed index drop should not propagate the exception");
    }

    @Test
    void testDeleteFromTableDueToUpgrade_TableExists_DeletesAndReturnsTrue() {
        Table table = currentModel.findTable("sym_test_table");
        boolean result = listener.deleteFromTableDueToUpgrade(table, sqlTemplate, sqlScript);
        assertTrue(result, "An existing table's rows should be deleted");
        verify(sqlTemplate, times(1)).update("delete from sym_test_table");
    }

    @Test
    void testDeleteFromTables_OneTableMissing_SkipsItAndReturnsTrue() {
        String[] tableNames = { "sym_test_table", "sym_missing_table" };
        boolean result = listener.deleteFromTables(tableNames, currentModel, sqlTemplate, sqlScript);
        assertTrue(result, "A missing table should be skipped without affecting the overall result");
        verify(sqlTemplate, times(1)).update("delete from sym_test_table");
        verify(sqlTemplate, never()).update("delete from sym_missing_table");
    }
}
