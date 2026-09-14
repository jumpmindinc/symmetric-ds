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

import java.util.Map;

import org.jumpmind.db.DbTestUtils;
import org.jumpmind.db.platform.ase.AseDatabasePlatform;
import org.jumpmind.db.platform.greenplum.GreenplumPlatform;
import org.jumpmind.db.platform.informix.InformixDatabasePlatform;
import org.jumpmind.db.platform.ingres.IngresDatabasePlatform;
import org.jumpmind.db.platform.redshift.RedshiftDatabasePlatform;
import org.jumpmind.db.platform.sqlanywhere.SqlAnywhereDatabasePlatform;
import org.jumpmind.db.platform.sqlite.SqliteDatabasePlatform;
import org.jumpmind.db.platform.voltdb.VoltDbDatabasePlatform;
import org.jumpmind.symmetric.io.AbstractWriterTest;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DatabaseWriterConflictTest extends AbstractWriterTest {
    private final static String TEST_TABLE = "test_dataloader_parent";
    private final static String[] TEST_KEYS = { "id" };
    private final static String[] TEST_COLUMNS = { "id", "name", "pid" };
    private final static String TEST_TABLE_CHILD = "test_dataloader_child";
    private final static String[] TEST_KEYS_CHILD = { "id" };
    private final static String[] TEST_COLUMNS_CHILD = { "id", "pid" };
    private final static String TEST_TABLE_GRANDCHILD = "test_dataloader_grandchild";
    private final static String[] TEST_KEYS_GRANDCHILD = { "id" };
    private final static String[] TEST_COLUMNS_GRANDCHILD = { "id", "pid" };
    private final static String TEST_TABLE_CHILD_NOPK = "test_dataloader_child_nopk";
    private final static String[] TEST_KEYS_CHILD_NOPK = { "id", "pid" };
    private final static String[] TEST_COLUMNS_CHILD_NOPK = { "id", "pid" };

    private enum WhichTable {
        PARENT, CHILD, GRANDCHILD, CHILD_NOPK
    };

    private WhichTable whichTable;
    private static boolean shouldTest;

    @BeforeAll
    public static void setup() throws Exception {
        platform = DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT);
        platform.createDatabase(platform.readDatabaseFromXml("/testDatabaseWriter.xml", true),
                true, false);
        // Ignore SQLite because it doesn't enforce FKs, and its unique constraint error message doesn't tell you the index name
        // TODO: Sybase ASE has metadata problem in the DDL reader for exported keys
        // TODO: Informix has metadata problem in the DDL reader for exported keys
        // TODO: Untested on Volt, Greenplum, Redshift
        // TODO: Ingres does not display UK index that caused SQl exception
        shouldTest = !(platform instanceof SqliteDatabasePlatform || platform instanceof SqlAnywhereDatabasePlatform ||
                platform instanceof AseDatabasePlatform || platform instanceof InformixDatabasePlatform ||
                platform instanceof VoltDbDatabasePlatform || platform instanceof GreenplumPlatform ||
                platform instanceof RedshiftDatabasePlatform || platform instanceof IngresDatabasePlatform);
    }

    @Test
    public void testInsertUkViolation() throws Exception {
        insert(getNextId(), "dupe", null);
        insert(getNextId(), "dupe", null);
    }

    @Test
    public void testInsertUkViolationDeleteFkViolation() throws Exception {
        String firstId = insert(getNextId(), "twin2", null);
        String secondId = insert(getNextId(), "depends1", firstId);
        String thirdId = insert(getNextId(), "depends2", firstId);
        insert(getNextId(), "depends3", secondId);
        insert(getNextId(), "depends4", thirdId);
        insert(getNextId(), "twin2", null);
    }

    @Test
    public void testInsertUkViolationDeleteFkViolationChildTable() throws Exception {
        String firstId = insert(getNextId(), "twin", null);
        whichTable = WhichTable.CHILD;
        String secondId = insert(getNextId(), firstId);
        String thirdId = insert(getNextId(), firstId);
        whichTable = WhichTable.GRANDCHILD;
        insert(getNextId(), secondId);
        insert(getNextId(), thirdId);
        whichTable = WhichTable.PARENT;
        insert(getNextId(), "twin", null);
    }

    @Test
    public void testInsertPkViolation() throws Exception {
        String firstId = insert(getNextId(), "insert", null);
        insert(firstId, "insert", null);
    }

    @Test
    public void testInsertPkViolationUpdateUkViolation() throws Exception {
        String firstId = insert(getNextId(), "insert-update", null);
        insert(getNextId(), "insert-update-dupe", null);
        insert(firstId, "insert-update-dupe", null);
    }

    @Test
    public void testInsertPkViolationUpdateUkViolationDeleteFkViolation() throws Exception {
        String firstId = insert(getNextId(), "deep", null);
        String secondId = insert(getNextId(), "deep-dupe", firstId);
        insert(getNextId(), "deep-dupe-child", secondId);
        insert(firstId, "deep-dupe", null);
    }

    @Test
    public void testInsertPkViolationUpdateUkViolationDeleteFkViolationChildTable() throws Exception {
        String firstId = insert(getNextId(), "deep2", null);
        String secondId = insert(getNextId(), "deep2-dupe", firstId);
        whichTable = WhichTable.CHILD;
        String thirdId = insert(getNextId(), secondId);
        whichTable = WhichTable.GRANDCHILD;
        insert(getNextId(), thirdId);
        whichTable = WhichTable.PARENT;
        insert(firstId, "deep2-dupe", null);
    }

    @Test
    public void testUpdateFkViolation() throws Exception {
        String firstId = insert(getNextId(), "update-fk", null);
        insert(getNextId(), "update-fk-child", firstId);
        update(firstId, getNextId(), "update-fk2", null);
    }

    @Test
    public void testUpdateFkViolationChildTable() throws Exception {
        String firstId = insert(getNextId(), "update-fk-parent", null);
        String secondId = insert(getNextId(), "update-fk2-parent", firstId);
        whichTable = WhichTable.CHILD;
        insert(getNextId(), secondId);
        whichTable = WhichTable.PARENT;
        update(firstId, getNextId(), "update-fk-parent2", null);
    }

    @Test
    public void testUpdateUkFkViolation() throws Exception {
        String firstId = insert(getNextId(), "update-ukfk", null);
        insert(getNextId(), "update-ukfk-child", firstId);
        update(firstId, getNextId(), "update-ukfk", null);
    }

    @Test
    public void testUpdateUkViolation() throws Exception {
        String firstId = insert(getNextId(), "update-uk1", null);
        insert(getNextId(), "update-uk2", null);
        update(firstId, getNextId(), "update-uk2", null);
    }

    @Test
    public void testUpdateUkFkViolationChildTable() throws Exception {
        String firstId = insert(getNextId(), "update2-ukfk", null);
        whichTable = WhichTable.CHILD;
        insert(getNextId(), firstId);
        whichTable = WhichTable.PARENT;
        update(firstId, getNextId(), "update2-ukfk", null);
    }

    @Test
    public void testUpdatePkViolation() throws Exception {
        String firstId = insert(getNextId(), "update-pk1", null);
        String secondId = insert(getNextId(), "update-pk2", null);
        update(secondId, firstId, "update-pk2", null);
    }

    @Test
    public void testUpdatePkViolationDeleteFkViolationBlockingSelf() throws Exception {
        String firstId = insert(getNextId(), "update2-pk1", null);
        String secondId = insert(getNextId(), "update2-pk2", firstId);
        update(secondId, firstId, "update2-pk2", null);
    }

    @Test
    public void testUpdateUkViolationDeleteFkViolationBlockingSelf() throws Exception {
        String firstId = insert(getNextId(), "update3-pk1", null);
        String secondId = insert(getNextId(), "update3-pk2", firstId);
        update(secondId, secondId, "update3-pk1", null);
    }

    @Test
    public void testUpdatePkViolationSameUniqueName() throws Exception {
        String firstId = insert(getNextId(), "same-pk1", null);
        String secondId = insert(getNextId(), "same-pk2", firstId);
        update(secondId, firstId, "same-pk1", null);
    }

    @Test
    public void testUpdatePkViolationDeleteFkViolation() throws Exception {
        String firstId = insert(getNextId(), "pk1", null);
        String secondId = insert(getNextId(), "pk2", null);
        String thirdId = insert(getNextId(), "pk3", secondId);
        insert(getNextId(), "pk4", thirdId);
        update(firstId, secondId, "pk5", null);
    }

    @Test
    public void testUpdateUkPkViolationDeleteFkViolation() throws Exception {
        String firstId = insert(getNextId(), "ukpk1", null);
        String secondId = insert(getNextId(), "ukpk2", null);
        String thirdId = insert(getNextId(), "ukpk3", secondId);
        insert(getNextId(), "ukpk4", thirdId);
        update(firstId, secondId, "ukpk2", null);
    }

    @Test
    public void testUpdateNoRowsInsertUkViolation() throws Exception {
        insert(getNextId(), "norowsuk", null);
        String id = getNextId();
        update(id, id, "norowsuk", null);
    }

    @Test
    public void testUpdateNoRowsInsertUkViolationDeleteFkViolation() throws Exception {
        String firstId = insert(getNextId(), "norowsukfk", null);
        insert(getNextId(), "norowsukfk2", firstId);
        String id = getNextId();
        update(id, id, "norowsukfk", null);
    }

    @Test
    public void testDeleteFkViolation() throws Exception {
        String firstId = insert(getNextId(), "delete1", null);
        String secondId = insert(getNextId(), "delete2", firstId);
        insert(getNextId(), "delete3", secondId);
        delete(firstId, firstId, "delete1", null);
    }

    @Test
    public void testDeleteFkViolationChildTable() throws Exception {
        String firstId = insert(getNextId(), "deleteparent", null);
        whichTable = WhichTable.CHILD;
        String secondId = insert(getNextId(), firstId);
        whichTable = WhichTable.GRANDCHILD;
        insert(getNextId(), secondId);
        whichTable = WhichTable.PARENT;
        delete(firstId, firstId, "deleteparent", null);
    }

    @Test
    public void testDeleteFkViolationChildTableNoPk() throws Exception {
        String firstId = insert(getNextId(), "deleteparentnopk", null);
        whichTable = WhichTable.CHILD_NOPK;
        insert(getNextId(), firstId);
        whichTable = WhichTable.PARENT;
        delete(firstId, firstId, "deleteparentnopk", null);
    }

    private String insert(String... values) {
        if (shouldTest) {
            try {
                writeData(new CsvData(DataEventType.INSERT, values), values);
                return values[0];
            } catch (AssertionError e) {
                if (platform.getDatabaseInfo().isRequiresSavePointsInTransaction()) {
                    // we did our corrections, but the original batch needs to be retried
                    writeData(new CsvData(DataEventType.INSERT, values), values);
                    return values[0];
                } else {
                    throw e;
                }
            }
        }
        return null;
    }

    private void update(String id, String... values) {
        if (shouldTest) {
            try {
                writeData(new CsvData(DataEventType.UPDATE, new String[] { id }, values), values);
            } catch (AssertionError e) {
                if (platform.getDatabaseInfo().isRequiresSavePointsInTransaction()) {
                    // we did our corrections, but the original batch needs to be retried
                    writeData(new CsvData(DataEventType.UPDATE, new String[] { id }, values), values);
                } else {
                    throw e;
                }
            }
        }
    }

    private void delete(String id, String... values) {
        if (shouldTest) {
            try {
                writeData(new CsvData(DataEventType.DELETE, new String[] { id }, values), null);
            } catch (AssertionError e) {
                if (platform.getDatabaseInfo().isRequiresSavePointsInTransaction()) {
                    // we did our corrections, but the original batch needs to be retried
                    writeData(new CsvData(DataEventType.DELETE, new String[] { id }, values), null);
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    protected void assertTestTableEquals(String testTableId, String[] expectedValues) {
        String sql = "select " + getSelect(getTestColumns()) + " from " + getTestTable() + " where "
                + getWhere(getTestKeys());
        Map<String, Object> results = null;
        if (whichTable != WhichTable.CHILD_NOPK) {
            results = platform.getSqlTemplate().queryForMap(sql, Long.valueOf(testTableId));
        } else {
            Long[] l = new Long[expectedValues.length];
            for (int i = 0; i < expectedValues.length; i++) {
                l[i] = Long.valueOf(expectedValues[i]);
            }
            results = platform.getSqlTemplate().queryForMap(sql, (Object[]) l);
        }
        assertEquals(getTestColumns(), expectedValues, results);
    }

    @Override
    protected String getTestTable() {
        if (whichTable == WhichTable.CHILD)
            return TEST_TABLE_CHILD;
        else if (whichTable == WhichTable.GRANDCHILD)
            return TEST_TABLE_GRANDCHILD;
        else if (whichTable == WhichTable.CHILD_NOPK) {
            return TEST_TABLE_CHILD_NOPK;
        } else
            return TEST_TABLE;
    }

    @Override
    protected String[] getTestKeys() {
        if (whichTable == WhichTable.CHILD)
            return TEST_KEYS_CHILD;
        else if (whichTable == WhichTable.GRANDCHILD)
            return TEST_KEYS_GRANDCHILD;
        else if (whichTable == WhichTable.CHILD_NOPK) {
            return TEST_KEYS_CHILD_NOPK;
        } else
            return TEST_KEYS;
    }

    @Override
    protected String[] getTestColumns() {
        if (whichTable == WhichTable.CHILD)
            return TEST_COLUMNS_CHILD;
        else if (whichTable == WhichTable.GRANDCHILD)
            return TEST_COLUMNS_GRANDCHILD;
        else if (whichTable == WhichTable.CHILD_NOPK) {
            return TEST_COLUMNS_CHILD_NOPK;
        } else
            return TEST_COLUMNS;
    }
}
