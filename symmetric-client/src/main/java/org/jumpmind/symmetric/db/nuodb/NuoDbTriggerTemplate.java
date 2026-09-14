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
package org.jumpmind.symmetric.db.nuodb;

import java.util.HashMap;

import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.util.SymmetricUtils;

public class NuoDbTriggerTemplate extends AbstractTriggerTemplate {
    public NuoDbTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''";
        stringColumnTemplate = "cast(case when $(tableAlias).`$(columnName)` is null then '' else concat('\"',replace(replace($(tableAlias).`$(columnName)`,'\\\\','\\\\\\\\'),'\"','\\\"'),'\"') end as char)\n";
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else concat('\"',cast($(tableAlias).\"$(columnName)\" as char),'\"') end \n";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else concat('\"',cast($(tableAlias).\"$(columnName)\" as char),'\"') end\n";
        clobColumnTemplate = stringColumnTemplate;
        blobColumnTemplate = "''";
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else concat('\"',$(tableAlias).\"$(columnName)\",'\"') end\n";
        triggerConcatCharacter = "||";
        newTriggerValue = "new";
        oldTriggerValue = "old";
        oldColumnPrefix = "";
        newColumnPrefix = "";
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate",
                "create trigger $(triggerName) for $(schemaName)$(tableName) after insert                                                                                                                           \n"
                        +
                        "                                for each row as                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_insert_text) \n" +
                        "                                  if ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) )                                                                                                 \n"
                        +
                        "                                    insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
                        +
                        "                                    values(                                                                                                                                                            \n"
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            \n"
                        +
                        "                                      'I',                                                                                                                                                             \n"
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             \n"
                        +
                        "                                      concat($(columns)                                                                                                                                                \n"
                        +
                        "                                       ),                                                                                                                                                              \n"
                        +
                        "                                      $(channelExpression), $(txIdExpression), $(defaultSchema)$(prefixName)_get_session_variable('sync_node_disabled'),                                                                                                        \n"
                        +
                        "                                      $(externalSelect),                                                                                                                                               \n"
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                \n"
                        +
                        "                                    );                                                                                                                                                                 \n"
                        +
                        "                                  end_if;                                                                                                                                                              \n"
                        +
                        "                                  $(custom_on_insert_text)                                                                                                                                                \n"
                        +
                        "                                end_trigger                                                                                                                                                                    ");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "create trigger $(triggerName) for $(schemaName)$(tableName) after insert                                                                                                                                \n"
                        +
                        "                                for each row as                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_insert_text) \n" +
                        "                                  if( $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) )                                                                                                 \n"
                        +
                        "                                    insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
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
                        "                                      $(channelExpression), $(txIdExpression), $(defaultSchema)$(prefixName)_get_session_variable('sync_node_disabled'),                                                                                                        \n"
                        +
                        "                                      $(externalSelect),                                                                                                                                               \n"
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                \n"
                        +
                        "                                    );                                                                                                                                                                 \n"
                        +
                        "                                  end_if;                                                                                                                                                              \n"
                        +
                        "                                  $(custom_on_insert_text)                                                                                                                                                \n"
                        +
                        "                                end_trigger                                                                                                                                                                    ");
        sqlTemplates.put("updateTriggerTemplate",
                "create trigger $(triggerName) for $(schemaName)$(tableName) after update                                                                                                                                \n"
                        +
                        "                                for each row as                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_update_text) \n" +
                        "                                  if( $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) )                                                                                                 \n"
                        +
                        "                                   if( $(dataHasChangedCondition) )                                                                                                                                  \n"
                        +
                        "                                       insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
                        +
                        "                                       values(                                                                                                                                                           \n"
                        +
                        "                                         '$(targetTableName)',                                                                                                                                           \n"
                        +
                        "                                         'U',                                                                                                                                                            \n"
                        +
                        "                                         $(triggerHistoryId),                                                                                                                                            \n"
                        +
                        "                                         concat($(oldKeys)                                                                                                                                               \n"
                        +
                        "                                          ),                                                                                                                                                             \n"
                        +
                        "                                         concat($(columns)),                                                                                                                                                   \n"
                        +
                        "                                         concat($(oldColumns)),                                                                                                                                                   \n"
                        +
                        "                                         $(channelExpression), $(txIdExpression), $(defaultSchema)$(prefixName)_get_session_variable('sync_node_disabled'),                                                                                                       \n"
                        +
                        "                                         $(externalSelect),                                                                                                                                              \n"
                        +
                        "                                         CURRENT_TIMESTAMP                                                                                                                                               \n"
                        +
                        "                                       );                                                                                                                                                                \n"
                        +
                        "                                   end_if;                                                                                                                                                               \n"
                        +
                        "                                  end_if;                                                                                                                                                                \n"
                        +
                        "                                  $(custom_on_update_text)                                                                                                                                                  \n"
                        +
                        "                                end_trigger                                                                                                                                                                      ");
        sqlTemplates.put("deleteTriggerTemplate",
                "create trigger $(triggerName) for $(schemaName)$(tableName) after delete                                                                                                                                \n"
                        +
                        "                                for each row as                                                                                                                                                     \n"
                        +
                        "                                  $(custom_before_delete_text) \n" +
                        "                                  if( $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) )                                                                                                 \n"
                        +
                        "                                    insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n"
                        +
                        "                                    values(                                                                                                                                                            \n"
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            \n"
                        +
                        "                                      'D',                                                                                                                                                             \n"
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             \n"
                        +
                        "                                      concat($(oldKeys)                                                                                                                                                \n"
                        +
                        "                                       ),                                                                                                                                                              \n"
                        +
                        "                                       concat($(oldColumns)                                                                                                                                            \n"
                        +
                        "                                       ),                                                                                                                                                              \n"
                        +
                        "                                      $(channelExpression), $(txIdExpression), $(defaultSchema)$(prefixName)_get_session_variable('sync_node_disabled'),                                                                                                        \n"
                        +
                        "                                      $(externalSelect),                                                                                                                                               \n"
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                \n"
                        +
                        "                                    );                                                                                                                                                                 \n"
                        +
                        "                                  end_if;                                                                                                                                                              \n"
                        +
                        "                                  $(custom_on_delete_text)                                                                                                                                                \n"
                        +
                        "                                end_trigger                                                                                                                                                                    ");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select concat($(columns)) from $(schemaName)$(tableName) as t where $(whereClause)                                                                                                                        ");
    }

    @Override
    protected String castDatetimeColumnToString(String columnName) {
        return "cast(\n" + SymmetricUtils.quote(symmetricDialect, columnName) + " as char)";
    }
}