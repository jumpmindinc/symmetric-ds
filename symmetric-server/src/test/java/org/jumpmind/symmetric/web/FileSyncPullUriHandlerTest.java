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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

class FileSyncPullUriHandlerTest {
    private static final String CLIENT_NODE_ID = "client-1";
    private static final String OTHER_NODE_ID = "client-2";
    private IConfigurationService configurationService;
    private HttpServletRequest request;
    private FileSyncPullUriHandler handler;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        configurationService = mock(IConfigurationService.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(new NodeChannels());
        request = mock(HttpServletRequest.class);
        handler = new FileSyncPullUriHandler(engine);
    }

    @Test
    void buildSuspendIgnoreChannels_pullingNodeSuspendedFilesync_suspendsFilesyncForThatNode() {
        when(request.getHeader(WebConstants.SUSPENDED_CHANNELS)).thenReturn(Constants.CHANNEL_FILESYNC);
        when(request.getHeader(WebConstants.IGNORED_CHANNELS)).thenReturn(Constants.CHANNEL_FILESYNC_RELOAD);
        NodeChannels nodeChannels = handler.buildSuspendIgnoreChannels(CLIENT_NODE_ID, request);
        assertEquals(Set.of(CLIENT_NODE_ID), nodeChannels.getSuspendChannels().getByChannelId(Constants.CHANNEL_FILESYNC));
        assertEquals(Set.of(CLIENT_NODE_ID), nodeChannels.getIgnoreChannels().getByChannelId(Constants.CHANNEL_FILESYNC_RELOAD));
    }

    @Test
    void buildSuspendIgnoreChannels_localNodeSuspendedFilesync_includesLocalSuspends() {
        NodeChannels localChannels = new NodeChannels();
        localChannels.addSuspendChannels(CLIENT_NODE_ID, Constants.CHANNEL_FILESYNC);
        localChannels.addIgnoreChannels(OTHER_NODE_ID, Constants.CHANNEL_FILESYNC_RELOAD);
        when(configurationService.getSuspendIgnoreChannelLists()).thenReturn(localChannels);
        NodeChannels nodeChannels = handler.buildSuspendIgnoreChannels(CLIENT_NODE_ID, request);
        assertEquals(Set.of(CLIENT_NODE_ID), nodeChannels.getSuspendChannels().getByChannelId(Constants.CHANNEL_FILESYNC));
        assertEquals(Set.of(OTHER_NODE_ID), nodeChannels.getIgnoreChannels().getByChannelId(Constants.CHANNEL_FILESYNC_RELOAD));
    }

    @Test
    void buildSuspendIgnoreChannels_noHeadersAndNoLocalControls_suspendsNothing() {
        NodeChannels nodeChannels = handler.buildSuspendIgnoreChannels(CLIENT_NODE_ID, request);
        assertNull(nodeChannels.getSuspendChannels().getByChannelId(Constants.CHANNEL_FILESYNC));
        assertNull(nodeChannels.getIgnoreChannels().getByChannelId(Constants.CHANNEL_FILESYNC));
    }
}
