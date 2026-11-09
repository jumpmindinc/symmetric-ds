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
package org.jumpmind.symmetric.io.data.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

class ClickHouseDatabaseWriterTest {
    @Test
    void testReportedCount_updateAndDeleteReportOneRowEvenWhenDriverSaysZero() {
        assertEquals(1, ClickHouseDatabaseWriter.reportedCount(DmlType.UPDATE, 0));
        assertEquals(1, ClickHouseDatabaseWriter.reportedCount(DmlType.DELETE, 0));
    }

    @Test
    void testReportedCount_insertKeepsTheDriverCount() {
        assertEquals(1, ClickHouseDatabaseWriter.reportedCount(DmlType.INSERT, 1));
        assertEquals(0, ClickHouseDatabaseWriter.reportedCount(DmlType.INSERT, 0));
    }

    @Test
    void testToTruncate_deleteWithoutWhere_becomesTruncate() {
        assertEquals("truncate table item", ClickHouseDatabaseWriter.toTruncate("delete from item"));
        assertEquals("truncate table \"db\".\"item\"", ClickHouseDatabaseWriter.toTruncate("delete from \"db\".\"item\""));
        assertEquals("truncate table `my table`", ClickHouseDatabaseWriter.toTruncate("DELETE FROM `my table`;"));
        assertEquals("truncate table db.item", ClickHouseDatabaseWriter.toTruncate("  delete   from   db.item  ;  "));
    }

    @Test
    void testToTruncate_deleteWithWhere_isNotChanged() {
        String sql = "delete from item where id = 1";
        assertEquals(sql, ClickHouseDatabaseWriter.toTruncate(sql));
        sql = "delete from \"item\" where \"id\" = ?";
        assertEquals(sql, ClickHouseDatabaseWriter.toTruncate(sql));
    }

    @Test
    void testToTruncate_otherStatements_areNotChanged() {
        assertEquals("truncate table item", ClickHouseDatabaseWriter.toTruncate("truncate table item"));
        assertEquals("insert into item values (1)", ClickHouseDatabaseWriter.toTruncate("insert into item values (1)"));
        assertTrue(ClickHouseDatabaseWriter.toTruncate("update item set a = 1").startsWith("update"));
    }

    @Test
    void testConstructor_sendsDatesAndTimesAsText() {
        DatabaseWriterSettings settings = new DatabaseWriterSettings();
        assertFalse(settings.isTreatDateTimeFieldsAsVarchar());
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        new ClickHouseDatabaseWriter(platform, platform, "sym", null, settings);
        assertTrue(settings.isTreatDateTimeFieldsAsVarchar());
    }

    private static final String[] COLUMNS = { "id", "region", "name" };
    private static final String[] KEY = { "id", "region" };

    @Test
    void testSortingKeyChanged_keyValueDiffers() {
        assertTrue(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, new String[] { "1", "east" }, new String[] { "2", "east", "a" }, COLUMNS,
                Set.of("id", "region")));
        assertTrue(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, new String[] { "1", "east" }, new String[] { "1", "west", "a" }, COLUMNS,
                Set.of("id", "region")));
    }

    @Test
    void testSortingKeyChanged_sameKeyIsNotAChange() {
        assertFalse(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, new String[] { "1", "east" }, new String[] { "1", "east", "changed" }, COLUMNS,
                Set.of("id", "region")));
    }

    @Test
    void testSortingKeyChanged_nullValuesAreComparedSafely() {
        assertFalse(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, new String[] { "1", null }, new String[] { "1", null, "a" }, COLUMNS,
                Set.of("id", "region")));
        assertTrue(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, new String[] { "1", null }, new String[] { "1", "east", "a" }, COLUMNS,
                Set.of("id", "region")));
    }

    @Test
    void testSortingKeyChanged_onlyColumnsInTheSortingKeyCount() {
        // The target was created with only "id" in its ORDER BY, so changing "region" is an ordinary update
        assertFalse(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, new String[] { "1", "east" }, new String[] { "1", "west", "a" }, COLUMNS,
                Set.of("id")));
    }

    @Test
    void testSortingKeyChanged_keyColumnNamesAreMatchedIgnoringCaseOfTheSortingKey() {
        assertTrue(ClickHouseDatabaseWriter.sortingKeyChanged(new String[] { "ID" }, new String[] { "1" }, new String[] { "2" }, new String[] { "ID" },
                Set.of("id")));
    }

    @Test
    void testSortingKeyChanged_missingInformationIsNotAChange() {
        assertFalse(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, null, new String[] { "2", "east", "a" }, COLUMNS, Set.of("id")));
        assertFalse(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, new String[] { "1" }, new String[] { "2", "east", "a" }, COLUMNS, Set.of("id")));
        assertFalse(ClickHouseDatabaseWriter.sortingKeyChanged(KEY, new String[] { "1", "east" }, null, COLUMNS, Set.of("id")));
    }

    @Test
    void testChangedSortingKeyColumns_namesOnlyTheColumnsThatChanged() {
        assertEquals(List.of("id"), ClickHouseDatabaseWriter.changedSortingKeyColumns(KEY, new String[] { "1", "east" },
                new String[] { "2", "east", "a" }, COLUMNS, Set.of("id", "region")));
        assertEquals(List.of("id", "region"), ClickHouseDatabaseWriter.changedSortingKeyColumns(KEY, new String[] { "1", "east" },
                new String[] { "2", "west", "a" }, COLUMNS, Set.of("id", "region")));
        assertTrue(ClickHouseDatabaseWriter.changedSortingKeyColumns(KEY, new String[] { "1", "east" }, new String[] { "1", "east", "a" }, COLUMNS,
                Set.of("id", "region")).isEmpty());
    }
}
