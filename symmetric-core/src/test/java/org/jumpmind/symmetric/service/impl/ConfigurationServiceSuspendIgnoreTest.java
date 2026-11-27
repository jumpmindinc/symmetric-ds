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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;

import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeChannels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationServiceSuspendIgnoreTest {
    private static final String HUB_NODE_ID = "hub";
    private static final String DEFAULT_CHANNEL = "default";
    private static final String STORE_1 = "store-1";
    private static final String STORE_2 = "store-2";
    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = mock(ConfigurationService.class, CALLS_REAL_METHODS);
    }

    @Test
    void getSuspendIgnoreChannelLists_channelSuspendedForTwoNodes_suspendsBothNodes() {
        NodeChannel defaultChannel = newNodeChannel(DEFAULT_CHANNEL);
        defaultChannel.setSuspendEnabled(STORE_1, true);
        defaultChannel.setSuspendEnabled(STORE_2, true);
        doReturn(List.of(defaultChannel)).when(configurationService).getNodeChannels(HUB_NODE_ID, false);
        NodeChannels nodeChannels = configurationService.getSuspendIgnoreChannelLists(HUB_NODE_ID);
        assertEquals(Set.of(STORE_1, STORE_2), nodeChannels.getSuspendChannels().getByChannelId(DEFAULT_CHANNEL));
        assertTrue(nodeChannels.getIgnoreChannels().isEmpty());
    }

    @Test
    void getSuspendIgnoreChannelLists_channelIgnoredForTwoNodes_ignoresBothNodes() {
        NodeChannel defaultChannel = newNodeChannel(DEFAULT_CHANNEL);
        defaultChannel.setIgnoreEnabled(STORE_1, true);
        defaultChannel.setIgnoreEnabled(STORE_2, true);
        doReturn(List.of(defaultChannel)).when(configurationService).getNodeChannels(HUB_NODE_ID, false);
        NodeChannels nodeChannels = configurationService.getSuspendIgnoreChannelLists(HUB_NODE_ID);
        assertEquals(Set.of(STORE_1, STORE_2), nodeChannels.getIgnoreChannels().getByChannelId(DEFAULT_CHANNEL));
        assertTrue(nodeChannels.getSuspendChannels().isEmpty());
    }

    @Test
    void getSuspendIgnoreChannelLists_controlRowWithNeitherFlag_addsNothing() {
        NodeChannel defaultChannel = newNodeChannel(DEFAULT_CHANNEL);
        defaultChannel.setSuspendEnabled(STORE_1, false);
        doReturn(List.of(defaultChannel)).when(configurationService).getNodeChannels(HUB_NODE_ID, false);
        NodeChannels nodeChannels = configurationService.getSuspendIgnoreChannelLists(HUB_NODE_ID);
        assertTrue(nodeChannels.getSuspendChannels().isEmpty());
        assertTrue(nodeChannels.getIgnoreChannels().isEmpty());
    }

    private static NodeChannel newNodeChannel(String channelId) {
        NodeChannel nodeChannel = new NodeChannel(channelId);
        nodeChannel.setNodeId(HUB_NODE_ID);
        return nodeChannel;
    }
}
