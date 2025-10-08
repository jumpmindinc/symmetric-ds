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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the trigger via which a table will be synchronized.
 */
public class Trigger implements IModelObject, Cloneable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(Trigger.class);
    private static final String DEFAULT_CONDITION = "1=1";
    private String triggerId;
    private String sourceTableName;
    private String sourceTableNameLowerCase;
    private String sourceSchemaName;
    private String sourceCatalogName;
    private boolean isSourceTableNameWildCarded;
    private boolean isSourceSchemaWildCarded;
    private boolean isSourceCatalogWildCarded;
    private boolean isSourceTableNameExpanded;
    private String sourceTableNameUnescaped;
    private String sourceSchemaNameUnescaped;
    private String sourceCatalogNameUnescaped;
    private String channelId = Constants.CHANNEL_DEFAULT;
    private String reloadChannelId = Constants.CHANNEL_RELOAD;
    private boolean syncOnUpdate = true;
    private boolean syncOnInsert = true;
    private boolean syncOnDelete = true;
    private boolean syncOnIncomingBatch = false;
    private boolean useStreamLobs = false;
    private boolean useCaptureLobs = false;
    private boolean useCaptureOldData = true;
    private boolean useHandleKeyUpdates = true;
    private boolean streamRow = false;
    private String nameForInsertTrigger;
    private String nameForUpdateTrigger;
    private String nameForDeleteTrigger;
    private String syncOnUpdateCondition = DEFAULT_CONDITION;
    private String syncOnInsertCondition = DEFAULT_CONDITION;
    private String syncOnDeleteCondition = DEFAULT_CONDITION;
    private String channelExpression = null;
    private String customBeforeUpdateText;
    private String customBeforeInsertText;
    private String customBeforeDeleteText;
    private String customOnUpdateText;
    private String customOnInsertText;
    private String customOnDeleteText;
    private String excludedColumnNames = null;
    private String includedColumnNames = null;
    private String syncKeyNames = null;
    private String timeBasedCaptureColumn = null;
    /**
     * This is a SQL expression that creates a unique id which the sync process can use to 'group' events together and commit together.
     */
    private String txIdExpression = null;
    private String externalSelect = null;
    private Date createTime;
    private Date lastUpdateTime;
    private String lastUpdateBy;

    public Trigger() {
    }

    public Trigger(String tableName, String channelId) {
        this.triggerId = tableName;
        setSourceTableName(tableName);
        this.channelId = channelId;
    }

    public Trigger(String tableName, String channelId, boolean syncOnIncomingBatch) {
        this(tableName, channelId);
        this.syncOnIncomingBatch = syncOnIncomingBatch;
    }

    final public String qualifiedSourceTableName() {
        return qualifiedSourceTablePrefix() + sourceTableName;
    }

    final public String qualifiedSourceTablePrefix() {
        String schemaPlus = (getSourceSchemaName() != null ? getSourceSchemaName() + "." : "");
        String catalogPlus = (getSourceCatalogName() != null ? getSourceCatalogName() + "." : "")
                + schemaPlus;
        return catalogPlus;
    }

    public void nullOutBlankFields() {
        if (StringUtils.isBlank(sourceCatalogName)) {
            sourceCatalogName = null;
            sourceCatalogNameUnescaped = null;
            isSourceCatalogWildCarded = false;
        }
        if (StringUtils.isBlank(sourceSchemaName)) {
            sourceSchemaName = null;
            sourceSchemaNameUnescaped = null;
            isSourceSchemaWildCarded = false;
        }
        syncOnInsertCondition = Objects.toString(StringUtils.trimToNull(syncOnInsertCondition), DEFAULT_CONDITION);
        syncOnUpdateCondition = Objects.toString(StringUtils.trimToNull(syncOnUpdateCondition), DEFAULT_CONDITION);
        syncOnDeleteCondition = Objects.toString(StringUtils.trimToNull(syncOnDeleteCondition), DEFAULT_CONDITION);
        externalSelect = StringUtils.trimToNull(externalSelect);
        excludedColumnNames = StringUtils.trimToNull(excludedColumnNames);
        includedColumnNames = StringUtils.trimToNull(includedColumnNames);
        syncKeyNames = StringUtils.trimToNull(syncKeyNames);
        channelExpression = StringUtils.trimToNull(channelExpression);
        customBeforeInsertText = StringUtils.trimToNull(customBeforeInsertText);
        customBeforeUpdateText = StringUtils.trimToNull(customBeforeUpdateText);
        customBeforeDeleteText = StringUtils.trimToNull(customBeforeDeleteText);
        customOnInsertText = StringUtils.trimToNull(customOnInsertText);
        customOnUpdateText = StringUtils.trimToNull(customOnUpdateText);
        customOnDeleteText = StringUtils.trimToNull(customOnDeleteText);
        timeBasedCaptureColumn = StringUtils.trimToNull(timeBasedCaptureColumn);
    }

    public Column[] filterExcludedAndIncludedColumns(Column[] src) {
        return filterIncludedColumns(filterExcludedColumns(src));
    }

    public Column[] filterExcludedColumns(Column[] src) {
        if (src != null) {
            List<String> excludedColumnNames = getExcludedColumnNamesAsList();
            List<Column> filtered = new ArrayList<Column>(src.length);
            for (int i = 0; i < src.length; i++) {
                Column col = src[i];
                if (!excludedColumnNames.contains(col.getName().toLowerCase())) {
                    filtered.add(col);
                }
            }
            return filtered.toArray(new Column[filtered.size()]);
        } else {
            return new Column[0];
        }
    }

    public Column[] filterIncludedColumns(Column[] src) {
        if (src != null) {
            List<String> includedColumnNames = getIncludedColumnNamesAsList();
            if (includedColumnNames.size() == 0) {
                return src;
            }
            List<Column> filtered = new ArrayList<Column>(src.length);
            for (int i = 0; i < src.length; i++) {
                Column col = src[i];
                if (includedColumnNames.contains(col.getName().toLowerCase())) {
                    filtered.add(col);
                }
            }
            return filtered.toArray(new Column[filtered.size()]);
        } else {
            return new Column[0];
        }
    }

    public Column[] getSyncKeysColumnsForTable(Table table) {
        List<String> syncKeys = getSyncKeyNamesAsList();
        if (syncKeys.size() > 0) {
            List<Column> columns = new ArrayList<Column>();
            for (String syncKey : syncKeys) {
                Column col = table.getColumnWithName(syncKey);
                if (col != null) {
                    columns.add(col);
                } else {
                    log.error("The sync key column '{}' was specified for the '{}' trigger but was not found in the table", syncKey, triggerId);
                }
            }
            if (columns.size() > 0) {
                return columns.toArray(new Column[columns.size()]);
            } else {
                return table.getPrimaryKeyColumns();
            }
        } else {
            return table.getPrimaryKeyColumns();
        }
    }

    /**
     * When dealing with columns, always use this method to order the columns so that the primary keys are first.
     */
    public Column[] orderColumnsForTable(Table table) {
        if (table != null) {
            Column[] pks = getSyncKeysColumnsForTable(table);
            Column[] cols = table.getColumns();
            List<Column> orderedColumns = new ArrayList<Column>(cols.length);
            for (int i = 0; i < pks.length; i++) {
                orderedColumns.add(pks[i]);
            }
            List<Column> endingColumns = new ArrayList<Column>();
            for (int i = 0; i < cols.length; i++) {
                boolean syncKey = false;
                for (int j = 0; j < pks.length; j++) {
                    if (cols[i].getName().equals(pks[j].getName())) {
                        syncKey = true;
                        break;
                    }
                }
                if ("LONG".equalsIgnoreCase(cols[i].getJdbcTypeName())) {
                    endingColumns.add(cols[i]);
                } else if (!syncKey) {
                    orderedColumns.add(cols[i]);
                }
            }
            for (Column column : endingColumns) {
                orderedColumns.add(column);
            }
            Column[] result = orderedColumns.toArray(new Column[orderedColumns.size()]);
            return filterExcludedAndIncludedColumns(result);
        } else {
            return new Column[0];
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getExcludedColumnNamesAsList() {
        if (StringUtils.isNotBlank(excludedColumnNames)) {
            StringTokenizer tokenizer = new StringTokenizer(excludedColumnNames, ",");
            List<String> columnNames = new ArrayList<String>(tokenizer.countTokens());
            while (tokenizer.hasMoreTokens()) {
                columnNames.add(tokenizer.nextToken().toLowerCase().trim());
            }
            return columnNames;
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getIncludedColumnNamesAsList() {
        if (StringUtils.isNotBlank(includedColumnNames)) {
            StringTokenizer tokenizer = new StringTokenizer(includedColumnNames, ",");
            List<String> columnNames = new ArrayList<String>(tokenizer.countTokens());
            while (tokenizer.hasMoreTokens()) {
                columnNames.add(tokenizer.nextToken().toLowerCase().trim());
            }
            return columnNames;
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    public boolean hasChangedSinceLastTriggerBuild(Date lastTriggerBuildTime) {
        return lastTriggerBuildTime == null || getLastUpdateTime() == null
                || lastTriggerBuildTime.before(getLastUpdateTime());
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public String getSourceTableName() {
        return sourceTableName;
    }

    public String getSourceTableNameLowerCase() {
        return sourceTableNameLowerCase;
    }

    public String getSourceTableNameUnescaped() {
        return sourceTableNameUnescaped;
    }

    public boolean isSourceWildCarded() {
        return isSourceTableNameWildCarded() || isSourceCatalogNameWildCarded() || isSourceSchemaNameWildCarded();
    }

    public boolean isSourceTableNameWildCarded() {
        return isSourceTableNameWildCarded;
    }

    public boolean isSourceTableNameExpanded() {
        return isSourceTableNameExpanded;
    }

    public void setSourceTableNameExpanded(boolean isSourceTableNameExpanded) {
        this.isSourceTableNameExpanded = isSourceTableNameExpanded;
    }

    public boolean isSourceCatalogNameWildCarded() {
        return isSourceCatalogWildCarded;
    }

    public boolean isSourceSchemaNameWildCarded() {
        return isSourceSchemaWildCarded;
    }

    public String getChannelExpression() {
        return channelExpression;
    }

    public void setChannelExpression(String channelExpression) {
        this.channelExpression = channelExpression;
    }

    public void setSourceTableName(String sourceTableName) {
        this.sourceTableName = sourceTableName;
        this.sourceTableNameLowerCase = sourceTableName == null ? null : sourceTableName.toLowerCase();
        this.isSourceTableNameWildCarded = FormatUtils.isWildCarded(sourceTableName);
        if (!this.isSourceTableNameWildCarded) {
            this.sourceTableNameUnescaped = FormatUtils.unescapeWildCards(sourceTableName);
        }
    }

    public String getSourceSchemaName() {
        return sourceSchemaName;
    }

    public String getSourceSchemaNameUnescaped() {
        return sourceSchemaNameUnescaped;
    }

    public void setSourceSchemaName(String sourceSchemaName) {
        this.sourceSchemaName = sourceSchemaName;
        this.isSourceSchemaWildCarded = FormatUtils.isWildCarded(sourceSchemaName);
        if (!this.isSourceSchemaWildCarded) {
            this.sourceSchemaNameUnescaped = FormatUtils.unescapeWildCards(sourceSchemaName);
        }
    }

    public String getSourceCatalogName() {
        return sourceCatalogName;
    }

    public String getSourceCatalogNameUnescaped() {
        return sourceCatalogNameUnescaped;
    }

    public void setSourceCatalogName(String sourceCatalogName) {
        this.sourceCatalogName = sourceCatalogName;
        this.isSourceCatalogWildCarded = FormatUtils.isWildCarded(sourceCatalogName);
        if (!this.isSourceCatalogWildCarded) {
            this.sourceCatalogNameUnescaped = FormatUtils.unescapeWildCards(sourceCatalogName);
        }
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getReloadChannelId() {
        return reloadChannelId;
    }

    public void setReloadChannelId(String reloadChannelId) {
        this.reloadChannelId = reloadChannelId;
    }

    public boolean isSyncOnUpdate() {
        return syncOnUpdate;
    }

    public void setSyncOnUpdate(boolean syncOnUpdate) {
        this.syncOnUpdate = syncOnUpdate;
    }

    public boolean isSyncOnInsert() {
        return syncOnInsert;
    }

    public void setSyncOnInsert(boolean syncOnInsert) {
        this.syncOnInsert = syncOnInsert;
    }

    public boolean isSyncOnDelete() {
        return syncOnDelete;
    }

    public void setSyncOnDelete(boolean syncOnDelete) {
        this.syncOnDelete = syncOnDelete;
    }

    public boolean isSyncOnIncomingBatch() {
        return syncOnIncomingBatch;
    }

    public void setSyncOnIncomingBatch(boolean syncOnIncomingBatch) {
        this.syncOnIncomingBatch = syncOnIncomingBatch;
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

    public String getNameForDeleteTrigger() {
        return nameForDeleteTrigger;
    }

    public void setNameForDeleteTrigger(String nameForDeleteTrigger) {
        this.nameForDeleteTrigger = nameForDeleteTrigger;
    }

    public String getSyncOnUpdateCondition() {
        return syncOnUpdateCondition;
    }

    public void setSyncOnUpdateCondition(String syncOnUpdateCondition) {
        this.syncOnUpdateCondition = syncOnUpdateCondition;
    }

    public String getSyncOnInsertCondition() {
        return syncOnInsertCondition;
    }

    public void setSyncOnInsertCondition(String syncOnInsertCondition) {
        this.syncOnInsertCondition = syncOnInsertCondition;
    }

    public String getSyncOnDeleteCondition() {
        return syncOnDeleteCondition;
    }

    public void setSyncOnDeleteCondition(String syncOnDeleteCondition) {
        this.syncOnDeleteCondition = syncOnDeleteCondition;
    }

    public String getCustomBeforeUpdateText() {
        return customBeforeUpdateText;
    }

    public void setCustomBeforeUpdateText(String customBeforeUpdateText) {
        this.customBeforeUpdateText = customBeforeUpdateText;
    }

    public String getCustomBeforeInsertText() {
        return customBeforeInsertText;
    }

    public void setCustomBeforeInsertText(String customBeforeInsertText) {
        this.customBeforeInsertText = customBeforeInsertText;
    }

    public String getCustomBeforeDeleteText() {
        return customBeforeDeleteText;
    }

    public void setCustomBeforeDeleteText(String customBeforeDeleteText) {
        this.customBeforeDeleteText = customBeforeDeleteText;
    }

    public String getCustomOnUpdateText() {
        return customOnUpdateText;
    }

    public void setCustomOnUpdateText(String customOnUpdateText) {
        this.customOnUpdateText = customOnUpdateText;
    }

    public String getCustomOnInsertText() {
        return customOnInsertText;
    }

    public void setCustomOnInsertText(String customOnInsertText) {
        this.customOnInsertText = customOnInsertText;
    }

    public String getCustomOnDeleteText() {
        return customOnDeleteText;
    }

    public void setCustomOnDeleteText(String customOnDeleteText) {
        this.customOnDeleteText = customOnDeleteText;
    }

    public String getExcludedColumnNames() {
        return excludedColumnNames;
    }

    public void setExcludedColumnNames(String excludedColumnNames) {
        this.excludedColumnNames = excludedColumnNames;
    }

    public String getIncludedColumnNames() {
        return includedColumnNames;
    }

    public void setIncludedColumnNames(String includedColumnNames) {
        this.includedColumnNames = includedColumnNames;
    }

    public String getTxIdExpression() {
        return txIdExpression;
    }

    public void setTxIdExpression(String txIdExpression) {
        this.txIdExpression = txIdExpression;
    }

    public String getExternalSelect() {
        return externalSelect;
    }

    public void setExternalSelect(String externalSelect) {
        this.externalSelect = externalSelect;
    }

    public void setLastUpdateBy(String updatedBy) {
        this.lastUpdateBy = updatedBy;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastModifiedOn) {
        this.lastUpdateTime = lastModifiedOn;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createdOn) {
        this.createTime = createdOn;
    }

    public void setUseStreamLobs(boolean useStreamLobs) {
        this.useStreamLobs = useStreamLobs;
    }

    public boolean isUseStreamLobs() {
        return useStreamLobs;
    }

    public void setUseCaptureLobs(boolean useCaptureLobs) {
        this.useCaptureLobs = useCaptureLobs;
    }

    public boolean isUseCaptureLobs() {
        return useCaptureLobs;
    }

    public boolean isUseHandleKeyUpdates() {
        return useHandleKeyUpdates;
    }

    public void setUseHandleKeyUpdates(boolean useHandleKeyUpdates) {
        this.useHandleKeyUpdates = useHandleKeyUpdates;
    }

    public void setUseCaptureOldData(boolean useCaptureOldData) {
        this.useCaptureOldData = useCaptureOldData;
    }

    public boolean isUseCaptureOldData() {
        return useCaptureOldData;
    }

    public void setSyncKeyNames(String syncKeys) {
        this.syncKeyNames = syncKeys;
    }

    public String getSyncKeyNames() {
        return syncKeyNames;
    }

    public boolean isStreamRow() {
        return streamRow;
    }

    public void setStreamRow(boolean streamRow) {
        this.streamRow = streamRow;
    }

    public String getTimeBasedCaptureColumn() {
        return timeBasedCaptureColumn;
    }

    public void setTimeBasedCaptureColumn(String timeBasedCaptureColumn) {
        this.timeBasedCaptureColumn = timeBasedCaptureColumn;
    }

    @SuppressWarnings("unchecked")
    private List<String> getSyncKeyNamesAsList() {
        if (StringUtils.isNotBlank(syncKeyNames)) {
            StringTokenizer tokenizer = new StringTokenizer(syncKeyNames, ",");
            List<String> columnNames = new ArrayList<String>(tokenizer.countTokens());
            while (tokenizer.hasMoreTokens()) {
                String columnName = tokenizer.nextToken().toLowerCase().trim();
                if (!columnNames.contains(columnName)) {
                    columnNames.add(columnName);
                }
            }
            return columnNames;
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    public String getFullyQualifiedSourceTableName() {
        return Table.getFullyQualifiedTableName(sourceCatalogName, sourceSchemaName, sourceTableName);
    }

    public long toHashedValue() {
        long hashedValue = triggerId != null ? triggerId.hashCode() : 0;
        if (null != sourceTableName) {
            hashedValue += sourceTableName.hashCode();
        }
        if (null != channelId) {
            hashedValue += channelId.hashCode();
        }
        if (null != sourceSchemaName) {
            hashedValue += sourceSchemaName.hashCode();
        }
        if (null != sourceCatalogName) {
            hashedValue += sourceCatalogName.hashCode();
        }
        hashedValue += syncOnUpdate ? "syncOnUpdate".hashCode() : 0;
        hashedValue += syncOnInsert ? "syncOnInsert".hashCode() : 0;
        hashedValue += syncOnDelete ? "syncOnDelete".hashCode() : 0;
        hashedValue += syncOnIncomingBatch ? "syncOnIncomingBatch".hashCode() : 0;
        hashedValue += useStreamLobs ? "useStreamLobs".hashCode() : 0;
        hashedValue += useCaptureLobs ? "useCaptureLobs".hashCode() : 0;
        hashedValue += useCaptureOldData ? "useCaptureOldData".hashCode() : 0;
        hashedValue += useHandleKeyUpdates ? "useHandleKeyUpdates".hashCode() : 0;
        if (null != nameForInsertTrigger) {
            hashedValue += nameForInsertTrigger.hashCode();
        }
        if (null != nameForUpdateTrigger) {
            hashedValue += nameForUpdateTrigger.hashCode();
        }
        if (null != nameForDeleteTrigger) {
            hashedValue += nameForDeleteTrigger.hashCode();
        }
        if (null != syncOnUpdateCondition) {
            hashedValue += syncOnUpdateCondition.hashCode();
        }
        if (null != syncOnInsertCondition) {
            hashedValue += syncOnInsertCondition.hashCode();
        }
        if (null != syncOnDeleteCondition) {
            hashedValue += syncOnDeleteCondition.hashCode();
        }
        if (null != customBeforeUpdateText) {
            hashedValue += customBeforeUpdateText.hashCode();
        }
        if (null != customBeforeInsertText) {
            hashedValue += customBeforeInsertText.hashCode();
        }
        if (null != customBeforeDeleteText) {
            hashedValue += customBeforeDeleteText.hashCode();
        }
        if (null != customOnUpdateText) {
            hashedValue += customOnUpdateText.hashCode();
        }
        if (null != customOnInsertText) {
            hashedValue += customOnInsertText.hashCode();
        }
        if (null != customOnDeleteText) {
            hashedValue += customOnDeleteText.hashCode();
        }
        if (null != excludedColumnNames) {
            hashedValue += excludedColumnNames.hashCode();
        }
        if (null != includedColumnNames) {
            hashedValue += includedColumnNames.hashCode();
        }
        if (null != externalSelect) {
            hashedValue += externalSelect.hashCode();
        }
        if (null != txIdExpression) {
            hashedValue += txIdExpression.hashCode();
        }
        if (null != syncKeyNames) {
            hashedValue += syncKeyNames.hashCode();
        }
        return hashedValue;
    }

    public boolean matchesCatalogName(String catalogName, boolean ignoreCase) {
        return matches(sourceCatalogName, catalogName, ignoreCase);
    }

    public boolean matchesSchemaName(String schemaName, boolean ignoreCase) {
        return matches(sourceSchemaName, schemaName, ignoreCase);
    }

    protected boolean matches(String match, String target, boolean ignoreCase) {
        boolean matches = false;
        String[] wildcardTokens = match.split(",");
        for (String wildcardToken : wildcardTokens) {
            if (FormatUtils.isWildCardMatch(target, wildcardToken, ignoreCase)) {
                if (!wildcardToken.startsWith(FormatUtils.NEGATE_TOKEN)) {
                    matches = true;
                } else {
                    matches = false;
                    break;
                }
            }
        }
        return matches;
    }

    public boolean matches(Table table, String defaultCatalog, String defaultSchema,
            boolean ignoreCase) {
        boolean catalogMatch = false;
        if (isSourceCatalogWildCarded) {
            catalogMatch = matches(sourceCatalogName, table.getCatalog(), ignoreCase);
        } else {
            catalogMatch = (Strings.CS.equals(sourceCatalogNameUnescaped, table.getCatalog()) ||
                    (StringUtils.isBlank(sourceCatalogName) && Strings.CS.equals(defaultCatalog, table.getCatalog())));
        }
        boolean schemaMatch = false;
        if (isSourceSchemaWildCarded) {
            schemaMatch = matches(sourceSchemaName, table.getSchema(), ignoreCase);
        } else {
            schemaMatch = (Strings.CS.equals(sourceSchemaNameUnescaped, table.getSchema()) ||
                    (StringUtils.isBlank(sourceSchemaName) && Strings.CS.equals(defaultSchema, table.getSchema())));
        }
        boolean tableMatch = false;
        if (isSourceTableNameWildCarded) {
            tableMatch = matches(sourceTableName, table.getName(), ignoreCase);
        } else if (isSourceTableNameExpanded) {
            tableMatch = matches(sourceTableName.replace("$(targetExternalId)", "*"), table.getName(), ignoreCase);
        } else {
            tableMatch = ignoreCase ? table.getName().equalsIgnoreCase(sourceTableNameUnescaped)
                    : table.getName().equals(sourceTableNameUnescaped);
        }
        return catalogMatch && schemaMatch && tableMatch;
    }

    public boolean matches(Trigger trigger) {
        return Strings.CS.equals(sourceCatalogName, trigger.sourceCatalogName)
                && Strings.CS.equals(sourceSchemaName, trigger.sourceSchemaName)
                && trigger.sourceTableName.equalsIgnoreCase(sourceTableName);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trigger && triggerId != null) {
            return triggerId.equals(((Trigger) obj).triggerId);
        } else {
            return super.equals(obj);
        }
    }

    @Override
    public int hashCode() {
        return triggerId != null ? triggerId.hashCode() : super.hashCode();
    }

    @Override
    public String toString() {
        if (triggerId != null) {
            return triggerId;
        } else {
            return super.toString();
        }
    }

    public Trigger copy() {
        Trigger trigger = null;
        try {
            trigger = (Trigger) super.clone();
        } catch (CloneNotSupportedException e) {
        }
        return trigger;
    }
}