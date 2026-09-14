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
package org.jumpmind.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.sql.Types;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.jumpmind.db.io.DatabaseXmlUtil;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.platform.h2.H2DdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlScript;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DatabasePlatformTest {
    private static IDatabasePlatform platform;
    protected final static String SIMPLE_TABLE = "test_simple_table";
    protected final static String UPPERCASE_TABLE = "TEST_UPPERCASE_TABLE";
    protected Level originalLevel;

    @BeforeAll
    public static void setup() throws Exception {
        platform = DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT);
        platform.createDatabase(DatabaseXmlUtil.read(new InputStreamReader(
                DatabasePlatformTest.class.getResourceAsStream("/testCreateDatabase.xml"))), true,
                true);
    }

    @BeforeEach
    public void turnOnDebug() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext context) {
            originalLevel = context.getLogger("org.jumpmind.db").getEffectiveLevel();
            context.getLogger("org.jumpmind.db").setLevel(Level.TRACE);
        }
    }

    // @After
    public void turnOffDebug() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext context) {
            context.getLogger("org.jumpmind.db").setLevel(originalLevel);
        }
    }

    @Test
    public void testTableRebuild() {
        Table table = new Table("TEST_REBUILD");
        table.addColumn(new Column("ID1", true));
        table.getColumnWithName("ID1").setTypeCode(Types.INTEGER);
        table.getColumnWithName("ID1").setRequired(true);
        table.addColumn(new Column("NOTES"));
        table.getColumnWithName("NOTES").setTypeCode(Types.VARCHAR);
        table.getColumnWithName("NOTES").setSize("20");
        table.getColumnWithName("NOTES").setDefaultValue("1234");
        Table origTable = table.copy();
        Table tableFromDatabase = dropCreateAndThenReadTable(table);
        assertNotNull(tableFromDatabase);
        assertEquals(2, tableFromDatabase.getColumnCount());
        ISqlTemplate template = platform.getSqlTemplate();
        String delimiter = platform.getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        assertEquals(1, template.update(String.format("insert into %s%s%s values(?,?)", delimiter, tableFromDatabase.getName(), delimiter), 1, "test"));
        table.addColumn(new Column("ID2", true));
        table.getColumnWithName("ID2").setTypeCode(Types.VARCHAR);
        table.getColumnWithName("ID2").setSize("20");
        table.getColumnWithName("ID2").setRequired(true);
        table.getColumnWithName("ID2").setDefaultValue("value");
        table.addColumn(new Column("NOTES2"));
        table.getColumnWithName("NOTES2").setTypeCode(Types.VARCHAR);
        table.getColumnWithName("NOTES2").setSize("20");
        table.getColumnWithName("NOTES2").setDefaultValue("1234");
        // alter to add two columns that will cause a table rebuild
        platform.alterTables(false, table);
        tableFromDatabase = (Table) platform.getRelationFromCache(table.getName(), true);
        assertNotNull(tableFromDatabase);
        assertEquals(4, tableFromDatabase.getColumnCount());
        assertEquals(1, template.queryForLong(String.format("select count(*) from %s%s%s", delimiter, tableFromDatabase.getName(), delimiter)));
        // alter to remove two columns that will cause a table rebuild
        platform.alterTables(false, origTable);
        tableFromDatabase = (Table) platform.getRelationFromCache(origTable.getName(), true);
        assertNotNull(tableFromDatabase);
        assertEquals(2, tableFromDatabase.getColumnCount());
        assertEquals(1, template.queryForLong(String.format("select count(*) from %s%s%s", delimiter, tableFromDatabase.getName(), delimiter)));
    }

    @Test
    public void testAddDefaultValueToVarcharColumn() throws Exception {
        Table table = new Table("TEST_ADD_DEFAULT");
        table.addColumn(new Column("ID1", true));
        table.getColumnWithName("ID1").setTypeCode(Types.INTEGER);
        table.getColumnWithName("ID1").setRequired(true);
        table.addColumn(new Column("NOTES"));
        table.getColumnWithName("NOTES").setTypeCode(Types.VARCHAR);
        table.getColumnWithName("NOTES").setSize("20");
        dropCreateAndThenReadTable(table);
        final String DEFAULT_VALUE = "SOMETHING";
        table.getColumnWithName("NOTES").setDefaultValue(DEFAULT_VALUE);
        platform.alterTables(false, table);
        Table tableFromDatabase = (Table) platform.getRelationFromCache(table.getName(), true);
        assertEquals(DEFAULT_VALUE, tableFromDatabase.getColumnWithName("NOTES").getDefaultValue());
    }

    @Test
    public void testChangeNotNullToNullColumn() throws Exception {
        Table table = new Table("TEST_NULL_DEFAULT");
        table.addColumn(new Column("ID1", true));
        table.getColumnWithName("ID1").setTypeCode(Types.INTEGER);
        table.getColumnWithName("ID1").setRequired(true);
        table.addColumn(new Column("ANUM"));
        table.getColumnWithName("ANUM").setTypeCode(Types.INTEGER);
        table.getColumnWithName("ANUM").setRequired(true);
        dropCreateAndThenReadTable(table);
        table.getColumnWithName("ANUM").setRequired(false);
        platform.alterTables(false, table);
        Table tableFromDatabase = (Table) platform.getRelationFromCache(table.getName(), true);
        assertFalse(tableFromDatabase.getColumnWithName("ANUM").isRequired());
    }

    @Test
    public void testExportDefaultValueWithUnderscores() {
        Table table = new Table("TEST_DEFAULT_UNDERSCORES");
        table.addColumn(new Column("ID", true));
        table.getColumnWithName("ID").setTypeCode(Types.INTEGER);
        table.getColumnWithName("ID").setRequired(true);
        table.addColumn(new Column("NOTES"));
        table.getColumnWithName("NOTES").setTypeCode(Types.VARCHAR);
        table.getColumnWithName("NOTES").setSize("20");
        table.getColumnWithName("NOTES").setDefaultValue("this_has_underscores");
        Table tableFromDatabase = dropCreateAndThenReadTable(table);
        assertEquals(table.getColumnWithName("NOTES").getDefaultValue(),
                tableFromDatabase.getColumnWithName("NOTES").getDefaultValue());
    }

    protected Table dropCreateAndThenReadTable(Table table) {
        Database database = new Database();
        database.addTable(table);
        platform.createDatabase(database, true, false);
        return (Table) platform.getRelationFromCache(table.getName(), true);
    }

    @Test
    public void testDisableAutoincrement() throws Exception {
        Table table = new Table("TEST_AUTOPK_DISABLE");
        table.addColumn(new Column("ID", true));
        table.getColumnWithName("ID").setTypeCode(Types.INTEGER);
        table.getColumnWithName("ID").setAutoIncrement(true);
        table.getColumnWithName("ID").setRequired(true);
        table.addColumn(new Column("COL1"));
        table.getColumnWithName("COL1").setTypeCode(Types.VARCHAR);
        table.getColumnWithName("COL1").setSize("100");
        platform.alterCaseToMatchDatabaseDefaultCase(table);
        Table tableFromDatabase = dropCreateAndThenReadTable(table);
        table.getColumnWithName("ID").setAutoIncrement(false);
        table.getColumnWithName("COL1").setSize("254");
        table.getColumnWithName("COL1").setRequired(true);
        platform.alterTables(false, table);
        tableFromDatabase = (Table) platform.getRelationFromCache(table.getName(), true);
        assertFalse(tableFromDatabase.getColumnWithName("ID").isAutoIncrement());
        /* sqlite character fields do not limit based on size */
        if (!platform.getName().equals(DatabaseNamesConstants.SQLITE)) {
            assertEquals(254, tableFromDatabase.getColumnWithName("COL1").getSizeAsInt());
        }
    }

    @Test
    public void testUpgradePrimaryKeyAutoIncrementFromIntToBigInt() throws Exception {
        boolean upgradeSupported = !platform.getName().equals(DatabaseNamesConstants.DERBY)
                && !platform.getName().equals(DatabaseNamesConstants.HSQLDB2)
                && !platform.getName().equals(DatabaseNamesConstants.INFORMIX)
                && !platform.getName().equals(DatabaseNamesConstants.DB2)
                && !platform.getName().equals(DatabaseNamesConstants.ASE)
                && !platform.getName().equals(DatabaseNamesConstants.MSSQL2000)
                && !platform.getName().equals(DatabaseNamesConstants.MSSQL2005)
                && !platform.getName().equals(DatabaseNamesConstants.MSSQL2008)
                && !platform.getName().equals(DatabaseNamesConstants.MSSQL2016)
                && !platform.getName().equals(DatabaseNamesConstants.SQLANYWHERE)
                && !platform.getName().equals(DatabaseNamesConstants.INGRES);
        if (upgradeSupported) {
            Table table = new Table("TEST_UPGRADE");
            table.addColumn(new Column("ID", true));
            table.getColumnWithName("ID").setTypeCode(Types.INTEGER);
            table.getColumnWithName("ID").setAutoIncrement(true);
            table.getColumnWithName("ID").setRequired(true);
            table.addColumn(new Column("NOTES"));
            table.getColumnWithName("NOTES").setTypeCode(Types.VARCHAR);
            table.getColumnWithName("NOTES").setSize("100");
            Table tableFromDatabase = dropCreateAndThenReadTable(table);
            ISqlTransaction transaction = null;
            ISqlTemplate template = platform.getSqlTemplate();
            transaction = template.startSqlTransaction();
            transaction.execute("CREATE SEQUENCE TEST_UPGRADE_ID_SEQ START WITH 1 INCREMENT BY 1;");
            transaction.execute("CALL NEXTVAL('TEST_UPGRADE_ID_SEQ')");
            transaction.commit();
            transaction.close();
            assertNotNull(tableFromDatabase);
            assertTrue(tableFromDatabase.getColumnWithName("ID").isPrimaryKey());
            String insertSql = "insert into \"TEST_UPGRADE\" (\"ID\",\"NOTES\") values(null,?)";
            insertSql = insertSql.replaceAll("\"", platform.getDatabaseInfo().getDelimiterToken());
            long id1 = platform.getSqlTemplate()
                    .insertWithGeneratedKey(insertSql, "ID", getSequenceName(platform),
                            new Object[] { "test" }, new int[] { Types.VARCHAR });
            table.getColumnWithName("ID").setTypeCode(Types.BIGINT);
            IDdlBuilder builder = platform.getDdlBuilder();
            String alterSql = builder.alterTable(tableFromDatabase, table);
            assertFalse(alterSql, alterSql.toLowerCase().contains("create table"));
            new SqlScript(alterSql, platform.getSqlTemplate(), true, platform.getSqlScriptReplacementTokens()).execute(true);
            tableFromDatabase = (Table) platform.getRelationFromCache(table.getName(), true);
            assertEquals(Types.BIGINT, table.getColumnWithName("ID").getMappedTypeCode());
            assertTrue(tableFromDatabase.getColumnWithName("ID").isPrimaryKey());
            transaction = template.startSqlTransaction();
            transaction.execute("CALL NEXTVAL('TEST_UPGRADE_ID_SEQ')");
            transaction.commit();
            transaction.close();
            long id2 = platform.getSqlTemplate()
                    .insertWithGeneratedKey(insertSql, "ID", getSequenceName(platform),
                            new Object[] { "test" }, new int[] { Types.VARCHAR });
            assertNotSame(id1, id2);
        }
    }

    protected String getSequenceName(IDatabasePlatform platform) {
        if (platform.getName().equals(DatabaseNamesConstants.ORACLE) || platform.getName().equals(DatabaseNamesConstants.ORACLE122) || platform.getName()
                .equals(DatabaseNamesConstants.ORACLE23)) {
            return "TEST_UPGRADE_ID";
        } else if (platform.getName().equals(DatabaseNamesConstants.INTERBASE)) {
            return "SEQ_TEST_UPGRADE_ID";
        } else {
            return "test_upgrade_id";
        }
    }

    @Test
    public void testCreateAndReadTestSimpleTable() throws Exception {
        Table table = (Table) platform.getRelationFromCache(SIMPLE_TABLE, true);
        assertNotNull("Could not find " + SIMPLE_TABLE, table);
        assertEquals("The id column was not read in as an autoincrement column", true, table
                .getColumnWithName("id").isAutoIncrement());
    }

    @Test
    public void testReadTestUppercase() throws Exception {
        Table table = (Table) platform.getRelationFromCache(UPPERCASE_TABLE, true);
        assertNotNull("Could not find " + UPPERCASE_TABLE, table);
        assertEquals("The id column was not read in as an autoincrement column", true, table
                .getColumnWithName("id").isAutoIncrement());
    }

    @Test
    public void testNvarcharType() {
        Table table = new Table("test_nvarchar");
        table.addColumn(new Column("id", true, Types.INTEGER, 0, 0));
        table.addColumn(new Column("note", false, ColumnTypes.NVARCHAR, 100, 0));
        platform.createTables(true, false, table);
    }

    @Test
    public void getPermissionsTest() {
        List<PermissionResult> results = platform.checkSymTablePermissions(PermissionType.values());
        for (PermissionResult result : results) {
            assertTrue(result.toString(), result.getStatus() != Status.FAIL);
        }
    }

    @Test
    public void testEnumType() {
        boolean enumSupported = (platform.getName().equals(DatabaseNamesConstants.MYSQL) ||
                platform.getName().equals(DatabaseNamesConstants.NUODB));
        if (enumSupported) {
            Table table = new Table("table1");
            table.addColumn(new Column("col1", false));
            Column column = table.getColumnWithName("col1");
            column.setTypeCode(Types.VARCHAR);
            column.setJdbcTypeCode(Types.SMALLINT);
            column.setSizeAndScale(2, 0);
            column.setRequired(true);
            column.setJdbcTypeName("enum");
            PlatformColumn fc = new PlatformColumn();
            fc.setType("enum");
            fc.setSize(2);
            fc.setName(platform.getName());
            fc.setEnumValues(new String[] { "a", "b", "c", "d" });
            column.addPlatformColumn(fc);
            Database database = new Database();
            database.addTable(table);
            platform.createDatabase(database, true, false);
            Database readDatabase = platform.getDdlReader().readRelations(null, null, null);
            Table[] readTables = readDatabase.getTables();
            for (Table t : readTables) {
                if (t.getName().equalsIgnoreCase("table1")) {
                    for (Column c : t.getColumns()) {
                        assertTrue(c.getJdbcTypeName().equalsIgnoreCase("enum"));
                        PlatformColumn readFc = c.getPlatformColumns().get(platform.getName());
                        assertNotNull("Platform column not created for enum column type in platform " + platform.getName(), readFc);
                        assertTrue("Platform column not created as an enum in platform " + platform.getName(), readFc.getType().equalsIgnoreCase("enum"));
                    }
                    // Pick a database platform that does not implement enum, and check definition of column (should be varchar)
                    String ddl = new H2DdlBuilder().createTable(table);
                    assertTrue("Non-implementing enum platform not defined as type varchar", ddl.contains("varchar") || ddl.contains("VARCHAR"));
                }
            }
        }
    }

    @Test
    public void testMassageForLimitOffset() {
        if (platform.supportsLimitOffset()) {
            ISqlTemplate template = platform.getSqlTemplate();
            ISqlTransaction transaction = null;
            try {
                transaction = template.startSqlTransaction();
                Table table = (Table) platform.getRelationFromCache(UPPERCASE_TABLE, true);
                DatabaseInfo dbInfo = platform.getDatabaseInfo();
                String quote = dbInfo.getDelimiterToken();
                String catalogSeparator = dbInfo.getCatalogSeparator();
                String schemaSeparator = dbInfo.getSchemaSeparator();
                transaction.allowInsertIntoAutoIncrementColumns(true, table, quote, catalogSeparator, schemaSeparator);
                String insertSql = "insert into \"" + UPPERCASE_TABLE + "\" (\"id\",\"text\") values(?,?)";
                insertSql = insertSql.replaceAll("\"", platform.getDatabaseInfo().getDelimiterToken());
                int id = 0;
                for (char letter = 'a'; letter <= 'z'; letter++) {
                    id++;
                    transaction.prepareAndExecute(insertSql, id, String.valueOf(letter));
                }
                transaction.commit();
            } catch (Throwable e) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw new RuntimeException(e);
            } finally {
                if (transaction != null) {
                    transaction.close();
                }
            }
            String delimiter = platform.getDatabaseInfo().getDelimiterToken();
            String selectSql = "select " + delimiter + "text" + delimiter + " from " + delimiter + UPPERCASE_TABLE + delimiter + " order by " + delimiter + "id"
                    + delimiter + " asc";
            String testSql = platform.massageForLimitOffset(selectSql, 5, 0);
            List<Row> testResult = template.query(testSql);
            assertNotNull("The result set was null when testing the limit", testResult);
            assertEquals("The result set wasn't correctly limited when testing the limit", 5, testResult.size());
            assertEquals("The result set was unnecessarily offset", "a", testResult.get(0).getString("text"));
            testSql = platform.massageForLimitOffset(selectSql, 30, 5);
            testResult = template.query(testSql);
            assertNotNull("The result set was null when testing the offset", testResult);
            assertEquals("The result set was unnecessarily limited", 21, testResult.size());
            assertEquals("The result set wasn't correctly offset when testing the offset", "f", testResult.get(0).getString("text"));
            testSql = platform.massageForLimitOffset(selectSql, 5, 5);
            testResult = template.query(testSql);
            assertNotNull("The result set wasn't correctly limited when ", testResult);
            assertEquals("The result set wasn't correctly limited when testing the limit and offset", 5, testResult.size());
            assertEquals("The result set wasn't correctly offset when testing the limit and offset", "f",
                    testResult.get(0).getString("text"));
        }
    }
}
