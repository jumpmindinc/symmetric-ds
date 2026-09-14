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
package org.jumpmind.symmetric.db.sqlite;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.HashMap;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.util.FormatUtils;

public class SqliteTriggerTemplate extends AbstractTriggerTemplate {
    public SqliteTriggerTemplate(AbstractSymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        String sqliteFunctionToOverride = symmetricDialect.getParameterService().getString(ParameterConstants.SQLITE_TRIGGER_FUNCTION_TO_USE);
        String sourceNodeExpression;
        if (isBlank(sqliteFunctionToOverride)) {
            sourceNodeExpression = "(select context_value from $(prefixName)_context where name = 'sync_node_disabled')";
        } else {
            sourceNodeExpression = "(select substr(" + sqliteFunctionToOverride + "(), 10) from sqlite_master where " + sqliteFunctionToOverride
                    + "() like 'DISABLED:%')";
        }
        // formatter:off
        triggerConcatCharacter = "||";
        newTriggerValue = "new";
        oldTriggerValue = "old";
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace($(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"') || '\"' end";
        clobColumnTemplate = stringColumnTemplate;
        emptyColumnTemplate = "''";
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else ('\"' || cast($(tableAlias).\"$(columnName)\" as varchar) || '\"') end";
        datetimeColumnTemplate = "case when strftime('%Y-%m-%d %H:%M:%f',$(tableAlias).\"$(columnName)\") is null then '' else ('\"' || strftime('%Y-%m-%d %H:%M:%f', $(tableAlias).\"$(columnName)\") || '\"') end";
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when $(tableAlias).\"$(columnName)\" = 1 then '\"1\"' else '\"0\"' end";
        blobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(hex($(tableAlias).\"$(columnName)\"),'\\','\\\\'),'\"','\\\"') || '\"' end ";
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates
                .put("insertTriggerTemplate",
                        "create trigger $(triggerName) after insert on $(schemaName)$(tableName)    \n"
                                + "for each row     \n"
                                + "  when ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition))    \n"
                                + "  begin    \n"
                                + "    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)    \n"
                                + "    values(    \n" + "      '$(targetTableName)',    \n" + "      'I',    \n"
                                + "      $(triggerHistoryId),                                          \n"
                                + "      $(columns),    \n" + "      $(channelExpression), null," + sourceNodeExpression + ",    \n"
                                + "      $(externalSelect),    \n" + "     strftime('%Y-%m-%d %H:%M:%f','now','localtime')    \n" + "    );    \n"
                                + "        $(custom_on_insert_text)                                                                            \n"
                                + "end");
        sqlTemplates
                .put("insertReloadTriggerTemplate",
                        "create trigger $(triggerName) after insert on $(schemaName)$(tableName)    \n"
                                + "for each row     \n"
                                + "  when ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition))    \n"
                                + "  begin    \n"
                                + "    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)    \n"
                                + "    values(    \n" + "      '$(targetTableName)',    \n" + "      'R',    \n"
                                + "      $(triggerHistoryId),                                          \n"
                                + "      $(newKeys),    \n" + "      $(channelExpression), null," + sourceNodeExpression + ",    \n"
                                + "      $(externalSelect),    \n" + "     strftime('%Y-%m-%d %H:%M:%f','now','localtime')    \n" + "    );    \n"
                                + "        $(custom_on_insert_text)                                                                            \n"
                                + "end");
        sqlTemplates
                .put("updateTriggerTemplate",
                        "create trigger $(triggerName) after update on $(schemaName)$(tableName)   \n"
                                + "for each row    \n"
                                + "  when ($(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition)) and ($(dataHasChangedCondition))       \n"
                                + "  begin   \n"
                                + "    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)   \n"
                                + "    values(   \n" + "      '$(targetTableName)',   \n" + "      'U',   \n"
                                + "      $(triggerHistoryId),   \n" + "      $(oldKeys),   \n"
                                + "      $(columns),   \n" + "      $(oldColumns),   \n"
                                + "      $(channelExpression), null," + sourceNodeExpression + ",   \n" + "      $(externalSelect),   \n"
                                + "      strftime('%Y-%m-%d %H:%M:%f','now','localtime')  \n" + "    );   \n"
                                + "      $(custom_on_update_text)                                                                            \n"
                                + "end  ");
        sqlTemplates
                .put("updateReloadTriggerTemplate",
                        "create trigger $(triggerName) after update on $(schemaName)$(tableName)   \n"
                                + "for each row    \n"
                                + "  when ($(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition)) and ($(dataHasChangedCondition))       \n"
                                + "  begin   \n"
                                + "    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)   \n"
                                + "    values(   \n" + "      '$(targetTableName)',   \n" + "      'R',   \n"
                                + "      $(triggerHistoryId),   \n" + "      $(oldKeys),   \n"
                                + "      $(channelExpression), null," + sourceNodeExpression + ",   \n" + "      $(externalSelect),   \n"
                                + "      strftime('%Y-%m-%d %H:%M:%f','now','localtime')  \n" + "    );   \n"
                                + "      $(custom_on_update_text)                                                                            \n"
                                + "end  ");
        sqlTemplates
                .put("deleteTriggerTemplate",
                        "create trigger $(triggerName) after delete on $(schemaName)$(tableName)    \n"
                                + "for each row     \n"
                                + "  when ($(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition))      \n"
                                + "  begin    \n"
                                + "    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)    \n"
                                + "    values(    \n" + "      '$(targetTableName)',    \n" + "      'D',    \n"
                                + "      $(triggerHistoryId),    \n" + "      $(oldKeys),    \n"
                                + "       $(oldColumns),    \n" + "      $(channelExpression), null," + sourceNodeExpression + ",    \n"
                                + "      $(externalSelect),    \n" + "     strftime('%Y-%m-%d %H:%M:%f','now','localtime') \n" + "    );     \n"
                                + "      $(custom_on_delete_text)                                                                            \n"
                                + "end");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select $(columns) from $(schemaName)$(tableName) t where $(whereClause)");
        // formatter:on
    }

    @Override
    protected String replaceTemplateVariables(DataEventType dml, Trigger trigger,
            TriggerHistory history, Channel channel, String tablePrefix, Table originalTable, Table table,
            String defaultCatalog, String defaultSchema, String ddl) {
        ddl = super.replaceTemplateVariables(dml, trigger, history, channel, tablePrefix, originalTable, table,
                defaultCatalog, defaultSchema, ddl);
        ddl = FormatUtils.replace("anyColumnChanged",
                buildColumnsAreNotEqualString(table, newTriggerValue, oldTriggerValue), ddl);
        return ddl;
    }

    private String buildColumnsAreNotEqualString(Table table, String table1Name, String table2Name) {
        StringBuilder builder = new StringBuilder();
        for (Column column : table.getColumns()) {
            if (builder.length() > 0) {
                builder.append(" or ");
            }
            builder.append(String.format("((%1$s.\"%2$s\" IS NOT NULL AND %3$s.\"%2$s\" IS NOT NULL AND %1$s.\"%2$s\"<>%3$s.\"%2$s\") or "
                    + "(%1$s.\"%2$s\" IS NULL AND %3$s.\"%2$s\" IS NOT NULL) or "
                    + "(%1$s.\"%2$s\" IS NOT NULL AND %3$s.\"%2$s\" IS NULL))", table1Name, column.getName(), table2Name));
        }
        if (builder.length() == 0) {
            builder.append("1=1");
        }
        return builder.toString();
    }
}
