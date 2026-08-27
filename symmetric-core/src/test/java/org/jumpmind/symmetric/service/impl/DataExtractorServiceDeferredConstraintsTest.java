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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataExtractorServiceDeferredConstraintsTest {
    private static final long LOAD_ID = 7929;
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ISqlTemplate sqlTemplateDirty;
    private INodeService nodeService;
    private TestableDataExtractorService service;
    private Node targetNode;

    static class TestableDataExtractorService extends DataExtractorService {
        AtomicInteger sendPasses = new AtomicInteger();

        TestableDataExtractorService(ISymmetricEngine engine) {
            super(engine);
        }

        @Override
        public List<ExtractRequest> getTablesForExtractByLoadId(long loadId) {
            sendPasses.incrementAndGet();
            return Collections.emptyList();
        }
    }

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getSqlReplacementTokens()).thenReturn(new HashMap<String, String>());
        when(platform.getSqlTemplate()).thenReturn(mock(ISqlTemplate.class));
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        IDataService dataService = mock(IDataService.class);
        nodeService = mock(INodeService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getDataService()).thenReturn(dataService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getNodeId()).thenReturn("source");
        service = new TestableDataExtractorService(engine);
        targetNode = new Node();
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(true);
        TableReloadRequest createTableLoad = new TableReloadRequest();
        createTableLoad.setCreateTable(true);
        when(dataService.getTableReloadRequest(anyLong())).thenReturn(createTableLoad);
        when(sqlTemplateDirty.queryForLong(any(), any(), any())).thenReturn(0L);
    }

    @Test
    void checkSendDeferredForeignKeys_deferConstraintsDisabled_neverSends() {
        when(parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)).thenReturn(false);
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(0, service.sendPasses.get(), "nothing should be sent when constraint deferral is off");
    }

    @Test
    void checkSendDeferredForeignKeys_extractThreadsStillRunning_skipsWithoutConsumingClaim() {
        when(sqlTemplateDirty.queryForLong(any(), any(), any())).thenReturn(3L).thenReturn(0L);
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(0, service.sendPasses.get(), "should skip while other extract threads are incomplete");
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(1, service.sendPasses.get(),
                "a skipped call must not consume the claim; the last thread to finish still has to send");
    }

    @Test
    void checkSendDeferredForeignKeys_calledAgainForSameLoad_sendsOnlyOnce() {
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(1, service.sendPasses.get(), "second call for the same load must be a no-op");
    }

    @Test
    void checkSendDeferredForeignKeys_differentLoads_eachLoadSendsOnce() {
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        service.checkSendDeferredForeignKeys(LOAD_ID + 1, targetNode);
        assertEquals(2, service.sendPasses.get(), "the claim is per load, not global");
    }

    @Test
    void checkSendDeferredForeignKeys_concurrentExtractThreads_exactlyOneSends() throws Exception {
        int threadCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                    service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
                } catch (Exception e) {
                    // fall through; the assert below will report the missing pass
                } finally {
                    done.countDown();
                }
            }).start();
        }
        done.await();
        assertEquals(1, service.sendPasses.get(),
                "all racing extract threads see zero incomplete requests, but only one may send (SYM-7929)");
    }

    @Test
    void handleLoadTerminated_releasesClaim_soANewLoadRunCanSendAgain() {
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(1, service.sendPasses.get());
        TableReloadStatus status = new TableReloadStatus();
        status.setLoadId((int) LOAD_ID);
        status.setFullLoad(true);
        status.setCompleted(true);
        service.handleLoadTerminated(mock(ISqlTransaction.class), status, "target");
        service.checkSendDeferredForeignKeys(LOAD_ID, targetNode);
        assertEquals(2, service.sendPasses.get(),
                "terminating the load must release its claim so a later load with the same id could send");
    }
}
