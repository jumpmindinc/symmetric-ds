/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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
import org.jumpmind.symmetric.io.data.DataEventType;

/**
 * Defines the trigger via which a table will be synchronized.
 */
public class TriggerRouter implements IModelObject, Cloneable {
    private static final long serialVersionUID = 1L;
    private boolean enabled = true;
    /**
     * This is the order in which the definitions will be processed.
     */
    private int initialLoadOrder = 50;
    private String initialLoadSelect;
    private String initialLoadDeleteStmt;
    private Trigger trigger;
    private Router router;
    private Date createTime;
    private Date lastUpdateTime;
    private String lastUpdateBy;
    private boolean pingBackEnabled = false;
    private String description;
    private String dataRefreshType;

    public TriggerRouter() {
        this(new Trigger(), new Router());
    }

    public TriggerRouter(Trigger trigger, Router router) {
        this.trigger = trigger;
        this.router = router;
        createTime = new Date();
        lastUpdateTime = new Date();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setCreateTime(Date createdOn) {
        this.createTime = createdOn;
    }

    public void setLastUpdateTime(Date lastModifiedOn) {
        this.lastUpdateTime = lastModifiedOn;
    }

    public void setLastUpdateBy(String updatedBy) {
        this.lastUpdateBy = updatedBy;
    }

    public int getInitialLoadOrder() {
        return initialLoadOrder;
    }

    public void setInitialLoadOrder(int order) {
        this.initialLoadOrder = order;
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    public Router getRouter() {
        return router;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setInitialLoadSelect(String initialLoadSelect) {
        this.initialLoadSelect = initialLoadSelect;
    }

    public String getInitialLoadSelect() {
        return initialLoadSelect;
    }

    public String getInitialLoadDeleteStmt() {
        return initialLoadDeleteStmt;
    }

    public void setInitialLoadDeleteStmt(String initialLoadDeleteStmt) {
        this.initialLoadDeleteStmt = initialLoadDeleteStmt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDataRefreshType() {
        return dataRefreshType;
    }

    public void setDataRefreshType(String dataRefreshType) {
        this.dataRefreshType = dataRefreshType;
    }

    public boolean isRouted(DataEventType event) {
        switch (event) {
            case INSERT:
                return router.isSyncOnInsert();
            case DELETE:
                return router.isSyncOnDelete();
            case UPDATE:
                return router.isSyncOnUpdate();
            default:
                return true;
        }
    }

    public String getTargetSchema(String defaultSchema, TriggerHistory triggerHistory) {
        if (router != null && !StringUtils.isBlank(router.getTargetSchemaName())) {
            return router.getTargetSchemaName();
        } else if (router != null && router.isUseSourceCatalogSchema() && triggerHistory != null) {
            return triggerHistory.getSourceSchemaName();
        } else {
            return defaultSchema;
        }
    }

    public String getTargetCatalog(String defaultCatalog, TriggerHistory triggerHistory) {
        if (router != null && !StringUtils.isBlank(router.getTargetCatalogName())) {
            return router.getTargetCatalogName();
        } else if (router != null && router.isUseSourceCatalogSchema() && triggerHistory != null) {
            return triggerHistory.getSourceCatalogName();
        } else {
            return defaultCatalog;
        }
    }

    public String getTargetTable(TriggerHistory triggerHistory) {
        if (router != null && !StringUtils.isBlank(router.getTargetTableName())) {
            return router.getTargetTableName();
        }
        if (triggerHistory != null) {
            return triggerHistory.getSourceTableName();
        }
        if (trigger != null && !StringUtils.isBlank(trigger.getSourceTableName())) {
            return trigger.getSourceTableName();
        } else {
            return null;
        }
    }

    public String qualifiedTargetTableName(TriggerHistory triggerHistory) {
        String catalog = getTargetCatalog(null, triggerHistory);
        String schema = getTargetSchema(null, triggerHistory);
        String tableName = getTargetTable(triggerHistory);
        if (!StringUtils.isBlank(schema)) {
            tableName = schema + "." + tableName;
        }
        if (!StringUtils.isBlank(catalog)) {
            tableName = catalog + "." + tableName;
        }
        return tableName;
    }

    public void setPingBackEnabled(boolean pingBackEnabled) {
        this.pingBackEnabled = pingBackEnabled;
    }

    public boolean isPingBackEnabled() {
        return pingBackEnabled;
    }

    public boolean isSame(TriggerRouter triggerRouter) {
        return (this.trigger == null && triggerRouter.trigger == null)
                || (this.trigger != null && triggerRouter.trigger != null && this.trigger
                        .matches(triggerRouter.trigger))
                        && (this.router == null && triggerRouter.router == null)
                || (this.router != null && triggerRouter.router != null && this.router
                        .equals(triggerRouter.router));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((router == null) ? 0 : router.hashCode());
        result = prime * result + ((trigger == null) ? 0 : trigger.hashCode());
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
        TriggerRouter other = (TriggerRouter) obj;
        if (router == null) {
            if (other.router != null)
                return false;
        } else if (!router.equals(other.router))
            return false;
        if (trigger == null) {
            if (other.trigger != null)
                return false;
        } else if (!trigger.equals(other.trigger))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return (trigger != null ? trigger.toString() : "") + ":"
                + (router != null ? router.toString() : "");
    }

    public String getIdentifier() {
        return getTrigger().getTriggerId() + getRouter().getRouterId();
    }

    public String getTriggerId() {
        return this.trigger != null ? this.trigger.getTriggerId() : null;
    }

    public String getRouterId() {
        return this.router != null ? this.router.getRouterId() : null;
    }

    public void setTriggerId(String triggerId) {
        if (this.trigger == null) {
            this.trigger = new Trigger();
        }
        this.trigger.setTriggerId(triggerId);
    }

    public void setRouterId(String routerId) {
        if (this.router == null) {
            this.router = new Router();
        }
        this.router.setRouterId(routerId);
    }

    public TriggerRouter copy() {
        TriggerRouter triggerRouter = null;
        try {
            triggerRouter = (TriggerRouter) super.clone();
            triggerRouter.setTrigger(trigger.copy());
            triggerRouter.setRouter(router.copy());
        } catch (CloneNotSupportedException e) {
        }
        return triggerRouter;
    }
}