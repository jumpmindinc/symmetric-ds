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
package org.jumpmind.symmetric.db.mysql;

import java.util.HashMap;

import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.util.SymmetricUtils;

public class MySqlTriggerTemplate extends AbstractTriggerTemplate {
    public MySqlTriggerTemplate(ISymmetricDialect symmetricDialect, boolean isConvertZeroDateToNull, String characterSet) {
        super(symmetricDialect);
        // @formatter:off

        emptyColumnTemplate = "''" ;
        stringColumnTemplate = "cast(if($(tableAlias).`$(columnName)` is null,'',concat('\"',replace(replace($(tableAlias).`$(columnName)`,'\\\\','\\\\\\\\'),'\"','\\\\\"'),'\"')) as char)\n" ;                               
        geometryColumnTemplate = "if($(tableAlias).`$(columnName)` is null,'',concat('\"',replace(replace(astext($(tableAlias).`$(columnName)`),'\\\\','\\\\\\\\'),'\"','\\\\\"'),'\"'))\n" ;
        numberColumnTemplate = "if($(tableAlias).`$(columnName)` is null,'',concat('\"',cast($(tableAlias).`$(columnName)` as char),'\"'))\n" ;
        datetimeColumnTemplate = "if($(tableAlias).`$(columnName)` is null" + (isConvertZeroDateToNull ? " or $(tableAlias).`$(columnName)` = '0000-00-00'" : "") + ",'',concat('\"',cast($(tableAlias).`$(columnName)` as char),'\"'))\n" ;
        clobColumnTemplate =    stringColumnTemplate;
        blobColumnTemplate = "if($(tableAlias).`$(columnName)` is null,'',concat('\"',hex($(tableAlias).`$(columnName)`),'\"'))\n" ;
        booleanColumnTemplate = "if($(tableAlias).`$(columnName)` is null,'',concat('\"',cast($(tableAlias).`$(columnName)` as unsigned),'\"'))\n" ;
        triggerConcatCharacter = "," ;
        newTriggerValue = "new" ;
        oldTriggerValue = "old" ;
        oldColumnPrefix = "" ;
        newColumnPrefix = "" ;

        sqlTemplates = new HashMap<String,String>();
        sqlTemplates.put("insertTriggerTemplate" ,
"create trigger $(triggerName) after insert on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row begin                                                                                                                                                     \n" +
"                                  $(custom_before_insert_text) \n" +
"                                  if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"                                    values(                                                                                                                                                            \n" +
"                                      '$(targetTableName)',                                                                                                                                            \n" +
"                                      'I',                                                                                                                                                             \n" +
"                                      $(triggerHistoryId),                                                                                                                                             \n" +
"                                      concat($(columns)                                                                                                                                                \n" +
"                                       ),                                                                                                                                                              \n" +
"                                      $(channelExpression), $(txIdExpression), @sync_node_disabled,                                                                                                        \n" +
"                                      $(externalSelect),                                                                                                                                               \n" +
"                                      CURRENT_TIMESTAMP                                                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_insert_text)                                                                                                                                                \n" +
"                                end                                                                                                                                                                    " );

        sqlTemplates.put("insertReloadTriggerTemplate" ,
"create trigger $(triggerName) after insert on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row begin                                                                                                                                                     \n" +
"                                  $(custom_before_insert_text) \n" +
"                                  if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"                                    values(                                                                                                                                                            \n" +
"                                      '$(targetTableName)',                                                                                                                                            \n" +
"                                      'R',                                                                                                                                                             \n" +
"                                      $(triggerHistoryId),                                                                                                                                             \n" +
"                                      concat($(newKeys)),                                                                                                                                             \n" +
"                                      $(channelExpression), $(txIdExpression), @sync_node_disabled,                                                                                                        \n" +
"                                      $(externalSelect),                                                                                                                                               \n" +
"                                      CURRENT_TIMESTAMP                                                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_insert_text)                                                                                                                                                \n" +
"                                end                                                                                                                                                                    " );

        sqlTemplates.put("updateTriggerTemplate" ,
"create trigger $(triggerName) after update on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row begin                                                                                                                                                     \n" +
"                                  DECLARE var_row_data longtext character set " + characterSet + ";\n" +
"                                  DECLARE var_old_data longtext character set " + characterSet + ";\n" +
"                                  $(custom_before_update_text) \n" +
"                                  if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                   set var_row_data = concat($(columns));                                                                                                                              \n" +
"                                   set var_old_data = concat($(oldColumns));                                                                                                                           \n" +
"                                   if $(dataHasChangedCondition) then                                                                                                                                  \n" +
"                                        insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"                                        values(                                                                                                                                                           \n" +
"                                          '$(targetTableName)',                                                                                                                                           \n" +
"                                          'U',                                                                                                                                                            \n" +
"                                          $(triggerHistoryId),                                                                                                                                            \n" +
"                                          concat($(oldKeys)                                                                                                                                               \n" +
"                                           ),                                                                                                                                                             \n" +
"                                          var_row_data,                                                                                                                                                   \n" +
"                                          var_old_data,                                                                                                                                                   \n" +
"                                          $(channelExpression), $(txIdExpression), @sync_node_disabled,                                                                                                       \n" +
"                                          $(externalSelect),                                                                                                                                              \n" +
"                                          CURRENT_TIMESTAMP                                                                                                                                               \n" +
"                                        );                                                                                                                                                                \n" +
"                                    end if;                                                                                                                                                               \n" +
"                                  end if;                                                                                                                                                                \n" +
"                                  $(custom_on_update_text)                                                                                                                                                  \n" +
"                                end                                                                                                                                                                      " );

        sqlTemplates.put("updateReloadTriggerTemplate" ,
"create trigger $(triggerName) after update on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row begin                                                                                                                                                     \n" +
"                                  $(custom_before_update_text) \n" +
"                                  if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                   if $(dataHasChangedCondition) then                                                                                                                                  \n" +
"                                       insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"                                       values(                                                                                                                                                           \n" +
"                                         '$(targetTableName)',                                                                                                                                           \n" +
"                                         'R',                                                                                                                                                            \n" +
"                                         $(triggerHistoryId),                                                                                                                                            \n" +
"                                         concat($(oldKeys)                                                                                                                                               \n" + 
"                                          ),                                                                                                                                            \n" +
"                                         $(channelExpression), $(txIdExpression), @sync_node_disabled,                                                                                                       \n" +
"                                         $(externalSelect),                                                                                                                                              \n" +
"                                         CURRENT_TIMESTAMP                                                                                                                                               \n" +
"                                       );                                                                                                                                                                \n" +
"                                   end if;                                                                                                                                                               \n" +
"                                  end if;                                                                                                                                                                \n" +
"                                  $(custom_on_update_text)                                                                                                                                                  \n" +
"                                end                                                                                                                                                                      " );

        sqlTemplates.put("deleteTriggerTemplate" ,
"create trigger $(triggerName) after delete on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row begin                                                                                                                                                     \n" +
"                                  $(custom_before_delete_text) \n" +
"                                  if $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"                                    values(                                                                                                                                                            \n" +
"                                      '$(targetTableName)',                                                                                                                                            \n" +
"                                      'D',                                                                                                                                                             \n" +
"                                      $(triggerHistoryId),                                                                                                                                             \n" +
"                                      concat($(oldKeys)                                                                                                                                                \n" +
"                                       ),                                                                                                                                                              \n" +
"                                       concat($(oldColumns)                                                                                                                                            \n" +
"                                       ),                                                                                                                                                              \n" +
"                                      $(channelExpression), $(txIdExpression), @sync_node_disabled,                                                                                                        \n" +
"                                      $(externalSelect),                                                                                                                                               \n" +
"                                      CURRENT_TIMESTAMP                                                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_delete_text)                                                                                                                                                \n" +
"                                end                                                                                                                                                                    " );

        sqlTemplates.put("initialLoadSqlTemplate", "select concat($(columns)) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                        " );
    }

    @Override
    protected String castDatetimeColumnToString(String columnName) {
        return "cast(\n" + SymmetricUtils.quote(symmetricDialect, columnName) + " as char)";
    }
}