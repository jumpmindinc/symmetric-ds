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

import java.io.Serializable;
import java.io.StringReader;
import java.util.Date;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.SchemaObject;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.csv.CsvReader;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.io.data.DataEventType;

/**
 * Maps to the table sync audit table which tracks the history of sync trigger creation.
 * <p/>
 * This table also tracks the columns and the primary keys as of the create date so that if the table definition changes while we still have events to process
 * (as may be the case when distributing events to remote locations), then we still have the history of what the columns and primary keys were at the time.
 */
public class TriggerHistory implements Serializable {
    private static final long serialVersionUID = 1L;
    private int triggerHistoryId;
    private String triggerId;
    private String sourceTableName;
    private String sourceTableNameLowerCase;
    private String sourceSchemaName;
    private String sourceCatalogName;
    private Date createTime;
    private String columnNames;
    private String[] parsedColumnNames;
    private String pkColumnNames;
    private String[] parsedPkColumnNames;
    private boolean isMissingPk;
    private String nameForInsertTrigger;
    private String nameForUpdateTrigger;
    private String nameForDeleteTrigger;
    private String errorMessage;
    private Date inactiveTime;
    /**
     * This is a hash based on the tablename, column names, and column data types. It is used to effectively version a table so we know when it changes.
     */
    private int tableHash;
    /**
     * This is a hash based on the values in the trigger configuration table.
     */
    private long triggerRowHash;
    /**
     * This is a hash of the trigger templates used for generating the trigger text.
     */
    private long triggerTemplateHash;
    private TriggerReBuildReason lastTriggerBuildReason;

    public TriggerHistory() {
        createTime = new Date();
    }

    public TriggerHistory(int triggerHistoryId) {
        this();
        this.triggerHistoryId = triggerHistoryId;
    }

    public TriggerHistory(String tableName, String pkColumnNames, String columnNames) {
        this();
        this.sourceTableName = tableName;
        this.pkColumnNames = pkColumnNames;
        this.columnNames = columnNames;
        this.isMissingPk = Objects.equals(pkColumnNames, columnNames) || StringUtils.isBlank(pkColumnNames);
    }

    public TriggerHistory(Relation relation, Trigger trigger, AbstractTriggerTemplate triggerTemplate) {
        this(relation, trigger, triggerTemplate, null);
    }

    public TriggerHistory(Relation relation, Trigger trigger, AbstractTriggerTemplate triggerTemplate, TriggerReBuildReason reason) {
        this();
        if (triggerTemplate == null) {
            throw new SymmetricException("triggerTemplate cannot be null. Does the current dialect have a TriggerTemplate?");
        }
        this.lastTriggerBuildReason = reason;
        this.sourceTableName = (trigger.isSourceTableNameWildCarded() || trigger.isSourceTableNameExpanded()) ? relation.getName()
                : trigger.getSourceTableNameUnescaped();
        this.columnNames = Relation.getCommaDeliminatedColumns(trigger.orderColumnsForTable(relation));
        this.sourceSchemaName = trigger.isSourceSchemaNameWildCarded() ? relation.getSchema() : trigger.getSourceSchemaNameUnescaped();
        this.sourceCatalogName = trigger.isSourceCatalogNameWildCarded() ? relation.getCatalog() : trigger.getSourceCatalogNameUnescaped();
        this.triggerId = trigger.getTriggerId();
        this.pkColumnNames = Relation.getCommaDeliminatedColumns(trigger.filterExcludedAndIncludedColumns(trigger
                .getSyncKeysColumnsForTable(relation)));
        this.isMissingPk = !relation.hasPrimaryKey() || relation.isMadeAllColumnsPrimaryKey() || StringUtils.isBlank(pkColumnNames);
        this.triggerRowHash = trigger.toHashedValue();
        this.triggerTemplateHash = triggerTemplate.toHashedValue();
        this.tableHash = relation.calculateHashcode();
    }

    public TriggerHistory(Trigger trigger) {
        this.sourceCatalogName = trigger.getSourceCatalogName();
        this.sourceSchemaName = trigger.getSourceSchemaName();
        this.sourceTableName = trigger.getSourceTableName();
        this.triggerId = trigger.getTriggerId();
    }

    public String getTriggerNameForDmlType(DataEventType type) {
        switch (type) {
            case INSERT:
                return getNameForInsertTrigger();
            case UPDATE:
                return getNameForUpdateTrigger();
            case DELETE:
                return getNameForDeleteTrigger();
            default:
                break;
        }
        throw new IllegalStateException();
    }

    public String[] getParsedColumnNames() {
        if (parsedColumnNames == null && columnNames != null) {
            parsedColumnNames = parseColumnNames(columnNames);
        }
        return parsedColumnNames;
    }

    public int indexOfColumnName(String columnName, boolean ignoreCase) {
        String[] columnNames = getParsedColumnNames();
        int i = 0;
        for (String col : columnNames) {
            if (ignoreCase && col.equalsIgnoreCase(columnName)) {
                return i;
            } else if (col.equals(columnName)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public String[] getParsedPkColumnNames() {
        if (parsedPkColumnNames == null && pkColumnNames != null) {
            parsedPkColumnNames = parseColumnNames(pkColumnNames);
        }
        return parsedPkColumnNames;
    }

    public int getTableHash() {
        return tableHash;
    }

    public void setTableHash(int tableHash) {
        this.tableHash = tableHash;
    }

    public String getFullyQualifiedSourceTableName() {
        return SchemaObject.getFullyQualifiedName(sourceCatalogName, sourceSchemaName, sourceTableName);
    }

    public String getSourceTableName() {
        return sourceTableName;
    }

    public void setSourceTableName(String tableName) {
        this.sourceTableName = tableName;
        sourceTableNameLowerCase = null;
    }

    public String getSourceTableNameLowerCase() {
        if (sourceTableNameLowerCase == null) {
            sourceTableNameLowerCase = sourceTableName.toLowerCase();
        }
        return sourceTableNameLowerCase;
    }

    public String getColumnNames() {
        return columnNames;
    }

    public void setColumnNames(String allColumnData) {
        this.columnNames = allColumnData;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public TriggerReBuildReason getLastTriggerBuildReason() {
        return lastTriggerBuildReason;
    }

    public void setLastTriggerBuildReason(TriggerReBuildReason lastTriggerBuildReason) {
        this.lastTriggerBuildReason = lastTriggerBuildReason;
    }

    public String getPkColumnNames() {
        return pkColumnNames;
    }

    public void setPkColumnNames(String pkColumnData) {
        this.pkColumnNames = pkColumnData;
    }

    public boolean isMissingPk() {
        return isMissingPk;
    }

    public void setIsMissingPk(boolean isMissingPk) {
        this.isMissingPk = isMissingPk;
    }

    public int getTriggerHistoryId() {
        return triggerHistoryId;
    }

    public void setTriggerHistoryId(int tableSyncAuditId) {
        this.triggerHistoryId = tableSyncAuditId;
    }

    public String getNameForDeleteTrigger() {
        return nameForDeleteTrigger;
    }

    public void setNameForDeleteTrigger(String nameForDeleteTrigger) {
        this.nameForDeleteTrigger = nameForDeleteTrigger;
    }

    public String getNameForInsertTrigger() {
        return nameForInsertTrigger;
    }

    public void setNameForInsertTrigger(String nameForInsertTrigger) {
        this.nameForInsertTrigger = nameForInsertTrigger;
    }

    public String getNameForUpdateTrigger() {
        return nameForUpdateTrigger;
    }

    public void setNameForUpdateTrigger(String nameForUpdateTrigger) {
        this.nameForUpdateTrigger = nameForUpdateTrigger;
    }

    public String getSourceSchemaName() {
        return sourceSchemaName;
    }

    public void setSourceSchemaName(String schemaName) {
        this.sourceSchemaName = schemaName;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public Date getInactiveTime() {
        return inactiveTime;
    }

    public void setInactiveTime(Date inactiveTime) {
        this.inactiveTime = inactiveTime;
    }

    public String getSourceCatalogName() {
        return sourceCatalogName;
    }

    public void setSourceCatalogName(String sourceCatalogName) {
        this.sourceCatalogName = sourceCatalogName;
    }

    public long getTriggerRowHash() {
        return triggerRowHash;
    }

    public void setTriggerRowHash(long triggerRowHash) {
        this.triggerRowHash = triggerRowHash;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int toVirtualTriggerHistId() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((columnNames == null) ? 0 : columnNames.hashCode());
        result = prime * result + ((sourceCatalogName == null) ? 0 : sourceCatalogName.hashCode());
        result = prime * result + ((sourceSchemaName == null) ? 0 : sourceSchemaName.hashCode());
        result = prime * result + ((sourceTableName == null) ? 0 : sourceTableName.hashCode());
        return result;
    }

    public long getTriggerTemplateHash() {
        return triggerTemplateHash;
    }

    public void setTriggerTemplateHash(long triggerTemplateHash) {
        this.triggerTemplateHash = triggerTemplateHash;
    }

    protected String[] parseColumnNames(String argColumnNames) {
        if (argColumnNames.indexOf('"') == -1) {
            return argColumnNames.split(",");
        }
        try (CsvReader reader = new CsvReader(new StringReader(argColumnNames), ',')) {
            if (reader.readRecord()) {
                return reader.getValues();
            } else {
                throw new SymmetricException("Failed to read a record from CsvReader.");
            }
        } catch (Exception ex) {
            throw new SymmetricException("Failed to parse columns [" + argColumnNames + "]", ex);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + triggerHistoryId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof TriggerHistory && ((TriggerHistory) obj).triggerHistoryId == triggerHistoryId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("triggerHistoryId", triggerHistoryId).append("triggerId", triggerId).append(
                "sourceCatalogName", sourceCatalogName).append("sourceSchemaName", sourceSchemaName).append("sourceTableName", sourceTableName).append(
                        "columnNames", columnNames).append("pkColumnNames", pkColumnNames).append("inactiveTime", inactiveTime).append("createTime", createTime)
                .toString();
    }
}