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

import org.jumpmind.db.DbTestUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.io.AbstractWriterTest;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.TargetDmlAction;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransformDatabaseWriterDeleteActionTest extends AbstractWriterTest {
    private static final String SOURCE_TABLE = "delete_action_source";
    private static final String TARGET_TABLE = "delete_action_target";
    private static final Table SOURCE = new Table(SOURCE_TABLE, new Column("id", true), new Column("name"));

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        platform = DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT);
    }

    @BeforeEach
    void createTargetTable() {
        platform.getSqlTemplate().update("drop table if exists " + TARGET_TABLE);
        platform.getSqlTemplate().update("create table " + TARGET_TABLE + " (id integer primary key, name varchar(100), operation char(1))");
    }

    @Test
    void testDeleteWithOldDataAppliesDeleteOnlyColumns() {
        writeData(newWriter(), new TableCsvData(SOURCE, insert(), update(), deleteWithOldData()));
        assertTargetRow("b", "D");
    }

    @Test
    void testDeleteWithoutOldDataAppliesDeleteOnlyColumns() {
        writeData(newWriter(), new TableCsvData(SOURCE, insert(), update(), new CsvData(DataEventType.DELETE, new String[] { "1" }, null)));
        assertTargetRow("b", "D");
    }

    @Test
    void testUpdateWithOldDataAppliesUpdateOnlyColumns() {
        writeData(newWriter(), new TableCsvData(SOURCE, insert(), update()));
        assertTargetRow("b", "U");
    }

    private TransformDatabaseWriter newWriter() {
        TransformTable transform = new TransformTable(SOURCE_TABLE, TARGET_TABLE, TransformPoint.LOAD, new TransformColumn("id", "id", true),
                operation("I", IncludeOnType.INSERT), operation("U", IncludeOnType.UPDATE), operation("D", IncludeOnType.DELETE));
        transform.setUpdateAction(TargetDmlAction.UPD_ROW.name());
        transform.setDeleteAction(TargetDmlAction.UPDATE_COL);
        DatabaseWriterSettings settings = new DatabaseWriterSettings();
        settings.setDefaultConflictSetting(new Conflict());
        return new TransformDatabaseWriter(platform, platform, "sym", settings, TransformWriterTest.buildDefaultColumnTransforms(),
                new TransformTable[] { transform });
    }

    private static TransformColumn operation(String value, IncludeOnType includeOn) {
        TransformColumn column = new TransformColumn(null, "operation", false, "const", value);
        column.setIncludeOn(includeOn);
        return column;
    }

    private static CsvData insert() {
        return new CsvData(DataEventType.INSERT, new String[] { "1", "a" });
    }

    private static CsvData update() {
        CsvData update = new CsvData(DataEventType.UPDATE, new String[] { "1", "b" }, new String[] { "1", "a" }, null);
        update.putParsedData(CsvData.PK_DATA, new String[] { "1" });
        return update;
    }

    private static CsvData deleteWithOldData() {
        CsvData delete = new CsvData(DataEventType.DELETE, new String[] { "1" }, null);
        delete.putParsedData(CsvData.OLD_DATA, new String[] { "1", "b" });
        return delete;
    }

    private static void assertTargetRow(String expectedName, String expectedOperation) {
        Row row = platform.getSqlTemplate().queryForRow("select name, operation from " + TARGET_TABLE + " where id = 1");
        Assertions.assertEquals(expectedName, row.getString("name"));
        Assertions.assertEquals(expectedOperation, row.getString("operation"));
    }
}
