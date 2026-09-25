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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IClusterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusterLockRefreshListenerTest {
    private IClusterService clusterService;
    private DataContext context;
    private ClusterLockRefreshListener listener;

    @BeforeEach
    void setUp() {
        clusterService = mock(IClusterService.class);
        context = mock(DataContext.class);
        listener = new ClusterLockRefreshListener(clusterService);
    }

    @Test
    void testBeforeBatchStarted() {
        assertTrue(listener.beforeBatchStarted(context));
        verifyNoInteractions(clusterService);
    }

    @Test
    void testBatchProgressUpdate_refreshesTheInitialLoadExtractLock() {
        listener.batchProgressUpdate(context);
        verify(clusterService).refreshLock(ClusterConstants.INITIAL_LOAD_EXTRACT);
    }

    @Test
    void testBatchSuccessful_doesNotTouchTheLock() {
        listener.afterBatchStarted(context);
        listener.beforeBatchEnd(context);
        listener.batchSuccessful(context);
        listener.dataRowProcessed();
        verifyNoInteractions(clusterService);
    }

    @Test
    void testBatchInError_doesNotTouchTheLock() {
        listener.batchInError(context, new RuntimeException("boom"));
        verifyNoInteractions(clusterService);
    }
}
