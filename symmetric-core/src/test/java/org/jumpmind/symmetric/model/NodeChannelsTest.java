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
package org.jumpmind.symmetric.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.junit.jupiter.api.Test;

class NodeChannelsTest {
    private static final String DEFAULT_CHANNEL = "default";
    private static final String RELOAD_CHANNEL = "reload";
    private static final String NODE_1 = "store-1";
    private static final String NODE_2 = "store-2";

    @Test
    void addSuspendChannels_headerThenLocalListForAnotherNode_suspendsBothNodes() {
        NodeChannels local = new NodeChannels();
        local.addSuspendChannel(DEFAULT_CHANNEL, NODE_2);
        NodeChannels merged = new NodeChannels();
        merged.addSuspendChannels(NODE_1, DEFAULT_CHANNEL);
        merged.addSuspendChannels(local.getSuspendChannels());
        assertTrue(merged.isBatchSuspended(newBatch(NODE_1, DEFAULT_CHANNEL)));
        assertTrue(merged.isBatchSuspended(newBatch(NODE_2, DEFAULT_CHANNEL)));
    }

    @Test
    void addSuspendChannel_twoControlRowsOnOneChannel_suspendsBothNodes() {
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addSuspendChannel(DEFAULT_CHANNEL, NODE_1);
        nodeChannels.addSuspendChannel(DEFAULT_CHANNEL, NODE_2);
        assertEquals(Set.of(NODE_1, NODE_2), nodeChannels.getSuspendChannels().getByChannelId(DEFAULT_CHANNEL));
    }

    @Test
    void addIgnoreChannels_headersFromTwoNodes_ignoresBothNodes() {
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addIgnoreChannels(NODE_1, DEFAULT_CHANNEL + "," + RELOAD_CHANNEL);
        nodeChannels.addIgnoreChannels(NODE_2, DEFAULT_CHANNEL);
        assertTrue(nodeChannels.isBatchIgnored(newBatch(NODE_1, RELOAD_CHANNEL)));
        assertTrue(nodeChannels.isBatchIgnored(newBatch(NODE_2, DEFAULT_CHANNEL)));
        assertFalse(nodeChannels.isBatchIgnored(newBatch(NODE_2, RELOAD_CHANNEL)));
    }

    @Test
    void addSuspendChannels_emptyOrNullHeader_addsNoChannel() {
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addSuspendChannels(NODE_1, "");
        nodeChannels.addSuspendChannels(NODE_1, null);
        nodeChannels.addIgnoreChannels(NODE_1, ",");
        assertTrue(nodeChannels.getSuspendChannels().isEmpty());
        assertTrue(nodeChannels.getIgnoreChannels().isEmpty());
    }

    @Test
    void addSuspendChannels_nullMap_addsNothing() {
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addSuspendChannels((ChannelNodesMap) null);
        assertTrue(nodeChannels.getSuspendChannels().isEmpty());
    }

    @Test
    void getSuspendChannelsAsString_nodeOnTwoChannels_returnsSortedChannelsForThatNodeOnly() {
        NodeChannels nodeChannels = new NodeChannels();
        nodeChannels.addSuspendChannel(RELOAD_CHANNEL, NODE_1);
        nodeChannels.addSuspendChannel(DEFAULT_CHANNEL, NODE_1);
        nodeChannels.addSuspendChannel(DEFAULT_CHANNEL, NODE_2);
        assertEquals(DEFAULT_CHANNEL + "," + RELOAD_CHANNEL, nodeChannels.getSuspendChannelsAsString(NODE_1));
        assertEquals(DEFAULT_CHANNEL, nodeChannels.getSuspendChannelsAsString(NODE_2));
        assertEquals("", nodeChannels.getIgnoreChannelsAsString(NODE_1));
    }

    private static OutgoingBatch newBatch(String nodeId, String channelId) {
        return new OutgoingBatch(nodeId, channelId, Status.NE);
    }
}
