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
package org.jumpmind.symmetric.db.sqlanywhere;

import java.sql.Types;
import java.util.HashMap;

import org.apache.commons.lang3.NotImplementedException;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.util.FormatUtils;

public class SqlAnywhereTriggerTemplate extends AbstractTriggerTemplate {
    public SqlAnywhereTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''";
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace($(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"') + '\"' end";
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else ('\"' + cast($(tableAlias).\"$(columnName)\" as varchar) + '\"') end";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else ('\"' + replace(convert(varchar,$(tableAlias).\"$(columnName)\",21),'T',' ') + '\"') end";
        clobColumnTemplate = "case when $(origTableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(cast($(origTableAlias).\"$(columnName)\" as varchar(16384)),'\\','\\\\'),'\"','\\\"') + '\"' end";
        blobColumnTemplate = "case when $(origTableAlias).\"$(columnName)\" is null then '' else '\"' + base64_encode($(origTableAlias).\"$(columnName)\") + '\"' end";
        binaryColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + base64_encode($(tableAlias).\"$(columnName)\") + '\"' end";
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when $(tableAlias).\"$(columnName)\" = 1 then '\"1\"' else '\"0\"' end";
        triggerConcatCharacter = "+";
        newTriggerValue = "inserted";
        oldTriggerValue = "deleted";
        oldColumnPrefix = "";
        newColumnPrefix = "";
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate",
                "create trigger $(triggerName) on $(schemaName)$(tableName) for insert as                                                                                                                               "
                        +
                        "                                begin                                                                                                                                                                  "
                        +
                        "                                  declare @DataRow varchar(16384)                                                                                                                                      "
                        +
                        "                                  $(declareNewKeyVariables)                                                                                                                                            "
                        +
                        "                                  declare @ChannelId varchar(128)                                                                                                                                      "
                        +
                        "                                  $(custom_before_insert_text) \n" +
                        "                                  if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           "
                        +
                        "                                    declare DataCursor cursor for                                                                                                                                      "
                        +
                        "                                    $(if:containsBlobClobColumns)                                                                                                                                      "
                        +
                        "                                       select $(columns) $(newKeyNames), $(channelExpression) from inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) where $(syncOnInsertCondition)"
                        +
                        "                                    $(else:containsBlobClobColumns)                                                                                                                                    "
                        +
                        "                                       select $(columns) $(newKeyNames), $(channelExpression) from inserted where $(syncOnInsertCondition)                                                                                  "
                        +
                        "                                    $(end:containsBlobClobColumns)                                                                                                                                     "
                        +
                        "                                       open DataCursor                                                                                                                                                 "
                        +
                        "                                       fetch next DataCursor into @DataRow $(newKeyVariables), @ChannelId                                                                                                   "
                        +
                        "                                       while @@FETCH_STATUS = 0 begin                                                                                                                                  "
                        +
                        "                                           insert into $(defaultCatalog)$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time) "
                        +
                        "                                             values('$(targetTableName)','I', $(triggerHistoryId), @DataRow, @ChannelId, $(txIdExpression), $(defaultCatalog)$(defaultSchema)$(prefixName)_node_disabled(0), $(externalSelect), getdate())                                   "
                        +
                        "                                           fetch next DataCursor into @DataRow $(newKeyVariables), @ChannelId                                                                                                 "
                        +
                        "                                       end                                                                                                                                                             "
                        +
                        "                                       close DataCursor                                                                                                                                                "
                        +
                        "                                       deallocate DataCursor                                                                                                                                           "
                        +
                        "                                  end                                                                                                                                                                  "
                        +
                        "                                  $(custom_on_insert_text)                                                                                                                                             "
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("updateTriggerTemplate",
                "create trigger $(triggerName) on $(schemaName)$(tableName) for update as                                                                                                                               "
                        +
                        "                                begin                                                                                                                                                                  "
                        +
                        "                                  declare @DataRow varchar(16384)                                                                                                                                      "
                        +
                        "                                  declare @OldPk varchar(2000)                                                                                                                                         "
                        +
                        "                                  declare @OldDataRow varchar(16384)                                                                                                                                   "
                        +
                        "                                  declare @ChannelId varchar(128)                                                                                                                                      "
                        +
                        "                                  $(declareOldKeyVariables)                                                                                                                                            "
                        +
                        "                                  $(declareNewKeyVariables)                                                                                                                                            "
                        +
                        "                                  $(custom_before_update_text) \n" +
                        "                                  if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           "
                        +
                        "                                    declare DataCursor cursor for                                                                                                                                      "
                        +
                        "                                    $(if:containsBlobClobColumns)                                                                                                                                      "
                        +
                        "                                       select $(columns), $(oldKeys), $(oldColumns) $(oldKeyNames) $(newKeyNames), $(channelExpression) from inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) inner join deleted on $(oldNewPrimaryKeyJoin) where $(syncOnUpdateCondition)"
                        +
                        "                                    $(else:containsBlobClobColumns)                                                                                                                                    "
                        +
                        "                                       select $(columns), $(oldKeys), $(oldColumns) $(oldKeyNames) $(newKeyNames), $(channelExpression) from inserted inner join deleted on $(oldNewPrimaryKeyJoin) where $(syncOnUpdateCondition)                                    "
                        +
                        "                                    $(end:containsBlobClobColumns)                                                                                                                                     "
                        +
                        "                                       open DataCursor                                                                                                                                                 "
                        +
                        "                                       fetch next DataCursor into @DataRow, @OldPk, @OldDataRow $(oldKeyVariables) $(newKeyVariables), @ChannelId                                                      "
                        +
                        "                                       while @@FETCH_STATUS = 0 begin                                                                                                                                  "
                        +
                        "                                         insert into $(defaultCatalog)$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) "
                        +
                        "                                           values('$(targetTableName)','U', $(triggerHistoryId), @DataRow, @OldPk, @OldDataRow, @ChannelId, $(txIdExpression), $(defaultCatalog)$(defaultSchema)$(prefixName)_node_disabled(0), $(externalSelect), getdate())"
                        +
                        "                                         fetch next DataCursor into @DataRow, @OldPk, @OldDataRow $(oldKeyVariables) $(newKeyVariables), @ChannelId                                                    "
                        +
                        "                                       end                                                                                                                                                             "
                        +
                        "                                       close DataCursor                                                                                                                                                "
                        +
                        "                                       deallocate DataCursor                                                                                                                                           "
                        +
                        "                                    end                                                                                                                                                                "
                        +
                        "                                  $(custom_on_update_text)                                                                                                                                             "
                        +
                        "                                  end                                                                                                                                                                  ");
        sqlTemplates.put("deleteTriggerTemplate",
                "create trigger $(triggerName) on $(schemaName)$(tableName) for delete as                                                                                                                               "
                        +
                        "                                begin                                                                                                                                                                  "
                        +
                        "                                  declare @OldPk varchar(2000)                                                                                                                                         "
                        +
                        "                                  declare @OldDataRow varchar(16384)                                                                                                                                   "
                        +
                        "                                  declare @ChannelId varchar(128)                                                                                                                                      "
                        +
                        "                                  $(declareOldKeyVariables)                                                                                                                                            "
                        +
                        "                                  $(custom_before_delete_text) \n" +
                        "                                  if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           "
                        +
                        "                                    declare DataCursor cursor for                                                                                                                                      "
                        +
                        "                                      select $(oldKeys), $(oldColumns) $(oldKeyNames), $(channelExpression) from deleted where $(syncOnDeleteCondition)                                                                      "
                        +
                        "                                      open DataCursor                                                                                                                                                  "
                        +
                        "                                       fetch next DataCursor into @OldPk, @OldDataRow $(oldKeyVariables), @ChannelId                                                                                   "
                        +
                        "                                       while @@FETCH_STATUS = 0 begin                                                                                                                                  "
                        +
                        "                                         insert into $(defaultCatalog)$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) "
                        +
                        "                                           values('$(targetTableName)','D', $(triggerHistoryId), @OldPk, @OldDataRow, @ChannelId, $(txIdExpression), $(defaultCatalog)$(defaultSchema)$(prefixName)_node_disabled(0), $(externalSelect), getdate())"
                        +
                        "                                         fetch next DataCursor into @OldPk,@OldDataRow $(oldKeyVariables), @ChannelId                                                                                  "
                        +
                        "                                       end                                                                                                                                                             "
                        +
                        "                                       close DataCursor                                                                                                                                                "
                        +
                        "                                       deallocate DataCursor                                                                                                                                           "
                        +
                        "                                  end                                                                                                                                                                  "
                        +
                        "                                  $(custom_on_delete_text)                                                                                                                                             "
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select $(columns) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                                ");
    }

    @Override
    protected String replaceTemplateVariables(DataEventType dml, Trigger trigger,
            TriggerHistory history, Channel channel, String tablePrefix, Table originalTable, Table table,
            String defaultCatalog, String defaultSchema, String ddl) {
        ddl = super.replaceTemplateVariables(dml, trigger, history, channel, tablePrefix, originalTable, table,
                defaultCatalog, defaultSchema, ddl);
        Column[] columns = table.getPrimaryKeyColumns();
        ddl = FormatUtils.replace("declareOldKeyVariables",
                buildKeyVariablesDeclare(columns, "old"), ddl);
        ddl = FormatUtils.replace("declareNewKeyVariables",
                buildKeyVariablesDeclare(columns, "new"), ddl);
        return ddl;
    }

    @Override
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
                    text += "varchar(16384)\n";
                    break;
                case Types.BLOB:
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                case -10: // SQL-Server ntext binary type
                    text += "varbinary(16384)\n";
                    break;
                case Types.OTHER:
                    text += "varbinary(16384)\n";
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
            text += appendSemicolonAfterDeclare();
        }
        return text;
    }

    protected String appendSemicolonAfterDeclare() {
        return "";
    }

    @Override
    protected boolean useTriggerTemplateForColumnTemplatesDuringInitialLoad(Column column) {
        boolean result = super.useTriggerTemplateForColumnTemplatesDuringInitialLoad(column);
        if (column != null) {
            if (column.getJdbcTypeName() != null && column.getJdbcTypeName().toUpperCase().contains(TypeMap.VARBIT)) {
                result = true;
            }
        }
        return result;
    }
}