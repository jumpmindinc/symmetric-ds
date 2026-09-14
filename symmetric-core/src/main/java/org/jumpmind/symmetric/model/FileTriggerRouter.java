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

public class FileTriggerRouter implements IModelObject {
    private static final long serialVersionUID = 1L;
    private FileTrigger fileTrigger;
    private Router router;
    private boolean enabled = true;
    private boolean initialLoadEnabled;
    private String targetBaseDir;
    private FileConflictStrategy conflictStrategy = FileConflictStrategy.SOURCE_WINS;
    private String conflictStrategyString;
    private Date createTime = new Date();
    private String lastUpdateBy;
    private Date lastUpdateTime;

    public FileTriggerRouter(FileTrigger fileTrigger, Router router) {
        this.fileTrigger = fileTrigger;
        this.router = router;
    }

    public FileTriggerRouter() {
    }

    public void setFileTrigger(FileTrigger fileTrigger) {
        this.fileTrigger = fileTrigger;
    }

    public FileTrigger getFileTrigger() {
        return fileTrigger;
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    public Router getRouter() {
        return router;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setInitialLoadEnabled(boolean initialLoadEnabled) {
        this.initialLoadEnabled = initialLoadEnabled;
    }

    public boolean isInitialLoadEnabled() {
        return initialLoadEnabled;
    }

    public String getTargetBaseDir() {
        return targetBaseDir;
    }

    public void setTargetBaseDir(String targetBaseDir) {
        this.targetBaseDir = targetBaseDir;
    }

    public FileConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setConflictStrategy(FileConflictStrategy conflictStrategy) {
        this.conflictStrategy = conflictStrategy;
        this.conflictStrategyString = conflictStrategy.name();
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

    public String getConflictStrategyString() {
        return conflictStrategyString;
    }

    public void setConflictStrategyString(String conflictStrategyString) {
        this.conflictStrategyString = conflictStrategyString;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fileTrigger == null) ? 0 : fileTrigger.hashCode());
        result = prime * result + ((router == null) ? 0 : router.hashCode());
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
        FileTriggerRouter other = (FileTriggerRouter) obj;
        if (fileTrigger == null) {
            if (other.fileTrigger != null)
                return false;
        } else if (!fileTrigger.equals(other.fileTrigger))
            return false;
        if (router == null) {
            if (other.router != null)
                return false;
        } else if (!router.equals(other.router))
            return false;
        return true;
    }

    public String getTriggerId() {
        return this.fileTrigger != null ? this.fileTrigger.getTriggerId() : null;
    }

    public String getRouterId() {
        return this.router != null ? this.router.getRouterId() : null;
    }

    public void setTriggerId(String triggerId) {
        if (this.fileTrigger == null) {
            this.fileTrigger = new FileTrigger();
        }
        this.fileTrigger.setTriggerId(triggerId);
    }

    public void setRouterId(String routerId) {
        if (this.router == null) {
            this.router = new Router();
        }
        this.router.setRouterId(routerId);
    }
}
