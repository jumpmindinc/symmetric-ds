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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.TestConstants;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.OutgoingBatches;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.ChannelRouterContext;
import org.jumpmind.symmetric.route.DataGapRouteReader;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractRouterServiceTest extends AbstractServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(AbstractRouterServiceTest.class);
    private static final String SELECT_COUNT_FROM_SYM_OUTGOING_BATCH_WHERE_NOT = "select count(*) from sym_outgoing_batch where status = 'NE' and node_id!=?";
    private static final String SELECT_COUNT_FROM_SYM_OUTGOING_BATCH = "select count(*) from sym_outgoing_batch where status = 'NE' and node_id=?";
    final static String TEST_TABLE_1 = "test_routing_data_1";
    final static String TEST_TABLE_2 = "test_routing_data_2";
    final static String TEST_SUBTABLE = "test_routing_data_subtable";
    final static Node NODE_GROUP_NODE_1 = new Node("00001", TestConstants.TEST_CLIENT_NODE_GROUP);
    final static Node NODE_GROUP_NODE_2 = new Node("00002", TestConstants.TEST_CLIENT_NODE_GROUP);
    final static Node NODE_GROUP_NODE_3 = new Node("00003", TestConstants.TEST_CLIENT_NODE_GROUP);
    final static Node NODE_UNROUTED = new Node(Constants.UNROUTED_NODE_ID, null);

    @Test
    public void testRoutingScenarios() throws Exception {
        testMultiChannelRoutingToEveryone();
        testLookupTableRouting();
        testColumnMatchTransactionalOnlyRoutingToNode1();
        testSubSelectNonTransactionalRoutingToNode1();
        testSyncIncomingBatch();
        // testLargeNumberOfEventsToManyNodes();
        testBshTransactionalRoutingOnUpdate();
        testBshRoutingDeletesToNode3();
        testColumnMatchSubtableRoutingToNode1();
        testColumnMatchOnNull();
        testColumnMatchOnNotNull();
        testSyncOnColumnChange();
        testSyncIncomingBatchWhenUnrouted();
        testDefaultRouteToTargetNodeGroupOnly();
        testUnroutedDataCreatedBatch();
        testGapRouting();
        testGapWithGapAtBegining();
        testGapWithGapAtEnd();
        testDataGapExpired();
        testLotsOfGaps();
        testNoResend();
        testDontSelectOldDataDuringRouting();
        testMaxNumberOfDataToRoute();
    }

    public void testMultiChannelRoutingToEveryone() {
        resetBatches();
        TriggerRouter trigger1 = getTestRoutingTableTrigger(TEST_TABLE_1);
        getTriggerRouterService().saveTriggerRouter(trigger1);
        TriggerRouter trigger2 = getTestRoutingTableTrigger(TEST_TABLE_2);
        getTriggerRouterService().saveTriggerRouter(trigger2);
        getTriggerRouterService().syncTriggers();
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        NodeChannel otherChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID_OTHER, false);
        Assert.assertEquals(50, testChannel.getMaxBatchSize());
        Assert.assertEquals(1, otherChannel.getMaxBatchSize());
        /*
         * Should be 1 batch for table 1 on the testchannel w/ max batch size of 50
         */
        insert(TEST_TABLE_1, 5, false);
        /* this should generate 15 batches because the max batch size is 1 */
        insert(TEST_TABLE_2, 15, false);
        insert(TEST_TABLE_1, 50, true);
        getRouterService().routeData(true);
        final int EXPECTED_BATCHES = getDbDialect().supportsTransactionId() ? 16 : 17;
        OutgoingBatches batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false);
        filterForChannels(batches, testChannel, otherChannel);
        Assert.assertEquals(EXPECTED_BATCHES, batches.getBatches().size());
        Assert.assertEquals(getDbDialect().supportsTransactionId() ? 1 : 2, countBatchesForChannel(batches, testChannel));
        Assert.assertEquals(15, countBatchesForChannel(batches, otherChannel));
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_2.getNodeId(), false);
        filterForChannels(batches, testChannel, otherChannel);
        // Node 2 has sync disabled
        Assert.assertEquals(0, batches.getBatches().size());
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false);
        filterForChannels(batches, testChannel, otherChannel);
        Assert.assertEquals(EXPECTED_BATCHES, batches.getBatches().size());
        resetBatches();
        // should be 2 batches for table 1 on the testchannel w/ max batch size
        // of 50
        insert(TEST_TABLE_1, 50, false);
        // this should generate 1 batches because the max batch size is 1, but
        // the batch is transactional
        insert(TEST_TABLE_2, 15, true);
        insert(TEST_TABLE_1, 50, false);
        getRouterService().routeData(true);
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false);
        filterForChannels(batches, testChannel, otherChannel);
        Assert.assertEquals(getDbDialect().supportsTransactionId() ? 10 : 17, batches.getBatches().size());
        Assert.assertEquals(2, countBatchesForChannel(batches, testChannel));
        Assert.assertEquals(getDbDialect().supportsTransactionId() ? 8 : 15, countBatchesForChannel(batches, otherChannel));
    }

    public void testLookupTableRouting() {
        getDbDialect().truncateTable("test_lookup_table");
        long oldValue = getParameterService().getLong(ParameterConstants.CACHE_CHANNEL_DEFAULT_ROUTER_IN_MS);
        getParameterService().saveParameter(ParameterConstants.CACHE_CHANNEL_DEFAULT_ROUTER_IN_MS, 1l, "test");
        getParameterService().refreshFromDatabase();
        getSqlTemplate().update("insert into test_lookup_table values ('A',?)", NODE_GROUP_NODE_1.getExternalId());
        getSqlTemplate().update("insert into test_lookup_table values ('B',?)", NODE_GROUP_NODE_1.getExternalId());
        getSqlTemplate().update("insert into test_lookup_table values ('C',?)", NODE_GROUP_NODE_3.getExternalId());
        getSqlTemplate().update("insert into test_lookup_table values ('D',?)", NODE_GROUP_NODE_3.getExternalId());
        getSqlTemplate().update("insert into test_lookup_table values ('D',?)", NODE_GROUP_NODE_1.getExternalId());
        TriggerRouter triggerRouter = getTestRoutingTableTrigger(TEST_TABLE_1);
        triggerRouter.getRouter().setRouterType("lookuptable");
        triggerRouter.getRouter().setRouterExpression(
                "LOOKUP_TABLE=test_lookup_table\nKEY_COLUMN=routing_varchar\nLOOKUP_KEY_COLUMN=column_one\nEXTERNAL_ID_COLUMN=column_two");
        getTriggerRouterService().saveTriggerRouter(triggerRouter);
        getTriggerRouterService().syncTriggers();
        getRouterService().routeData(true);
        resetBatches();
        insert(TEST_TABLE_1, 5, true, null, "A");
        int unroutedCount = countUnroutedBatches();
        getRouterService().routeData(true);
        Assert.assertEquals(1, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH, NODE_GROUP_NODE_1.getNodeId()));
        Assert.assertEquals(0, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH_WHERE_NOT, NODE_GROUP_NODE_1.getNodeId()));
        Assert.assertEquals(unroutedCount, countUnroutedBatches());
        resetBatches();
        insert(TEST_TABLE_1, 5, true, null, "B");
        getRouterService().routeData(true);
        Assert.assertEquals(1, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH, NODE_GROUP_NODE_1.getNodeId()));
        Assert.assertEquals(0, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH_WHERE_NOT, NODE_GROUP_NODE_1.getNodeId()));
        Assert.assertEquals(unroutedCount, countUnroutedBatches());
        resetBatches();
        insert(TEST_TABLE_1, 10, true, null, "C");
        getRouterService().routeData(true);
        Assert.assertEquals(1, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH, NODE_GROUP_NODE_3.getNodeId()));
        Assert.assertEquals(0, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH_WHERE_NOT, NODE_GROUP_NODE_3.getNodeId()));
        Assert.assertEquals(unroutedCount, countUnroutedBatches());
        resetBatches();
        insert(TEST_TABLE_1, 5, true, null, "D");
        getRouterService().routeData(true);
        Assert.assertEquals(1, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH, NODE_GROUP_NODE_1.getNodeId()));
        Assert.assertEquals(1, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH, NODE_GROUP_NODE_3.getNodeId()));
        Assert.assertEquals(0,
                getSqlTemplate().queryForInt("select count(*) from sym_outgoing_batch where status = 'NE' and node_id not in (?,?)",
                        NODE_GROUP_NODE_1.getNodeId(), NODE_GROUP_NODE_3.getNodeId()));
        Assert.assertEquals(unroutedCount, countUnroutedBatches());
        resetBatches();
        insert(TEST_TABLE_1, 1, true, null, "F");
        getRouterService().routeData(true);
        Assert.assertEquals(0, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH, NODE_GROUP_NODE_1.getNodeId()));
        Assert.assertEquals(0, getSqlTemplate().queryForInt(SELECT_COUNT_FROM_SYM_OUTGOING_BATCH, NODE_GROUP_NODE_3.getNodeId()));
        Assert.assertEquals(1, countUnroutedBatches() - unroutedCount);
        resetBatches();
        getParameterService().saveParameter(ParameterConstants.CACHE_CHANNEL_DEFAULT_ROUTER_IN_MS, oldValue, "test");
        getParameterService().refreshFromDatabase();
    }

    public void testColumnMatchTransactionalOnlyRoutingToNode1() {
        resetBatches();
        TriggerRouter trigger1 = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger1.getRouter().setRouterType("column");
        trigger1.getRouter().setRouterExpression("ROUTING_VARCHAR=:NODE_ID");
        getTriggerRouterService().saveTriggerRouter(trigger1);
        getTriggerRouterService().syncTriggers();
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(10000);
        testChannel.setBatchAlgorithm("transactional");
        getConfigurationService().saveChannel(testChannel, true);
        // should be 51 batches for table 1
        insert(TEST_TABLE_1, 500, true);
        insert(TEST_TABLE_1, 50, false);
        getRouterService().routeData(true);
        final int EXPECTED_BATCHES = getDbDialect().supportsTransactionId() ? 51 : 550;
        OutgoingBatches batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false);
        filterForChannels(batches, testChannel);
        Assert.assertEquals(EXPECTED_BATCHES, batches.getBatches().size());
        Assert.assertEquals(EXPECTED_BATCHES, countBatchesForChannel(batches, testChannel));
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_2.getNodeId(), false);
        filterForChannels(batches, testChannel);
        // Node 2 has sync disabled
        Assert.assertEquals(0, batches.getBatches().size());
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false);
        filterForChannels(batches, testChannel);
        // Batch was targeted only at node 1
        Assert.assertEquals(0, batches.getBatches().size());
        resetBatches();
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        execute("delete from " + TEST_TABLE_1, null);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        getRouterService().routeData(true);
        Assert.assertEquals(getDbDialect().supportsTransactionId() ? 1 : 705,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        resetBatches();
    }

    public void testSubSelectNonTransactionalRoutingToNode1() {
        resetBatches();
        TriggerRouter trigger1 = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger1.getRouter().setRouterType("subselect");
        trigger1.getRouter().setRouterExpression("c.node_id=:ROUTING_VARCHAR");
        getTriggerRouterService().saveTriggerRouter(trigger1);
        getTriggerRouterService().syncTriggers();
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(1000);
        testChannel.setMaxBatchSize(5);
        testChannel.setBatchAlgorithm("nontransactional");
        getConfigurationService().saveChannel(testChannel, true);
        // should be 100 batches for table 1, even though we committed the
        // changes as part of a transaction
        insert(TEST_TABLE_1, 500, true);
        getRouterService().routeData(true);
        final int EXPECTED_BATCHES = 100;
        OutgoingBatches batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false);
        filterForChannels(batches, testChannel);
        Assert.assertEquals(EXPECTED_BATCHES, batches.getBatches().size());
        Assert.assertEquals(EXPECTED_BATCHES, countBatchesForChannel(batches, testChannel));
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_2.getNodeId(), false);
        filterForChannels(batches, testChannel);
        // Node 2 has sync disabled
        Assert.assertEquals(0, batches.getBatches().size());
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false);
        filterForChannels(batches, testChannel);
        // Batch was targeted only at node 1
        Assert.assertEquals(0, batches.getBatches().size());
        resetBatches();
    }

    public void testSyncIncomingBatch() throws Exception {
        resetBatches();
        TriggerRouter trigger1 = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger1.getTrigger().setSyncOnIncomingBatch(true);
        trigger1.getRouter().setRouterExpression(null);
        trigger1.getRouter().setRouterType("default");
        getTriggerRouterService().saveTriggerRouter(trigger1);
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(1000);
        testChannel.setMaxBatchSize(50);
        testChannel.setBatchAlgorithm("default");
        getConfigurationService().saveChannel(testChannel, true);
        getTriggerRouterService().syncTriggers();
        insert(TEST_TABLE_1, 10, true, NODE_GROUP_NODE_1.getNodeId());
        getRouterService().routeData(true);
        OutgoingBatches batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false);
        filterForChannels(batches, testChannel);
        Assert.assertEquals("Should have been 0.  We did the insert as if the data had come from node 1.", 0, batches.getBatches().size());
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false);
        filterForChannels(batches, testChannel);
        Assert.assertEquals(1, batches.getBatches().size());
        resetBatches();
    }

    public void testLargeNumberOfEventsToManyNodes() {
        resetBatches();
        TriggerRouter trigger1 = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger1.getRouter().setRouterType("column");
        // set up a constant to force the data to be routed through the column
        // data matcher, but to everyone
        trigger1.getRouter().setRouterExpression("ROUTING_VARCHAR=00001");
        getTriggerRouterService().saveTriggerRouter(trigger1);
        getTriggerRouterService().syncTriggers();
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(10000);
        testChannel.setMaxBatchSize(10000);
        testChannel.setBatchAlgorithm("default");
        getConfigurationService().saveChannel(testChannel, true);
        final int ROWS_TO_INSERT = 1000;
        final int NODES_TO_INSERT = 10;
        logger.info("About to insert %s nodes", NODES_TO_INSERT);
        for (int i = 0; i < 1000; i++) {
            String nodeId = String.format("100%s", i);
            getRegistrationService().openRegistration(TestConstants.TEST_CLIENT_NODE_GROUP, nodeId);
            Node node = getNodeService().findNode(nodeId);
            node.setSyncEnabled(true);
            getNodeService().save(node);
        }
        logger.info("Done inserting %s nodes", NODES_TO_INSERT);
        logger.info("About to insert %s rows", ROWS_TO_INSERT);
        insert(TEST_TABLE_1, ROWS_TO_INSERT, false);
        logger.info("Done inserting %s rows", ROWS_TO_INSERT);
        logger.info("About to route data");
        getRouterService().routeData(true);
        logger.info("Done routing data");
    }

    public void testBshTransactionalRoutingOnUpdate() {
        resetBatches();
        TriggerRouter trigger1 = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger1.getRouter().setRouterType("bsh");
        trigger1.getRouter().setRouterExpression("targetNodes.add(ROUTING_VARCHAR); targetNodes.add(OLD_ROUTING_VARCHAR);");
        getTriggerRouterService().saveTriggerRouter(trigger1);
        getTriggerRouterService().syncTriggers();
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(1000);
        testChannel.setMaxBatchSize(5);
        testChannel.setBatchAlgorithm("transactional");
        getConfigurationService().saveChannel(testChannel, true);
        long ts = System.currentTimeMillis();
        ISqlTransaction transaction = null;
        int count = 0;
        try {
            transaction = getSqlTemplate().startSqlTransaction();
            count = transaction.prepareAndExecute(String.format("update %s set routing_varchar=?", TEST_TABLE_1),
                    NODE_GROUP_NODE_3.getNodeId());
            transaction.commit();
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
        logger.info("Just recorded a change to " + count + " rows in " + TEST_TABLE_1 + " in " + (System.currentTimeMillis() - ts) + "ms");
        ts = System.currentTimeMillis();
        getRouterService().routeData(true);
        logger.info("Just routed " + count + " rows in " + TEST_TABLE_1 + " in " + (System.currentTimeMillis() - ts) + "ms");
        OutgoingBatches batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false);
        filterForChannels(batches, testChannel);
        Assert.assertEquals(getDbDialect().supportsTransactionId() ? 1 : 510, batches.getBatches().size());
        Assert.assertEquals(getDbDialect().supportsTransactionId() ? count : 1, (int) batches.getBatches().get(0).getDataRowCount());
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_2.getNodeId(), false);
        filterForChannels(batches, testChannel);
        // Node 2 has sync disabled
        Assert.assertEquals(0, batches.getBatches().size());
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false);
        filterForChannels(batches, testChannel);
        Assert.assertEquals(getDbDialect().supportsTransactionId() ? 1 : 510, batches.getBatches().size());
        Assert.assertEquals(getDbDialect().supportsTransactionId() ? count : 1, (int) batches.getBatches().get(0).getDataRowCount());
        resetBatches();
    }

    public void testBshRoutingDeletesToNode3() {
        resetBatches();
        TriggerRouter trigger1 = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger1.getRouter().setRouterType("bsh");
        trigger1.getRouter().setRouterExpression(
                "targetNodes.add(ROUTING_VARCHAR); if (OLD_ROUTING_VARCHAR != void) { targetNodes.add(OLD_ROUTING_VARCHAR); }");
        getTriggerRouterService().saveTriggerRouter(trigger1);
        getTriggerRouterService().syncTriggers();
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(10000);
        final int MAX_BATCH_SIZE = 100;
        testChannel.setMaxBatchSize(MAX_BATCH_SIZE);
        testChannel.setBatchAlgorithm("nontransactional");
        getConfigurationService().saveChannel(testChannel, true);
        int count = getSqlTemplate().update(String.format("delete from %s", TEST_TABLE_1));
        getRouterService().routeData(true);
        OutgoingBatches batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false);
        filterForChannels(batches, testChannel);
        Assert.assertEquals(count / MAX_BATCH_SIZE + (count % MAX_BATCH_SIZE > 0 ? 1 : 0), batches.getBatches().size());
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_2.getNodeId(), false);
        // Node 2 has sync disabled
        Assert.assertEquals(0, batches.getBatches().size());
        batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false);
        filterForChannels(batches, testChannel);
        // Batch was targeted only at node 3
        Assert.assertEquals(0, batches.getBatches().size());
        resetBatches();
    }

    public void testColumnMatchSubtableRoutingToNode1() {
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(10000);
        testChannel.setBatchAlgorithm("transactional");
        getConfigurationService().saveChannel(testChannel, true);
        TriggerRouter trigger2 = getTestRoutingTableTrigger(TEST_SUBTABLE);
        trigger2.getRouter().setRouterType("column");
        trigger2.getRouter().setRouterExpression("EXTERNAL_DATA=:NODE_ID");
        String name = getPlatform().getName();
        if (name.equals(DatabaseNamesConstants.DERBY) || name.equals(DatabaseNamesConstants.MSSQL2000)
                || name.equals(DatabaseNamesConstants.MSSQL2005) || name.equals(DatabaseNamesConstants.MSSQL2008)
                || name.equals(DatabaseNamesConstants.MSSQL2016)
                || name.equals(DatabaseNamesConstants.ASE) || name.equals(DatabaseNamesConstants.SQLANYWHERE)
                || name.equals(DatabaseNamesConstants.INGRES)) {
            // TODO could not get subselect to work in trigger text for derby or
            // mssql. probably need to work on derby's support of
            // external_select a bit more
            trigger2.getTrigger().setExternalSelect("'" + NODE_GROUP_NODE_1.getNodeId() + "'");
        } else {
            trigger2.getTrigger()
                    .setExternalSelect("select routing_varchar from " + TEST_TABLE_1 + " where pk=$(curTriggerValue).$(curColumnPrefix)FK");
        }
        getTriggerRouterService().saveTriggerRouter(trigger2);
        getTriggerRouterService().syncTriggers();
        insert(TEST_TABLE_1, 1, true);
        getRouterService().routeData(true);
        resetBatches();
        int pk = getSqlTemplate()
                .queryForInt("select pk from " + TEST_TABLE_1 + " where routing_varchar='" + NODE_GROUP_NODE_1.getNodeId() + "'");
        getSqlTemplate().update("insert into " + TEST_SUBTABLE + " (fk) values(?)", pk);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        getRouterService().routeData(true);
        Assert.assertEquals(1,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_2.getNodeId(), false), testChannel));
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false), testChannel));
        resetBatches();
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        execute("delete from " + TEST_SUBTABLE, null);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_2.getNodeId(), false), testChannel));
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false), testChannel));
        getRouterService().routeData(true);
        Assert.assertEquals(1,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_2.getNodeId(), false), testChannel));
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_3.getNodeId(), false), testChannel));
        resetBatches();
    }

    public void testColumnMatchOnNull() {
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        TriggerRouter trigger = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger.getRouter().setRouterType("column");
        trigger.getRouter().setRouterExpression("ROUTING_VARCHAR=NULL");
        getTriggerRouterService().saveTriggerRouter(trigger);
        getTriggerRouterService().syncTriggers();
        resetBatches();
        update(TEST_TABLE_1, "Not Routed");
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        getRouterService().routeData(true);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        resetBatches();
        update(TEST_TABLE_1, null);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        getRouterService().routeData(true);
        Assert.assertEquals(1,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
    }

    public void testColumnMatchOnNotNull() {
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        TriggerRouter trigger = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger.getRouter().setRouterType("column");
        trigger.getRouter().setRouterExpression("ROUTING_VARCHAR!=NULL");
        getTriggerRouterService().saveTriggerRouter(trigger);
        getTriggerRouterService().syncTriggers();
        resetBatches();
        update(TEST_TABLE_1, "Not Routed");
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        getRouterService().routeData(true);
        Assert.assertEquals(1,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        resetBatches();
        update(TEST_TABLE_1, null);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        getRouterService().routeData(true);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
    }

    public void testSyncOnColumnChange() {
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(10000);
        testChannel.setBatchAlgorithm("transactional");
        getConfigurationService().saveChannel(testChannel, true);
        TriggerRouter trigger1 = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger1.getRouter().setRouterType("bsh");
        trigger1.getRouter().setRouterExpression("ROUTING_INT != null && !ROUTING_INT.equals(OLD_ROUTING_INT)");
        getTriggerRouterService().saveTriggerRouter(trigger1);
        getTriggerRouterService().syncTriggers();
        resetBatches();
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        insert(TEST_TABLE_1, 1, true);
        getRouterService().routeData(true);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        resetBatches();
        int pk = getSqlTemplate()
                .queryForInt("select pk from " + TEST_TABLE_1 + " where routing_varchar='" + NODE_GROUP_NODE_1.getNodeId() + "'");
        getSqlTemplate().update("update " + TEST_TABLE_1 + " set routing_int=1 where pk=?", pk);
        getRouterService().routeData(true);
        Assert.assertEquals(1,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        resetBatches();
        getSqlTemplate().update("update " + TEST_TABLE_1 + " set routing_int=1 where pk=?", new Object[] { pk });
        getRouterService().routeData(true);
        Assert.assertEquals(0,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
        resetBatches();
        getSqlTemplate().update("update " + TEST_TABLE_1 + " set routing_int=10 where pk=?", new Object[] { pk });
        getRouterService().routeData(true);
        Assert.assertEquals(1,
                countBatchesForChannel(getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false), testChannel));
    }

    public void testSyncIncomingBatchWhenUnrouted() throws Exception {
        resetBatches();
        TriggerRouter triggerRouter = getTestRoutingTableTrigger(TEST_TABLE_1);
        triggerRouter.getTrigger().setSyncOnIncomingBatch(true);
        triggerRouter.getRouter().setRouterType("bsh");
        triggerRouter.getRouter().setRouterExpression("return " + NODE_GROUP_NODE_1.getNodeId());
        getTriggerRouterService().saveTriggerRouter(triggerRouter);
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(1000);
        testChannel.setMaxBatchSize(50);
        testChannel.setBatchAlgorithm("default");
        getConfigurationService().saveChannel(testChannel, true);
        getTriggerRouterService().syncTriggers();
        insert(TEST_TABLE_1, 10, true, NODE_GROUP_NODE_1.getNodeId());
        int unroutedCount = countUnroutedBatches();
        getRouterService().routeData(true);
        OutgoingBatches batches = getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false);
        filterForChannels(batches, testChannel);
        Assert.assertEquals("Should have been 0.  We did the insert as if the data had come from node 1.", 0, batches.getBatches().size());
        Assert.assertTrue(countUnroutedBatches() > unroutedCount);
        resetBatches();
    }

    public void testDefaultRouteToTargetNodeGroupOnly() throws Exception {
        setUpDefaultTriggerRouterForTable1();
        resetBatches();
        insert(TEST_TABLE_1, 1, true);
        getRouterService().routeData(true);
        Assert.assertEquals(1, getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false).getBatches().size());
        Node node2 = getNodeService().findNode("00030");
        Assert.assertNotNull(node2);
        Assert.assertEquals(0, getOutgoingBatchService().getOutgoingBatches(node2.getNodeId(), false).getBatches().size());
        resetBatches();
    }

    public void testUnroutedDataCreatedBatch() {
        // TODO
    }

    public void testGapRouting() throws Exception {
        try {
            if (getDbDialect().canGapsOccurInCapturedDataIds()) {
                setUpDefaultTriggerRouterForTable1();
                resetBatches();
                Assert.assertEquals(1, getDataService().findDataGaps().size());
                // route again to make sure we still only have one gap
                getRouterService().routeData(true);
                Assert.assertEquals(1, getDataService().findDataGaps().size());
                insertGaps(2, 1, 2);
                getRouterService().routeData(true);
                // route again to calculate gaps
                getRouterService().routeData(true);
                Assert.assertEquals(1,
                        getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false).getBatches().size());
                List<DataGap> gaps = getDataService().findDataGaps();
                Assert.assertEquals(2, gaps.size());
                DataGap gap = gaps.get(0);
                Assert.assertEquals(0, gap.getEndId() - gap.getStartId());
                // route again to make sure the gaps don't disappear
                getRouterService().routeData(true);
                getRouterService().routeData(true);
                gaps = getDataService().findDataGaps();
                Assert.assertEquals(2, gaps.size());
                gap = gaps.get(0);
                Assert.assertEquals(0, gap.getEndId() - gap.getStartId());
            }
        } catch (Exception ex) {
            logger.error("", ex);
            throw ex;
        }
    }

    public void testGapWithGapAtBegining() {
        if (getDbDialect().canGapsOccurInCapturedDataIds()) {
            setUpDefaultTriggerRouterForTable1();
            resetBatches();
            List<DataGap> gaps = getDataService().findDataGaps();
            Assert.assertEquals(1, gaps.size());
            // evidently, derby only leaves a gap of one, no matter how many
            // rows you insert
            String name = getPlatform().getName();
            int gapsize = name.equals(DatabaseNamesConstants.DERBY) ? 1 : 10;
            insert(TEST_TABLE_1, gapsize, true, null, NODE_GROUP_NODE_1.getNodeId(), true);
            insert(TEST_TABLE_1, 10, true, null, NODE_GROUP_NODE_1.getNodeId(), false);
            routeAndCreateGaps();
            Assert.assertEquals(1, getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false).getBatches().size());
            gaps = getDataService().findDataGaps();
            Assert.assertEquals(2, gaps.size());
            DataGap gap = gaps.get(0);
            Assert.assertEquals("The gap's start id was " + gap.getStartId() + " end id was " + gap.getEndId(), gapsize - 1,
                    gap.getEndId() - gap.getStartId());
            routeAndCreateGaps();
            gaps = getDataService().findDataGaps();
            Assert.assertEquals(2, gaps.size());
            gap = gaps.get(0);
            Assert.assertEquals(gapsize - 1, gap.getEndId() - gap.getStartId());
        }
    }

    public void testGapWithGapAtEnd() {
        if (getDbDialect().canGapsOccurInCapturedDataIds()) {
            engine.getParameterService().saveParameter(ParameterConstants.CLUSTER_LOCKING_ENABLED, true, "test");
            setUpDefaultTriggerRouterForTable1();
            resetBatches();
            Assert.assertEquals(1, getDataService().findDataGaps().size());
            long startId = getSqlTemplate().queryForLong("select max(start_id) from sym_data_gap");
            getSqlTemplate().update("delete from sym_data_gap");
            getDataService().insertDataGap(new DataGap(startId, startId + 10));
            getDataService().insertDataGap(
                    new DataGap(startId + 11, startId + 11 + getParameterService().getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE)));
            insertGaps(8, 0, 1);
            routeAndCreateGaps();
            Assert.assertEquals(1, getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false).getBatches().size());
            List<DataGap> gaps = getDataService().findDataGaps();
            Assert.assertEquals(2, gaps.size());
            DataGap gap = gaps.get(0);
            Assert.assertEquals(startId + 8, gap.getStartId());
            routeAndCreateGaps();
        }
    }

    public void testDataGapExpired() throws Exception {
        if (getDbDialect().canGapsOccurInCapturedDataIds()) {
            resetGaps();
            testGapRouting();
            List<DataGap> gaps = getDataService().findDataGaps();
            Assert.assertEquals(2, gaps.size());
            DataGap gap = gaps.get(0);
            Assert.assertEquals(0, gap.getEndId() - gap.getStartId());
            Calendar time = Calendar.getInstance();
            time.add(Calendar.DATE, -10);
            getSqlTemplate().update("update sym_data_gap set create_time=?", time.getTime());
            routeAndCreateGaps();
            ;
            gaps = getDataService().findDataGaps();
            Assert.assertEquals("Gap should have expired", 2, gaps.size());
        }
    }

    public void testLotsOfGaps() {
        if (getDbDialect().canGapsOccurInCapturedDataIds()) {
            setUpDefaultTriggerRouterForTable1();
            resetGaps();
            resetBatches();
            insertGaps(5, 3, 100);
            routeAndCreateGaps();
            Assert.assertEquals(10, getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false).getBatches().size());
            List<DataGap> gaps = getDataService().findDataGaps();
            Assert.assertEquals(100, gaps.size());
            insertGaps(5, 3, 100);
            routeAndCreateGaps();
            gaps = getDataService().findDataGaps();
            Assert.assertEquals(200, gaps.size());
            resetGaps();
        }
    }

    public void testNoResend() {
        resetBatches();
        Assert.assertEquals(0, getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false).getBatches().size());
        getSqlTemplate().update("delete from sym_data_gap");
        routeAndCreateGaps();
        Assert.assertEquals(0, getOutgoingBatchService().getOutgoingBatches(NODE_GROUP_NODE_1.getNodeId(), false).getBatches().size());
    }

    public void testDontSelectOldDataDuringRouting() throws Exception {
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setUseOldDataToRoute(false);
        testChannel.setMaxBatchSize(50);
        testChannel.setBatchAlgorithm("nontransactional");
        getConfigurationService().saveChannel(testChannel, true);
        TriggerRouter trigger = getTestRoutingTableTrigger(TEST_TABLE_1);
        trigger.getRouter().setRouterType("column");
        trigger.getRouter().setRouterExpression("ROUTING_VARCHAR=:NODE_ID");
        getTriggerRouterService().saveTriggerRouter(trigger);
        // clean setup
        deleteAll(TEST_TABLE_1);
        insert(TEST_TABLE_1, 100, true);
        getRouterService().routeData(true);
        resetBatches();
        // delete
        deleteAll(TEST_TABLE_1);
        ISqlTransaction transaction = getSqlTemplate().startSqlTransaction();
        ChannelRouterContext context = new ChannelRouterContext(TestConstants.TEST_ROOT_EXTERNAL_ID, testChannel, transaction, null);
        context.setDataGaps(engine.getDataService().findDataGaps());
        DataGapRouteReader reader = new DataGapRouteReader(context, engine);
        reader.run();
        List<Data> list = new ArrayList<Data>();
        do {
            Data data = reader.take();
            if (data != null) {
                list.add(data);
            } else {
                break;
            }
        } while (true);
        transaction.close();
        Assert.assertEquals(100, list.size());
        for (Data data : list) {
            Assert.assertNull(data.toParsedOldData());
            Assert.assertNotNull(data.toParsedPkData());
        }
    }

    public void testMaxNumberOfDataToRoute() {
        // TODO
    }

    protected void setUpDefaultTriggerRouterForTable1() {
        TriggerRouter triggerRouter = getTestRoutingTableTrigger(TEST_TABLE_1);
        triggerRouter.getRouter().setRouterType("default");
        triggerRouter.getRouter().setRouterExpression(null);
        getTriggerRouterService().saveTriggerRouter(triggerRouter);
        NodeChannel testChannel = getConfigurationService().getNodeChannel(TestConstants.TEST_CHANNEL_ID, false);
        testChannel.setMaxBatchToSend(1000);
        testChannel.setMaxBatchSize(50);
        testChannel.setBatchAlgorithm("default");
        getConfigurationService().saveChannel(testChannel, true);
        getTriggerRouterService().syncTriggers();
    }

    protected int countUnroutedBatches() {
        return getSqlTemplate().queryForInt("select count(*) from sym_outgoing_batch where node_id=?", Constants.UNROUTED_NODE_ID);
    }

    protected TriggerRouter getTestRoutingTableTrigger(String tableName) {
        TriggerRouter trigger = null;
        Set<TriggerRouter> triggerRouters = getTriggerRouterService().getTriggerRouterForTableForCurrentNode(null, null, tableName, true);
        if (triggerRouters == null || triggerRouters.size() == 0) {
            trigger = new TriggerRouter();
            trigger.getTrigger().setSourceTableName(tableName);
            trigger.getTrigger().setTriggerId(tableName);
            trigger.getRouter().setNodeGroupLink(new NodeGroupLink(TestConstants.TEST_ROOT_NODE_GROUP, TestConstants.TEST_CLIENT_NODE_GROUP));
            if (tableName.equals(TEST_TABLE_2)) {
                trigger.getTrigger().setChannelId(TestConstants.TEST_CHANNEL_ID_OTHER);
            } else {
                trigger.getTrigger().setChannelId(TestConstants.TEST_CHANNEL_ID);
            }
        } else {
            trigger = triggerRouters.iterator().next();
        }
        return trigger;
    }

    protected void filterForChannels(OutgoingBatches batches, NodeChannel... channels) {
        for (Iterator<OutgoingBatch> iterator = batches.getBatches().iterator(); iterator.hasNext();) {
            OutgoingBatch outgoingBatch = iterator.next();
            boolean foundChannel = false;
            for (NodeChannel nodeChannel : channels) {
                if (outgoingBatch.getChannelId().equals(nodeChannel.getChannelId())) {
                    foundChannel = true;
                }
            }
            if (!foundChannel) {
                iterator.remove();
            }
        }
    }

    protected int countBatchesForChannel(OutgoingBatches batches, NodeChannel channel) {
        int count = 0;
        for (Iterator<OutgoingBatch> iterator = batches.getBatches().iterator(); iterator.hasNext();) {
            OutgoingBatch outgoingBatch = iterator.next();
            count += outgoingBatch.getChannelId().equals(channel.getChannelId()) ? 1 : 0;
        }
        return count;
    }

    protected void insertGaps(int insertedCount, int rollbackCount, int repeatCount) {
        for (int i = 0; i < repeatCount; i++) {
            insert(TEST_TABLE_1, insertedCount, true, null, NODE_GROUP_NODE_1.getNodeId(), false);
            insert(TEST_TABLE_1, rollbackCount, true, null, NODE_GROUP_NODE_1.getNodeId(), true);
        }
    }

    protected void deleteAll(final String tableName) {
        getSqlTemplate().update("delete from " + tableName);
    }

    protected void insert(final String tableName, final int count, boolean transactional) {
        insert(tableName, count, transactional, null);
    }

    protected void insert(final String tableName, final int count, boolean transactional, final String node2disable) {
        insert(tableName, count, transactional, node2disable, NODE_GROUP_NODE_1.getNodeId());
    }

    protected void insert(final String tableName, final int count, boolean transactional, final String node2disable,
            final String routingVarcharFieldValue) {
        insert(tableName, count, transactional, node2disable, routingVarcharFieldValue, false);
    }

    protected void insert(final String tableName, final int count, boolean transactional, final String node2disable,
            final String routingVarcharFieldValue, final boolean rollback) {
        ISymmetricDialect dialect = getDbDialect();
        IDatabasePlatform platform = dialect.getPlatform();
        String columnName = platform.alterCaseToMatchDatabaseDefaultCase("ROUTING_VARCHAR");
        ISqlTransaction transaction = null;
        try {
            transaction = platform.getSqlTemplate().startSqlTransaction();
            if (node2disable != null) {
                dialect.disableSyncTriggers(transaction, node2disable);
            }
            transaction.prepare(String.format("insert into %s (%s) values(?)", tableName, columnName));
            for (int i = 0; i < count; i++) {
                transaction.addRow(i, new Object[] { routingVarcharFieldValue }, new int[] { Types.VARCHAR });
                if (!transactional) {
                    transaction.commit();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            if (node2disable != null) {
                dialect.enableSyncTriggers(transaction);
            }
            if (rollback) {
                transaction.rollback();
            } else {
                transaction.flush();
                transaction.commit();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
    }

    protected void update(String tableName, String value) {
        IDatabasePlatform platform = getDbDialect().getPlatform();
        String columnName = platform.alterCaseToMatchDatabaseDefaultCase("ROUTING_VARCHAR");
        getSqlTemplate().update(String.format("insert into %s (%s) values(?)", tableName, columnName), value);
    }

    protected void execute(final String sql, final String node2disable) {
        ISymmetricDialect dialect = getDbDialect();
        IDatabasePlatform platform = dialect.getPlatform();
        ISqlTransaction transaction = null;
        try {
            transaction = platform.getSqlTemplate().startSqlTransaction();
            if (node2disable != null) {
                dialect.disableSyncTriggers(transaction, node2disable);
            }
            transaction.prepareAndExecute(sql);
            if (node2disable != null) {
                dialect.enableSyncTriggers(transaction);
            }
            transaction.commit();
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
    }
}
