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

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.common.ParameterConstants;

public class TableReloadRequest {
    protected String targetNodeId;
    protected String sourceNodeId;
    protected String triggerId;
    protected String routerId;
    protected boolean createTable;
    protected boolean deleteFirst;
    protected String reloadSelect;
    protected String beforeCustomSql;
    protected Date reloadTime;
    protected String channelId;
    protected Date createTime = new Date();
    protected Date lastUpdateTime = new Date();
    protected String lastUpdateBy;
    protected long loadId;
    protected boolean processed;

    public TableReloadRequest(TableReloadRequestKey key) {
        this.targetNodeId = key.getTargetNodeId();
        this.sourceNodeId = key.getSourceNodeId();
        this.triggerId = key.getTriggerId();
        this.routerId = key.getRouterId();
        this.createTime = key.getCreateTime();
    }

    public TableReloadRequest() {
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public String getRouterId() {
        return routerId;
    }

    public void setRouterId(String routerId) {
        this.routerId = routerId;
    }

    public String getReloadSelect() {
        return reloadSelect;
    }

    public void setReloadSelect(String reloadSelect) {
        this.reloadSelect = reloadSelect;
    }

    public String getBeforeCustomSql() {
        return beforeCustomSql;
    }

    public void setBeforeCustomSql(String beforeCustomSql) {
        this.beforeCustomSql = beforeCustomSql;
    }

    public Date getReloadTime() {
        return reloadTime;
    }

    public void setReloadTime(Date reloadTime) {
        this.reloadTime = reloadTime;
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

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public boolean isCreateTable() {
        return createTable;
    }

    public void setCreateTable(boolean createTable) {
        this.createTable = createTable;
    }

    public boolean isDeleteFirst() {
        return deleteFirst;
    }

    public void setDeleteFirst(boolean deleteFirst) {
        this.deleteFirst = deleteFirst;
    }

    public boolean isFullLoadRequest() {
        return ParameterConstants.ALL.equals(getTriggerId()) && ParameterConstants.ALL.equals(getRouterId()) && getChannelId() == null;
    }

    public boolean isChannelRequest() {
        return getChannelId() != null;
    }

    public String getIdentifier() {
        return getTriggerId() + getRouterId();
    }

    public long getLoadId() {
        return loadId;
    }

    public void setLoadId(long loadId) {
        this.loadId = loadId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public TableReloadRequestKey getTableReloadRequestKey() {
        return new TableReloadRequestKey(this.targetNodeId, this.sourceNodeId, this.triggerId, this.routerId, this.createTime);
    }

    public boolean hasSetupBatches() {
        return isCreateTable() || isDeleteFirst() || !StringUtils.isBlank(getBeforeCustomSql());
    }
}
