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
package org.jumpmind.symmetric.transport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.Test;

public class TransportUtilsTest {
    @Test
    public void testCountLoadedBatchesWithEmptyList() {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        int[] result = TransportUtils.countLoadedBatches(batchList);
        assertArrayEquals(new int[] { 0, 0 }, result);
    }

    @Test
    public void testCountLoadedBatchesWithNoLoadedBatches() {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        OutgoingBatch batch1 = new OutgoingBatch("node1", "channel1", Status.NE);
        batch1.setDataRowCount(10);
        OutgoingBatch batch2 = new OutgoingBatch("node1", "channel1", Status.SE);
        batch2.setDataRowCount(20);
        batchList.add(batch1);
        batchList.add(batch2);
        int[] result = TransportUtils.countLoadedBatches(batchList);
        assertArrayEquals(new int[] { 0, 0 }, result);
    }

    @Test
    public void testCountLoadedBatchesWithSomeLoadedBatches() {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        OutgoingBatch batch1 = new OutgoingBatch("node1", "channel1", Status.LD);
        batch1.setDataRowCount(10);
        OutgoingBatch batch2 = new OutgoingBatch("node1", "channel1", Status.NE);
        batch2.setDataRowCount(20);
        OutgoingBatch batch3 = new OutgoingBatch("node1", "channel1", Status.LD);
        batch3.setDataRowCount(15);
        batchList.add(batch1);
        batchList.add(batch2);
        batchList.add(batch3);
        int[] result = TransportUtils.countLoadedBatches(batchList);
        assertEquals(25, result[0]);
        assertEquals(2, result[1]);
    }

    @Test
    public void testCountLoadedBatchesWithAllLoadedBatches() {
        List<OutgoingBatch> batchList = new ArrayList<OutgoingBatch>();
        OutgoingBatch batch1 = new OutgoingBatch("node1", "channel1", Status.LD);
        batch1.setDataRowCount(5);
        OutgoingBatch batch2 = new OutgoingBatch("node1", "channel1", Status.LD);
        batch2.setDataRowCount(10);
        batchList.add(batch1);
        batchList.add(batch2);
        int[] result = TransportUtils.countLoadedBatches(batchList);
        assertEquals(15, result[0]);
        assertEquals(2, result[1]);
    }

    @Test
    public void testBuildReadyQueuesHeaderWhenSyncUseReadyQueuesDisabled() {
        IParameterService parameterService = mock(IParameterService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)).thenReturn(false);
        String result = TransportUtils.buildReadyQueuesHeader(parameterService, configurationService, outgoingBatchService, "node1");
        assertNull(result);
    }

    @Test
    public void testBuildReadyQueuesHeaderWhenOnlyOneQueue() {
        IParameterService parameterService = mock(IParameterService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)).thenReturn(true);
        when(configurationService.getQueues(false)).thenReturn(Arrays.asList("default"));
        String result = TransportUtils.buildReadyQueuesHeader(parameterService, configurationService, outgoingBatchService, "node1");
        assertNull(result);
    }

    @Test
    public void testBuildReadyQueuesHeaderWhenRouteOnExtractEnabled() {
        IParameterService parameterService = mock(IParameterService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)).thenReturn(true);
        when(configurationService.getQueues(false)).thenReturn(Arrays.asList("default", "queue1"));
        when(parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)).thenReturn(true);
        String result = TransportUtils.buildReadyQueuesHeader(parameterService, configurationService, outgoingBatchService, "node1");
        assertNull(result);
    }

    @Test
    public void testBuildReadyQueuesHeaderReturnsQueues() {
        IParameterService parameterService = mock(IParameterService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)).thenReturn(true);
        when(configurationService.getQueues(false)).thenReturn(Arrays.asList("default", "queue1", "queue2"));
        when(parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)).thenReturn(false);
        Collection<String> readyQueues = Arrays.asList("queue1", "queue2");
        when(outgoingBatchService.getReadyQueues("node1", false)).thenReturn(readyQueues);
        String result = TransportUtils.buildReadyQueuesHeader(parameterService, configurationService, outgoingBatchService, "node1");
        assertEquals("queue1,queue2", result);
    }

    @Test
    public void testBuildReadyQueuesHeaderWithEmptyQueues() {
        IParameterService parameterService = mock(IParameterService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.SYNC_USE_READY_QUEUES)).thenReturn(true);
        when(configurationService.getQueues(false)).thenReturn(Arrays.asList("default", "queue1"));
        when(parameterService.is(ParameterConstants.ROUTE_ON_EXTRACT)).thenReturn(false);
        Collection<String> readyQueues = new ArrayList<String>();
        when(outgoingBatchService.getReadyQueues("node1", false)).thenReturn(readyQueues);
        String result = TransportUtils.buildReadyQueuesHeader(parameterService, configurationService, outgoingBatchService, "node1");
        assertEquals("", result);
    }

    @Test
    public void testBuildPendingBatchCountsHeaderWhenHybridDisabled() {
        IParameterService parameterService = mock(IParameterService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.HYBRID_PUSH_PULL_ENABLED)).thenReturn(false);
        String result = TransportUtils.buildPendingBatchCountsHeader(parameterService, outgoingBatchService, "node1");
        assertNull(result);
    }

    @Test
    public void testBuildPendingBatchCountsHeaderWithNullMap() {
        IParameterService parameterService = mock(IParameterService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.HYBRID_PUSH_PULL_ENABLED)).thenReturn(true);
        when(outgoingBatchService.countOutgoingBatchesPendingByChannel("node1")).thenReturn(null);
        String result = TransportUtils.buildPendingBatchCountsHeader(parameterService, outgoingBatchService, "node1");
        assertNull(result);
    }

    @Test
    public void testBuildPendingBatchCountsHeaderWithEmptyMap() {
        IParameterService parameterService = mock(IParameterService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.HYBRID_PUSH_PULL_ENABLED)).thenReturn(true);
        when(outgoingBatchService.countOutgoingBatchesPendingByChannel("node1")).thenReturn(new HashMap<>());
        String result = TransportUtils.buildPendingBatchCountsHeader(parameterService, outgoingBatchService, "node1");
        assertNull(result);
    }

    @Test
    public void testBuildPendingBatchCountsHeaderWithBatchCounts() {
        IParameterService parameterService = mock(IParameterService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.HYBRID_PUSH_PULL_ENABLED)).thenReturn(true);
        Map<String, Integer> batchCounts = new LinkedHashMap<String, Integer>();
        batchCounts.put("channel1", 5);
        batchCounts.put("channel2", 10);
        when(outgoingBatchService.countOutgoingBatchesPendingByChannel("node1")).thenReturn(batchCounts);
        String result = TransportUtils.buildPendingBatchCountsHeader(parameterService, outgoingBatchService, "node1");
        assertEquals("channel1:5,channel2:10", result);
    }

    @Test
    public void testBuildPendingBatchCountsHeaderWithSingleChannel() {
        IParameterService parameterService = mock(IParameterService.class);
        IOutgoingBatchService outgoingBatchService = mock(IOutgoingBatchService.class);
        when(parameterService.is(ParameterConstants.HYBRID_PUSH_PULL_ENABLED)).thenReturn(true);
        Map<String, Integer> batchCounts = new LinkedHashMap<String, Integer>();
        batchCounts.put("channel1", 3);
        when(outgoingBatchService.countOutgoingBatchesPendingByChannel("node1")).thenReturn(batchCounts);
        String result = TransportUtils.buildPendingBatchCountsHeader(parameterService, outgoingBatchService, "node1");
        assertEquals("channel1:3", result);
    }
}
