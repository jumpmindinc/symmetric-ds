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

public class LoadFilter implements IModelObject {
    private static final long serialVersionUID = 1L;

    public enum LoadFilterType {
        BSH, JAVA, SQL
    };

    private String loadFilterId;
    private LoadFilterType loadFilterType = LoadFilterType.BSH;
    private String targetCatalogName;
    private String targetSchemaName;
    private String targetTableName;
    private boolean filterOnUpdate = true;
    private boolean filterOnInsert = true;
    private boolean filterOnDelete = true;
    private String beforeWriteScript;
    private String afterWriteScript;
    private String batchCompleteScript;
    private String batchCommitScript;
    private String batchRollbackScript;
    private String handleErrorScript;
    private Date createTime = new Date();
    private String lastUpdateBy = "symmetricds";
    private Date lastUpdateTime = new Date();
    private int loadFilterOrder;
    private boolean failOnError = true;

    public int getLoadFilterOrder() {
        return loadFilterOrder;
    }

    public void setLoadFilterOrder(int loadFilterOrder) {
        this.loadFilterOrder = loadFilterOrder;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public String getLoadFilterId() {
        return loadFilterId;
    }

    public void setLoadFilterId(String loadFilterId) {
        this.loadFilterId = loadFilterId;
    }

    public String getTargetCatalogName() {
        return targetCatalogName;
    }

    public void setTargetCatalogName(String targetCatalogName) {
        this.targetCatalogName = targetCatalogName;
    }

    public String getTargetSchemaName() {
        return targetSchemaName;
    }

    public void setTargetSchemaName(String targetSchemaName) {
        this.targetSchemaName = targetSchemaName;
    }

    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
    }

    public boolean isFilterOnUpdate() {
        return filterOnUpdate;
    }

    public void setFilterOnUpdate(boolean filterOnUpdate) {
        this.filterOnUpdate = filterOnUpdate;
    }

    public boolean isFilterOnInsert() {
        return filterOnInsert;
    }

    public void setFilterOnInsert(boolean filterOnInsert) {
        this.filterOnInsert = filterOnInsert;
    }

    public boolean isFilterOnDelete() {
        return filterOnDelete;
    }

    public void setFilterOnDelete(boolean filterOnDelete) {
        this.filterOnDelete = filterOnDelete;
    }

    public String getBeforeWriteScript() {
        return beforeWriteScript;
    }

    public void setBeforeWriteScript(String beforeWriteScript) {
        this.beforeWriteScript = beforeWriteScript;
    }

    public String getAfterWriteScript() {
        return afterWriteScript;
    }

    public void setAfterWriteScript(String afterWriteScript) {
        this.afterWriteScript = afterWriteScript;
    }

    public String getBatchCompleteScript() {
        return batchCompleteScript;
    }

    public void setBatchCompleteScript(String batchCompleteScript) {
        this.batchCompleteScript = batchCompleteScript;
    }

    public String getBatchCommitScript() {
        return batchCommitScript;
    }

    public void setBatchCommitScript(String batchCommitScript) {
        this.batchCommitScript = batchCommitScript;
    }

    public String getBatchRollbackScript() {
        return batchRollbackScript;
    }

    public void setBatchRollbackScript(String batchRollbackScript) {
        this.batchRollbackScript = batchRollbackScript;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public LoadFilterType getLoadFilterType() {
        return loadFilterType;
    }

    public void setLoadFilterType(LoadFilterType loadFilterType) {
        this.loadFilterType = loadFilterType;
    }

    public void setHandleErrorScript(String handleErrorScript) {
        this.handleErrorScript = handleErrorScript;
    }

    public String getHandleErrorScript() {
        return handleErrorScript;
    }

    public String getEvents() {
        StringBuilder events = new StringBuilder();
        buildStringBasedList(getBeforeWriteScript(), events, "Before Write");
        buildStringBasedList(getAfterWriteScript(), events, "After Write");
        buildStringBasedList(getBatchCompleteScript(), events, "Batch Complete");
        buildStringBasedList(getBatchCommitScript(), events, "Batch Commit");
        buildStringBasedList(getBatchRollbackScript(), events, "Batch Rollback");
        buildStringBasedList(getHandleErrorScript(), events, "Handle Error");
        return events.toString();
    }

    private void buildStringBasedList(String source, StringBuilder target, String value) {
        if (source != null && !source.equals("")) {
            if (target.length() > 0) {
                target.append(", ");
            }
            target.append(value);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LoadFilter && loadFilterId != null) {
            return loadFilterId.equals(((LoadFilter) obj).loadFilterId);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return loadFilterId != null ? loadFilterId.hashCode() : super.hashCode();
    }

    @Override
    public String toString() {
        if (loadFilterId != null) {
            return loadFilterId;
        } else {
            return super.toString();
        }
    }
}
