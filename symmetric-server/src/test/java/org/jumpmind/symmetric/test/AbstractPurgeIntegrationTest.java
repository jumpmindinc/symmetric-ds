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
import static org.junit.Assert.assertTrue;

import java.time.Duration;

import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.TestConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class AbstractPurgeIntegrationTest extends AbstractIntegrationTest {
    protected static final String TEST_TABLE = "test_triggers_table";
    private static final String COUNT_NOT_OK_BATCHES_SQL = "select count(*) from sym_outgoing_batch where status <> ?";
    private static final String UPDATE_BATCH_STATUS_SQL = "update sym_outgoing_batch set status = ? where batch_id in "
            + "(select batch_id from sym_data_event where data_id = ?)";
    static {
        System.setProperty("h2.baseDir", "./");
    }

    @Test
    public void test01CreateServer() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            ISymmetricEngine server = getServer();
            server.getParameterService().saveParameter(ParameterConstants.FILE_SYNC_ENABLE, false, "unit_test");
            checkForFailedTriggers(true, false);
        });
    }

    @Test
    public void test02RegisterClientWithRoot() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            getServer().openRegistration(TestConstants.TEST_CLIENT_NODE_GROUP, TestConstants.TEST_CLIENT_EXTERNAL_ID);
            getClient().start();
            getClient().getParameterService().saveParameter(ParameterConstants.FILE_SYNC_ENABLE, false, "unit_test");
            clientPull();
            assertTrue("The client did not register", getClient().isRegistered());
        });
    }

    protected void prepareForPurge() {
        ISymmetricEngine server = getServer();
        server.getParameterService().saveParameter(ParameterConstants.PURGE_RETENTION_MINUTES, -60 * 24, "test");
        server.getParameterService().saveParameter(ParameterConstants.PURGE_STRANDED_DATA_RECAPTURE_ENABLED, true, "test");
        clientPull();
        server.route();
        server.getSqlTemplate().update("update sym_outgoing_batch set status = ? where node_id <> ?", OutgoingBatch.Status.OK.name(),
                TestConstants.TEST_CLIENT_EXTERNAL_ID);
        assertEquals(0, server.getSqlTemplate().queryForInt(COUNT_NOT_OK_BATCHES_SQL, OutgoingBatch.Status.OK.name()));
        purgeOutgoing();
    }

    protected void purgeOutgoing() {
        getServer().getPurgeService().purgeOutgoing(true);
    }

    protected long insertAndRoute(int id) {
        serverTestService.insertIntoTestTriggerTable(new Object[] { id, "purge" + id, "first pass" });
        getServer().route();
        ISqlTemplate template = getServer().getSqlTemplate();
        long dataId = template.queryForLong("select max(data_id) from sym_data where table_name = ?", TEST_TABLE);
        assertTrue("Data " + dataId + " was not routed", template.queryForInt("select count(*) from sym_data_event where data_id = ?", dataId) > 0);
        return dataId;
    }

    protected void markStuckWithoutEvents(long dataId) {
        ISqlTemplate template = getServer().getSqlTemplate();
        template.update(UPDATE_BATCH_STATUS_SQL, OutgoingBatch.Status.RT.name(), dataId);
        template.update("delete from sym_data_event where data_id = ?", dataId);
        template.update("delete from sym_data where data_id = ?", dataId);
    }

    protected void setBatchesOk(long dataId) {
        getServer().getSqlTemplate().update(UPDATE_BATCH_STATUS_SQL, OutgoingBatch.Status.OK.name(), dataId);
    }

    protected void setBatchOk(long dataId, String nodeId) {
        getServer().getSqlTemplate().update(UPDATE_BATCH_STATUS_SQL + " and node_id = ?", OutgoingBatch.Status.OK.name(), dataId, nodeId);
    }

    protected int countData(long dataId) {
        return getServer().getSqlTemplate().queryForInt("select count(*) from sym_data where data_id = ?", dataId);
    }
}
