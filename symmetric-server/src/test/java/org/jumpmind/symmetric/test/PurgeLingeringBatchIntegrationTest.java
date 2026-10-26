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

import static org.junit.Assert.assertEquals;

import java.time.Duration;

import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.TestConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PurgeLingeringBatchIntegrationTest extends AbstractPurgeIntegrationTest {
    private static final String OFFLINE_EXTERNAL_ID = "00002";

    @Test
    public void test03KeepDataHeldByOutstandingBatchOfAnotherNode() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            String offlineNodeId = addOfflineNode();
            routeToEachNodeInSeparateBatches();
            prepareForPurge();
            markStuckWithoutEvents(insertAndRoute(101));
            long heldDataId = insertAndRoute(102);
            assertEquals(2, countBatches(heldDataId));
            setBatchesOk(insertAndRoute(103));
            setBatchesOk(insertAndRoute(104));
            purgeOutgoing();
            assertEquals(1, countData(heldDataId));
            setBatchOk(heldDataId, TestConstants.TEST_CLIENT_EXTERNAL_ID);
            setBatchesOk(insertAndRoute(105));
            insertAndRoute(106);
            purgeOutgoing();
            assertEquals(1, countOutstandingBatches(heldDataId, offlineNodeId));
            assertEquals(1, countData(heldDataId));
        });
    }

    @Test
    public void test04PurgeDataWhenAllBatchesComplete() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            long heldDataId = insertAndRoute(201);
            assertEquals(2, countBatches(heldDataId));
            setBatchesOk(insertAndRoute(202));
            setBatchesOk(insertAndRoute(203));
            purgeOutgoing();
            assertEquals(1, countData(heldDataId));
            setBatchesOk(heldDataId);
            setBatchesOk(insertAndRoute(204));
            long lastDataId = insertAndRoute(205);
            purgeOutgoing();
            assertEquals(0, countData(heldDataId));
            assertEquals(0, getServer().getSqlTemplate().queryForInt("select count(*) from sym_data where data_id > ?", lastDataId));
        });
    }

    protected String addOfflineNode() {
        ISymmetricEngine server = getServer();
        server.openRegistration(TestConstants.TEST_CLIENT_NODE_GROUP, OFFLINE_EXTERNAL_ID);
        ISqlTemplate template = server.getSqlTemplate();
        template.update("update sym_node set sync_enabled = 1 where external_id = ?", OFFLINE_EXTERNAL_ID);
        return template.queryForString("select node_id from sym_node where external_id = ?", OFFLINE_EXTERNAL_ID);
    }

    protected void routeToEachNodeInSeparateBatches() {
        ISymmetricEngine server = getServer();
        server.getParameterService().saveParameter(ParameterConstants.ROUTING_USE_COMMON_GROUPS, false, "test");
        server.getSqlTemplate().update("update sym_router set router_type = 'column', router_expression = 'ID!=0' where router_id = '1000'");
        server.clearCaches();
    }

    protected int countBatches(long dataId) {
        return getServer().getSqlTemplate().queryForInt("select count(distinct batch_id) from sym_data_event where data_id = ?", dataId);
    }

    protected int countOutstandingBatches(long dataId, String nodeId) {
        return getServer().getSqlTemplate().queryForInt("select count(*) from sym_outgoing_batch b inner join sym_data_event e on e.batch_id = b.batch_id "
                + "where e.data_id = ? and b.node_id = ? and b.status <> ?", dataId, nodeId, OutgoingBatch.Status.OK.name());
    }
}
