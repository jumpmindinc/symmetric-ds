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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuckDbDatabaseWriterTest {
    private static final int ID_INDEX = 0;
    private static final int NOTES_INDEX = 1;
    private static final int QUANTITY_INDEX = 2;
    private DuckDbDatabaseWriter writer;
    private Table table;

    @BeforeEach
    void setUp() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        table = new Table("test_customer");
        table.addColumn(new Column("id", true, Types.VARCHAR, 0, 0));
        table.addColumn(new Column("notes", false, Types.VARCHAR, 0, 0));
        table.addColumn(new Column("quantity", false, Types.INTEGER, 0, 0));
        writer = new DuckDbDatabaseWriter(platform, platform, "sym_", null, new DatabaseWriterSettings());
        writer.sourceTable = table;
        writer.targetTable = table;
    }

    private CsvData createUpdate(String[] rowData, String[] oldData) {
        CsvData data = new CsvData(DataEventType.UPDATE, new String[] { "1" }, rowData);
        data.putParsedData(CsvData.OLD_DATA, oldData);
        return data;
    }

    private boolean doesColumnNeedUpdated(int index, CsvData data, boolean applyChangesOnly) {
        return writer.doesColumnNeedUpdated(index, table.getColumn(index), data, data.getParsedData(CsvData.ROW_DATA),
                data.getParsedData(CsvData.OLD_DATA), applyChangesOnly);
    }

    @Test
    void testDoesColumnNeedUpdated_varcharUpdatedToNullWithMissingOldValueNeedsUpdated() {
        CsvData data = createUpdate(new String[] { "1", null, "5" }, new String[] { "1", null, "5" });
        data.setNoBinaryOldData(true);
        assertTrue(doesColumnNeedUpdated(NOTES_INDEX, data, true));
        assertTrue(data.getChangedDataIndicators()[NOTES_INDEX]);
    }

    @Test
    void testDoesColumnNeedUpdated_blankVarcharWithCapturedOldLobDataDoesNotNeedUpdated() {
        CsvData data = createUpdate(new String[] { "1", null, "5" }, new String[] { "1", null, "5" });
        assertFalse(doesColumnNeedUpdated(NOTES_INDEX, data, true));
        assertFalse(data.getChangedDataIndicators()[NOTES_INDEX]);
    }

    @Test
    void testDoesColumnNeedUpdated_varcharWithEmptyOldValueNeedsUpdated() {
        CsvData data = createUpdate(new String[] { "1", "", "5" }, new String[] { "1", "", "5" });
        data.setNoBinaryOldData(true);
        assertTrue(doesColumnNeedUpdated(NOTES_INDEX, data, true));
        assertTrue(data.getChangedDataIndicators()[NOTES_INDEX]);
    }

    @Test
    void testDoesColumnNeedUpdated_unchangedVarcharWithCapturedOldValueDoesNotNeedUpdated() {
        CsvData data = createUpdate(new String[] { "1", "abc", "5" }, new String[] { "1", "abc", "5" });
        assertFalse(doesColumnNeedUpdated(NOTES_INDEX, data, true));
        assertFalse(data.getChangedDataIndicators()[NOTES_INDEX]);
    }

    @Test
    void testDoesColumnNeedUpdated_changedVarcharNeedsUpdated() {
        CsvData data = createUpdate(new String[] { "1", "xyz", "5" }, new String[] { "1", "abc", "5" });
        assertTrue(doesColumnNeedUpdated(NOTES_INDEX, data, true));
    }

    @Test
    void testDoesColumnNeedUpdated_nonVarcharWithMissingOldValueDoesNotNeedUpdated() {
        CsvData data = createUpdate(new String[] { "1", "abc", null }, new String[] { "1", "abc", null });
        assertFalse(doesColumnNeedUpdated(QUANTITY_INDEX, data, true));
        assertFalse(data.getChangedDataIndicators()[QUANTITY_INDEX]);
    }

    @Test
    void testDoesColumnNeedUpdated_unchangedKeyWithoutOldDataDoesNotNeedUpdated() {
        CsvData data = createUpdate(new String[] { "1", null, "5" }, null);
        assertFalse(doesColumnNeedUpdated(ID_INDEX, data, true));
    }

    @Test
    void testDoesColumnNeedUpdated_unchangedVarcharWithoutApplyChangesOnlyNeedsUpdated() {
        CsvData data = createUpdate(new String[] { "1", "abc", "5" }, new String[] { "1", "abc", "5" });
        assertTrue(doesColumnNeedUpdated(NOTES_INDEX, data, false));
        assertTrue(data.getChangedDataIndicators()[NOTES_INDEX]);
    }

    @Test
    void testDoesColumnNeedUpdated_withoutApplyChangesOnlyUpdatesAllColumnsExceptUnchangedKey() {
        CsvData data = createUpdate(new String[] { "1", null, "5" }, new String[] { "1", null, "5" });
        assertFalse(doesColumnNeedUpdated(ID_INDEX, data, false));
        assertTrue(doesColumnNeedUpdated(NOTES_INDEX, data, false));
        assertTrue(doesColumnNeedUpdated(QUANTITY_INDEX, data, false));
        assertFalse(data.getChangedDataIndicators()[ID_INDEX]);
    }
}
