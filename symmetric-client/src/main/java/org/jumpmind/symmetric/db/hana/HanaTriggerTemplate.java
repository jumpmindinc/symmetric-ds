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
package org.jumpmind.symmetric.db.hana;

import java.util.HashMap;

import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class HanaTriggerTemplate extends AbstractTriggerTemplate {
    public HanaTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''";
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else concat(concat('\"', replace(replace($(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"')), '\"') end \n";
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else concat(concat('\"',cast($(tableAlias).\"$(columnName)\" as char)),'\"') end \n";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else concat(concat('\"',cast($(tableAlias).\"$(columnName)\" as char)),'\"') end\n";
        clobColumnTemplate = stringColumnTemplate;
        blobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || bintohex($(tableAlias).\"$(columnName)\") || '\"' end \n";
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else concat(concat('\"',cast($(tableAlias).\"$(columnName)\" as char)),'\"') end \n";
        triggerConcatCharacter = "||";
        newTriggerValue = ":new";
        oldTriggerValue = ":old";
        oldColumnPrefix = "";
        newColumnPrefix = "";
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate",
                "create trigger $(triggerName) after insert on $(schemaName)$(tableName)  " +
                        "                                referencing new row new, old row old                                                                                                                                   \n"
                        +
                        "                                for each row begin                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_insert_text) \n" +
                        "                                  if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n"
                        +
                        "                                    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
                        +
                        "                                    values(                                                                                                                                                            \n"
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            \n"
                        +
                        "                                      'I',                                                                                                                                                             \n"
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             \n"
                        +
                        "                                      $(columns),                                                                                                                                                      \n"
                        +
                        "                                      $(channelExpression), $(txIdExpression), session_context('sync_node_disabled'),                                                                                                        \n"
                        +
                        "                                      $(externalSelect),                                                                                                                                               \n"
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                \n"
                        +
                        "                                    );                                                                                                                                                                 \n"
                        +
                        "                                  end if;                                                                                                                                                              \n"
                        +
                        "                                  $(custom_on_insert_text)                                                                                                                                                \n"
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "create trigger $(triggerName) after insert on $(schemaName)$(tableName)                                                                                                                                \n"
                        +
                        "                                referencing new row new, old row old   "
                        + "                              for each row begin                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_insert_text) \n" +
                        "                                  if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n"
                        +
                        "                                    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
                        +
                        "                                    values(                                                                                                                                                            \n"
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            \n"
                        +
                        "                                      'R',                                                                                                                                                             \n"
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             \n"
                        +
                        "                                      $(newKeys),                                                                                                                                             \n"
                        +
                        "                                      $(channelExpression), $(txIdExpression), session_context('sync_node_disabled'),                                                                                                        \n"
                        +
                        "                                      $(externalSelect),                                                                                                                                               \n"
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                \n"
                        +
                        "                                    );                                                                                                                                                                 \n"
                        +
                        "                                  end if;                                                                                                                                                              \n"
                        +
                        "                                  $(custom_on_insert_text)                                                                                                                                                \n"
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("updateTriggerTemplate",
                "create trigger $(triggerName) after update on $(schemaName)$(tableName)                                                                                                                                \n"
                        +
                        "                                referencing new row new, old row old                                                                                                                                   \n"
                        +
                        "                                for each row begin                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_update_text) \n" +
                        "                                  if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n"
                        +
                        "                                   if $(dataHasChangedCondition) then                                                                                                                                  \n"
                        +
                        "                                       insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
                        +
                        "                                       values(                                                                                                                                                           \n"
                        +
                        "                                         '$(targetTableName)',                                                                                                                                           \n"
                        +
                        "                                         'U',                                                                                                                                                            \n"
                        +
                        "                                         $(triggerHistoryId),                                                                                                                                            \n"
                        +
                        "                                         $(oldKeys),                                                                                                                                                             \n"
                        +
                        "                                         $(columns),                                                                                                                                                   \n"
                        +
                        "                                         $(oldColumns),                                                                                                                                                   \n"
                        +
                        "                                         $(channelExpression), $(txIdExpression), session_context('sync_node_disabled'),                                                                                                       \n"
                        +
                        "                                         $(externalSelect),                                                                                                                                              \n"
                        +
                        "                                         CURRENT_TIMESTAMP                                                                                                                                               \n"
                        +
                        "                                       );                                                                                                                                                                \n"
                        +
                        "                                   end if;                                                                                                                                                               \n"
                        +
                        "                                  end if;                                                                                                                                                                \n"
                        +
                        "                                  $(custom_on_update_text)                                                                                                                                                  \n"
                        +
                        "                                end                                                                                                                                                                      ");
        sqlTemplates.put("updateReloadTriggerTemplate",
                "create trigger $(triggerName) after update on $(schemaName)$(tableName)                                                                                                                                \n"
                        +
                        "                                  referencing new row new, old row old                                                                                                                                   \n"
                        +
                        "                                  for each row begin                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_update_text) \n" +
                        "                                  if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n"
                        +
                        "                                   if $(dataHasChangedCondition) then                                                                                                                                  \n"
                        +
                        "                                       insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
                        +
                        "                                       values(                                                                                                                                                           \n"
                        +
                        "                                         '$(targetTableName)',                                                                                                                                           \n"
                        +
                        "                                         'U',                                                                                                                                                            \n"
                        +
                        "                                         $(triggerHistoryId),                                                                                                                                            \n"
                        +
                        "                                         $(oldKeys),                                                                                                                                            \n"
                        +
                        "                                         $(channelExpression), $(txIdExpression), session_context('sync_node_disabled'),                                                                                                       \n"
                        +
                        "                                         $(externalSelect),                                                                                                                                              \n"
                        +
                        "                                         CURRENT_TIMESTAMP                                                                                                                                               \n"
                        +
                        "                                       );                                                                                                                                                                \n"
                        +
                        "                                   end if;                                                                                                                                                               \n"
                        +
                        "                                  end if;                                                                                                                                                                \n"
                        +
                        "                                  $(custom_on_update_text)                                                                                                                                                  \n"
                        +
                        "                                end                                                                                                                                                                      ");
        sqlTemplates.put("deleteTriggerTemplate",
                "create trigger $(triggerName) after delete on $(schemaName)$(tableName)                                                                                                                                \n"
                        +
                        "                                referencing new row new, old row old                                                                                                                                   \n"
                        +
                        "                                for each row begin                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_delete_text) \n" +
                        "                                  if $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n"
                        +
                        "                                    insert into $(defaultCatalog)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
                        +
                        "                                    values(                                                                                                                                                            \n"
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            \n"
                        +
                        "                                      'D',                                                                                                                                                             \n"
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             \n"
                        +
                        "                                      $(oldKeys),                                                                                                                                                              \n"
                        +
                        "                                      $(oldColumns),                                                                                                                                                              \n"
                        +
                        "                                      $(channelExpression), $(txIdExpression), session_context('sync_node_disabled'),                                                                                                        \n"
                        +
                        "                                      $(externalSelect),                                                                                                                                               \n"
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                \n"
                        +
                        "                                    );                                                                                                                                                                 \n"
                        +
                        "                                  end if;                                                                                                                                                              \n"
                        +
                        "                                  $(custom_on_delete_text)                                                                                                                                                \n"
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select $(columns) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                        ");
    }
}
