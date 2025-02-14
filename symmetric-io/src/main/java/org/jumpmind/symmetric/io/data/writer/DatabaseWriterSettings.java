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
package org.jumpmind.symmetric.io.data.writer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IAlterDatabaseInterceptor;
import org.jumpmind.symmetric.io.data.Batch;

public class DatabaseWriterSettings {
    protected long maxRowsBeforeCommit = 10000;
    // Milliseconds to sleep between commits.
    protected long commitSleepInterval = 5;
    protected boolean treatDateTimeFieldsAsVarchar = false;
    protected boolean treatBitFieldsAsInteger = false;
    protected boolean usePrimaryKeysFromSource = true;
    protected Conflict defaultConflictSetting;
    protected boolean createTableFailOnError = true;
    protected boolean alterTable = true;
    protected boolean createTableDropFirst = false;
    protected boolean applyChangesOnly = true;
    protected boolean createTableAlterCaseToMatchDatabaseDefault = false;
    protected boolean createTableWithoutDefaultsOnError = false;
    protected boolean ignoreMissingTables = true;
    protected boolean saveCurrentValueOnError = false;
    protected boolean fitToColumn = false;
    protected boolean autoResolveForeignKeyViolationDelete = true;
    protected boolean autoResolvePrimaryKeyViolation = true;
    protected boolean autoResolveUniqueIndexViolation = true;
    protected boolean autoResolveUniqueIndexIgnoreNullValues = true;
    protected boolean autoResolveCaptureDeleteMissingRows;
    protected boolean logConflictResolution = false;
    protected boolean logSqlParamsOnError = true;
    protected String textColumnExpression;
    protected Map<String, Conflict> conflictSettingsByChannel;
    protected Map<String, Conflict> conflictSettingsByTable;
    protected List<IDatabaseWriterFilter> databaseWriterFilters;
    protected List<IDatabaseWriterErrorHandler> databaseWriterErrorHandlers;
    protected List<ResolvedData> resolvedData;
    protected IAlterDatabaseInterceptor[] alterDatabaseInterceptors;
    protected Set<String> conflictLosingParentRows;
    protected boolean ignoreSqlDataEventFailures = false;
    protected boolean createIndexConvertUniqueToNonuniqueWhenColumnsNotRequired = true;
    protected boolean stripOutCommentsInScripts = true;
    protected String runtimeConfigTriggerPrefix = "sym";
    protected boolean createTableIncludeApplicationTriggers = false;
    protected boolean keepBulkStagingFiles = false;
    protected String msSqlBulkLoadBcpCodePage;

    public void setAlterDatabaseInterceptors(IAlterDatabaseInterceptor[] alterDatabaseInterceptors) {
        this.alterDatabaseInterceptors = alterDatabaseInterceptors;
    }

    public IAlterDatabaseInterceptor[] getAlterDatabaseInterceptors() {
        return alterDatabaseInterceptors;
    }

    public boolean isAlterTable() {
        return alterTable;
    }

    public void setAlterTable(boolean alterTable) {
        this.alterTable = alterTable;
    }

    public boolean isCreateTableDropFirst() {
        return createTableDropFirst;
    }

    public void setCreateTableDropFirst(boolean createTableDropFirst) {
        this.createTableDropFirst = createTableDropFirst;
    }

    public boolean isCreateTableFailOnError() {
        return createTableFailOnError;
    }

    public void setCreateTableFailOnError(boolean createTableFailOnError) {
        this.createTableFailOnError = createTableFailOnError;
    }

    public long getMaxRowsBeforeCommit() {
        return maxRowsBeforeCommit;
    }

    public void setMaxRowsBeforeCommit(long maxRowsBeforeCommit) {
        this.maxRowsBeforeCommit = maxRowsBeforeCommit;
    }

    public boolean isTreatDateTimeFieldsAsVarchar() {
        return treatDateTimeFieldsAsVarchar;
    }

    public void setTreatDateTimeFieldsAsVarchar(boolean treatDateTimeFieldsAsVarchar) {
        this.treatDateTimeFieldsAsVarchar = treatDateTimeFieldsAsVarchar;
    }

    public boolean isTreatBitFieldsAsInteger() {
        return treatBitFieldsAsInteger;
    }

    public void setTreatBitFieldsAsInteger(boolean treatBitFieldsAsInteger) {
        this.treatBitFieldsAsInteger = treatBitFieldsAsInteger;
    }

    public boolean isUsePrimaryKeysFromSource() {
        return usePrimaryKeysFromSource;
    }

    public void setUsePrimaryKeysFromSource(boolean usePrimaryKeysFromSource) {
        this.usePrimaryKeysFromSource = usePrimaryKeysFromSource;
    }

    public Conflict getDefaultConflictSetting() {
        return defaultConflictSetting;
    }

    public void setDefaultConflictSetting(Conflict defaultConflictSetting) {
        this.defaultConflictSetting = defaultConflictSetting;
    }

    public boolean isCreateTableAlterCaseToMatchDatabaseDefault() {
        return createTableAlterCaseToMatchDatabaseDefault;
    }

    public void setCreateTableAlterCaseToMatchDatabaseDefault(
            boolean createTableAlterCaseToMatchDatabaseDefault) {
        this.createTableAlterCaseToMatchDatabaseDefault = createTableAlterCaseToMatchDatabaseDefault;
    }

    public boolean isCreateTableWithoutDefaultsOnError() {
        return createTableWithoutDefaultsOnError;
    }

    public void setCreateTableWithoutDefaultsOnError(boolean createTableWithoutDefaultsOnError) {
        this.createTableWithoutDefaultsOnError = createTableWithoutDefaultsOnError;
    }

    public Map<String, Conflict> getConflictSettingsByChannel() {
        return conflictSettingsByChannel;
    }

    public void setConflictSettingsByChannel(Map<String, Conflict> conflictSettingsByChannel) {
        this.conflictSettingsByChannel = conflictSettingsByChannel;
    }

    public Map<String, Conflict> getConflictSettingsByTable() {
        return conflictSettingsByTable;
    }

    public void setConflictSettingsByTable(Map<String, Conflict> conflictSettingsByTable) {
        this.conflictSettingsByTable = conflictSettingsByTable;
    }

    public List<IDatabaseWriterFilter> getDatabaseWriterFilters() {
        return databaseWriterFilters;
    }

    public void setDatabaseWriterFilters(List<IDatabaseWriterFilter> databaseWriterFilters) {
        this.databaseWriterFilters = databaseWriterFilters;
    }

    public void setResolvedData(ResolvedData... resolvedData) {
        this.resolvedData = new ArrayList<ResolvedData>();
        if (resolvedData != null) {
            for (ResolvedData data : resolvedData) {
                this.resolvedData.add(data);
            }
        }
    }

    public void setResolvedData(List<ResolvedData> resolvedData) {
        this.resolvedData = resolvedData;
    }

    public List<ResolvedData> getResolvedData() {
        return resolvedData;
    }

    public void setDatabaseWriterErrorHandlers(
            List<IDatabaseWriterErrorHandler> databaseWriterErrorHandlers) {
        this.databaseWriterErrorHandlers = databaseWriterErrorHandlers;
    }

    public List<IDatabaseWriterErrorHandler> getDatabaseWriterErrorHandlers() {
        return databaseWriterErrorHandlers;
    }

    public ResolvedData getResolvedData(long rowNumber) {
        if (resolvedData != null) {
            for (ResolvedData data : resolvedData) {
                if (data.getRowNumber() == rowNumber) {
                    return data;
                }
            }
        }
        return null;
    }

    public void setIgnoreMissingTables(boolean ignoreMissingTables) {
        this.ignoreMissingTables = ignoreMissingTables;
    }

    public boolean isIgnoreMissingTables() {
        return ignoreMissingTables;
    }

    public void addErrorHandler(IDatabaseWriterErrorHandler handler) {
        if (this.databaseWriterErrorHandlers == null) {
            this.databaseWriterErrorHandlers = new ArrayList<IDatabaseWriterErrorHandler>();
        }
        this.databaseWriterErrorHandlers.add(handler);
    }

    public Conflict pickConflict(Table table, Batch batch) {
        Conflict settings = null;
        String fullyQualifiedName = table.getFullyQualifiedTableNameLowerCase();
        if (conflictSettingsByTable != null) {
            Conflict found = conflictSettingsByTable.get(fullyQualifiedName);
            if (found == null) {
                found = conflictSettingsByTable.get(table.getName().toLowerCase());
            }
            if (found != null
                    && (StringUtils.isBlank(found.getTargetChannelId()) || found
                            .getTargetChannelId().equals(batch.getChannelId()))) {
                settings = found;
            }
        }
        if (settings == null && conflictSettingsByChannel != null) {
            settings = conflictSettingsByChannel.get(batch.getChannelId());
        }
        if (settings == null) {
            settings = defaultConflictSetting;
        }
        if (settings == null) {
            settings = new Conflict();
        }
        return settings;
    }

    public long getCommitSleepInterval() {
        return commitSleepInterval;
    }

    public void setCommitSleepInterval(long commitSleepInterval) {
        this.commitSleepInterval = commitSleepInterval;
    }

    public boolean isSaveCurrentValueOnError() {
        return saveCurrentValueOnError;
    }

    public void setSaveCurrentValueOnError(boolean saveCurrentValueOnError) {
        this.saveCurrentValueOnError = saveCurrentValueOnError;
    }

    public void setFitToColumn(boolean fitToColumn) {
        this.fitToColumn = fitToColumn;
    }

    public boolean isFitToColumn() {
        return fitToColumn;
    }

    public boolean isAutoResolveForeignKeyViolationDelete() {
        return autoResolveForeignKeyViolationDelete;
    }

    public void setAutoResolveForeignKeyViolationDelete(boolean autoResolveForeignKeyViolationDelete) {
        this.autoResolveForeignKeyViolationDelete = autoResolveForeignKeyViolationDelete;
    }

    public boolean isAutoResolvePrimaryKeyViolation() {
        return autoResolvePrimaryKeyViolation;
    }

    public void setAutoResolvePrimaryKeyViolation(boolean autoResolvePrimaryKeyViolation) {
        this.autoResolvePrimaryKeyViolation = autoResolvePrimaryKeyViolation;
    }

    public boolean isAutoResolveUniqueIndexViolation() {
        return autoResolveUniqueIndexViolation;
    }

    public void setAutoResolveUniqueIndexViolation(boolean autoResolveUniqueIndexViolation) {
        this.autoResolveUniqueIndexViolation = autoResolveUniqueIndexViolation;
    }

    public boolean isAutoResolveUniqueIndexIgnoreNullValues() {
        return autoResolveUniqueIndexIgnoreNullValues;
    }

    public void setAutoResolveUniqueIndexIgnoreNullValues(boolean autoResolveUniqueIndexIgnoreNullValues) {
        this.autoResolveUniqueIndexIgnoreNullValues = autoResolveUniqueIndexIgnoreNullValues;
    }

    public boolean isAutoResolveCaptureDeleteMissingRows() {
        return autoResolveCaptureDeleteMissingRows;
    }

    public void setAutoResolveCaptureDeleteMissingRows(boolean autoResolveCaptureDeleteMissingRows) {
        this.autoResolveCaptureDeleteMissingRows = autoResolveCaptureDeleteMissingRows;
    }

    public void setLogConflictResolution(boolean logConflictResolution) {
        this.logConflictResolution = logConflictResolution;
    }

    public boolean isLogConflictResolution() {
        return logConflictResolution;
    }

    public void setLogSqlParamsOnError(boolean logSqlParamsOnError) {
        this.logSqlParamsOnError = logSqlParamsOnError;
    }

    public boolean isLogSqlParamsOnError() {
        return logSqlParamsOnError;
    }

    public void setTextColumnExpression(String textColumnExpression) {
        this.textColumnExpression = textColumnExpression;
    }

    public String getTextColumnExpression() {
        return textColumnExpression;
    }

    public void setApplyChangesOnly(boolean applyChangesOnly) {
        this.applyChangesOnly = applyChangesOnly;
    }

    public boolean isApplyChangesOnly() {
        return applyChangesOnly;
    }

    public Set<String> getConflictLosingParentRows() {
        return conflictLosingParentRows;
    }

    public void setConflictLosingParentRows(Set<String> conflictLosingParentRows) {
        this.conflictLosingParentRows = conflictLosingParentRows;
    }

    public boolean isIgnoreSqlDataEventFailures() {
        return ignoreSqlDataEventFailures;
    }

    public void setIgnoreSqlDataEventFailures(boolean ignoreSqlDataEventFailures) {
        this.ignoreSqlDataEventFailures = ignoreSqlDataEventFailures;
    }

    public boolean isCreateIndexConvertUniqueToNonuniqueWhenColumnsNotRequired() {
        return createIndexConvertUniqueToNonuniqueWhenColumnsNotRequired;
    }

    public void setCreateIndexConvertUniqueToNonuniqueWhenColumnsNotRequired(boolean createIndexConvertUniqueToNonuniqueWhenColumnsNotRequired) {
        this.createIndexConvertUniqueToNonuniqueWhenColumnsNotRequired = createIndexConvertUniqueToNonuniqueWhenColumnsNotRequired;
    }

    public boolean isStripOutCommentsInScripts() {
        return stripOutCommentsInScripts;
    }

    public void setStripOutCommentsInScripts(boolean stripOutCommentsInScripts) {
        this.stripOutCommentsInScripts = stripOutCommentsInScripts;
    }

    public String getRuntimeConfigTriggerPrefix() {
        return runtimeConfigTriggerPrefix;
    }

    public void setRuntimeConfigTriggerPrefix(String runtimeConfigTriggerPrefix) {
        this.runtimeConfigTriggerPrefix = runtimeConfigTriggerPrefix;
    }

    public boolean isCreateTableIncludeApplicationTriggers() {
        return createTableIncludeApplicationTriggers;
    }

    public void setCreateTableIncludeApplicationTriggers(boolean createTableIncludeApplicationTriggers) {
        this.createTableIncludeApplicationTriggers = createTableIncludeApplicationTriggers;
    }

    public boolean isKeepBulkStagingFiles() {
        return keepBulkStagingFiles;
    }

    public void setKeepBulkStagingFiles(boolean keepBulkStagingFiles) {
        this.keepBulkStagingFiles = keepBulkStagingFiles;
    }

    public String getMsSqlBulkLoadBcpCodePage() {
        return msSqlBulkLoadBcpCodePage;
    }

    public void setMsSqlBulkLoadBcpCodePage(String msSqlBulkLoadBcpCodePage) {
        this.msSqlBulkLoadBcpCodePage = msSqlBulkLoadBcpCodePage;
    }
}
