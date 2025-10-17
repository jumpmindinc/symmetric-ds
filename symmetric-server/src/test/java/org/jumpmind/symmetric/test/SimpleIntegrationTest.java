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
package org.jumpmind.symmetric.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Level;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.TestConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.db.nuodb.NuoDbSymmetricDialect;
import org.jumpmind.symmetric.io.data.transform.ColumnPolicy;
import org.jumpmind.symmetric.io.data.transform.ConstantColumnTransform;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeChannelControl;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.NodeStatus;
import org.jumpmind.symmetric.model.OutgoingBatches;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IIncomingBatchService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.util.AppUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class SimpleIntegrationTest extends AbstractIntegrationTest {
    static final String THIS_IS_A_TEST = "This is a test";
    static {
        System.setProperty("h2.baseDir", "./");
    }
    public static boolean testFlag = false;
    static final String updateOrderHeaderStatusSql = "update test_order_header set status = ? where order_id = ?";
    static final String insertStoreStatusSql = "insert into test_store_status (store_id, register_id, status) values(?,?,?)";
    static final String updateStoreStatusSql = "update test_store_status set status = ? where store_id = ? and register_id = ?";
    static final String selectStoreStatusSql = "select status from test_store_status where store_id = ? and register_id = ?";
    static final String enableKeyWordTriggerSql = "update sym_trigger set sync_on_insert = 1, sync_on_update = 1, sync_on_delete = 1 where source_table_name = 'test_key_word'";
    static final String alterKeyWordSql = "alter table test_key_word add \"key word\" char(1)";
    static final String alterKeyWordSql2 = "alter table test_key_word add \"case\" char(1)";
    static final String insertKeyWordSql = "insert into test_key_word (id, \"key word\", \"case\") values (?, ?, ?)";
    static final String updateKeyWordSql = "update test_key_word set \"key word\" = ?, \"case\" = ? where id = ?";
    static final String selectKeyWordSql = "select \"key word\", \"case\" from test_key_word where id = ?";
    static final String nullSyncColumnLevelSql = "update test_sync_column_level set string_value = null, time_value = null, date_value = null, bigint_value = null, decimal_value = null where id = ?";
    static final String deleteSyncColumnLevelSql = "delete from test_sync_column_level where id = ?";
    static final String updateSyncColumnLevelSql = "update test_sync_column_level set $(column) = ? where id = ?";
    static final String selectSyncColumnLevelSql = "select count(*) from test_sync_column_level where id = ? and $(column) = ?";
    static final String isRegistrationClosedSql = "select count(*) from sym_node_security where registration_enabled=0 and node_id=?";
    static final String makeHeartbeatOld = "update sym_node_host set heartbeat_time={ts '2000-01-01 01:01:01.000'} where node_id=?";
    static final String deleteNode = "delete from sym_node where node_id=?";
    static final byte[] BINARY_DATA = new byte[] { 0x01, 0x02, 0x03 };

    @Test
    public void test01CreateServer() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            ISymmetricEngine server = getServer();
            assertNotNull(server);
            server.getParameterService().saveParameter(ParameterConstants.FILE_SYNC_ENABLE, false, "unit_test");
            checkForFailedTriggers(true, false);
        });
    }

    @Test
    public void test02RegisterClientWithRoot() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            ISymmetricEngine rootEngine = getServer();
            INodeService rootNodeService = rootEngine.getNodeService();
            rootEngine.openRegistration(TestConstants.TEST_CLIENT_NODE_GROUP,
                    TestConstants.TEST_CLIENT_EXTERNAL_ID);
            assertTrue("The registration for the client should be opened now", rootNodeService
                    .findNodeSecurity(TestConstants.TEST_CLIENT_EXTERNAL_ID).isRegistrationEnabled());
            getClient().start();
            getClient().getParameterService().saveParameter(ParameterConstants.FILE_SYNC_ENABLE, false, "unit_test");
            clientPull();
            assertTrue("The client did not register", getClient().isRegistered());
            assertFalse("The registration for the client should be closed now", rootNodeService
                    .findNodeSecurity(TestConstants.TEST_CLIENT_EXTERNAL_ID).isRegistrationEnabled());
            IStatisticManager statMgr = getClient().getStatisticManager();
            statMgr.flush();
            checkForFailedTriggers(true, true);
        });
    }

    @Test
    public void test03InitialLoad() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            boolean sendingLob1 = serverTestService.insertIntoTestUseStreamLob(100, "test_use_stream_lob", THIS_IS_A_TEST);
            boolean sendingLob2 = serverTestService.insertIntoTestUseStreamLob(100, "test_use_capture_lob", THIS_IS_A_TEST);
            Customer customer = new Customer(301, "Linus", true, "42 Blanket Street", "Santa Claus",
                    "IN", 90009, new Date(), new Date(), THIS_IS_A_TEST, BINARY_DATA);
            serverTestService.insertCustomer(customer);
            serverTestService.insertIntoTestTriggerTable(new Object[] { 1, "wow", "mom" });
            serverTestService.insertIntoTestTriggerTable(new Object[] { 2, "mom", "wow" });
            INodeService rootNodeService = getServer().getNodeService();
            INodeService clientNodeService = getClient().getNodeService();
            String nodeId = rootNodeService.findNodeByExternalId(TestConstants.TEST_CLIENT_NODE_GROUP,
                    TestConstants.TEST_CLIENT_EXTERNAL_ID).getNodeId();
            getServer().reloadNode(nodeId, "test");
            IOutgoingBatchService rootOutgoingBatchService = getServer().getOutgoingBatchService();
            assertFalse(rootOutgoingBatchService.isInitialLoadComplete(nodeId));
            assertTrue(rootNodeService.findNodeSecurity(TestConstants.TEST_CLIENT_EXTERNAL_ID)
                    .isInitialLoadEnabled());
            do {
                clientPull();
            } while (!rootOutgoingBatchService.isInitialLoadComplete(nodeId));
            assertFalse(rootNodeService.findNodeSecurity(TestConstants.TEST_CLIENT_EXTERNAL_ID)
                    .isInitialLoadEnabled());
            NodeSecurity clientNodeSecurity = clientNodeService
                    .findNodeSecurity(TestConstants.TEST_CLIENT_EXTERNAL_ID);
            assertFalse(clientNodeSecurity.isInitialLoadEnabled());
            assertNotNull(clientNodeSecurity.getInitialLoadTime());
            IIncomingBatchService clientIncomingBatchService = getClient().getIncomingBatchService();
            // Running a query with dirty reads to make sure platform allows it
            getServer().getDataService().findMaxDataId();
            assertEquals("The initial load errored out." + printRootAndClientDatabases(), 0,
                    clientIncomingBatchService.countIncomingBatchesInError());
            assertEquals(
                    "test_triggers_table on the client did not contain the expected number of rows", 2,
                    clientTestService.countTestTriggersTable());
            assertEquals(
                    "test_customer on the client did not contain the expected number of rows", 2,
                    clientTestService.count("test_customer"));
            assertEquals("Initial load was not successful according to the client",
                    NodeStatus.DATA_LOAD_COMPLETED, clientNodeService.getNodeStatus());
            assertEquals("Initial load was not successful accordign to the root", false,
                    rootNodeService.findNodeSecurity(TestConstants.TEST_CLIENT_EXTERNAL_ID)
                            .isInitialLoadEnabled());
            if (sendingLob1) {
                clientTestService.assertTestBlobIsInDatabase(100, "test_use_capture_lob",
                        THIS_IS_A_TEST);
            }
            if (sendingLob2) {
                clientTestService.assertTestBlobIsInDatabase(100, "test_use_stream_lob",
                        THIS_IS_A_TEST);
            }
        });
    }

    @Test
    public void test04LobSyncUsingStreaming() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            String text = "Another test.  Should not find this in text in sym_data, but it should be in the client database";
            if (serverTestService.insertIntoTestUseStreamLob(200, "test_use_stream_lob", text)) {
                // IDatabasePlatform platform = getServer().getDatabasePlatform();
                String rowData = getServer()
                        .getSqlTemplate()
                        .queryForObject(
                                "select row_data from sym_data where data_id = (select max(data_id) from sym_data)",
                                String.class);
                assertTrue("Did not find the id in the row data", rowData.contains("200"));
                assertEquals("\"200\",,,,,,", rowData);
                clientPull();
                clientTestService.assertTestBlobIsInDatabase(200, "test_use_stream_lob", text);
            }
        });
    }

    @Test
    public void test05LobSyncUsingCapture() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            String text = "Another test.  Should not find this in text in sym_data, but it should be in the client database";
            if (serverTestService.insertIntoTestUseStreamLob(200, "test_use_capture_lob", text)) {
                String rowData = getServer()
                        .getSqlTemplate()
                        .queryForObject(
                                "select row_data from sym_data where data_id = (select max(data_id) from sym_data)",
                                String.class);
                assertTrue("Did not find the id in the row data", rowData.contains("200"));
                clientPull();
                clientTestService.assertTestBlobIsInDatabase(200, "test_use_capture_lob", text);
                String updateText = "The text was updated";
                serverTestService.updateTestUseStreamLob(200, "test_use_capture_lob", updateText);
                clientPull();
                serverTestService.assertTestBlobIsInDatabase(200, "test_use_capture_lob", updateText);
            }
        });
    }

    @Test
    public void test06SyncToClient() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            // test pulling no data
            clientPull();
            final byte[] BIG_BINARY = new byte[200];
            for (int i = 0; i < BIG_BINARY.length; i++) {
                BIG_BINARY[i] = 0x01;
            }
            final String TEST_CLOB = "This is my test's test";
            // now change some data that should be sync'd
            Customer customer = new Customer(101, "Charlie Brown", true,
                    "300 Grub Street \\o", "New Yorl", "NY", 90009, new Date(), new Date(), TEST_CLOB,
                    BIG_BINARY);
            if (serverTestService.getSymmetricDialect() instanceof NuoDbSymmetricDialect) {
                customer.setAddress("300 Grub Street");
            }
            serverTestService.insertCustomer(customer);
            clientPull();
            assertTrue("The customer was not sync'd to the client."
                    + printRootAndClientDatabases(), clientTestService.doesCustomerExist(101));
            assertEquals("The customer address was incorrect"
                    + printRootAndClientDatabases(), customer.getAddress(), clientTestService.getCustomer(101).getAddress());
            if (getServer().getSymmetricDialect().isClobSyncSupported()) {
                assertEquals("The CLOB notes field on customer was not sync'd to the client",
                        serverTestService.getCustomerNotes(101),
                        clientTestService.getCustomerNotes(101));
            }
            if (getServer().getSymmetricDialect().isBlobSyncSupported()) {
                byte[] data = clientTestService.getCustomerIcon(101);
                assertTrue("The BLOB icon field on customer was not sync'd to the client",
                        Objects.deepEquals(data, BIG_BINARY));
            }
        });
    }

    @Test
    public void test07SyncToClientMultipleUpdates() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            // test pulling no data
            clientPull();
            final int NEW_ZIP = 44444;
            final String NEW_NAME = "JoJo Duh Doh";
            // now change some data that should be sync'd
            serverTestService.updateCustomer(100, "zip", NEW_ZIP, Types.NUMERIC);
            serverTestService.updateCustomer(100, "name", NEW_NAME, Types.VARCHAR);
            boolean didPullData = clientPull();
            assertTrue(didPullData);
            Customer clientCustomer = clientTestService.getCustomer(100);
            assertEquals(NEW_ZIP, clientCustomer.getZip());
            assertEquals(NEW_NAME, clientCustomer.getName());
        });
    }

    @Test
    public void test08InsertSqlEvent() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            assertEquals(getClient().getSqlTemplate().queryForInt(
                    "select count(*) from sym_node where schema_version='test'"), 0);
            getServer()
                    .getDataService().insertSqlEvent(TestConstants.TEST_CLIENT_NODE,
                            "update sym_node set schema_version='test'", false, -1, null);
            clientPull();
            assertTrue(getClient().getSqlTemplate().queryForInt(
                    "select count(*) from sym_node where schema_version='test'") > 0);
        });
    }

    @Test
    public void test09EmptyNullLob() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            Customer customer = new Customer(300, "Eric", true, "100 Main Street", "Columbus", "OH",
                    43082, new Date(), new Date(), "", new byte[0]);
            serverTestService.insertCustomer(customer);
            clientPull();
            assertNotNull("Customer 300 did not sync to the client", clientTestService.getCustomer(300));
            if (getServer().getSymmetricDialect().isClobSyncSupported()) {
                if (isClientInterbase()) {
                    // Putting an empty string into a CLOB on Interbase results in a
                    // NULL value
                    assertNull("Expected null CLOB", clientTestService.getCustomerNotes(300));
                } else {
                    assertEquals("Expected empty CLOB", serverTestService.getCustomerNotes(300),
                            clientTestService.getCustomerNotes(300));
                }
            }
            if (getServer().getSymmetricDialect().isBlobSyncSupported()) {
                assertArrayEquals("Expected empty BLOB", serverTestService.getCustomerIcon(300),
                        clientTestService.getCustomerIcon(300));
            }
            Table table = getServer().getSymmetricDialect().getPlatform()
                    .getTableFromCache("test_customer", false);
            // Test null large object
            serverTestService.updateCustomer(300, "notes", null, table.getColumnWithName("notes")
                    .getMappedTypeCode());
            serverTestService.updateCustomer(300, "icon", null, table.getColumnWithName("icon")
                    .getMappedTypeCode());
            clientPull();
            if (getServer().getSymmetricDialect().isClobSyncSupported()) {
                assertNull("Expected null CLOB", clientTestService.getCustomerNotes(300));
            }
            if (getServer().getSymmetricDialect().isBlobSyncSupported()) {
                assertNull("Expected null BLOB", clientTestService.getCustomerIcon(300));
            }
        });
    }

    @Test
    public void test10LargeLob() {
        if (!isServerOracle()) {
            return;
        }
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            String bigString = StringUtils.rightPad("Feeling tired... ", 6000, "Z");
            Customer customer = new Customer(400, "Eric", true, "100 Main Street", "Columbus", "OH",
                    43082, new Date(), new Date(), bigString, bigString.getBytes(Charset.defaultCharset()));
            serverTestService.insertCustomer(customer);
            clientPull();
        });
    }

    @Test
    public void test11SuspendIgnorePushRemoteBatches() throws Exception {
        // test suspend / ignore with remote database specifying the suspends
        // and ignores
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            Date date = DateUtils.parseDate("2007-01-03", new String[] { "yyyy-MM-dd" });
            Order order = new Order("101", 100, null, date);
            order.getOrderDetails().add(
                    new OrderDetail("101", 1, "STK", "110000065", 3, new BigDecimal("3.33")));
            assertNull(serverTestService.getOrder(order.getOrderId()));
            clientTestService.insertOrder(order);
            boolean pushedData = clientPush();
            assertTrue("Client data was not batched and pushed", pushedData);
            assertNotNull(serverTestService.getOrder(order.getOrderId()));
            IConfigurationService rootConfigurationService = getServer().getConfigurationService();
            IOutgoingBatchService clientOutgoingBatchService = getClient().getOutgoingBatchService();
            NodeChannel c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_CLIENT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, true);
            rootConfigurationService.saveNodeChannel(c, true);
            date = DateUtils.parseDate("2007-01-03", new String[] { "yyyy-MM-dd" });
            order = new Order("102", 100, null, date);
            order.getOrderDetails().add(
                    new OrderDetail("102", 1, "STK", "110000065", 3, new BigDecimal("3.33")));
            assertNull(serverTestService.getOrder(order.getOrderId()));
            clientTestService.insertOrder(order);
            clientPush();
            OutgoingBatches batches = clientOutgoingBatchService.getOutgoingBatches(
                    TestConstants.TEST_ROOT_NODE.getNodeId(), false);
            assertEquals("There should be one outgoing batches.", 1, batches.getBatches().size());
            assertNull("The order record was synchronized when it should not have been",
                    serverTestService.getOrder(order.getOrderId()));
            c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_CLIENT_EXTERNAL_ID, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, true);
            rootConfigurationService.saveNodeChannel(c, true);
            clientPush();
            batches = clientOutgoingBatchService
                    .getOutgoingBatches(TestConstants.TEST_ROOT_NODE.getNodeId(), false);
            assertEquals("There should be no outgoing batches", 0, batches.getBatches().size());
            assertNull("The order record was synchronized when it should not have been",
                    serverTestService.getOrder(order.getOrderId()));
            // Cleanup!
            c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_CLIENT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, false);
            rootConfigurationService.saveNodeChannel(c, true);
            clientPush();
        });
    }

    @Test
    public void test12SuspendIgnorePushLocalBatches() throws Exception {
        // test suspend / ignore with local database specifying the suspends
        // and ignores
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            Date date = DateUtils.parseDate("2007-01-03", new String[] { "yyyy-MM-dd" });
            Order order = new Order("105", 100, null, date);
            order.getOrderDetails().add(
                    new OrderDetail("105", 1, "STK", "110000065", 3, new BigDecimal("3.33")));
            assertNull(serverTestService.getOrder(order.getOrderId()));
            clientTestService.insertOrder(order);
            clientPush();
            assertNotNull(serverTestService.getOrder(order.getOrderId()));
            IConfigurationService clientConfigurationService = getClient().getConfigurationService();
            IOutgoingBatchService clientOutgoingBatchService = getClient().getOutgoingBatchService();
            NodeChannel c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_CLIENT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, true);
            clientConfigurationService.saveNodeChannel(c, true);
            order = new Order("106", 100, null, date);
            order.getOrderDetails().add(
                    new OrderDetail("106", 1, "STK", "110000065", 3, new BigDecimal("3.33")));
            clientPush();
            OutgoingBatches batches = clientOutgoingBatchService.getOutgoingBatches(
                    TestConstants.TEST_ROOT_NODE.getNodeId(), false);
            assertEquals("There should be no outgoing batches since suspended locally", 0,
                    batches.getBatches().size());
            assertNull(serverTestService.getOrder(order.getOrderId()));
            c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_CLIENT_EXTERNAL_ID, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, true);
            clientConfigurationService.saveNodeChannel(c, true);
            clientPush();
            batches = clientOutgoingBatchService
                    .getOutgoingBatches(TestConstants.TEST_ROOT_NODE.getNodeId(), false);
            assertEquals("There should be no outgoing batches since suspended locally", 0,
                    batches.getBatches().size());
            assertNull(serverTestService.getOrder(order.getOrderId()));
            // Cleanup!
            c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_CLIENT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, false);
            clientConfigurationService.saveNodeChannel(c, true);
            clientPush();
        });
    }

    @Test
    public void test13SuspendIgnorePullRemoteBatches() throws Exception {
        // test suspend / ignore with remote database specifying the suspends
        // and ignores
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            // Should not sync when status = null
            Date date = DateUtils.parseDate("2009-09-30", new String[] { "yyyy-MM-dd" });
            Order order = new Order("42", 100, "C", date);
            serverTestService.insertOrder(order);
            clientPull();
            IOutgoingBatchService rootOutgoingBatchService = getServer().getOutgoingBatchService();
            OutgoingBatches batches = rootOutgoingBatchService.getOutgoingBatches(
                    TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertNotNull(clientTestService.getOrder(order.getOrderId()));
            assertEquals("There should be no outgoing batches", 0, batches.getBatches().size());
            // Suspend the channel...
            IConfigurationService rootConfigurationService = getServer().getConfigurationService();
            NodeChannel c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, true);
            rootConfigurationService.saveNodeChannel(c, true);
            date = DateUtils.parseDate("2009-09-30", new String[] { "yyyy-MM-dd" });
            order = new Order("43", 100, "C", date);
            serverTestService.insertOrder(order);
            clientPull();
            batches = rootOutgoingBatchService
                    .getOutgoingBatches(TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertEquals("There should be 1 outgoing batch", 1, batches.getBatches().size());
            assertNull(clientTestService.getOrder(order.getOrderId()));
            c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, true);
            rootConfigurationService.saveNodeChannel(c, true);
            // ignore
            clientPull();
            batches = rootOutgoingBatchService
                    .getOutgoingBatches(TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertNull(clientTestService.getOrder(order.getOrderId()));
            assertEquals("There should be no outgoing batches", 0, batches.getBatches().size());
            // Cleanup!
            c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, false);
            rootConfigurationService.saveNodeChannel(c, true);
            clientPull();
            batches = rootOutgoingBatchService
                    .getOutgoingBatches(TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertEquals("There should be no outgoing batches", 0, batches.getBatches().size());
            assertNull(clientTestService.getOrder(order.getOrderId()));
        });
    }

    @Test
    public void test14SuspendIgnorePullRemoteLocalComboBatches() throws Exception {
        // test suspend / ignore with remote database specifying the suspends
        // and ignores
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            Date date = DateUtils.parseDate("2009-09-30", new String[] { "yyyy-MM-dd" });
            Order order = new Order("442", 100, "C", date);
            serverTestService.insertOrder(order);
            clientPull();
            IOutgoingBatchService rootOutgoingBatchService = getServer().getOutgoingBatchService();
            OutgoingBatches batches = rootOutgoingBatchService.getOutgoingBatches(
                    TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertNotNull(clientTestService.getOrder(order.getOrderId()));
            assertEquals("There should be no outgoing batches", 0, batches.getBatches().size());
            IConfigurationService rootConfigurationService = getServer().getConfigurationService();
            IConfigurationService clientConfigurationService = getClient().getConfigurationService();
            // suspend on remote
            NodeChannel c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, true);
            rootConfigurationService.saveNodeChannel(c, true);
            // ignore on local
            c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, true);
            clientConfigurationService.saveNodeChannel(c, true);
            order = new Order("443", 100, "C", date);
            serverTestService.insertOrder(order);
            clientPull();
            batches = rootOutgoingBatchService
                    .getOutgoingBatches(TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertEquals("There should be no outgoing batches", 0, batches.getBatches().size());
            assertNull(clientTestService.getOrder(order.getOrderId()));
            // ignore on remote
            c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, true);
            c.setSuspendEnabled(NodeChannelControl.ALL, false);
            rootConfigurationService.saveNodeChannel(c, true);
            // suspend on local
            c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, true);
            clientConfigurationService.saveNodeChannel(c, true);
            order = new Order("444", 100, "C", date);
            clientPull();
            batches = rootOutgoingBatchService
                    .getOutgoingBatches(TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertEquals("There should be no outgoing batches", 0, batches.getBatches().size());
            assertNull(clientTestService.getOrder(order.getOrderId()));
            // Cleanup!
            c = rootConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, false);
            rootConfigurationService.saveNodeChannel(c, true);
            c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, false);
            clientConfigurationService.saveNodeChannel(c, true);
            clientPull();
        });
    }

    @Test
    public void test15UpdateDataWithNoChangesSyncToClient() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            int clientIncomingBatchCount = getIncomingBatchCountForClient();
            int rowsUpdated = getServer().getSqlTemplate().update(
                    "update test_sync_column_level set string_value=string_value");
            assertTrue(rowsUpdated > 0);
            clientPull();
            assertTrue(clientIncomingBatchCount <= getIncomingBatchCountForClient());
            assertEquals(0, getIncomingBatchNotOkCountForClient());
            assertEquals(0, getServer().getOutgoingBatchService().countOutgoingBatchesInError());
            assertEquals(serverTestService.count("test_sync_column_level"),
                    clientTestService.count("test_sync_column_level"));
        });
    }

    @Test
    public void test16SuspendIgnorePullLocalBatches() throws Exception {
        // test suspend / ignore with local database specifying suspends and
        // ignores
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            // Should not sync when status = null
            Date date = DateUtils.parseDate("2009-09-30", new String[] { "yyyy-MM-dd" });
            Order order = new Order("44", 100, "C", date);
            serverTestService.insertOrder(order);
            clientPull();
            IOutgoingBatchService rootOutgoingBatchService = getServer().getOutgoingBatchService();
            OutgoingBatches batches = rootOutgoingBatchService.getOutgoingBatches(
                    TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertNotNull(clientTestService.getOrder(order.getOrderId()));
            assertEquals("There should be no outgoing batches", 0, batches.getBatches().size());
            // Suspend the channel...
            IConfigurationService clientConfigurationService = getClient().getConfigurationService();
            NodeChannel c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, true);
            clientConfigurationService.saveNodeChannel(c, true);
            order = new Order("45", 100, "C", date);
            serverTestService.insertOrder(order);
            clientPull();
            batches = rootOutgoingBatchService
                    .getOutgoingBatches(TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertNull(clientTestService.getOrder(order.getOrderId()));
            assertEquals("There should be 1 outgoing batches", 1, batches.getBatches().size());
            c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, true);
            clientConfigurationService.saveNodeChannel(c, true);
            // ignore
            clientPull();
            assertNoPendingBatchesOnServer();
            c = clientConfigurationService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            c.setSuspendEnabled(NodeChannelControl.ALL, false);
            c.setIgnoreEnabled(NodeChannelControl.ALL, false);
            clientConfigurationService.saveNodeChannel(c, true);
            clientPull();
        });
    }

    @Test
    public void test17SyncToRootAutoGeneratedPrimaryKey() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            final String NEW_VALUE = "unique new value one value";
            clientTestService
                    .insertIntoTestTriggerTable(new Object[] { 3, "value one", "value \" two" });
            clientPush();
            getClient().getSqlTemplate().update("update test_triggers_table set string_one_value=?",
                    NEW_VALUE);
            final String verifySql = "select count(*) from test_triggers_table where string_one_value=?";
            assertEquals(3, getClient().getSqlTemplate().queryForInt(verifySql, NEW_VALUE));
            clientPush();
            assertEquals(3, getServer().getSqlTemplate().queryForInt(verifySql, NEW_VALUE));
        });
    }

    @Test
    public void test18ReopenRegistration() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            getServer().reOpenRegistration(TestConstants.TEST_CLIENT_EXTERNAL_ID);
            clientPull();
            assertEquals(
                    1,
                    getServer().getSqlTemplate().queryForInt(isRegistrationClosedSql,
                            TestConstants.TEST_CLIENT_EXTERNAL_ID));
        });
    }

    @Test
    public void test19SyncToRoot() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            Date date = DateUtils.parseDate("2007-01-03", new String[] { "yyyy-MM-dd" });
            Order order = new Order("10", 100, null, date);
            order.getOrderDetails().add(
                    new OrderDetail("10", 1, "STK", "110000065", 3, new BigDecimal("3.33")));
            clientTestService.insertOrder(order);
            assertNull(serverTestService.getOrder(order.getOrderId()));
            clientPush();
            assertNotNull(serverTestService.getOrder(order.getOrderId()));
        });
    }

    @Test
    public void test20SyncInsertCondition() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            // Should not sync when status = null
            Date date = DateUtils.parseDate("2007-01-02", new String[] { "yyyy-MM-dd" });
            Order order = new Order("11", 100, null, date);
            serverTestService.insertOrder(order);
            clientPull();
            assertNoPendingBatchesOnServer();
            assertNull(clientTestService.getOrder(order.getOrderId()));
            // Should sync when status = C
            order = new Order("12", 100, "C", date);
            serverTestService.insertOrder(order);
            clientPull();
            assertNotNull(clientTestService.getOrder(order.getOrderId()));
        });
    }

    @Test
    public void test21OneColumnTableWithPrimaryKeyUpdate() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            getServer().getSqlTemplate().update("insert into one_column_table values(1)");
            assertEquals(getClient().getSqlTemplate().queryForInt(
                    "select count(*) from one_column_table where my_one_column=1"), 0);
            clientPull();
            assertEquals(getClient().getSqlTemplate().queryForInt(
                    "select count(*) from one_column_table where my_one_column=1"), 1);
            getServer().getSqlTemplate().update(
                    "update one_column_table set my_one_column=1 where my_one_column=1");
            clientPull();
            assertNoPendingBatchesOnServer();
        });
    }

    @Test
    public void test22SyncUpdateCondition() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            Order order = clientTestService.getOrder("1");
            assertNull(order);
            getServer().getSqlTemplate().update(updateOrderHeaderStatusSql, "C", "1");
            clientPull();
            order = clientTestService.getOrder("1");
            assertNotNull(order);
            assertEquals("C", order.getStatus());
        });
    }

    @Test
    public void test23IgnoreNodeChannel() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            INodeService rootNodeService = getServer().getNodeService();
            IConfigurationService rootConfigService = getServer().getConfigurationService();
            rootNodeService.ignoreNodeChannelForExternalId(true, TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_NODE_GROUP, TestConstants.TEST_ROOT_EXTERNAL_ID);
            rootConfigService.clearCache();
            NodeChannel channel = rootConfigService.getNodeChannel(TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_EXTERNAL_ID, false);
            assertNotNull(channel);
            assertTrue(channel.isIgnoreEnabled(NodeChannelControl.ALL));
            assertFalse(channel.isSuspendEnabled(NodeChannelControl.ALL));
            Customer customer = new Customer(201, "Charlie Dude", true, "300 Grub Street", "New York",
                    "NY", 90009, new Date(), new Date(), THIS_IS_A_TEST, BINARY_DATA);
            serverTestService.insertCustomer(customer);
            clientPull();
            assertNull(clientTestService.getCustomer(customer.getCustomerId()));
            rootNodeService.ignoreNodeChannelForExternalId(false, TestConstants.TEST_CHANNEL_ID,
                    TestConstants.TEST_ROOT_NODE_GROUP, TestConstants.TEST_ROOT_EXTERNAL_ID);
            rootConfigService.clearCache();
            clientPull();
            assertNull(clientTestService.getCustomer(customer.getCustomerId()));
            getClient().getConfigurationService().clearCache();
        });
    }

    @Test
    public void test24SyncUpdateWithEmptyKey() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            if (getClient().getSymmetricDialect().getPlatform().getDatabaseInfo()
                    .isEmptyStringNulled()) {
                return;
            }
            getClient().getSqlTemplate().update(insertStoreStatusSql,
                    new Object[] { "00001", "", 1 });
            clientPush();
            getClient().getSqlTemplate().update(updateStoreStatusSql,
                    new Object[] { 2, "00001", "" });
            clientPush();
            int status = -1;
            if (getServer().getSymmetricDialect().getPlatform().getDatabaseInfo().isBlankCharColumnSpacePadded()) {
                status = getServer().getSqlTemplate().queryForInt(selectStoreStatusSql,
                        new Object[] { "00001", "   " });
            } else {
                status = getServer().getSqlTemplate().queryForInt(selectStoreStatusSql,
                        new Object[] { "00001", "" });
            }
            assertEquals("Wrong store status", 2, status);
        });
        logTestComplete();
    }

    @Test
    public void test25TestPurge() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            // do an extra push & pull to make sure we have events cleared out
            clientPull();
            clientPush();
            Thread.sleep(2000);
            IParameterService parameterService = getServer().getParameterService();
            int purgeRetentionMinues = parameterService
                    .getInt(ParameterConstants.PURGE_RETENTION_MINUTES);
            // set purge in the future just in case the database time is different
            // than the current time
            parameterService.saveParameter(ParameterConstants.PURGE_RETENTION_MINUTES, -60 * 24, "test");
            int beforePurge = getServer().getSqlTemplate().queryForInt("select count(*) from sym_data");
            getServer().purge();
            int afterPurge = getServer().getSqlTemplate().queryForInt("select count(*) from sym_data");
            Timestamp maxCreateTime = (Timestamp) getServer().getSqlTemplate().queryForObject(
                    "select max(create_time) from sym_data", Timestamp.class);
            Timestamp minCreateTime = (Timestamp) getServer().getSqlTemplate().queryForObject(
                    "select min(create_time) from sym_data", Timestamp.class);
            assertTrue("Expected data rows to have been purged at the root.  There were "
                    + beforePurge + " row before and " + afterPurge
                    + " rows after. The max create_time in sym_data was " + maxCreateTime
                    + " and the min create_time in sym_data was " + minCreateTime
                    + " and the current time of the server is " + new Date(),
                    (beforePurge - afterPurge) > 0);
            parameterService.saveParameter(ParameterConstants.PURGE_RETENTION_MINUTES,
                    purgeRetentionMinues, "test");
            parameterService = getClient().getParameterService();
            purgeRetentionMinues = parameterService.getInt(ParameterConstants.PURGE_RETENTION_MINUTES);
            // set purge in the future just in case the database time is different
            // than the current time
            parameterService.saveParameter(ParameterConstants.PURGE_RETENTION_MINUTES, -60 * 24, "test");
            beforePurge = getClient().getSqlTemplate().queryForInt("select count(*) from sym_data");
            getClient().purge();
            afterPurge = getClient().getSqlTemplate().queryForInt("select count(*) from sym_data");
            maxCreateTime = (Timestamp) getClient().getSqlTemplate().queryForObject(
                    "select max(create_time) from sym_data", Timestamp.class);
            minCreateTime = (Timestamp) getClient().getSqlTemplate().queryForObject(
                    "select min(create_time) from sym_data", Timestamp.class);
            assertTrue("Expected data rows to have been purged at the client.  There were "
                    + beforePurge + " row before anf " + afterPurge
                    + " rows after. . The max create_time in sym_data was " + maxCreateTime
                    + " and the min create_time in sym_data was " + minCreateTime
                    + " and the current time of the server is " + new Date(),
                    (beforePurge - afterPurge) > 0);
            parameterService.saveParameter(ParameterConstants.PURGE_RETENTION_MINUTES,
                    purgeRetentionMinues, "test");
        });
    }

    @Test
    public void test25TestPurge2() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            // do an extra push & pull to make sure we have events cleared out
            clientPull();
            clientPush();
            int totalCount = getServer().getSqlTemplate().queryForInt("select count(*) from sym_data_event");
            int batchId = getServer().getSqlTemplate().queryForInt("select min(batch_id) from sym_outgoing_batch");
            int purgeCount = getServer().getSqlTemplate().queryForInt("select count(*) from sym_data_event where batch_id = ?", batchId);
            getServer().getSqlTemplate().update("delete from sym_outgoing_batch where batch_id = ?", batchId);
            IParameterService parameterService = getServer().getParameterService();
            int purgeRetentionMinues = parameterService.getInt(ParameterConstants.PURGE_RETENTION_MINUTES);
            // Put retention into future to force it to purge
            parameterService.saveParameter(ParameterConstants.PURGE_RETENTION_MINUTES, -60 * 24, "test");
            getServer().purge();
            parameterService.saveParameter(ParameterConstants.PURGE_RETENTION_MINUTES, purgeRetentionMinues, "test");
            int count = getServer().getSqlTemplate().queryForInt("select count(*) from sym_data_event where batch_id = ?", batchId);
            assertEquals(0, count);
            int newTotalCount = getServer().getSqlTemplate().queryForInt("select count(*) from sym_data_event");
            assertEquals(newTotalCount, totalCount - purgeCount);
        });
    }

    @Test
    public void test26Heartbeat() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            Level previous = setLoggingLevelForTest(Level.DEBUG);
            try {
                final String checkHeartbeatSql = "select max(heartbeat_time) from sym_node_host where node_id='"
                        + TestConstants.TEST_CLIENT_EXTERNAL_ID + "'";
                Date clientHeartbeatTimeBefore = getClient().getSqlTemplate().queryForObject(
                        checkHeartbeatSql, Timestamp.class);
                Thread.sleep(1000);
                getClient().heartbeat(true);
                Date clientHeartbeatTimeAfter = getClient().getSqlTemplate().queryForObject(
                        checkHeartbeatSql, Timestamp.class);
                assertNotSame("The heartbeat time was not updated at the client",
                        clientHeartbeatTimeAfter, clientHeartbeatTimeBefore);
                Date rootHeartbeatTimeBefore = getServer().getSqlTemplate().queryForObject(
                        checkHeartbeatSql, Timestamp.class);
                assertNotSame(
                        "The root heartbeat time should not be the same as the updated client heartbeat time",
                        clientHeartbeatTimeAfter, rootHeartbeatTimeBefore);
                while (clientPush()) {
                    // continue to push while there data to push
                    Thread.sleep(1000);
                }
                Date rootHeartbeatTimeAfter = getServer().getSqlTemplate().queryForObject(
                        checkHeartbeatSql, Timestamp.class);
                assertNotSame(
                        "The root heartbeat time before should have been different than the root heartbeat time after",
                        rootHeartbeatTimeBefore, rootHeartbeatTimeAfter);
                assertEquals(
                        "The root heartbeat time should have been equal to or after the client heartbeat time.",
                        rootHeartbeatTimeAfter.getTime() >= clientHeartbeatTimeAfter.getTime(), true);
            } finally {
                setLoggingLevelForTest(previous);
            }
        });
    }

    @Test
    public void test27VirtualTransactionId() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            getServer().getSqlTemplate().update(
                    "insert into test_very_long_table_name_1234 values('42')");
            if (getServer().getSymmetricDialect().isTransactionIdOverrideSupported()) {
                assertEquals(
                        "The hardcoded transaction id was not found.",
                        "42",
                        getServer()
                                .getSqlTemplate()
                                .queryForObject(
                                        "select transaction_id from sym_data where data_id in (select max(data_id) from sym_data)",
                                        String.class));
                assertEquals(
                        1,
                        getServer().getSqlTemplate().update(
                                "delete from test_very_long_table_name_1234 where id='42'"));
                assertEquals(
                        "The hardcoded transaction id was not found.",
                        "42",
                        getServer()
                                .getSqlTemplate()
                                .queryForObject(
                                        "select transaction_id from sym_data where data_id in (select max(data_id) from sym_data)",
                                        String.class));
            }
        });
    }

    @Test
    public void test28CaseSensitiveTableNames() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            String rquote = getServer().getSymmetricDialect().getPlatform().getDatabaseInfo()
                    .getDelimiterToken();
            String cquote = getClient().getSymmetricDialect().getPlatform().getDatabaseInfo()
                    .getDelimiterToken();
            getServer().getSqlTemplate().update(
                    "insert into " + rquote + "Test_Mixed_Case" + rquote + " values(?,?)", 1, "Hello");
            while (clientPull()) {
                AppUtils.sleep(1000);
            }
            assertEquals(
                    "Table name in mixed case was not synced",
                    1,
                    getClient().getSqlTemplate().queryForInt(
                            "select count(*) from " + cquote + "Test_Mixed_Case" + cquote + " where "
                                    + cquote + "Mixed_Case_Id" + cquote + " = 1"));
        });
    }

    @Test
    public void test29SyncShellCommand() throws Exception {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            IDataService rootDataService = getServer().getDataService();
            IOutgoingBatchService rootOutgoingBatchService = getServer().getOutgoingBatchService();
            synchronized (SimpleIntegrationTest.class) {
                testFlag = false;
            }
            String scriptData = "org.jumpmind.symmetric.test.SimpleIntegrationTest.testFlag=true;";
            rootDataService.sendScript(TestConstants.TEST_CLIENT_EXTERNAL_ID, scriptData, false);
            clientPull();
            OutgoingBatches batches = rootOutgoingBatchService.getOutgoingBatches(
                    TestConstants.TEST_CLIENT_NODE.getNodeId(), false);
            assertEquals(0, batches.countBatches(true));
            assertTrue("Expected the testFlag static variable to have been set to true", testFlag);
        });
    }

    @Test
    public void test30AutoConfigureTablesAfterAlreadyCreated() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            testAutoConfigureTablesAfterAlreadyCreated(getServer());
            testAutoConfigureTablesAfterAlreadyCreated(getClient());
        });
    }

    public static void main(String[] args) {
        SimpleIntegrationTest sit = new SimpleIntegrationTest();
        sit.test01CreateServer();
        sit.test02RegisterClientWithRoot();
        sit.test31ReloadMissingForeignKeys();
    }

    @Test
    public void test31ReloadMissingForeignKeys() {
        if (getClient().getSymmetricDialect().getName().equalsIgnoreCase(DatabaseNamesConstants.VOLTDB) ||
                getClient().getSymmetricDialect().getName().equalsIgnoreCase(DatabaseNamesConstants.REDSHIFT) ||
                getClient().getSymmetricDialect().getName().equalsIgnoreCase(DatabaseNamesConstants.GREENPLUM) ||
                getClient().getSymmetricDialect().getName().equalsIgnoreCase(DatabaseNamesConstants.SQLITE) ||
                getClient().getSymmetricDialect().getName().equalsIgnoreCase(DatabaseNamesConstants.GENERIC)) {
            return;
        }
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            logTestRunning();
            ISqlTransaction tran = getServer().getSqlTemplate().startSqlTransaction();
            getServer().getSymmetricDialect().disableSyncTriggers(tran, null);
            tran.execute("insert into test_a (id) values ('1')");
            tran.commit();
            getServer().getSqlTemplate().update("insert into test_b (id,  aid) values('1', '1')");
            int tries = 0;
            while (clientPull()) {
                if (tries++ > 10) {
                    Assert.fail("Unable to resolve missing foreign key");
                }
                AppUtils.sleep(1000);
            }
        });
    }

    @Test
    public void test32TransformsWithUpdateFirstAndIncludeOn() {
        Assertions.assertTimeout(Duration.ofMinutes(5), () -> {
            logTestRunning();
            NodeGroupLink groupLink = getClient().getConfigurationService().getNodeGroupLinkFor("test-root-group",
                    "test-node-group", false);
            TransformTableNodeGroupLink extractTransform = new TransformTableNodeGroupLink();
            extractTransform.setTransformId("test_transform_extract");
            extractTransform.setSourceTableName("test_transform");
            extractTransform.setTargetTableName("test_transform");
            extractTransform.setTransformPoint(TransformPoint.EXTRACT);
            extractTransform.setColumnPolicy(ColumnPolicy.SPECIFIED);
            extractTransform.setUpdateFirst(true);
            extractTransform.setNodeGroupLink(groupLink);
            TransformColumn idColumn = new TransformColumn("id", "id", true);
            idColumn.setTransformId("test_transform_extract");
            idColumn.setTransformOrder(1);
            extractTransform.addTransformColumn(idColumn);
            TransformColumn dataColumn = new TransformColumn("data", "data", false);
            dataColumn.setTransformId("test_transform_extract");
            dataColumn.setTransformOrder(2);
            extractTransform.addTransformColumn(dataColumn);
            TransformTableNodeGroupLink loadTransform = new TransformTableNodeGroupLink();
            loadTransform.setTransformId("test_transform_load");
            loadTransform.setSourceTableName("test_transform");
            loadTransform.setTargetTableName("test_transform");
            loadTransform.setTransformPoint(TransformPoint.LOAD);
            loadTransform.setNodeGroupLink(groupLink);
            TransformColumn insertOnlyColumn = new TransformColumn("", "insert_only", false, ConstantColumnTransform.NAME, "1");
            insertOnlyColumn.setTransformId("test_transform_load");
            insertOnlyColumn.setIncludeOn(IncludeOnType.INSERT);
            loadTransform.addTransformColumn(insertOnlyColumn);
            ITransformService transformService = getServer().getTransformService();
            transformService.saveTransformTable(extractTransform, true);
            transformService.saveTransformTable(loadTransform, true);
            clientPull();
            getServer().getSqlTemplate().update("insert into test_transform (id, data, insert_only) values (1, 'test1', 123)");
            getServer().getSqlTemplate().update("insert into test_transform (id, data, insert_only) values (2, 'test2', 321)");
            assertEquals("Target table is empty to begin with", getClient().getSqlTemplate().queryForInt(
                    "select count(*) from test_transform where insert_only=1"), 0);
            clientPull();
            assertEquals("Inserted rows sync to target table with insert_only value specified in transform",
                    getClient().getSqlTemplate().queryForInt(
                            "select count(*) from test_transform where insert_only=1"), 2);
            getClient().getSqlTemplate().update("delete from test_transform");
            assertEquals("Target table is empty after a delete", getClient().getSqlTemplate().queryForInt(
                    "select count(*) from test_transform where insert_only=1"), 0);
            getServer().getSqlTemplate().update("update test_transform set data='test'");
            clientPull();
            assertEquals("Updated rows sync to target table with insert_only value specified in transform when target table is empty",
                    getClient().getSqlTemplate().queryForInt(
                            "select count(*) from test_transform where data='test' and insert_only=1"), 2);
            getServer().getSqlTemplate().update("update test_transform set insert_only=2");
            clientPull();
            assertEquals("insert_only value in target table doesn't change on an update", getClient().getSqlTemplate().queryForInt(
                    "select count(*) from test_transform where insert_only=1"), 2);
        });
    }

    protected void testAutoConfigureTablesAfterAlreadyCreated(ISymmetricEngine engine) {
        ISymmetricDialect dialect = engine.getSymmetricDialect();
        assertEquals("Tables were altered when they should not have been", false, dialect.createOrAlterTablesIfNecessary());
    }

    @Test
    public void test99Shutdown() throws Exception {
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            logTestRunning();
            getClient().destroy();
            getWebServer().stop();
        });
    }
    // @Test
    // public void testSyncShellCommandError() throws Exception {
    // logTestRunning();
    // Level old = setLoggingLevelForTest(Level.OFF);
    // IDataService rootDataService = AppUtils.find(Constants.DATA_SERVICE,
    // getRootEngine());
    // IOutgoingBatchService rootOutgoingBatchService = AppUtils.find(
    // Constants.OUTGOING_BATCH_SERVICE, getRootEngine());
    // testFlag = false;
    // String scriptData =
    // "org.jumpmind.symmetric.test.SimpleIntegrationTest.nonExistentFlag=true;";
    // rootDataService.sendScript(TestConstants.TEST_CLIENT_EXTERNAL_ID,
    // scriptData, false);
    // clientPull();
    // OutgoingBatches batches = rootOutgoingBatchService
    // .getOutgoingBatches(TestConstants.TEST_CLIENT_NODE, false);
    // assertEquals(1, batches.countBatches(true));
    // assertFalse(testFlag);
    // rootOutgoingBatchService.markAllAsSentForNode(TestConstants.TEST_CLIENT_NODE);
    // setLoggingLevelForTest(old);
    // }
    //
    // /**
    // * TODO test on MSSQL
    // */
    // @Test
    // @ParameterExcluder("mssql")
    // public void testNoPrimaryKeySync() {
    // logTestRunning();
    // getServer().getSqlTemplate().update("insert into no_primary_key_table values(1, 2, 'HELLO')");
    // clientPull();
    // assertEquals(
    // getClient().getSqlTemplate()
    // .queryForInt("select two_column from no_primary_key_table where one_column=1"),
    // 2, "Table was not synced");
    // getServer().getSqlTemplate().update("update no_primary_key_table set two_column=3 where one_column=1");
    // clientPull();
    // assertEquals(
    // getClient().getSqlTemplate()
    // .queryForInt("select two_column from no_primary_key_table where one_column=1"),
    // 3, "Table was not updated");
    // getServer().getSqlTemplate().update("delete from no_primary_key_table");
    // clientPull();
    // assertEquals(getClient().getSqlTemplate().queryForInt("select count(*) from no_primary_key_table"),
    // 0, "Table was not deleted from");
    // }
    //
    // @Test
    // public void testReservedColumnNames() {
    // logTestRunning();
    // if (getRootDbDialect() instanceof Db2DbDialect
    // || getClientDbDialect() instanceof Db2DbDialect
    // || getRootDbDialect() instanceof FirebirdDbDialect
    // || getClientDbDialect() instanceof FirebirdDbDialect
    // || getRootDbDialect() instanceof InterbaseDbDialect
    // || getRootDbDialect() instanceof InformixDbDialect) {
    // return;
    // }
    // // alter the table to have column names that are not usually allowed
    // String rquote = getRootDbDialect().getIdentifierQuoteString();
    // String cquote = getClientDbDialect().getIdentifierQuoteString();
    // getServer().getSqlTemplate().update(alterKeyWordSql.replaceAll("\"",
    // rquote));
    // getServer().getSqlTemplate().update(alterKeyWordSql2.replaceAll("\"",
    // rquote));
    // getClient().getSqlTemplate().update(alterKeyWordSql.replaceAll("\"",
    // cquote));
    // getClient().getSqlTemplate().update(alterKeyWordSql2.replaceAll("\"",
    // cquote));
    //
    // getClientDbDialect().resetCachedTableModel();
    // getRootDbDialect().resetCachedTableModel();
    //
    // // enable the trigger for the table and update the client with
    // // configuration
    // getServer().getSqlTemplate().update(enableKeyWordTriggerSql);
    // getRootEngine().syncTriggers();
    // getRootEngine().reOpenRegistration(TestConstants.TEST_CLIENT_EXTERNAL_ID);
    // clientPull();
    //
    // getServer().getSqlTemplate().update(insertKeyWordSql.replaceAll("\"",
    // rquote), new
    // Object[] { 1, "x",
    // "a" });
    // clientPull();
    //
    // getServer().getSqlTemplate().update(updateKeyWordSql.replaceAll("\"",
    // rquote), new
    // Object[] { "y", "b",
    // 1 });
    // clientPull();
    //
    // List<Map<String, Object>> rowList =
    // getClient().getSqlTemplate().queryForList(
    // selectKeyWordSql.replaceAll("\"", cquote), new Object[] { 1 });
    // assertTrue(rowList.size() > 0);
    // Map<String, Object> columnMap = rowList.get(0);
    // assertEquals(columnMap.get("key word"), "y",
    // "Wrong key word value in table");
    // assertEquals(columnMap.get("case"), "b", "Wrong case value in table");
    // }
    //
    // @Test
    // public void testSyncColumnLevel() throws Exception {
    // logTestRunning();
    // int id = 1;
    // String[] columns = { "id", "string_value", "time_value", "date_value",
    // "bigint_value",
    // "decimal_value" };
    // Object[] values = new Object[] { id, "moredata",
    // getDate("2007-01-02 03:04:05"),
    // getDate("2007-02-01 05:03:04"), 600, new BigDecimal("34.10") };
    //
    // // Null out columns, change each column and sync one at a time
    // getClient().getSqlTemplate().update(nullSyncColumnLevelSql, new Object[]
    // { id });
    //
    // for (int i = 1; i < columns.length; i++) {
    // getServer().getSqlTemplate().update(replace("column", columns[i],
    // updateSyncColumnLevelSql),
    // new Object[] { values[i], id });
    // clientPull();
    // assertEquals(
    // getClient().getSqlTemplate().queryForInt(
    // replace("column", columns[i], selectSyncColumnLevelSql), new Object[] {
    // id, values[i] }), 1, "Table was not updated for column "
    // + columns[i]);
    // }
    // }
    //
    // @Test
    // public void testSyncColumnLevelTogether() throws Exception {
    // logTestRunning();
    // int id = 1;
    // String[] columns = { "id", "string_value", "time_value", "date_value",
    // "bigint_value",
    // "decimal_value" };
    // Object[] values = new Object[] { id, "moredata",
    // getDate("2008-01-02 03:04:05"),
    // getDate("2008-02-01 05:03:04"), 600, new BigDecimal("34.10") };
    //
    // // Null out columns, change all columns, sync all together
    // getServer().getSqlTemplate().update(nullSyncColumnLevelSql, new Object[]
    // { id });
    //
    // for (int i = 1; i < columns.length; i++) {
    // getServer().getSqlTemplate().update(replace("column", columns[i],
    // updateSyncColumnLevelSql),
    // new Object[] { values[i], id });
    // }
    // clientPull();
    // }
    //
    // @Test
    // public void testSyncColumnLevelFallback() throws Exception {
    // logTestRunning();
    // int id = 1;
    // String[] columns = { "id", "string_value", "time_value", "date_value",
    // "bigint_value",
    // "decimal_value" };
    // Object[] values = new Object[] { id, "fallback on insert",
    // getDate("2008-01-02 03:04:05"),
    // getDate("2008-02-01 05:03:04"), 600, new BigDecimal("34.10") };
    //
    // // Force a fallback of an update to insert the row
    // getClient().getSqlTemplate().update(deleteSyncColumnLevelSql, new
    // Object[] { id });
    // getServer().getSqlTemplate().update(replace("column", "string_value",
    // updateSyncColumnLevelSql),
    // new Object[] { values[1], id });
    // clientPull();
    //
    // for (int i = 1; i < columns.length; i++) {
    // assertEquals(
    // getClient().getSqlTemplate().queryForInt(
    // replace("column", columns[i], selectSyncColumnLevelSql), new Object[] {
    // id, values[i] }), 1, "Table was not updated for column "
    // + columns[i]);
    // }
    // }
    //
    // @Test
    // public void testSyncColumnLevelNoChange() throws Exception {
    // logTestRunning();
    // int id = 1;
    //
    // // Change a column to the same value, which on some systems will be
    // // captured
    // getServer().getSqlTemplate().update(replace("column", "string_value",
    // updateSyncColumnLevelSql),
    // new Object[] { "same", id });
    // getServer().getSqlTemplate().update(replace("column", "string_value",
    // updateSyncColumnLevelSql),
    // new Object[] { "same", id });
    // getClient().getSqlTemplate().update(deleteSyncColumnLevelSql, new
    // Object[] { id });
    // clientPull();
    // }
    //
    // @Test
    // public void testTargetTableNameSync() throws Exception {
    // logTestRunning();
    // assertEquals(0,
    // getClient().getSqlTemplate().queryForInt("select count(*) from test_target_table_b"));
    // getServer().getSqlTemplate().update("insert into test_target_table_a values('1','2')");
    // clientPull();
    // assertEquals(1,
    // getClient().getSqlTemplate().queryForInt("select count(*) from test_target_table_b"));
    // assertEquals(0,
    // getClient().getSqlTemplate().queryForInt("select count(*) from test_target_table_a"));
    // }
    //
    // @Test
    // public void testMaxRowsBeforeCommit() throws Exception {
    // logTestRunning();
    // IParameterService clientParameterService = (IParameterService)
    // getClient()
    // .getApplicationContext().getBean(Constants.PARAMETER_SERVICE);
    // long oldMaxRowsBeforeCommit = clientParameterService
    // .getLong(ParameterConstants.DATA_LOADER_MAX_ROWS_BEFORE_COMMIT);
    // clientParameterService.saveParameter(ParameterConstants.DATA_LOADER_MAX_ROWS_BEFORE_COMMIT,
    // 5);
    // int oldCount =
    // getClient().getSqlTemplate().queryForInt("select count(*) from one_column_table");
    // IStatisticManager statisticManager =
    // AppUtils.find(Constants.STATISTIC_MANAGER,
    // getClient());
    // statisticManager.flush();
    // getServer().getSqlTemplate().execute(new ConnectionCallback<Object>() {
    // public Object doInConnection(Connection con) throws SQLException,
    // DataAccessException {
    // con.setAutoCommit(false);
    // PreparedStatement stmt = con
    // .prepareStatement("insert into one_column_table values(?)");
    // for (int i = 400; i < 450; i++) {
    // stmt.setInt(1, i);
    // assertEquals(1, stmt.executeUpdate());
    // }
    // con.commit();
    // return null;
    // }
    // });
    // int count = 0;
    // do {
    // if (count > 0) {
    // logger.warn("If you see this message more than once the root database isn't respecting the fact that auto commit is set to false!");
    // }
    // count++;
    // } while (getClient().pull().wasDataProcessed());
    // int newCount =
    // getClient().getSqlTemplate().queryForInt("select count(*) from one_column_table");
    // assertEquals(50, newCount - oldCount);
    // clientParameterService.saveParameter(ParameterConstants.DATA_LOADER_MAX_ROWS_BEFORE_COMMIT,
    // oldMaxRowsBeforeCommit);
    // }
    //
    //
    // @Test
    // public void testSyncDisabled() {
    // logTestRunning();
    // clientPull();
    //
    // logTestRunning();
    //
    // Node clientIdentity = getClient().getNodeService().findIdentity();
    // Node clientNodeOnRoot = getRootEngine().getNodeService().findNode(
    // clientIdentity.getNodeId());
    //
    // assertEquals(true, clientNodeOnRoot.isSyncEnabled());
    //
    // // Update the heartbeat to be an old timestamp so the node will go
    // // offline
    // getServer().getSqlTemplate().update(makeHeartbeatOld, new Object[] {
    // clientIdentity.getNodeId() });
    // // Run the service to look for offline nodes
    // getRootEngine().getNodeService().checkForOfflineNodes();
    //
    // // Verify syncing was disabled for the node
    // clientNodeOnRoot =
    // getRootEngine().getNodeService().findNode(clientIdentity.getNodeId());
    // assertEquals(false, clientNodeOnRoot.isSyncEnabled());
    //
    // // Verify node security was deleted for the node
    // NodeSecurity clientNodeSecurity =
    // getRootEngine().getNodeService().findNodeSecurity(
    // clientIdentity.getNodeId());
    // assertNull(clientNodeSecurity);
    //
    // // A client pull will result in client getting a SyncDisabled return
    // // code which will cause the client's identity to be removed.
    // getClient().pull();
    // Node clientNodeAfterPull = getClient().getNodeService().findIdentity();
    // assertNull(clientNodeAfterPull);
    //
    // // Turn auto registration and reload on so the client will register and
    // // reload
    // getClient().getParameterService().saveParameter("auto.registration",
    // true);
    // getClient().getParameterService().saveParameter("auto.reload", true);
    // getRootEngine().getParameterService().saveParameter("auto.registration",
    // true);
    // getRootEngine().getParameterService().saveParameter("auto.reload", true);
    //
    // // A pull will cause the registration to occur and the node identify to
    // // be reestablished.
    // getClient().pull();
    // clientNodeAfterPull = getClient().getNodeService().findIdentity();
    // assertNotNull(clientNodeAfterPull);
    // }
    //
    // @Test
    // public void testClientNodeNotRegistered() throws Exception {
    // logTestRunning();
    //
    // Node clientIdentity = getClient().getNodeService().findIdentity();
    // Node clientNodeOnRoot = getRootEngine().getNodeService().findNode(
    // clientIdentity.getNodeId());
    // // Remove the client node from sym_node and sym_node_security
    // getServer().getSqlTemplate().update(deleteNode, new Object[] {
    // clientNodeOnRoot.getNodeId() });
    // getRootEngine().getNodeService().deleteNodeSecurity(clientNodeOnRoot.getNodeId());
    //
    // // Turn auto registration and reload on so the client will register and
    // // reload
    // getRootEngine().getParameterService().saveParameter("auto.registration",
    // true);
    // getRootEngine().getParameterService().saveParameter("auto.reload", true);
    //
    // turnOnNoKeysInUpdateParameter(true);
    // Date date = DateUtils.parseDate("2007-01-03", new String[] { "yyyy-MM-dd"
    // });
    // getClient().getSqlTemplate().update(insertOrderHeaderSql, new Object[] {
    // "99", 100,
    // null, date },
    // new int[] { Types.VARCHAR, Types.INTEGER, Types.CHAR, Types.DATE });
    // getClient().getSqlTemplate().update(insertOrderDetailSql, new Object[] {
    // "99", 1,
    // "STK", "110000099",
    // 3, 3.33 });
    //
    // // The push will cause a Registration Required return code which will
    // // cause the the client identify to be removed
    // // in order to force registration.
    // clientPush();
    //
    // // A pull will cause the registration to occur and the node identify to
    // // be
    // // reestablished.
    // clientPull();
    //
    // Node clientNodeAfterPull = getClient().getNodeService().findIdentity();
    // assertNotNull(clientNodeAfterPull);
    // }
    //
    // @Test
    // public void flushStatistics() {
    // logTestRunning();
    // IStatisticManager statMgr = (IStatisticManager)
    // getClient().getApplicationContext()
    // .getBean(Constants.STATISTIC_MANAGER);
    // statMgr.flush();
    //
    // statMgr = (IStatisticManager)
    // getRootEngine().getApplicationContext().getBean(
    // Constants.STATISTIC_MANAGER);
    // statMgr.flush();
    // }
    //
    // @Test
    // public void cleanupAfterTests() {
    // logTestRunning();
    // clientPull();
    // getClient().purge();
    // getRootEngine().purge();
    // }
    //
    // private String replace(String prop, String replaceWith, String
    // sourceString) {
    // return Strings.CS.replace(sourceString, "$(" + prop + ")", replaceWith);
    // }
    //
    // private Date getDate(String dateString) throws Exception {
    // if (!getClientDbDialect().isDateOverrideToTimestamp()
    // || !getRootDbDialect().isDateOverrideToTimestamp()) {
    // return DateUtils.parseDate(dateString.split(" ")[0], new String[] {
    // "yyyy-MM-dd" });
    // } else {
    // return DateUtils.parseDate(dateString, new String[] {
    // "yyyy-MM-dd HH:mm:ss" });
    // }
    // }
    //
}
