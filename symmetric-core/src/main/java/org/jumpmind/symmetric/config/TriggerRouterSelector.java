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
package org.jumpmind.symmetric.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jumpmind.symmetric.model.TriggerRouter;

/**
 * Utility class to pair down a list of triggers.
 */
public class TriggerRouterSelector {
    private String channelId;
    private String targetNodeGroupId;
    private Collection<TriggerRouter> triggers;

    public TriggerRouterSelector(Collection<TriggerRouter> triggers, String channelId, String targetNodeGroupId) {
        this.triggers = triggers;
        this.channelId = channelId;
        this.targetNodeGroupId = targetNodeGroupId;
    }

    public List<TriggerRouter> select() {
        List<TriggerRouter> filtered = new ArrayList<TriggerRouter>();
        for (TriggerRouter trigger : triggers) {
            if (trigger.getTrigger().getChannelId().equals(channelId)
                    && (targetNodeGroupId == null || trigger.getRouter().getNodeGroupLink().getTargetNodeGroupId().equals(targetNodeGroupId))) {
                filtered.add(trigger);
            }
        }
        return filtered;
    }
}