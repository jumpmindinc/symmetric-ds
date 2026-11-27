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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ChannelNodesMapTest {
    private static final String CHANNEL = "default";
    private static final String OTHER_CHANNEL = "reload";
    private static final String NODE_1 = "store-1";
    private static final String NODE_2 = "store-2";

    @Test
    void add_twoNodesOnOneChannel_keepsBothNodes() {
        ChannelNodesMap map = new ChannelNodesMap();
        map.add(CHANNEL, NODE_1);
        map.add(CHANNEL, NODE_2);
        assertEquals(Set.of(NODE_1, NODE_2), map.getByChannelId(CHANNEL));
    }

    @Test
    void addAll_otherMapHasSameChannelForAnotherNode_keepsBothNodes() {
        ChannelNodesMap map = new ChannelNodesMap();
        map.add(CHANNEL, NODE_1);
        ChannelNodesMap other = new ChannelNodesMap();
        other.add(CHANNEL, NODE_2);
        other.add(OTHER_CHANNEL, NODE_2);
        map.addAll(other);
        assertEquals(Set.of(NODE_1, NODE_2), map.getByChannelId(CHANNEL));
        assertEquals(Set.of(NODE_2), map.getByChannelId(OTHER_CHANNEL));
    }

    @Test
    void addAll_laterChangesToOtherMap_doNotLeakIntoThisMap() {
        ChannelNodesMap map = new ChannelNodesMap();
        ChannelNodesMap other = new ChannelNodesMap();
        other.add(CHANNEL, NODE_1);
        map.addAll(other);
        other.add(CHANNEL, NODE_2);
        assertEquals(Set.of(NODE_1), map.getByChannelId(CHANNEL));
    }

    @Test
    void contains_nodeAddedToChannel_isTrueOnlyForThatChannelAndNode() {
        ChannelNodesMap map = new ChannelNodesMap();
        map.add(CHANNEL, NODE_1);
        assertTrue(map.contains(CHANNEL, NODE_1));
        assertFalse(map.contains(CHANNEL, NODE_2));
        assertFalse(map.contains(OTHER_CHANNEL, NODE_1));
    }

    @Test
    void getChannelIds_nodeOnTwoChannels_returnsBothChannelsSorted() {
        ChannelNodesMap map = new ChannelNodesMap();
        map.add(OTHER_CHANNEL, NODE_1);
        map.add(CHANNEL, NODE_1);
        map.add(CHANNEL, NODE_2);
        assertEquals(Set.of(CHANNEL, OTHER_CHANNEL), map.getChannelIds(NODE_1));
        assertEquals(CHANNEL, map.getChannelIds(NODE_1).iterator().next());
        assertEquals(Set.of(CHANNEL), map.getChannelIds(NODE_2));
    }

    @Test
    void getByChannelId_unknownChannel_returnsNull() {
        assertNull(new ChannelNodesMap().getByChannelId(CHANNEL));
    }

    @Test
    void getByChannelId_returnedSet_isReadOnly() {
        ChannelNodesMap map = new ChannelNodesMap();
        map.add(CHANNEL, NODE_1);
        Set<String> targetNodeIds = map.getByChannelId(CHANNEL);
        assertThrows(UnsupportedOperationException.class, () -> targetNodeIds.add(NODE_2));
    }

    @Test
    void isEmpty_newMap_isTrueUntilANodeIsAdded() {
        ChannelNodesMap map = new ChannelNodesMap();
        assertTrue(map.isEmpty());
        map.add(CHANNEL, NODE_1);
        assertFalse(map.isEmpty());
    }
}
