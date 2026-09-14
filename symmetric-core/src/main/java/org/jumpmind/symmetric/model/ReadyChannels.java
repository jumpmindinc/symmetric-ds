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

import java.util.ArrayList;
import java.util.List;

public class ReadyChannels {
    private String nodeId;
    private List<String> channelIds = new ArrayList<>();
    private List<Integer> threadIds;

    public ReadyChannels(String nodeId) {
        this.nodeId = nodeId;
    }

    public void add(String channelId, Integer threadId) {
        channelIds.add(channelId);
        if (threadId != null) {
            if (threadIds == null) {
                threadIds = new ArrayList<>();
            }
            threadIds.add(threadId);
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public List<String> getChannelIds() {
        return channelIds;
    }

    public void setChannelIds(List<String> channelIds) {
        this.channelIds = channelIds;
    }

    public List<Integer> getThreadIds() {
        return threadIds;
    }

    public void setThreadIds(List<Integer> threadIds) {
        this.threadIds = threadIds;
    }

    public int getThreadIdCount() {
        return threadIds == null ? 0 : threadIds.size();
    }
}
