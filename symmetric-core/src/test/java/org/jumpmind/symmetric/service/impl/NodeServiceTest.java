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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeServiceTest {
    private static final String DELETED_NODE_ID = "deleted";
    private static final String OTHER_NODE_ID = "other";
    private static final long WORKER_TIMEOUT_SECONDS = 10;
    private final List<ProcessInfo> processInfos = new ArrayList<>();
    private final List<Thread> workers = new ArrayList<>();
    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect dialect = mock(ISymmetricDialect.class);
        IStatisticManager statisticManager = mock(IStatisticManager.class);
        when(dialect.getPlatform()).thenReturn(mock(IDatabasePlatform.class));
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        when(engine.getCacheManager()).thenReturn(mock(ICacheManager.class));
        when(engine.getExtensionService()).thenReturn(mock(IExtensionService.class));
        when(engine.getSecurityService()).thenReturn(mock(ISecurityService.class));
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(statisticManager.getProcessInfos()).thenReturn(processInfos);
        nodeService = new NodeService(engine);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        Thread.interrupted();
        for (Thread worker : workers) {
            worker.interrupt();
            worker.join(TimeUnit.SECONDS.toMillis(WORKER_TIMEOUT_SECONDS));
        }
    }

    @Test
    void testInterruptThreadsSkipsCallingThread() {
        processInfos.add(new ProcessInfo(new ProcessInfoKey("import", DELETED_NODE_ID, ProcessType.MANUAL_LOAD)));
        nodeService.interruptThreads(DELETED_NODE_ID);
        assertEquals(false, Thread.currentThread().isInterrupted());
    }

    @Test
    void testInterruptThreadsInterruptsOtherThreadOfTargetNode() throws InterruptedException {
        CountDownLatch interrupted = startWorkerFor(new ProcessInfoKey("source", DELETED_NODE_ID, ProcessType.PUSH_JOB_EXTRACT));
        nodeService.interruptThreads(DELETED_NODE_ID);
        assertEquals(true, interrupted.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    void testInterruptThreadsInterruptsOtherThreadOfSourceNode() throws InterruptedException {
        CountDownLatch interrupted = startWorkerFor(new ProcessInfoKey(DELETED_NODE_ID, "target", ProcessType.PULL_HANDLER_EXTRACT));
        nodeService.interruptThreads(DELETED_NODE_ID);
        assertEquals(true, interrupted.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    void testInterruptThreadsLeavesOtherNodesAlone() throws InterruptedException {
        CountDownLatch interrupted = startWorkerFor(new ProcessInfoKey("source", OTHER_NODE_ID, ProcessType.PUSH_JOB_EXTRACT));
        nodeService.interruptThreads(DELETED_NODE_ID);
        assertEquals(false, interrupted.await(200, TimeUnit.MILLISECONDS));
    }

    private CountDownLatch startWorkerFor(ProcessInfoKey key) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(WORKER_TIMEOUT_SECONDS * 3));
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        }, "node-service-test-worker");
        workers.add(worker);
        ProcessInfo info = new ProcessInfo(key);
        info.setThread(worker);
        processInfos.add(info);
        worker.start();
        started.await(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return interrupted;
    }
}
