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
package org.jumpmind.symmetric.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.BatchAckResult;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.impl.AcknowledgeService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.statistic.StatisticManager;
import org.jumpmind.symmetric.web.AckUriHandler;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AckUriHandlerTest {
    static final long BATCH_ID = 100;
    static final String NODE_ID = "NODE1";
    static final String CHANNEL_ID = "default";
    ISymmetricEngine engine;
    OutgoingBatch batch;
    HttpServletRequest request;
    HttpServletResponse response;
    Map<String, String[]> paramMap;

    @BeforeEach
    public void setup() throws IOException {
        engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform databasePlatform = mock(IDatabasePlatform.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        IRegistrationService registrationService = mock(IRegistrationService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        IConfigurationService configService = mock(IConfigurationService.class);
        IStatisticManager statMan = mock(StatisticManager.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        ISqlTransaction sqlTransaction = mock(ISqlTransaction.class);
        batch = new OutgoingBatch(NODE_ID, CHANNEL_ID, Status.LD);
        batch.setBatchId(BATCH_ID);
        when(outgoingBatchService.findOutgoingBatch(BATCH_ID, NODE_ID)).thenReturn(batch);
        when(databasePlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(symmetricDialect.getPlatform()).thenReturn(databasePlatform);
        when(engine.getDatabasePlatform()).thenReturn(databasePlatform);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getRegistrationService()).thenReturn(registrationService);
        when(engine.getOutgoingBatchService()).thenReturn(outgoingBatchService);
        when(engine.getConfigurationService()).thenReturn(configService);
        when(engine.getStatisticManager()).thenReturn(statMan);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        paramMap = new HashMap<String, String[]>();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getParameterMap()).thenReturn(paramMap);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    /**
     * Test that URL parameters are correctly mapped into a BatchInfo object
     */
    @Test
    public void testBatchInfoResend() throws Exception {
        paramMap.put(WebConstants.ACK_BATCH_NAME + BATCH_ID, new String[] { WebConstants.ACK_BATCH_RESEND });
        paramMap.put(WebConstants.ACK_NODE_ID + BATCH_ID, new String[] { NODE_ID });
        IAcknowledgeService ackService = new AcknowledgeService(engine) {
            public BatchAckResult ack(BatchAck batch) {
                assertEquals(BATCH_ID, batch.getBatchId());
                assertEquals(NODE_ID, batch.getNodeId());
                assertTrue(batch.isResend());
                assertTrue(batch.isOk());
                assertFalse(batch.isIgnored());
                return null;
            }
        };
        AckUriHandler uriHandler = new AckUriHandler(engine.getParameterService(), ackService);
        uriHandler.handle(request, response);
    }

    /**
     * Test that URL parameters are correctly mapped into a BatchInfo object
     */
    @Test
    public void testBatchInfoError() throws Exception {
        paramMap.put(WebConstants.ACK_BATCH_NAME + BATCH_ID, new String[] { "1234" });
        paramMap.put(WebConstants.ACK_NODE_ID + BATCH_ID, new String[] { NODE_ID });
        paramMap.put(WebConstants.ACK_SQL_STATE + BATCH_ID, new String[] { "LOCKWAIT" });
        paramMap.put(WebConstants.ACK_SQL_CODE + BATCH_ID, new String[] { "-911" });
        paramMap.put(WebConstants.ACK_SQL_MESSAGE + BATCH_ID, new String[] { "Lock timeout" });
        IAcknowledgeService ackService = new AcknowledgeService(engine) {
            public BatchAckResult ack(BatchAck batch) {
                assertEquals(BATCH_ID, batch.getBatchId());
                assertEquals(NODE_ID, batch.getNodeId());
                assertFalse(batch.isResend());
                assertFalse(batch.isOk());
                assertFalse(batch.isIgnored());
                assertEquals(1234, batch.getErrorLine());
                assertEquals("LOCKWAIT", batch.getSqlState());
                assertEquals(-911, batch.getSqlCode());
                assertEquals("Lock timeout", batch.getSqlMessage());
                return null;
            }
        };
        AckUriHandler uriHandler = new AckUriHandler(engine.getParameterService(), ackService);
        uriHandler.handle(request, response);
    }

    /**
     * Test that Ack Service correctly updates outgoing batch table
     */
    @Test
    public void testOutgoingBatchResend() throws Exception {
        paramMap.put(WebConstants.ACK_BATCH_NAME + BATCH_ID, new String[] { WebConstants.ACK_BATCH_RESEND });
        paramMap.put(WebConstants.ACK_NODE_ID + BATCH_ID, new String[] { NODE_ID });
        IAcknowledgeService ackService = new AcknowledgeService(engine);
        AckUriHandler uriHandler = new AckUriHandler(engine.getParameterService(), ackService);
        uriHandler.handle(request, response);
        assertEquals(BATCH_ID, batch.getBatchId());
        assertEquals(NODE_ID, batch.getNodeId());
        assertEquals(Status.RS, batch.getStatus());
        assertFalse(batch.isErrorFlag());
    }
}
