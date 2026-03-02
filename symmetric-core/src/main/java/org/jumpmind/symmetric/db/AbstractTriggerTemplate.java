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
package org.jumpmind.symmetric.db;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.lang.reflect.Field;
import java.sql.Types;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for generating dialect specific SQL such as trigger bodies and functions
 */
abstract public class AbstractTriggerTemplate {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected static final String ORIG_TABLE_ALIAS = "orig";
    protected static final String INSERT_TRIGGER_TEMPLATE = "insertTriggerTemplate";
    protected static final String UPDATE_TRIGGER_TEMPLATE = "updateTriggerTemplate";
    protected static final String INSERT_WITH_RELOAD_TRIGGER_TEMPLATE = "insertReloadTriggerTemplate";
    protected static final String UPDATE_WITH_RELOAD_TRIGGER_TEMPLATE = "updateReloadTriggerTemplate";
    protected static final String DELETE_TRIGGER_TEMPLATE = "deleteTriggerTemplate";
    protected static final String INITIAL_LOAD_SQL_TEMPLATE = "initialLoadSqlTemplate";
    protected Map<String, String> sqlTemplates;
    protected String emptyColumnTemplate = "''";
    protected String stringColumnTemplate;
    protected String xmlColumnTemplate;
    protected String arrayColumnTemplate;
    protected String numberColumnTemplate;
    protected String moneyColumnTemplate;
    protected String datetimeColumnTemplate;
    protected String timeColumnTemplate;
    protected String dateColumnTemplate;
    protected String dateTimeWithTimeZoneColumnTemplate;
    protected String dateTimeWithLocalTimeZoneColumnTemplate;
    protected String geometryColumnTemplate;
    protected String geographyColumnTemplate;
    protected String clobColumnTemplate;
    protected String blobColumnTemplate;
    protected String longColumnTemplate;
    protected String binaryColumnTemplate;
    protected String imageColumnTemplate;
    protected String wrappedBlobColumnTemplate;
    protected String booleanColumnTemplate;
    protected String sqlVariantColumnTemplate;
    protected String triggerConcatCharacter;
    protected String newTriggerValue;
    protected String oldTriggerValue;
    protected String oldColumnPrefix = "";
    protected String newColumnPrefix = "";
    protected String otherColumnTemplate;
    protected ISymmetricDialect symmetricDialect;
    protected int hashedValue = 0;

    protected AbstractTriggerTemplate(ISymmetricDialect symmetricDialect) {
        this.symmetricDialect = symmetricDialect;
    }

    /**
     * When {@link ParameterConstants#INITIAL_LOAD_CONCAT_CSV_IN_SQL_ENABLED} is false most dialects are going to want to still use the trigger templates
     * because they have type translation details (like geometry templates). However, some dialects cannot handle the complex SQL generated (Firebird). We
     * needed a way to tell the dialect that we want to select the columns straight up.
     */
    public boolean useTriggerTemplateForColumnTemplatesDuringInitialLoad() {
        return this.symmetricDialect.getParameterService().is(ParameterConstants.INITIAL_LOAD_USE_COLUMN_TEMPLATES_ENABLED);
    }

    /**
     * When INITIAL_LOAD_USE_COLUMN_TEMPLATES_ENABLED is true, column templates are used for all columns. When false, only specific column types have been
     * implemented to format data in code.
     */
    protected boolean useTriggerTemplateForColumnTemplatesDuringInitialLoad(Column column) {
        if (!useTriggerTemplateForColumnTemplatesDuringInitialLoad() && column != null) {
            int mappedType = column.getMappedTypeCode();
            if (mappedType == ColumnTypes.TIMESTAMPTZ || mappedType == ColumnTypes.TIMESTAMPLTZ || mappedType == ColumnTypes.TIMETZ) {
                return true;
            }
            String typeName = column.getJdbcTypeName();
            if (typeName != null && (typeName.equalsIgnoreCase("unichar") || typeName.equalsIgnoreCase("univarchar") || typeName.equalsIgnoreCase("unitext"))) {
                return true;
            }
            int type = column.getJdbcTypeCode();
            // These column types can be selected directly without a template
            if (type == Types.CHAR || type == Types.NCHAR || type == Types.VARCHAR || type == ColumnTypes.NVARCHAR
                    || type == Types.LONGVARCHAR || type == ColumnTypes.LONGNVARCHAR || type == Types.CLOB
                    || type == Types.TINYINT || type == Types.SMALLINT || type == Types.INTEGER || type == Types.BIGINT
                    || type == Types.NUMERIC || type == Types.BINARY || type == Types.VARBINARY
                    || (type == Types.BLOB && !requiresWrappedBlobTemplateForBlobType()) || type == Types.LONGVARBINARY
                    || type == Types.DECIMAL || type == Types.FLOAT || (type == Types.DOUBLE && !typeName.equalsIgnoreCase("money")) || type == Types.REAL
                    || type == ColumnTypes.MSSQL_NTEXT || type == Types.DATE || type == Types.TIME || type == Types.TIMESTAMP
                    || type == Types.BIT || type == Types.BOOLEAN) {
                return false;
            }
        }
        return true;
    }

    public String createInitalLoadSql(Node node, TriggerRouter triggerRouter, Table originalTable,
            TriggerHistory triggerHistory, Channel channel, String overrideSelectSql) {
        IParameterService parameterService = symmetricDialect.getParameterService();
        boolean dateTimeAsString = parameterService.is(
                ParameterConstants.DATA_LOADER_TREAT_DATETIME_AS_VARCHAR);
        boolean concatInCsv = parameterService.is(
                ParameterConstants.INITIAL_LOAD_CONCAT_CSV_IN_SQL_ENABLED);
        Table table = originalTable.copyAndFilterColumns(triggerHistory.getParsedColumnNames(),
                triggerHistory.getParsedPkColumnNames(), true, false);
        Column[] columns = table.getColumns();
        String textColumnExpression = parameterService.getString(ParameterConstants.DATA_EXTRACTOR_TEXT_COLUMN_EXPRESSION);
        String sql = null;
        String tableAlias = symmetricDialect.getInitialLoadTableAlias();
        if (concatInCsv) {
            sql = sqlTemplates.get(INITIAL_LOAD_SQL_TEMPLATE);
            String columnsText = buildColumnsString(tableAlias,
                    tableAlias, "", table, columns, DataEventType.INSERT,
                    false, channel, triggerRouter.getTrigger()).columnString;
            if (isNotBlank(textColumnExpression)) {
                columnsText = textColumnExpression.replace("$(columnName)", columnsText);
            }
            sql = FormatUtils.replace("columns", columnsText, sql);
        } else {
            sql = "select $(columns) from $(schemaName)$(tableName) t where $(whereClause)";
            StringBuilder columnList = new StringBuilder();
            for (int i = 0; i < columns.length; i++) {
                Column column = columns[i];
                if (column != null) {
                    if (i > 0) {
                        columnList.append(",");
                    }
                    String columnExpression = null;
                    if (useTriggerTemplateForColumnTemplatesDuringInitialLoad(column) && (!isUniTextColumn(column))) {
                        ColumnString columnString = fillOutColumnTemplate(tableAlias,
                                tableAlias, "", table, column, DataEventType.INSERT, false, channel,
                                triggerRouter.getTrigger(), true);
                        columnExpression = columnString.columnString;
                        if (isNotBlank(textColumnExpression)
                                && TypeMap.isTextType(column.getMappedTypeCode())) {
                            columnExpression = textColumnExpression.replace("$(columnName)",
                                    columnExpression);
                        }
                    } else {
                        columnExpression = SymmetricUtils.quote(symmetricDialect,
                                column.getName());
                        if (dateTimeAsString
                                && TypeMap.isDateTimeType(column.getMappedTypeCode())) {
                            columnExpression = castDatetimeColumnToString(column.getName());
                        } else if (isNotBlank(textColumnExpression)
                                && TypeMap.isTextType(column.getMappedTypeCode())) {
                            columnExpression = textColumnExpression.replace("$(columnName)",
                                    columnExpression);
                        }
                    }
                    columnList.append(columnExpression).append(" as ").append("x__").append(i);
                }
            }
            sql = FormatUtils.replace("columns", columnList.toString(), sql);
        }
        String initialLoadSelect = StringUtils.isBlank(triggerRouter.getInitialLoadSelect()) ? Constants.ALWAYS_TRUE_CONDITION
                : triggerRouter.getInitialLoadSelect();
        if (StringUtils.isNotBlank(overrideSelectSql)) {
            initialLoadSelect = overrideSelectSql;
        }
        sql = FormatUtils.replace("whereClause", initialLoadSelect, sql);
        sql = FormatUtils.replace("tableName", SymmetricUtils.quote(symmetricDialect, table.getName()), sql);
        sql = FormatUtils.replace("schemaName", getSourceTablePrefix(triggerHistory), sql);
        sql = FormatUtils.replace("schemaNameOnly", getSchemaNameOnly(triggerHistory), sql);
        sql = FormatUtils.replace(
                "primaryKeyWhereString",
                getPrimaryKeyWhereString(symmetricDialect.getInitialLoadTableAlias(),
                        table.hasPrimaryKey() ? table.getPrimaryKeyColumns() : table.getColumns()),
                sql);
        // Replace these parameters to give the initiaLoadContition a chance to
        // reference the node that is being loaded
        sql = FormatUtils.replace("groupId", node.getNodeGroupId(), sql);
        sql = FormatUtils.replace("externalId", node.getExternalId(), sql);
        sql = FormatUtils.replace("nodeId", node.getNodeId(), sql);
        sql = replaceDefaultSchemaAndCatalog(sql);
        sql = FormatUtils.replace("prefixName", symmetricDialect.getTablePrefix(), sql);
        sql = FormatUtils.replace("toClob",
                triggerRouter.getTrigger().isUseCaptureLobs() ? toClobExpression(table) : "", sql);
        return sql;
    }

    public boolean isUniTextColumn(Column column) {
        return column.getJdbcTypeName() == null ? false : column.getJdbcTypeName().equalsIgnoreCase("unitext");
    }

    public boolean[] getColumnPositionUsingTemplate(Table originalTable, TriggerHistory triggerHistory) {
        IParameterService parameterService = symmetricDialect.getParameterService();
        boolean concatInCsv = parameterService.is(ParameterConstants.INITIAL_LOAD_CONCAT_CSV_IN_SQL_ENABLED);
        Table table = originalTable.copyAndFilterColumns(triggerHistory.getParsedColumnNames(),
                triggerHistory.getParsedPkColumnNames(), true, false);
        Column[] columns = table.getColumns();
        boolean[] isColumnPositionUsingTemplate = new boolean[columns.length];
        if (!concatInCsv) {
            for (int i = 0; i < columns.length; i++) {
                isColumnPositionUsingTemplate[i] = useTriggerTemplateForColumnTemplatesDuringInitialLoad(columns[i]);
            }
        }
        return isColumnPositionUsingTemplate;
    }

    protected String castDatetimeColumnToString(String columnName) {
        return SymmetricUtils.quote(symmetricDialect, columnName);
    }

    protected String getSourceTablePrefix(Table table) {
        String prefix = (isNotBlank(table.getSchema()) ? table.getSchema() + symmetricDialect.getPlatform().getDatabaseInfo().getSchemaSeparator() : "");
        prefix = (isNotBlank(table.getCatalog()) ? table.getCatalog() + symmetricDialect.getPlatform().getDatabaseInfo().getCatalogSeparator() : "") + prefix;
        if (isBlank(prefix)) {
            prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultSchema()) ? SymmetricUtils
                    .quote(symmetricDialect, symmetricDialect.getPlatform().getDefaultSchema())
                    + "." : "");
            prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultCatalog()) ? SymmetricUtils
                    .quote(symmetricDialect, symmetricDialect.getPlatform().getDefaultCatalog())
                    + "." : "") + prefix;
        }
        return prefix;
    }

    protected String getSourceTableSchema(Table table) {
        String prefix = (isNotBlank(table.getSchema()) ? table.getSchema() : "");
        if (isBlank(prefix)) {
            prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultSchema()) ? symmetricDialect.getPlatform().getDefaultSchema() : "");
        }
        return prefix;
    }

    protected String getSourceTablePrefix(TriggerHistory triggerHistory) {
        String prefix = (isNotBlank(triggerHistory.getSourceSchemaName()) ? SymmetricUtils.quote(
                symmetricDialect, triggerHistory.getSourceSchemaName()) + symmetricDialect.getPlatform().getDatabaseInfo().getSchemaSeparator() : "");
        prefix = (isNotBlank(triggerHistory.getSourceCatalogName()) ? SymmetricUtils.quote(
                symmetricDialect, triggerHistory.getSourceCatalogName()) + symmetricDialect.getPlatform().getDatabaseInfo().getCatalogSeparator() : "")
                + prefix;
        if (isBlank(prefix)) {
            prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultSchema()) ? SymmetricUtils
                    .quote(symmetricDialect, symmetricDialect.getPlatform().getDefaultSchema())
                    + "." : "");
            prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultCatalog()) ? SymmetricUtils
                    .quote(symmetricDialect, symmetricDialect.getPlatform().getDefaultCatalog())
                    + "." : "") + prefix;
        }
        return prefix;
    }

    protected String getSourceTableSchema(TriggerHistory triggerHistory) {
        String prefix = (isNotBlank(triggerHistory.getSourceSchemaName()) ? triggerHistory.getSourceSchemaName() : "");
        if (isBlank(prefix)) {
            prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultSchema()) ? symmetricDialect.getPlatform().getDefaultSchema() : "");
        }
        return prefix;
    }

    protected String getSchemaNameOnly(TriggerHistory triggerHistory) {
        String prefix = (isNotBlank(triggerHistory.getSourceSchemaName()) ? SymmetricUtils.quote(
                symmetricDialect, triggerHistory.getSourceSchemaName()) + symmetricDialect.getPlatform().getDatabaseInfo().getSchemaSeparator() : "");
        if (isBlank(prefix)) {
            prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultSchema()) ? SymmetricUtils
                    .quote(symmetricDialect, symmetricDialect.getPlatform().getDefaultSchema())
                    + "." : "");
        }
        return prefix;
    }

    protected String replaceDefaultSchemaAndCatalog(String sql) {
        return replaceDefaultSchemaAndCatalog(sql, null, null);
    }

    protected String replaceDefaultSchemaAndCatalog(String sql, String catalog, String schema) {
        String defaultCatalog = catalog != null ? catalog : symmetricDialect.getPlatform().getDefaultCatalog();
        String defaultSchema = schema != null ? schema : symmetricDialect.getPlatform().getDefaultSchema();
        sql = replaceDefaultSchema(sql, defaultSchema);
        sql = replaceDefaultCatalog(sql, defaultCatalog);
        return sql;
    }

    public String createCsvDataSql(Trigger trigger, TriggerHistory triggerHistory, Table originalTable,
            Channel channel, String whereClause) {
        Table table = originalTable.copyAndFilterColumns(triggerHistory.getParsedColumnNames(),
                triggerHistory.getParsedPkColumnNames(), true, false);
        String sql = sqlTemplates.get(INITIAL_LOAD_SQL_TEMPLATE);
        Column[] columns = table.getColumns();
        String columnsText = buildColumnsString(symmetricDialect.getInitialLoadTableAlias(),
                symmetricDialect.getInitialLoadTableAlias(), "", table, columns, DataEventType.INSERT,
                false, channel, trigger).columnString;
        sql = FormatUtils.replace("columns", columnsText, sql);
        sql = FormatUtils.replace("toClob",
                trigger.isUseCaptureLobs() ? toClobExpression(table) : "", sql);
        sql = FormatUtils.replace("tableName", SymmetricUtils.quote(symmetricDialect, table.getName()), sql);
        sql = FormatUtils.replace("schemaName", getSourceTablePrefix(triggerHistory), sql);
        sql = FormatUtils.replace("schemaNameOnly", getSchemaNameOnly(triggerHistory), sql);
        sql = FormatUtils.replace("whereClause", whereClause, sql);
        sql = FormatUtils.replace(
                "primaryKeyWhereString",
                getPrimaryKeyWhereString(symmetricDialect.getInitialLoadTableAlias(),
                        table.hasPrimaryKey() ? table.getPrimaryKeyColumns() : table.getColumns()),
                sql);
        sql = replaceDefaultSchemaAndCatalog(sql);
        return sql;
    }

    public String createCsvPrimaryKeySql(Trigger trigger, TriggerHistory triggerHistory,
            Table table, Channel channel, String whereClause) {
        String sql = sqlTemplates.get(INITIAL_LOAD_SQL_TEMPLATE);
        Column[] columns = table.getPrimaryKeyColumns();
        String columnsText = buildColumnsString(symmetricDialect.getInitialLoadTableAlias(),
                symmetricDialect.getInitialLoadTableAlias(), "", table, columns, DataEventType.INSERT,
                false, channel, trigger).toString();
        sql = FormatUtils.replace("columns", columnsText, sql);
        sql = FormatUtils.replace("toClob",
                trigger.isUseCaptureLobs() ? toClobExpression(table) : "", sql);
        sql = FormatUtils.replace("tableName", SymmetricUtils.quote(symmetricDialect, table.getName()), sql);
        sql = FormatUtils.replace("schemaName",
                triggerHistory == null ? getSourceTablePrefix(table)
                        : getSourceTablePrefix(triggerHistory), sql);
        sql = FormatUtils.replace("schemaNameOnly", getSchemaNameOnly(triggerHistory), sql);
        sql = FormatUtils.replace("whereClause", whereClause, sql);
        sql = FormatUtils.replace(
                "primaryKeyWhereString",
                getPrimaryKeyWhereString(symmetricDialect.getInitialLoadTableAlias(),
                        table.hasPrimaryKey() ? table.getPrimaryKeyColumns() : table.getColumns()),
                sql);
        return sql;
    }

    public String createTriggerDDL(DataEventType dml, Trigger trigger, TriggerHistory history,
            Channel channel, String tablePrefix, Table originalTable, String defaultCatalog,
            String defaultSchema) {
        Table table = originalTable.copyAndFilterColumns(history.getParsedColumnNames(),
                history.getParsedPkColumnNames(), true, false);
        String ddl = sqlTemplates.get(dml.name().toLowerCase(Locale.US) + "TriggerTemplate");
        if (trigger.isStreamRow()) {
            String reloadDdl = sqlTemplates.get(dml.name().toLowerCase(Locale.US) + "ReloadTriggerTemplate");
            if (reloadDdl != null && reloadDdl.length() > 0) {
                ddl = reloadDdl;
            }
        }
        if (ddl == null) {
            throw new NotImplementedException(dml.name() + " trigger is not implemented for "
                    + symmetricDialect.getPlatform().getName());
        }
        return replaceTemplateVariables(dml, trigger, history, channel, tablePrefix, originalTable, table,
                defaultCatalog, defaultSchema, ddl);
    }

    public String createPostTriggerDDL(DataEventType dml, Trigger trigger, TriggerHistory history,
            Channel channel, String tablePrefix, Table originalTable, String defaultCatalog,
            String defaultSchema) {
        Table table = originalTable.copyAndFilterColumns(history.getParsedColumnNames(),
                history.getParsedPkColumnNames(), true, false);
        String ddl = sqlTemplates.get(dml.name().toLowerCase(Locale.US) + "PostTriggerTemplate");
        return replaceTemplateVariables(dml, trigger, history, channel, tablePrefix, originalTable, table,
                defaultCatalog, defaultSchema, ddl);
    }

    public String createDdlTrigger(String tablePrefix, String defaultCatalog, String defaultSchema, String triggerName) {
        String ddl;
        if (symmetricDialect.getParameterService().is(ParameterConstants.TRIGGER_CAPTURE_DDL_CHECK_TRIGGER_HIST, true)) {
            ddl = sqlTemplates.get("filteredDdlTriggerTemplate");
        } else {
            ddl = sqlTemplates.get("allDdlTriggerTemplate");
        }
        if (ddl == null) {
            return null;
        }
        ddl = FormatUtils.replace("triggerName", triggerName, ddl);
        ddl = FormatUtils.replace("prefixName", tablePrefix, ddl);
        ddl = replaceDefaultSchemaAndCatalog(ddl, defaultCatalog, defaultSchema);
        return ddl;
    }

    public String createPostDdlTriggerDDL(String tablePrefix, String triggerName) {
        String ddl = sqlTemplates.get("postDdlTriggerTemplate");
        if (ddl == null) {
            return null;
        }
        ddl = FormatUtils.replace("triggerName", triggerName, ddl);
        ddl = FormatUtils.replace("prefixName", tablePrefix, ddl);
        ddl = replaceDefaultSchemaAndCatalog(ddl);
        return ddl;
    }

    protected String getDefaultTargetTableName(Trigger trigger, TriggerHistory history) {
        String targetTableName = null;
        if (history != null) {
            targetTableName = history.getSourceTableName();
        } else {
            targetTableName = trigger.getSourceTableNameUnescaped();
        }
        return targetTableName;
    }

    protected String replaceTemplateVariables(DataEventType dml, Trigger trigger,
            TriggerHistory history, Channel channel, String tablePrefix, Table originalTable, Table table,
            String defaultCatalog, String defaultSchema, String ddl) {
        // We have a special case for this special template variable.
        // We are replacing this template variable with other template variables
        // Only replace this special variable with a template variable for the following combined case
        // Otherwise, just replace with $(channelExpression) and let normal template variable replacement do its thing.
        if (trigger.getChannelId().equals(Constants.CHANNEL_DYNAMIC) && dml.getDmlType() == DmlType.UPDATE
                && TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_SNAPSHOT).equals(table.getName())) {
            ddl = FormatUtils.replace("specialSqlServerSybaseChannelExpression", "$(oldTriggerValue).$(oldColumnPrefix)" + symmetricDialect.getPlatform()
                    .alterCaseToMatchDatabaseDefaultCase("channel_id"), ddl);
        } else {
            ddl = FormatUtils.replace("specialSqlServerSybaseChannelExpression", "$(channelExpression)", ddl);
        }
        ddl = FormatUtils.replace("specialSqlServerSybaseChannelExpression", getChannelExpression(), ddl);
        ddl = FormatUtils.replace("targetTableName", getDefaultTargetTableName(trigger, history),
                ddl);
        ddl = FormatUtils.replace("triggerName", history.getTriggerNameForDmlType(dml), ddl);
        ddl = FormatUtils.replace("channelName", trigger.getChannelId(), ddl);
        ddl = FormatUtils.replace("triggerHistoryId", Integer.toString(history.getTriggerHistoryId()), ddl);
        String triggerExpression = symmetricDialect.getTransactionTriggerExpression(defaultCatalog,
                defaultSchema, trigger);
        if (symmetricDialect.isTransactionIdOverrideSupported()
                && !StringUtils.isBlank(trigger.getTxIdExpression())) {
            triggerExpression = trigger.getTxIdExpression();
        }
        ddl = FormatUtils.replace("txIdExpression",
                symmetricDialect.preProcessTriggerSqlClause(triggerExpression), ddl);
        ddl = FormatUtils.replace("channelExpression", symmetricDialect.preProcessTriggerSqlClause(
                getChannelExpression(trigger, history, originalTable)), ddl);
        ddl = FormatUtils.replace("externalSelectForDelete", (StringUtils.isBlank(trigger.getExternalSelect()) ? "null"
                : "(" + convertExternalSelectToDelete(symmetricDialect.preProcessTriggerSqlClause(trigger.getExternalSelect()))
                        + ")"), ddl);
        ddl = FormatUtils.replace("externalSelectForInsert", (StringUtils.isBlank(trigger.getExternalSelect()) ? "null"
                : "(" + convertExternalSelectToInsert(symmetricDialect.preProcessTriggerSqlClause(trigger.getExternalSelect()))
                        + ")"), ddl);
        ddl = FormatUtils.replace("externalSelect", (StringUtils.isBlank(trigger.getExternalSelect()) ? "null"
                : "(" + symmetricDialect.preProcessTriggerSqlClause(trigger.getExternalSelect())
                        + ")"), ddl);
        ddl = FormatUtils.replace("syncOnInsertCondition",
                symmetricDialect.preProcessTriggerSqlClause(trigger.getSyncOnInsertCondition()),
                ddl);
        ddl = FormatUtils.replace("syncOnUpdateCondition",
                symmetricDialect.preProcessTriggerSqlClause(trigger.getSyncOnUpdateCondition()),
                ddl);
        ddl = FormatUtils.replace("syncOnDeleteCondition",
                symmetricDialect.preProcessTriggerSqlClause(trigger.getSyncOnDeleteCondition()),
                ddl);
        ddl = FormatUtils.replace("custom_before_insert_text",
                StringUtils.isBlank(trigger.getCustomBeforeInsertText()) ? "" : trigger.getCustomBeforeInsertText(), ddl);
        ddl = FormatUtils.replace("custom_before_update_text",
                StringUtils.isBlank(trigger.getCustomBeforeUpdateText()) ? "" : trigger.getCustomBeforeUpdateText(), ddl);
        ddl = FormatUtils.replace("custom_before_delete_text",
                StringUtils.isBlank(trigger.getCustomBeforeDeleteText()) ? "" : trigger.getCustomBeforeDeleteText(), ddl);
        ddl = FormatUtils.replace("custom_on_insert_text",
                StringUtils.isBlank(trigger.getCustomOnInsertText()) ? "" : trigger.getCustomOnInsertText(), ddl);
        ddl = FormatUtils.replace("custom_on_update_text",
                StringUtils.isBlank(trigger.getCustomOnUpdateText()) ? "" : trigger.getCustomOnUpdateText(), ddl);
        ddl = FormatUtils.replace("custom_on_delete_text",
                StringUtils.isBlank(trigger.getCustomOnDeleteText()) ? "" : trigger.getCustomOnDeleteText(), ddl);
        ddl = FormatUtils.replace("dataHasChangedCondition", symmetricDialect
                .preProcessTriggerSqlClause(symmetricDialect.getDataHasChangedCondition(trigger)),
                ddl);
        Trigger clonedTrigger = new Trigger();
        clonedTrigger.setUseCaptureLobs(true);
        ddl = FormatUtils.replace("dataHasChangedConditionClobAlways", symmetricDialect
                .preProcessTriggerSqlClause(symmetricDialect.getDataHasChangedCondition(clonedTrigger)),
                ddl);
        ddl = FormatUtils.replace("sourceNodeExpression",
                symmetricDialect.getSourceNodeExpression(), ddl);
        ddl = FormatUtils.replace("lobType", trigger.isUseCaptureLobs() ? getClobType(table) : getVarcharLobType(table), ddl);
        ddl = FormatUtils.replace("lobTypeClobAlways", getClobType(table), ddl);
        String syncTriggersExpression = symmetricDialect.getSyncTriggersExpression();
        ddl = FormatUtils.replace("syncOnIncomingBatchCondition",
                trigger.isSyncOnIncomingBatch() ? symmetricDialect.getSyncTriggersOnIncomingExpression()
                        : syncTriggersExpression, ddl);
        ddl = FormatUtils.replace("origTableAlias", ORIG_TABLE_ALIAS, ddl);
        Column[] orderedColumns = table.getColumns();
        ColumnString columnString = buildColumnsString(ORIG_TABLE_ALIAS, newTriggerValue,
                newColumnPrefix, table, orderedColumns, dml, false, channel, trigger);
        ddl = FormatUtils.replace("columns", columnString.toString(), ddl);
        Channel clonedChannel = new Channel();
        clonedChannel.setContainsBigLob(true);
        ColumnString columnClobAlways = buildColumnsString(ORIG_TABLE_ALIAS, newTriggerValue,
                newColumnPrefix, table, orderedColumns, dml, false, clonedChannel, trigger);
        String columnClobAlwaysString = FormatUtils.replace("toClob", toClobExpression(table), columnClobAlways.toString());
        ddl = FormatUtils.replace("columnsClobAlways", columnClobAlwaysString, ddl);
        ColumnString oldColumnsClobAlways = buildColumnsString(ORIG_TABLE_ALIAS,
                oldTriggerValue, oldColumnPrefix, table, orderedColumns, dml, true, clonedChannel, trigger);
        String oldColumnsClobAlwaysString = FormatUtils.replace("toClob", toClobExpression(table), oldColumnsClobAlways.toString());
        ddl = FormatUtils.replace("oldColumnsClobAlways",
                trigger.isUseCaptureOldData() ? oldColumnsClobAlwaysString : "null", ddl);
        ddl = replaceDefaultSchemaAndCatalog(ddl);
        ddl = FormatUtils.replace("virtualOldNewTable",
                buildVirtualTableSql(oldColumnPrefix, newColumnPrefix, originalTable.getColumns()), ddl);
        ddl = FormatUtils.replace(
                "oldColumns",
                trigger.isUseCaptureOldData() ? buildColumnsString(ORIG_TABLE_ALIAS,
                        oldTriggerValue, oldColumnPrefix, table, orderedColumns, dml, true, channel,
                        trigger).toString() : "null", ddl);
        String oldddl = null;
        for (oldddl = null; ddl != null && !ddl.equals(oldddl); ddl = this
                .eval(columnString.isBlobClob && !trigger.isUseStreamLobs(), "containsBlobClobColumns", ddl)) {
            oldddl = ddl;
        }
        oldddl = null;
        // some column templates need tableName and schemaName
        ddl = FormatUtils.replace("tableName", SymmetricUtils.quote(symmetricDialect, table.getName()), ddl);
        ddl = FormatUtils.replace("schemaName", getSourceTablePrefix(history), ddl);
        ddl = FormatUtils.replace("schemaNameOnly", getSchemaNameOnly(history), ddl);
        Column[] primaryKeyColumns = table.getPrimaryKeyColumns();
        ddl = FormatUtils.replace(
                "oldKeys",
                buildColumnsString(ORIG_TABLE_ALIAS, oldTriggerValue, oldColumnPrefix,
                        table, primaryKeyColumns, dml, true, channel, trigger).toString(), ddl);
        ddl = FormatUtils.replace(
                "newKeys",
                buildColumnsString(ORIG_TABLE_ALIAS, newTriggerValue, newColumnPrefix,
                        table, primaryKeyColumns, dml, true, channel, trigger).toString(), ddl);
        ddl = FormatUtils.replace(
                "oldNewPrimaryKeyJoin",
                aliasedPrimaryKeyJoin(oldTriggerValue, newTriggerValue,
                        primaryKeyColumns.length == 0 ? orderedColumns : primaryKeyColumns), ddl);
        ddl = FormatUtils.replace(
                "tableNewPrimaryKeyJoin",
                aliasedPrimaryKeyJoin(ORIG_TABLE_ALIAS, newTriggerValue,
                        primaryKeyColumns.length == 0 ? orderedColumns : primaryKeyColumns), ddl);
        ddl = FormatUtils.replace(
                "tableNewPrimaryKeyJoinByTableName",
                aliasedPrimaryKeyJoin(SymmetricUtils.quote(symmetricDialect, table.getName()), newTriggerValue,
                        primaryKeyColumns.length == 0 ? orderedColumns : primaryKeyColumns), ddl);
        ddl = FormatUtils.replace(
                "primaryKeyWhereString",
                getPrimaryKeyWhereString(dml == DataEventType.DELETE ? oldTriggerValue
                        : newTriggerValue, table.hasPrimaryKey() ? table.getPrimaryKeyColumns()
                                : table.getColumns()), ddl);
        String builtString = buildColumnNameString(oldTriggerValue, true, trigger,
                primaryKeyColumns);
        ddl = FormatUtils.replace("oldKeyNames", StringUtils.isNotBlank(builtString) ? ","
                + builtString : "", ddl);
        builtString = buildColumnNameString(newTriggerValue, true, trigger, primaryKeyColumns);
        ddl = FormatUtils.replace("newKeyNames", StringUtils.isNotBlank(builtString) ? ","
                + builtString : "", ddl);
        ddl = FormatUtils.replace("columnNames",
                buildColumnNameString(null, false, trigger, orderedColumns), ddl);
        ddl = FormatUtils.replace("pkColumnNames",
                buildColumnNameString(null, false, trigger, primaryKeyColumns), ddl);
        builtString = buildKeyVariablesString(primaryKeyColumns, "old");
        ddl = FormatUtils.replace("oldKeyVariables", StringUtils.isNotBlank(builtString) ? ","
                + builtString : "", ddl);
        builtString = buildKeyVariablesString(primaryKeyColumns, "new");
        ddl = FormatUtils.replace("newKeyVariables", StringUtils.isNotBlank(builtString) ? ","
                + builtString : "", ddl);
        ddl = FormatUtils.replace("varNewPrimaryKeyJoin",
                aliasedPrimaryKeyJoinVar(newTriggerValue, "new", primaryKeyColumns), ddl);
        ddl = FormatUtils.replace("varOldPrimaryKeyJoin",
                aliasedPrimaryKeyJoinVar(oldTriggerValue, "old", primaryKeyColumns), ddl);
        // replace $(newTriggerValue) and $(oldTriggerValue)
        ddl = FormatUtils.replace("newTriggerValue", newTriggerValue, ddl);
        ddl = FormatUtils.replace("oldTriggerValue", oldTriggerValue, ddl);
        ddl = FormatUtils.replace("newColumnPrefix", newColumnPrefix, ddl);
        ddl = FormatUtils.replace("oldColumnPrefix", oldColumnPrefix, ddl);
        ddl = FormatUtils.replace("prefixName", tablePrefix, ddl);
        ddl = replaceDefaultSchemaAndCatalog(ddl);
        ddl = FormatUtils.replace("hasPrimaryKeysDefined", getHasPrimaryKeysDefinedString(table), ddl);
        ddl = FormatUtils.replace("primaryKeysUpdated", getPrimaryKeysUpdatedString(table), ddl);
        ddl = FormatUtils.replace("toClob",
                trigger.isUseCaptureLobs() ? toClobExpression(table) : "", ddl);
        ddl = FormatUtils.replace("toClobAlways", toClobExpression(table), ddl);
        switch (dml) {
            case DELETE:
                ddl = FormatUtils.replace("curTriggerValue", oldTriggerValue, ddl);
                ddl = FormatUtils.replace("curColumnPrefix", oldColumnPrefix, ddl);
                break;
            case INSERT:
            case UPDATE:
            default:
                ddl = FormatUtils.replace("curTriggerValue", newTriggerValue, ddl);
                ddl = FormatUtils.replace("curColumnPrefix", newColumnPrefix, ddl);
                break;
        }
        return ddl;
    }

    private String convertExternalSelectToDelete(String externalSelect) {
        externalSelect = FormatUtils.replace("curTriggerValue", oldTriggerValue, externalSelect);
        externalSelect = FormatUtils.replace("curColumnPrefix", oldColumnPrefix, externalSelect);
        return externalSelect;
    }

    private String convertExternalSelectToInsert(String externalSelect) {
        externalSelect = FormatUtils.replace("curTriggerValue", newTriggerValue, externalSelect);
        externalSelect = FormatUtils.replace("curColumnPrefix", newColumnPrefix, externalSelect);
        return externalSelect;
    }

    private String getChannelExpression() {
        return null;
    }

    protected String toClobExpression(Table table) {
        return "";
    }

    protected String getClobType(Table table) {
        return "clob";
    }

    protected String getVarcharLobType(Table table) {
        return "";
    }

    protected String getChannelExpression(Trigger trigger, TriggerHistory history, Table originalTable) {
        if (trigger.getChannelId().equals(Constants.CHANNEL_DYNAMIC)) {
            if (StringUtils.isNotBlank(trigger.getChannelExpression())) {
                String expr = trigger.getChannelExpression();
                expr = FormatUtils.replace("schemaName", history == null ? getSourceTableSchema(originalTable)
                        : getSourceTableSchema(history), expr);
                return expr;
            } else {
                throw new IllegalStateException("When the channel is set to '" + Constants.CHANNEL_DYNAMIC + "', a channel expression must be provided.");
            }
        } else {
            return "'" + trigger.getChannelId() + "'";
        }
    }

    protected String buildVirtualTableSql(String oldTriggerValue, String newTriggerValue,
            Column[] columns) {
        if (oldTriggerValue.indexOf(".") >= 0) {
            oldTriggerValue = oldTriggerValue.substring(oldTriggerValue.indexOf(".") + 1);
        }
        if (newTriggerValue.indexOf(".") >= 0) {
            newTriggerValue = newTriggerValue.substring(newTriggerValue.indexOf(".") + 1);
        }
        StringBuilder b = new StringBuilder("(SELECT ");
        for (Column columnType : columns) {
            String column = columnType.getName();
            b.append("? as ");
            b.append("\"").append(newTriggerValue).append(column).append("\",");
        }
        for (Column columnType : columns) {
            String column = columnType.getName();
            b.append("? AS ");
            b.append("\"").append(oldTriggerValue).append(column).append("\",");
        }
        b.deleteCharAt(b.length() - 1);
        b.append(" FROM DUAL) T ");
        return b.toString();
    }

    protected String eval(boolean condition, String prop, String ddl) {
        if (ddl != null) {
            String ifStmt = "$(if:" + prop + ")";
            String elseStmt = "$(else:" + prop + ")";
            String endStmt = "$(end:" + prop + ")";
            int ifIndex = ddl.indexOf(ifStmt);
            if (ifIndex >= 0) {
                int endIndex = ddl.indexOf(endStmt);
                if (endIndex >= 0) {
                    String onTrue = ddl.substring(ifIndex + ifStmt.length(), endIndex);
                    String onFalse = "";
                    int elseIndex = onTrue.indexOf(elseStmt);
                    if (elseIndex >= 0) {
                        onFalse = onTrue.substring(elseIndex + elseStmt.length());
                        onTrue = onTrue.substring(0, elseIndex);
                    }
                    if (condition) {
                        ddl = ddl.substring(0, ifIndex) + onTrue
                                + ddl.substring(endIndex + endStmt.length());
                    } else {
                        ddl = ddl.substring(0, ifIndex) + onFalse
                                + ddl.substring(endIndex + endStmt.length());
                    }
                } else {
                    throw new IllegalStateException(ifStmt + " has to have a " + endStmt);
                }
            }
        }
        return ddl;
    }

    /***
     * Builds join with all primary key columns pairs prefixed by the new and old aliases.
     */
    protected String aliasedPrimaryKeyJoin(String aliasOne, String aliasTwo, Column[] columns) {
        StringBuilder b = new StringBuilder();
        for (int columnNo = 0; columnNo < columns.length; columnNo++) {
            Column column = columns[columnNo];
            String quotedColumnName = SymmetricUtils.quote(symmetricDialect, column.getName());
            if (columnNo > 0) {
                b.append(" and ");
            }
            if (!column.isRequired()) {
                b.append("(");
            }
            b.append(aliasOne).append(".").append(quotedColumnName);
            b.append("=").append(aliasTwo).append(".").append(quotedColumnName);
            if (!column.isRequired()) {
                b.append(" or (");
                b.append(aliasOne).append(".").append(quotedColumnName).append(" is null");
                b.append(" and ");
                b.append(aliasTwo).append(".").append(quotedColumnName).append(" is null))");
            }
        }
        return b.toString();
    }

    /***
     * Builds join with all primary key columns paired with prefixed parameter values.
     */
    protected String aliasedPrimaryKeyJoinVar(String alias, String prefix, Column[] columns) {
        StringBuilder b = new StringBuilder();
        for (int columnNo = 0; columnNo < columns.length; columnNo++) {
            Column column = columns[columnNo];
            String quotedColumnName = SymmetricUtils.quote(symmetricDialect, column.getName());
            String paramName = String.format("@%spk%d", prefix, columnNo);
            if (columnNo > 0) {
                b.append(" and ");
            }
            if (!column.isRequired()) {
                b.append("(");
            }
            b.append(alias).append(".").append(quotedColumnName);
            b.append("=").append(paramName);
            if (!column.isRequired()) {
                b.append(" or (");
                b.append(alias).append(".").append(quotedColumnName).append(" is null");
                b.append(" and ");
                b.append(paramName).append(" is null))");
            }
        }
        return b.toString();
    }

    /**
     * Specific to Derby. Needs to be removed when the initial load is refactored to concat in Java vs. in SQL
     */
    @Deprecated
    protected String getPrimaryKeyWhereString(String alias, Column[] columns) {
        return null;
    }

    protected boolean requiresWrappedBlobTemplateForBlobType() {
        return false;
    }

    protected boolean requiresEmptyLobTemplateForDeletes() {
        return false;
    }

    /***
     * Helps detect Large Object columns. Some LOBs are inaccessible to triggers or require specialized code.
     */
    protected boolean isLob(Column column) {
        return symmetricDialect.getPlatform().isLob(column);
    }

    protected String buildColumnNameString(String tableAlias, boolean quote, Trigger trigger,
            Column[] columns) {
        StringBuilder columnsText = new StringBuilder();
        for (Column column : columns) {
            boolean isLob = this.isLob(column);
            String columnName = column.getName();
            if (quote) {
                columnName = SymmetricUtils.quote(symmetricDialect, columnName);
            }
            if (!(isLob && trigger.isUseStreamLobs())) {
                if (StringUtils.isNotBlank(tableAlias)) {
                    columnsText.append(tableAlias);
                    columnsText.append(".");
                }
                columnsText.append(columnName);
            } else {
                columnsText.append("null");
            }
            columnsText.append(",");
        }
        return columnsText.length() > 0 ? columnsText.substring(0, columnsText.length() - 1) : columnsText.toString();
    }

    protected ColumnString buildColumnsString(String origTableAlias, String tableAlias,
            String columnPrefix, Table table, Column[] columns, DataEventType dml, boolean isOld,
            Channel channel, Trigger trigger) {
        String columnsText = "";
        boolean containsLob = false;
        String lastCommandToken = symmetricDialect.escapesTemplatesForDatabaseInserts() ? (triggerConcatCharacter
                + "'',''" + triggerConcatCharacter)
                : (triggerConcatCharacter + "','" + triggerConcatCharacter);
        for (int i = 0; i < columns.length; i++) {
            Column column = columns[i];
            if (column != null) {
                ColumnString columnString = fillOutColumnTemplate(origTableAlias, tableAlias,
                        columnPrefix, table, column, dml, isOld, channel, trigger, false);
                columnsText = columnsText + "\n          " + columnString.columnString
                        + lastCommandToken;
                containsLob |= columnString.isBlobClob;
            }
        }
        if (columnsText.endsWith(lastCommandToken)) {
            columnsText = columnsText
                    .substring(0, columnsText.length() - lastCommandToken.length());
        }
        return new ColumnString(columnsText, containsLob);
    }

    protected ColumnString fillOutColumnTemplate(String origTableAlias, String tableAlias,
            String columnPrefix, Table table, Column column, DataEventType dml, boolean isOld, Channel channel,
            Trigger trigger, boolean ignoreStreamLobs) {
        boolean isLob = this.isLob(column);
        String templateToUse = null;
        if (column.getJdbcTypeName() != null
                && (column.getJdbcTypeName().toUpperCase().contains(TypeMap.GEOMETRY))
                && StringUtils.isNotBlank(geometryColumnTemplate)) {
            templateToUse = geometryColumnTemplate;
        } else if (column.getJdbcTypeName() != null
                && (column.getJdbcTypeName().toUpperCase().contains(TypeMap.GEOGRAPHY))
                && StringUtils.isNotBlank(geographyColumnTemplate)) {
            templateToUse = geographyColumnTemplate;
        } else {
            switch (column.getMappedTypeCode()) {
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.INTEGER:
                case Types.BIGINT:
                case Types.FLOAT:
                case Types.REAL:
                case Types.DOUBLE:
                case Types.NUMERIC:
                case Types.DECIMAL:
                    templateToUse = numberColumnTemplate;
                    if (moneyColumnTemplate != null && column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains("MONEY")) {
                        templateToUse = moneyColumnTemplate;
                    }
                    break;
                case Types.CHAR:
                case Types.NCHAR:
                case Types.VARCHAR:
                case ColumnTypes.NVARCHAR:
                    templateToUse = stringColumnTemplate;
                    break;
                case ColumnTypes.SQLXML:
                    templateToUse = xmlColumnTemplate;
                    break;
                case Types.ARRAY:
                    templateToUse = arrayColumnTemplate;
                    break;
                case Types.LONGVARCHAR:
                case ColumnTypes.LONGNVARCHAR:
                    if (column.getJdbcTypeName().equalsIgnoreCase("LONG") && isNotBlank(longColumnTemplate)) {
                        templateToUse = longColumnTemplate;
                        isLob = false;
                        break;
                    } else if (!isLob) {
                        templateToUse = stringColumnTemplate;
                        templateToUse = FormatUtils.replace("columnSizeOrMax", "max", templateToUse);
                        break;
                    }
                case Types.CLOB:
                case Types.NCLOB:
                    if (isOld && symmetricDialect.needsToSelectLobData()) {
                        templateToUse = emptyColumnTemplate;
                    } else {
                        templateToUse = clobColumnTemplate;
                    }
                    break;
                case Types.BINARY:
                case Types.VARBINARY:
                    if (isNotBlank(binaryColumnTemplate)) {
                        templateToUse = binaryColumnTemplate;
                        break;
                    }
                case Types.BLOB:
                    if (requiresWrappedBlobTemplateForBlobType()) {
                        templateToUse = wrappedBlobColumnTemplate;
                        break;
                    }
                case Types.LONGVARBINARY:
                case ColumnTypes.MSSQL_NTEXT:
                    if (column.getJdbcTypeName() != null
                            && (column.getJdbcTypeName().toUpperCase().contains(TypeMap.IMAGE))
                            && StringUtils.isNotBlank(imageColumnTemplate)) {
                        if (isOld) {
                            templateToUse = emptyColumnTemplate;
                        } else {
                            templateToUse = imageColumnTemplate;
                        }
                    } else if (isOld && symmetricDialect.needsToSelectLobData()) {
                        templateToUse = emptyColumnTemplate;
                    } else {
                        templateToUse = blobColumnTemplate;
                    }
                    break;
                case Types.DATE:
                    if (noDateColumnTemplate()) {
                        templateToUse = datetimeColumnTemplate;
                        break;
                    }
                    templateToUse = dateColumnTemplate;
                    break;
                case Types.TIME:
                    if (noTimeColumnTemplate()) {
                        templateToUse = datetimeColumnTemplate;
                        break;
                    }
                    templateToUse = timeColumnTemplate;
                    break;
                case Types.TIMESTAMP:
                    templateToUse = datetimeColumnTemplate;
                    break;
                case Types.BOOLEAN:
                case Types.BIT:
                    templateToUse = booleanColumnTemplate;
                    break;
                case Types.ROWID:
                    templateToUse = stringColumnTemplate;
                    break;
                case ColumnTypes.MSSQL_SQL_VARIANT:
                    templateToUse = sqlVariantColumnTemplate;
                    break;
                default:
                    if (column.getJdbcTypeName() != null) {
                        if (column.getJdbcTypeName().toUpperCase().equals(TypeMap.INTERVAL)) {
                            templateToUse = numberColumnTemplate;
                            break;
                        } else if (column.getMappedType().equals(TypeMap.TIMESTAMPTZ)
                                && StringUtils.isNotBlank(this.dateTimeWithTimeZoneColumnTemplate)) {
                            templateToUse = this.dateTimeWithTimeZoneColumnTemplate;
                            break;
                        } else if (column.getMappedType().equals(TypeMap.TIMESTAMPLTZ)
                                && StringUtils
                                        .isNotBlank(this.dateTimeWithLocalTimeZoneColumnTemplate)) {
                            templateToUse = this.dateTimeWithLocalTimeZoneColumnTemplate;
                            break;
                        }
                    }
                    if (StringUtils.isBlank(templateToUse)
                            && StringUtils.isNotBlank(this.otherColumnTemplate)) {
                        templateToUse = this.otherColumnTemplate;
                        break;
                    }
                    throw new NotImplementedException(column.getName() + " is of type "
                            + column.getMappedType() + " with JDBC type of "
                            + column.getJdbcTypeName());
            }
        }
        if (dml == DataEventType.DELETE && isLob && requiresEmptyLobTemplateForDeletes()) {
            templateToUse = emptyColumnTemplate;
        } else if (isLob && trigger.isUseStreamLobs() && !ignoreStreamLobs) {
            templateToUse = emptyColumnTemplate;
        }
        if (templateToUse != null) {
            templateToUse = adjustColumnTemplate(templateToUse, column.getMappedTypeCode());
            templateToUse = templateToUse.trim();
        } else {
            throw new NotImplementedException(table.toString() + " " + column.toString());
        }
        String formattedColumnText = FormatUtils.replace("columnSizeOrMax",
                trigger.isUseCaptureLobs() ? "max" : "$(columnSize)", templateToUse);
        formattedColumnText = FormatUtils.replace("columnName",
                String.format("%s%s", columnPrefix, column.getName()), formattedColumnText);
        formattedColumnText = FormatUtils.replace("columnSize",
                getColumnSize(table, column), formattedColumnText);
        formattedColumnText = FormatUtils.replace("masterCollation",
                symmetricDialect.getMasterCollation(), formattedColumnText);
        if (isLob) {
            formattedColumnText = symmetricDialect.massageForLob(formattedColumnText, channel != null ? channel.isContainsBigLob() : true);
        }
        formattedColumnText = FormatUtils.replace("origTableAlias", origTableAlias,
                formattedColumnText);
        formattedColumnText = FormatUtils.replace("tableAlias", tableAlias, formattedColumnText);
        formattedColumnText = FormatUtils.replace("prefixName", symmetricDialect.getTablePrefix(),
                formattedColumnText);
        return new ColumnString(formattedColumnText, isLob);
    }

    protected String getColumnSize(Table table, Column column) {
        return column.getSize();
    }

    public String getOtherColumnTemplate() {
        return otherColumnTemplate;
    }

    protected boolean noTimeColumnTemplate() {
        return timeColumnTemplate == null || timeColumnTemplate.equals("null")
                || timeColumnTemplate.trim().equals("");
    }

    protected boolean noDateColumnTemplate() {
        return dateColumnTemplate == null || dateColumnTemplate.equals("null")
                || dateColumnTemplate.trim().equals("");
    }

    protected String adjustColumnTemplate(String template, int typeCode) {
        return template;
    }

    protected String buildKeyVariablesDeclare(Column[] columns, String prefix) {
        String text = "";
        for (int i = 0; i < columns.length; i++) {
            text += "declare @" + prefix + "pk" + i + " ";
            switch (columns[i].getMappedTypeCode()) {
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.INTEGER:
                case Types.BIGINT:
                    text += "bigint\n";
                    break;
                case Types.NUMERIC:
                case Types.DECIMAL:
                    text += "decimal\n";
                    break;
                case Types.FLOAT:
                case Types.REAL:
                case Types.DOUBLE:
                    text += "float\n";
                    break;
                case Types.CHAR:
                case Types.VARCHAR:
                case Types.NCHAR:
                case ColumnTypes.NVARCHAR:
                case ColumnTypes.LONGNVARCHAR:
                case Types.LONGVARCHAR:
                    text += "varchar(1000)\n";
                    break;
                case Types.DATE:
                    text += "date\n";
                    break;
                case Types.TIME:
                    text += "time\n";
                    break;
                case Types.TIMESTAMP:
                    text += "datetime\n";
                    break;
                case Types.BOOLEAN:
                case Types.BIT:
                    text += "bit\n";
                    break;
                case Types.CLOB:
                    text += "varchar(max)\n";
                    break;
                case Types.BLOB:
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                case -10: // SQL-Server ntext binary type
                    text += "varbinary(max)\n";
                    break;
                case Types.OTHER:
                    text += "varbinary(max)\n";
                    break;
                default:
                    if (columns[i].getJdbcTypeName() != null
                            && columns[i].getJdbcTypeName().equalsIgnoreCase("interval")) {
                        text += "interval";
                        break;
                    }
                    throw new NotImplementedException(columns[i] + " is of type "
                            + columns[i].getMappedType());
            }
        }
        return text;
    }

    protected String buildKeyVariablesString(Column[] columns, String prefix) {
        String text = "";
        for (int i = 0; i < columns.length; i++) {
            text += "@" + prefix + "pk" + i;
            if (i + 1 < columns.length) {
                text += ", ";
            }
        }
        return text;
    }

    public String getClobColumnTemplate() {
        return clobColumnTemplate;
    }

    public void setBooleanColumnTemplate(String booleanColumnTemplate) {
        this.booleanColumnTemplate = booleanColumnTemplate;
    }

    public String getNewTriggerValue() {
        return newTriggerValue;
    }

    public String getOldTriggerValue() {
        return oldTriggerValue;
    }

    public String getBlobColumnTemplate() {
        return blobColumnTemplate;
    }

    public String getWrappedBlobColumnTemplate() {
        return wrappedBlobColumnTemplate;
    }

    protected String replaceDefaultSchema(String ddl, String defaultSchema) {
        if (StringUtils.isNotBlank(defaultSchema)) {
            ddl = FormatUtils.replace("defaultSchema", SymmetricUtils.quote(symmetricDialect, defaultSchema) + ".", ddl);
        } else {
            ddl = FormatUtils.replace("defaultSchema", "", ddl);
        }
        return ddl;
    }

    protected String replaceDefaultCatalog(String ddl, String defaultCatalog) {
        if (StringUtils.isNotBlank(defaultCatalog)) {
            ddl = FormatUtils.replace("defaultCatalog", SymmetricUtils.quote(symmetricDialect, defaultCatalog) + ".", ddl);
        } else {
            ddl = FormatUtils.replace("defaultCatalog", "", ddl);
        }
        return ddl;
    }

    public String getTimeColumnTemplate() {
        return timeColumnTemplate;
    }

    public void setTimeColumnTemplate(String timeColumnTemplate) {
        this.timeColumnTemplate = timeColumnTemplate;
    }

    public String getDateColumnTemplate() {
        return dateColumnTemplate;
    }

    public void setDateColumnTemplate(String dateColumnTemplate) {
        this.dateColumnTemplate = dateColumnTemplate;
    }

    public String getImageColumnTemplate() {
        return imageColumnTemplate;
    }

    public void setImageColumnTemplate(String imageColumnTemplate) {
        this.imageColumnTemplate = imageColumnTemplate;
    }

    protected static class ColumnString {
        public String columnString;
        public boolean isBlobClob = false;

        public ColumnString(String columnExpression, boolean isBlobClob) {
            this.columnString = columnExpression;
            this.isBlobClob = isBlobClob;
        }

        @Override
        public String toString() {
            return StringUtils.isBlank(columnString) ? "null" : columnString;
        }
    }

    public int toHashedValue() {
        if (hashedValue == 0 && sqlTemplates != null) {
            for (String key : sqlTemplates.keySet()) {
                hashedValue += sqlTemplates.get(key).hashCode();
            }
            Field[] fields = getClass().getSuperclass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                if (field.getType().equals(String.class)) {
                    try {
                        String value = (String) field.get(this);
                        if (value != null) {
                            hashedValue += value.hashCode();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get hash code for field " + field.getName());
                    }
                }
            }
        }
        return hashedValue;
    }

    protected String getHasPrimaryKeysDefinedString(Table table) {
        return table.hasPrimaryKey() ? "1=1" : "1=2";
    }

    protected String getPrimaryKeysUpdatedString(Table table) {
        StringBuilder sb = new StringBuilder();
        for (String primaryKey : table.getPrimaryKeyColumnNames()) {
            if (sb.length() > 0) {
                sb.append(" OR ");
            } else {
                sb.append("(");
            }
            sb.append(" UPDATE(").append(SymmetricUtils.quote(symmetricDialect, primaryKey)).append(") ");
        }
        if (sb.length() > 0) {
            sb.append(")");
        }
        if (sb.length() > 0) {
            sb.insert(0, " AND ");
        }
        return sb.toString();
    }

    protected String getCreateTriggerString() {
        return "create trigger";
    }
}
