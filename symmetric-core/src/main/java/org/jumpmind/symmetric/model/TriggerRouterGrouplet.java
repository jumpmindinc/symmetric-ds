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

import java.util.Date;

public class TriggerRouterGrouplet implements Cloneable {
    public enum AppliesWhen {
        B, S, T
    };

    protected String triggerId;
    protected String routerId;
    protected AppliesWhen appliesWhen = AppliesWhen.B;
    protected Date createTime;
    protected Date lastUpdateTime;
    protected String lastUpdateBy;

    public String getRouterId() {
        return routerId;
    }

    public void setRouterId(String routerId) {
        this.routerId = routerId;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public AppliesWhen getAppliesWhen() {
        return appliesWhen;
    }

    public void setAppliesWhen(AppliesWhen appliesWhen) {
        this.appliesWhen = appliesWhen;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((appliesWhen == null) ? 0 : appliesWhen.hashCode());
        result = prime * result + ((routerId == null) ? 0 : routerId.hashCode());
        result = prime * result + ((triggerId == null) ? 0 : triggerId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TriggerRouterGrouplet other = (TriggerRouterGrouplet) obj;
        if (appliesWhen != other.appliesWhen)
            return false;
        if (routerId == null) {
            if (other.routerId != null)
                return false;
        } else if (!routerId.equals(other.routerId))
            return false;
        if (triggerId == null) {
            if (other.triggerId != null)
                return false;
        } else if (!triggerId.equals(other.triggerId))
            return false;
        return true;
    }

    public TriggerRouterGrouplet copy() {
        try {
            return (TriggerRouterGrouplet) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
