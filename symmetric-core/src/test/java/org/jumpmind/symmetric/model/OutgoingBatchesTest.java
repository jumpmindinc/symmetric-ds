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
package org.jumpmind.symmetric.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.junit.jupiter.api.Test;

public class OutgoingBatchesTest {
    public static final String[] nodeIds = { "0001", "0002", "0003", "0004", "0005" };

    @Test
    public void testBasicFunctions() {
        OutgoingBatches batches = buildSampleBatches("testChannel", 5);
        assertNotNull(batches);
        assertEquals(25, batches.getBatches().size());
        assertEquals(0, batches.getActiveChannels().size());
        batches.addActiveChannel(new NodeChannel("testChannel1"));
        batches.addActiveChannel(new NodeChannel("testChannel2"));
        assertEquals(2, batches.getActiveChannels().size());
    }

    @Test
    public void testBasicGetters() {
        OutgoingBatches batches = buildSampleBatches("testChannel", 5);
        assertNotNull(batches);
        assertEquals(25, batches.getBatches().size());
        assertEquals(0, batches.getActiveChannels().size());
        List<OutgoingBatch> batchList = batches.getBatchesForChannel("testChannel2");
        assertEquals(5, batchList.size());
        int i = 3;
        for (OutgoingBatch b : batchList) {
            assertEquals(b.getChannelId(), "testChannel2");
            assertEquals(i, b.getBatchId());
            i += 5;
        }
        batchList = batches.getBatchesForChannel(new Channel("testChannel1", 1));
        assertEquals(5, batchList.size());
        i = 2;
        for (OutgoingBatch b : batchList) {
            assertEquals(b.getChannelId(), "testChannel1");
            assertEquals(i, b.getBatchId());
            i += 5;
        }
        Set<String> channels = new HashSet<String>();
        channels.add("testChannel2");
        channels.add("testChannel3");
        channels.add("testChannel4");
        batchList = batches.getBatchesForChannels(channels);
        assertEquals(15, batchList.size());
    }

    @Test
    public void testWindowGetter() {
        // TODO
    }

    @Test
    public void testFilters() {
        OutgoingBatches batches = buildSampleBatches("testChannel", 5);
        assertNotNull(batches);
        assertEquals(25, batches.getBatches().size());
        assertEquals(0, batches.getActiveChannels().size());
        batches.filterBatchesForChannel("testChannel3");
        assertEquals(20, batches.getBatches().size());
        for (OutgoingBatch b : batches.getBatches()) {
            assertNotEquals(b.getChannelId(), "testChannel3");
        }
        batches.filterBatchesForChannel(new Channel("testChannel4", 1));
        assertEquals(15, batches.getBatches().size());
        for (OutgoingBatch b : batches.getBatches()) {
            assertNotEquals(b.getChannelId(), "testChannel3");
            assertNotEquals(b.getChannelId(), "testChannel4");
        }
        Set<String> channels = new HashSet<String>();
        channels.add("testChannel2");
        channels.add("testChannel5");
        batches.filterBatchesForChannels(channels);
        assertEquals(10, batches.getBatches().size());
        for (OutgoingBatch b : batches.getBatches()) {
            assertTrue(b.getChannelId().equals("testChannel1") || b.getChannelId().equals("testChannel0"));
        }
        batches = buildSampleBatches("testChannel", 5);
        batches.addActiveChannel(new NodeChannel("testChannel2"));
        batches.addActiveChannel(new NodeChannel("testChannel3"));
        batches.addActiveChannel(new NodeChannel("testChannel4"));
        batches.filterBatchesForInactiveChannels();
        assertEquals(15, batches.getBatches().size());
        for (OutgoingBatch b : batches.getBatches()) {
            assertTrue(b.getChannelId().equals("testChannel2") || b.getChannelId().equals("testChannel3")
                    || b.getChannelId().equals("testChannel4"));
        }
        batches = buildSampleBatches("testChannel", 5);
        NodeChannels nodeChannels = new NodeChannels();
        ChannelNodesMap channelNodesMap = new ChannelNodesMap();
        channelNodesMap.put("testChannel1", Collections.singleton(nodeIds[0]));
        nodeChannels.addSuspendChannels(channelNodesMap);
        batches.filterSuspendedBatches(nodeChannels);
        assertEquals(24, batches.getBatches().size());
        batches = buildSampleBatches("testChannel", 5);
        nodeChannels = new NodeChannels();
        channelNodesMap.put("testChannel2", Set.of(nodeIds[0], nodeIds[1], nodeIds[2]));
        nodeChannels.addIgnoreChannels(channelNodesMap);
        batches.filterIgnoredBatches(nodeChannels);
        assertEquals(21, batches.getBatches().size());
    }

    @Test
    public void testChannelSortingNoErrors() {
        List<NodeChannel> channels = new ArrayList<NodeChannel>();
        NodeChannel channelA = new NodeChannel("a");
        channelA.setProcessingOrder(1);
        NodeChannel channelB = new NodeChannel("b");
        channelB.setProcessingOrder(2);
        NodeChannel channelC = new NodeChannel("c");
        channelC.setProcessingOrder(3);
        channels.add(channelC);
        channels.add(channelB);
        channels.add(channelA);
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        OutgoingBatch batch1 = new OutgoingBatch("1", channelA.getChannelId(), Status.NE);
        batches.add(batch1);
        OutgoingBatch batch2 = new OutgoingBatch("1", channelB.getChannelId(), Status.NE);
        batches.add(batch2);
        OutgoingBatch batch3 = new OutgoingBatch("1", channelC.getChannelId(), Status.NE);
        batches.add(batch3);
        OutgoingBatches outgoingBatches = new OutgoingBatches(batches);
        outgoingBatches.sortChannels(channels);
        assertEquals(channelA, channels.get(0));
        assertEquals(channelB, channels.get(1));
        assertEquals(channelC, channels.get(2));
    }

    @Test
    public void testChannelSortingOneErrors() {
        List<NodeChannel> channels = new ArrayList<NodeChannel>();
        NodeChannel channelA = new NodeChannel("a");
        channelA.setProcessingOrder(1);
        NodeChannel channelB = new NodeChannel("b");
        channelB.setProcessingOrder(2);
        NodeChannel channelC = new NodeChannel("c");
        channelC.setProcessingOrder(3);
        channels.add(channelC);
        channels.add(channelB);
        channels.add(channelA);
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        OutgoingBatch batch1 = new OutgoingBatch("1", channelA.getChannelId(), Status.NE);
        batch1.setStatus(OutgoingBatch.Status.ER);
        batch1.setErrorFlag(true);
        batches.add(batch1);
        OutgoingBatch batch2 = new OutgoingBatch("1", channelB.getChannelId(), Status.NE);
        batches.add(batch2);
        OutgoingBatch batch3 = new OutgoingBatch("1", channelC.getChannelId(), Status.NE);
        batches.add(batch3);
        OutgoingBatches outgoingBatches = new OutgoingBatches(batches);
        outgoingBatches.sortChannels(channels);
        assertEquals(channelB, channels.get(0));
        assertEquals(channelC, channels.get(1));
        assertEquals(channelA, channels.get(2));
    }

    @Test
    public void testChannelSortingTwoErrors() {
        List<NodeChannel> channels = new ArrayList<NodeChannel>();
        NodeChannel channelA = new NodeChannel("a");
        channelA.setProcessingOrder(1);
        NodeChannel channelB = new NodeChannel("b");
        channelB.setProcessingOrder(2);
        NodeChannel channelC = new NodeChannel("c");
        channelC.setProcessingOrder(3);
        channels.add(channelC);
        channels.add(channelB);
        channels.add(channelA);
        Date startTime = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(startTime);
        // Channel A is in error and has oldest batch
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        OutgoingBatch batch1 = new OutgoingBatch("1", channelA.getChannelId(), Status.NE);
        batch1.setStatus(OutgoingBatch.Status.ER);
        batch1.setErrorFlag(true);
        batch1.setLastUpdatedTime(cal.getTime());
        batches.add(batch1);
        // Channel B is in error and has newest batch
        cal.setTime(startTime);
        cal.add(Calendar.SECOND, 1);
        OutgoingBatch batch2 = new OutgoingBatch("1", channelB.getChannelId(), Status.NE);
        batch2.setStatus(OutgoingBatch.Status.ER);
        batch2.setErrorFlag(true);
        batch2.setLastUpdatedTime(cal.getTime());
        batches.add(batch2);
        // Channel C is fine
        OutgoingBatch batch3 = new OutgoingBatch("1", channelC.getChannelId(), Status.NE);
        batches.add(batch3);
        OutgoingBatches outgoingBatches = new OutgoingBatches(batches);
        outgoingBatches.sortChannels(channels);
        // Order should be non-error channels, followed by error channels ordered by oldest first
        assertEquals(channelC, channels.get(0));
        assertEquals(channelA, channels.get(1));
        assertEquals(channelB, channels.get(2));
        // Make channel A look like it just tried, so it's newest
        cal.setTime(startTime);
        cal.add(Calendar.SECOND, 2);
        batch1.setLastUpdatedTime(cal.getTime());
        outgoingBatches.sortChannels(channels);
        assertEquals(channelC, channels.get(0));
        assertEquals(channelB, channels.get(1));
        assertEquals(channelA, channels.get(2));
        // Additional batch on channel A that is old
        cal.setTime(startTime);
        cal.add(Calendar.SECOND, -1);
        OutgoingBatch batch4 = new OutgoingBatch("1", channelA.getChannelId(), Status.NE);
        batch4.setStatus(OutgoingBatch.Status.ER);
        batch4.setErrorFlag(true);
        batch4.setLastUpdatedTime(cal.getTime());
        batches.add(batch4);
        // Additional batch on channel B that is oldest
        cal.setTime(startTime);
        cal.add(Calendar.SECOND, -2);
        OutgoingBatch batch5 = new OutgoingBatch("1", channelB.getChannelId(), Status.NE);
        batch5.setStatus(OutgoingBatch.Status.ER);
        batch5.setErrorFlag(true);
        batch5.setLastUpdatedTime(cal.getTime());
        batches.add(batch5);
        // Make channel B look like it just tried, so it's newest
        cal.setTime(startTime);
        cal.add(Calendar.SECOND, 3);
        batch2.setLastUpdatedTime(cal.getTime());
        // Sorting should get maximum last update time for each channel and sort error channels by oldest first
        outgoingBatches.sortChannels(channels);
        assertEquals(channelC, channels.get(0));
        assertEquals(channelA, channels.get(1));
        assertEquals(channelB, channels.get(2));
        // Make channel A look like it just tried, so it's newest
        cal.setTime(startTime);
        cal.add(Calendar.SECOND, 4);
        batch1.setLastUpdatedTime(cal.getTime());
        // Sorting should get maximum last update time for each channel and sort error channels by oldest first
        outgoingBatches.sortChannels(channels);
        assertEquals(channelC, channels.get(0));
        assertEquals(channelB, channels.get(1));
        assertEquals(channelA, channels.get(2));
    }

    protected OutgoingBatches buildSampleBatches(String channelId, int batchCount) {
        OutgoingBatches outgoingBatches = new OutgoingBatches();
        List<OutgoingBatch> batches = new ArrayList<OutgoingBatch>();
        int batchId = 1;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                OutgoingBatch b = new OutgoingBatch(nodeIds[i], "testChannel" + j, Status.NE);
                b.setBatchId(batchId++);
                batches.add(b);
            }
        }
        outgoingBatches.setBatches(batches);
        return outgoingBatches;
    }
}