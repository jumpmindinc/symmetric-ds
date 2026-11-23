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
package org.jumpmind.symmetric.service.impl;

import java.sql.Types;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.TestConstants;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.util.FormatUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
abstract public class AbstractTriggerRouterServiceTest extends AbstractServiceTest {
    public static final String TEST_TRIGGERS_TABLE = "test_triggers_table";
    public final static String CREATE_ORACLE_BINARY_TYPE = "create table test_oracle_binary_types (id varchar(4), num_one binary_float, num_two binary_double)";
    public final static String INSERT_ORACLE_BINARY_TYPE_1 = "insert into test_oracle_binary_types values('1', 2.04299998, 5.2212)";
    public final static String EXPECTED_INSERT_ORALCE_BINARY_TYPE_1 = "\"1\",\"2.04299998\",\"5.2212\"";
    public final static String CREATE_POSTGRES_BINARY_TYPE = "create table test_postgres_binary_types (id integer, binary_data oid, primary key(id))";
    public final static String INSERT_POSTGRES_BINARY_TYPE_1 = "insert into test_postgres_binary_types values(47, ?)";
    public final static String EXPECTED_INSERT_POSTGRES_BINARY_TYPE_1 = "\"47\",\"dGVzdCAxIDIgMw==\"";
    public final static String DROP_POSTGRES_BINARY_TYPE = "drop table if exists test_postgres_binary_types";
    public final static String INSERT = "insert into "
            + TEST_TRIGGERS_TABLE
            + " (string_one_value,string_two_value,long_string_value,time_value,date_value,boolean_value,bigint_value,decimal_value) "
            + "values(?,?,?,?,?,?,?,?)"; // '\\\\','\"','\"1\"',null,null,1,1,1)";
    public final static int[] INSERT_TYPES = new int[] { Types.VARCHAR, Types.VARCHAR,
            Types.VARCHAR, Types.TIMESTAMP, Types.DATE, Types.SMALLINT, Types.INTEGER, Types.DECIMAL };
    public final static Object[] INSERT1_VALUES = new Object[] { "\\\\", "\"", "\"1\"", null, null,
            1, 1, 1 };
    public final static Object[] INSERT2_VALUES = new Object[] { "here", "here", "1", null, null,
            1, 1, 1 };
    public final static Object[] INSERT3_VALUES = new Object[] { "inactive", "inactive", "0", null,
            null, 1, 1, 1 };
    public final static String EXPECTED_INSERT1_CSV_ENDSWITH = "\"\\\\\\\\\",\"\\\"\",\"\\\"1\\\"\",,,\"1\",\"1\",\"1\"";
    public final static String EXPECTED_INSERT2_CSV_ENDSWITH = "\"here\",\"here\",\"1\",,,\"1\",\"1\"";
    public final static String UNEXPECTED_INSERT3_CSV_ENDSWITH = "\"inactive\",\"inactive\",\"0\",,,\"1\",\"1\"";
    public final static String TEST_TRIGGER_WHERE_CLAUSE = "where source_table_name='"
            + TEST_TRIGGERS_TABLE + "' and channel_id='" + TestConstants.TEST_CHANNEL_ID + "'";
    public static final String insertSyncIncomingBatchSql = "insert into test_sync_incoming_batch (id, data) values (?, ?)";

    @Test
    public void test01ReplaceCharactersForTriggerName() {
        Assert.assertEquals("123456_54321",
                FormatUtils.replaceCharsToShortenName("123456_54321"));
        Assert.assertEquals("tst_1234_rght_n",
                FormatUtils.replaceCharsToShortenName("test_1234_right::_on"));
    }

    @Test
    public void test02SchemaSync() throws Exception {
        ITriggerRouterService service = getTriggerRouterService();
        // baseline
        service.syncTriggers();
        // get the current number of hist rows
        int origCount = getTriggerHistTableRowCount();
        Thread.sleep(1000);
        Calendar lastUpdateTime = Calendar.getInstance();
        // force the triggers to rebuild
        int expectedCount = origCount
                + getSqlTemplate()
                        .update("update sym_trigger set last_update_time=? where trigger_id in (select trigger_id from sym_trigger_router where router_id in (select router_id from sym_router where source_node_group_id=?))",
                                new Object[] { lastUpdateTime.getTime(),
                                        TestConstants.TEST_ROOT_NODE_GROUP });
        service.syncTriggers();
        Assert.assertEquals("Wrong trigger_hist row count. The original count was " + origCount
                + ".", expectedCount, getTriggerHistTableRowCount());
    }

    @Test
    public void test03SchemaSyncNoChanges() throws Exception {
        ITriggerRouterService service = getTriggerRouterService();
        service.syncTriggers();
        int origCount = getTriggerHistTableRowCount();
        Thread.sleep(1000);
        service.syncTriggers();
        Assert.assertEquals(
                "Wrong trigger_hist row count.  No new triggers should have been generated.",
                origCount, getTriggerHistTableRowCount());
    }

    private int getTriggerHistTableRowCount() {
        return getSqlTemplate().queryForInt("select count(*) from sym_trigger_hist");
    }

    @Test
    public void test04GetRouterById() throws Exception {
        Router router = getTriggerRouterService().getRouterById("3000");
        Assert.assertNotNull(router);
        Assert.assertEquals("3000", router.getRouterId());
        Assert.assertEquals("test-root-group", router.getNodeGroupLink().getSourceNodeGroupId());
        Assert.assertEquals("test-node-group2", router.getNodeGroupLink().getTargetNodeGroupId());
        router = getTriggerRouterService().getRouterById("666");
        Assert.assertNull(router);
    }

    @Test
    public void test05ValidateTestTableTriggers() throws Exception {
        ISqlTemplate jdbcTemplate = getSqlTemplate();
        int count = insert(INSERT1_VALUES, jdbcTemplate, getDbDialect());
        assertTrue(count == 1);
        String csvString = getNextDataRow();
        // DB2 captures decimal differently
        csvString = csvString.replaceFirst("\"00001\\.\"", "\"1\"");
        // Informix captures decimal differently
        csvString = csvString.replaceFirst("\"1.0000000000000000\"", "\"1\"");
        // ASA captures decimal differently
        csvString = csvString.replaceFirst("\"1.000000\"", "\"1\"");
        boolean match = csvString.endsWith(EXPECTED_INSERT1_CSV_ENDSWITH);
        assertTrue(match, "The full string we pulled from the database was " + csvString
                + " however, we expected the string to end with " + EXPECTED_INSERT1_CSV_ENDSWITH);
    }

    @Test
    public void test06InitialLoadSql() throws Exception {
        ITriggerRouterService triggerRouterService = getTriggerRouterService();
        IParameterService parameterService = getParameterService();
        parameterService.saveParameter(ParameterConstants.INITIAL_LOAD_CONCAT_CSV_IN_SQL_ENABLED, true, "unittest");
        TriggerRouter triggerRouter = triggerRouterService
                .getTriggerRouterForTableForCurrentNode(null, null, TEST_TRIGGERS_TABLE, true)
                .iterator().next();
        Table table = getDbDialect().getPlatform().getTableFromCache(triggerRouter.getTrigger().getSourceTableName(), true);
        Trigger trigger = triggerRouter.getTrigger();
        String sql = getDbDialect().createInitialLoadSqlFor(
                new Node("1", null, "1.0"),
                triggerRouter,
                table,
                triggerRouterService.getNewestTriggerHistoryForTrigger(trigger.getTriggerId(),
                        trigger.getSourceCatalogName(), trigger.getSourceSchemaName(),
                        trigger.getSourceTableName()),
                getConfigurationService().getChannel(triggerRouter.getTrigger().getChannelId()), null);
        List<String> csvStrings = getSqlTemplate().query(sql, new StringMapper());
        assertTrue(csvStrings.size() > 0);
        String csvString = csvStrings.get(0);
        // DB2 captures decimal differently
        csvString = csvString.replaceFirst("\"00001\\.\"", "\"1\"");
        // Informix captures decimal differently
        csvString = csvString.replaceFirst("\"1.0000000000000000\"", "\"1\"");
        // ASA captures decimal differently
        csvString = csvString.replaceFirst("\"1.000000\"", "\"1\"");
        assertTrue(csvString.endsWith(EXPECTED_INSERT1_CSV_ENDSWITH), "Received " + csvString
                + ", Expected the string to end with " + EXPECTED_INSERT1_CSV_ENDSWITH);
    }

    @Test
    public void test07CaptureOnlyChangedData() throws Exception {
        boolean oldvalue = getParameterService().is(
                ParameterConstants.TRIGGER_UPDATE_CAPTURE_CHANGED_DATA_ONLY);
        try {
            ISqlTemplate template = getSqlTemplate();
            getParameterService().saveParameter(
                    ParameterConstants.TRIGGER_UPDATE_CAPTURE_CHANGED_DATA_ONLY, true, "test");
            if (!Constants.ALWAYS_TRUE_CONDITION.equals(getDbDialect().getDataHasChangedCondition(
                    getTriggerRouterService().getTriggers().get(0)))) {
                forceRebuildOfTrigers();
                insert(INSERT2_VALUES, template, getDbDialect());
                Assert.assertTrue(template.queryForInt(
                        "select count(*) from " + TEST_TRIGGERS_TABLE) > 0);
                int dataCount = countData();
                template.update(
                        "update " + TEST_TRIGGERS_TABLE + " set string_one_value=string_one_value");
                Assert.assertEquals(dataCount, countData());
            }
        } finally {
            getParameterService().saveParameter(
                    ParameterConstants.TRIGGER_UPDATE_CAPTURE_CHANGED_DATA_ONLY, oldvalue, "test");
            forceRebuildOfTrigers();
        }
    }

    @Test
    public void test09ExcludedColumnsFunctionality() throws Exception {
        ITriggerRouterService service = getTriggerRouterService();
        ISqlTemplate jdbcTemplate = getSqlTemplate();
        assertEquals(1, jdbcTemplate.update(
                "update sym_trigger set excluded_column_names='BOOLEAN_VALUE', last_update_time=? "
                        + TEST_TRIGGER_WHERE_CLAUSE,
                new Object[] { new Date(System.currentTimeMillis() + 60000) }));
        service.syncTriggers();
        TriggerRouter triggerRouter = service
                .getTriggerRouterForTableForCurrentNode(null, null, TEST_TRIGGERS_TABLE, true)
                .iterator().next();
        assertEquals(
                jdbcTemplate
                        .queryForInt("select count(*) from sym_trigger_hist where trigger_id='"
                                + triggerRouter.getTrigger().getTriggerId()
                                + "' and inactive_time is null"),
                1, "We expected only one active record in the trigger_hist table for "
                        + TEST_TRIGGERS_TABLE);
        assertEquals(1, insert(INSERT2_VALUES, jdbcTemplate, getDbDialect()));
        String csvString = getNextDataRow();
        // DB2 captures decimal differently
        csvString = csvString.replaceFirst("\"00001\\.\"", "\"1\"");
        // Informix captures decimal differently
        csvString = csvString.replaceFirst("\"1.0000000000000000\"", "\"1\"");
        // ASA captures decimal differently
        csvString = csvString.replaceFirst("\"1.000000\"", "\"1\"");
        boolean match = csvString.endsWith(EXPECTED_INSERT2_CSV_ENDSWITH);
        assertTrue(match, "Received " + csvString + ", Expected the string to end with "
                + EXPECTED_INSERT2_CSV_ENDSWITH);
    }

    @Test
    public void test10DisableTriggers() throws Exception {
        ISymmetricDialect dbDialect = getDbDialect();
        ISqlTemplate jdbcTemplate = getSqlTemplate();
        ISqlTransaction transaction = jdbcTemplate.startSqlTransaction();
        try {
            dbDialect.disableSyncTriggers(transaction, null);
            int count = insert(INSERT1_VALUES, transaction, dbDialect);
            dbDialect.enableSyncTriggers(transaction);
            transaction.commit();
            assertTrue(count == 1);
            String csvString = getNextDataRow();
            // DB2 captures decimal differently
            csvString = csvString.replaceFirst("\"00001\\.\"", "\"1\"");
            // Informix captures decimal differently
            csvString = csvString.replaceFirst("\"1.0000000000000000\"", "\"1\"");
            // ASA captures decimal differently
            csvString = csvString.replaceFirst("\"1.000000\"", "\"1\"");
            boolean match = csvString.endsWith(EXPECTED_INSERT2_CSV_ENDSWITH);
            assertTrue(match, "Received " + csvString + ", Expected the string to end with " + EXPECTED_INSERT2_CSV_ENDSWITH);
        } finally {
            transaction.close();
        }
    }

    @Test
    public void test11BinaryColumnTypesForOracle() {
        ISymmetricDialect dialect = getDbDialect();
        Assumptions.assumeTrue(DatabaseNamesConstants.ORACLE.equals(dialect.getName()) || DatabaseNamesConstants.ORACLE122.equals(dialect.getName())
                || DatabaseNamesConstants.ORACLE23.equals(dialect.getName()));
        getSqlTemplate().update(CREATE_ORACLE_BINARY_TYPE);
        TriggerRouter trouter = new TriggerRouter();
        Trigger trigger = trouter.getTrigger();
        trigger.setSourceTableName("test_oracle_binary_types");
        trigger.setChannelId(TestConstants.TEST_CHANNEL_ID);
        Router router = trouter.getRouter();
        router.getNodeGroupLink().setSourceNodeGroupId(TestConstants.TEST_ROOT_NODE_GROUP);
        router.getNodeGroupLink().setTargetNodeGroupId(TestConstants.TEST_ROOT_NODE_GROUP);
        getTriggerRouterService().saveTriggerRouter(trouter);
        ITriggerRouterService triggerService = getTriggerRouterService();
        triggerService.syncTriggers();
        Assert.assertEquals("Some triggers must have failed to build.", 0, triggerService
                .getFailedTriggers().size());
        getSqlTemplate().update(INSERT_ORACLE_BINARY_TYPE_1);
        String csvString = getNextDataRow();
        Assert.assertEquals(EXPECTED_INSERT_ORALCE_BINARY_TYPE_1, csvString);
    }

    @Test
    public void test12BinaryColumnTypesForPostgres() {
        ISymmetricDialect dialect = getDbDialect();
        Assumptions.assumeTrue(DatabaseNamesConstants.POSTGRESQL.equals(dialect.getName()));
        getSqlTemplate().update(DROP_POSTGRES_BINARY_TYPE);
        getSqlTemplate().update(CREATE_POSTGRES_BINARY_TYPE);
        TriggerRouter trouter = new TriggerRouter();
        Trigger trigger = trouter.getTrigger();
        trigger.setSourceTableName("test_postgres_binary_types");
        trigger.setChannelId(TestConstants.TEST_CHANNEL_ID);
        Router router = trouter.getRouter();
        router.getNodeGroupLink().setSourceNodeGroupId(TestConstants.TEST_ROOT_NODE_GROUP);
        router.getNodeGroupLink().setTargetNodeGroupId(TestConstants.TEST_ROOT_NODE_GROUP);
        getTriggerRouterService().saveTriggerRouter(trouter);
        ITriggerRouterService triggerService = getTriggerRouterService();
        triggerService.syncTriggers();
        Assert.assertEquals("Some triggers must have failed to build.", 0, triggerService
                .getFailedTriggers().size());
        // new SerialBlob("test 1 2 3".getBytes())
        ISqlTransaction transaction = getSqlTemplate().startSqlTransaction();
        try {
            getSqlTemplate().update(INSERT_POSTGRES_BINARY_TYPE_1, "test 1 2 3".getBytes());
            transaction.commit();
        } finally {
            transaction.close();
        }
        String csvString = getNextDataRow();
        Assert.assertEquals(EXPECTED_INSERT_POSTGRES_BINARY_TYPE_1, csvString);
    }

    @Test
    public void test13BinaryColumnTypesForDerby() {
        ISymmetricDialect dialect = getDbDialect();
        Assumptions.assumeTrue(DatabaseNamesConstants.DERBY.equals(dialect.getName()));
        try {
            getSqlTemplate().update("drop table test_derby_binary_types");
        } catch (Exception e) {
        }
        getSqlTemplate()
                .update("create table test_derby_binary_types (id integer, data VARCHAR (100) FOR BIT DATA, data2 CHAR(12) FOR BIT DATA)");
        TriggerRouter trouter = new TriggerRouter();
        Trigger trigger = trouter.getTrigger();
        trigger.setSourceTableName("test_derby_binary_types");
        trigger.setChannelId(TestConstants.TEST_CHANNEL_ID);
        Router router = trouter.getRouter();
        router.getNodeGroupLink().setSourceNodeGroupId(TestConstants.TEST_ROOT_NODE_GROUP);
        router.getNodeGroupLink().setTargetNodeGroupId(TestConstants.TEST_ROOT_NODE_GROUP);
        getTriggerRouterService().saveTriggerRouter(trouter);
        ITriggerRouterService triggerService = getTriggerRouterService();
        triggerService.syncTriggers();
        Assert.assertEquals("Some triggers must have failed to build.", 0, triggerService
                .getFailedTriggers().size());
        getSqlTemplate().update("insert into test_derby_binary_types values (?, ?, ?)",
                new Object[] { 23, "test 1 2 3".getBytes(), "test 1 2 3".getBytes() });
        String csvString = getNextDataRow();
        Assert.assertEquals("\"23\",\"dGVzdCAxIDIgMw==\",\"dGVzdCAxIDIgMyAg\"", csvString);
    }

    @Test
    public void test14OverlappingTriggerIds() {
        ITriggerRouterService service = getTriggerRouterService();
        TriggerRouter trouter = new TriggerRouter();
        Trigger trigger = trouter.getTrigger();
        trigger.setTriggerId("test_overlap");
        trigger.setSourceTableName("test_overlap");
        trigger.setChannelId(TestConstants.TEST_CHANNEL_ID);
        Router router = trouter.getRouter();
        NodeGroupLink groupLink = new NodeGroupLink(TestConstants.TEST_ROOT_NODE_GROUP,
                TestConstants.TEST_ROOT_NODE_GROUP);
        router.setNodeGroupLink(groupLink);
        service.saveTriggerRouter(trouter);
        trigger.setTriggerId("tst_overlap");
        trigger.setSourceTableName("tst_overlap");
        service.saveTriggerRouter(trouter);
        service.syncTriggers();
        Assert.assertEquals("Some triggers must have failed to build.", 0, service.getFailedTriggers().size());
        TriggerHistory history0 = null;
        TriggerHistory history1 = null;
        boolean foundHistories = false;
        for (TriggerHistory history : service.getActiveTriggerHistories()) {
            if (history.getTriggerId().equals("test_overlap")) {
                history0 = history;
            } else if (history.getTriggerId().equals("tst_overlap")) {
                history1 = history;
            }
            if (history0 != null && history1 != null) {
                foundHistories = true;
                break;
            }
        }
        Assert.assertTrue("Missing rows in sym_trigger_hist.", foundHistories);
        Assert.assertTrue("Trigger names are not unique.",
                !history0.getNameForInsertTrigger().equals(history1.getNameForInsertTrigger())
                        && !history0.getNameForUpdateTrigger().equals(history1.getNameForUpdateTrigger())
                        && !history0.getNameForDeleteTrigger().equals(history1.getNameForDeleteTrigger()));
    }

    protected static int[] filterTypes(int[] types, ISymmetricDialect dbDialect) {
        boolean isBooleanSupported = isBooleanSupported(dbDialect);
        int[] filteredTypes = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == Types.BOOLEAN && !isBooleanSupported) {
                filteredTypes[i] = Types.INTEGER;
            } else {
                filteredTypes[i] = types[i];
            }
        }
        return filteredTypes;
    }

    public static int insert(Object[] values, ISqlTransaction transaction, ISymmetricDialect dbDialect) {
        return transaction.prepareAndExecute(INSERT, filterValues(values, dbDialect),
                filterTypes(INSERT_TYPES, dbDialect));
    }

    public static int insert(Object[] values, ISqlTemplate jdbcTemplate, ISymmetricDialect dbDialect) {
        return jdbcTemplate.update(INSERT, filterValues(values, dbDialect),
                filterTypes(INSERT_TYPES, dbDialect));
    }

    protected static boolean isBooleanSupported(ISymmetricDialect dbDialect) {
        return !(DatabaseNamesConstants.ORACLE.equals(dbDialect.getPlatform().getName())
                || DatabaseNamesConstants.ORACLE122.equals(dbDialect.getPlatform().getName())
                || DatabaseNamesConstants.DB2.equals(dbDialect.getPlatform().getName())
                || DatabaseNamesConstants.SQLANYWHERE.equals(dbDialect.getPlatform().getName()));
    }

    protected static Object[] filterValues(Object[] values, ISymmetricDialect dbDialect) {
        Object[] filteredValues = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Boolean && !isBooleanSupported(dbDialect)) {
                filteredValues[i] = ((Boolean) values[i]) ? Integer.valueOf(1) : Integer.valueOf(0);
            } else {
                filteredValues[i] = values[i];
            }
        }
        return filteredValues;
    }

    protected String getNextDataRow() {
        ISqlTemplate jdbcTemplate = getSqlTemplate();
        return jdbcTemplate
                .queryForObject(
                        "select row_data from sym_data where data_id = (select max(data_id) from sym_data)",
                        String.class);
    }
}
