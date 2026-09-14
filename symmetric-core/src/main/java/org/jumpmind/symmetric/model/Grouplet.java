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
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class Grouplet implements IModelObject {
    private static final long serialVersionUID = 1L;

    public enum GroupletLinkPolicy {
        I, E
    };

    protected List<GroupletLink> groupletLinks = new ArrayList<GroupletLink>();
    protected List<TriggerRouterGrouplet> triggerRouterGrouplets = new ArrayList<TriggerRouterGrouplet>();
    protected String groupletId;
    protected String description;
    protected GroupletLinkPolicy groupletLinkPolicy = GroupletLinkPolicy.I;
    protected Date createTime;
    protected Date lastUpdateTime;
    protected String lastUpdateBy;

    public String getGroupletId() {
        return groupletId;
    }

    public void setGroupletId(String groupletId) {
        this.groupletId = groupletId;
    }

    public GroupletLinkPolicy getGroupletLinkPolicy() {
        return groupletLinkPolicy;
    }

    public void setGroupletLinkPolicy(GroupletLinkPolicy groupletLinkPolicy) {
        this.groupletLinkPolicy = groupletLinkPolicy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = StringUtils.abbreviate(description, 255);
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setGroupletLinks(List<GroupletLink> groupletLinks) {
        this.groupletLinks = groupletLinks;
    }

    public List<GroupletLink> getGroupletLinks() {
        return groupletLinks;
    }

    public void setTriggerRouterGrouplets(List<TriggerRouterGrouplet> triggerRouterGrouplets) {
        this.triggerRouterGrouplets = triggerRouterGrouplets;
    }

    public List<TriggerRouterGrouplet> getTriggerRouterGrouplets() {
        return triggerRouterGrouplets;
    }
}
