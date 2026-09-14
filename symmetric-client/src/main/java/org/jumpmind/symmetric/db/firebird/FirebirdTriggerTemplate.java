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
package org.jumpmind.symmetric.db.firebird;

import java.util.HashMap;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.firebird.FirebirdDialect1DatabasePlatform;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class FirebirdTriggerTemplate extends AbstractTriggerTemplate {
    String quo = "";
    boolean isDialect1;

    public FirebirdTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        if (symmetricDialect.getPlatform().getDatabaseInfo().isDelimitedIdentifiersSupported()) {
            quo = symmetricDialect.getPlatform().getDatabaseInfo().getDelimiterToken();
        }
        isDialect1 = symmetricDialect.getPlatform() instanceof FirebirdDialect1DatabasePlatform;
        // @formatter:off
        emptyColumnTemplate = "''" ;
        stringColumnTemplate = "case when $(tableAlias)." + quo + "$(columnName)" + quo + " is null then '' else '\"' || sym_escape(substring($(tableAlias)." + quo + "$(columnName)" + quo + " from 1)) || '\"' end" ;
        clobColumnTemplate = stringColumnTemplate;
        numberColumnTemplate = "case when $(tableAlias)." + quo + "$(columnName)" + quo + " is null then '' else '\"' || $(tableAlias)." + quo + "$(columnName)" + quo + " || '\"' end" ;
        datetimeColumnTemplate = "case when $(tableAlias)." + quo + "$(columnName)" + quo + " is null then '' else '\"' || $(tableAlias)." + quo + "$(columnName)" + quo + " || '\"' end" ;
        blobColumnTemplate = "case when $(tableAlias)." + quo + "$(columnName)" + quo + " is null then '' else '\"' || sym_hex($(tableAlias)." + quo + "$(columnName)" + quo + ") || '\"' end" ;
        triggerConcatCharacter = "||" ;
        newTriggerValue = "new" ;
        oldTriggerValue = "old" ;
        oldColumnPrefix = "" ;
        newColumnPrefix = "" ;

        sqlTemplates = new HashMap<String,String>();
        sqlTemplates.put("insertTriggerTemplate" ,
"create trigger $(triggerName) for $(schemaName)$(tableName) after insert position 5 as                                                                                                                            \n" +
"   begin                                                                                                                                                                  \n" +
"     $(custom_before_insert_text) \n" +
"     if ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               \n" +
"     begin                                                                                                                                                                \n" +
"       insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"       (data_id, table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)                               \n" +
"       values(                                                                                                                                                            \n" +
"         gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1),                                                                                                                                                             \n" +
"         '$(targetTableName)',                                                                                                                                            \n" +
"         'I',                                                                                                                                                             \n" +
"         $(triggerHistoryId),                                                                                                                                             \n" +
"         $(columns),                                                                                                                                                      \n" +
"         $(channelExpression),                                                                                                                                                \n" +
"         $(txIdExpression),                                                                                                                                               \n" +
"         rdb$get_context('USER_SESSION', 'sync_node_disabled'),                                                                                                           \n" +
"         $(externalSelect),                                                                                                                                               \n" +
"         CURRENT_TIMESTAMP                                                                                                                                                \n" +
"       );                                                                                                                                                                 \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_on_insert_text)                                                                                                                                             \n" +
"   end                                                                                                                                                                    \n" );

        sqlTemplates.put("insertReloadTriggerTemplate" ,
"create trigger $(triggerName) for $(schemaName)$(tableName) after insert position 5 as                                                                                                                            \n" +
"   begin                                                                                                                                                                  \n" +
"     $(custom_before_insert_text) \n" +
"     if ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               \n" +
"     begin                                                                                                                                                                \n" +
"       insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"       (data_id, table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)                               \n" +
"       values(                                                                                                                                                            \n" +
"         gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1),                                                                                                                                                             \n" +
"         '$(targetTableName)',                                                                                                                                            \n" +
"         'R',                                                                                                                                                             \n" +
"         $(triggerHistoryId),                                                                                                                                             \n" +
"         $(newKeys),                                                                                                                                                      \n" +
"         $(channelExpression),                                                                                                                                                \n" +
"         $(txIdExpression),                                                                                                                                               \n" +
"         rdb$get_context('USER_SESSION', 'sync_node_disabled'),                                                                                                           \n" +
"         $(externalSelect),                                                                                                                                               \n" +
"         CURRENT_TIMESTAMP                                                                                                                                                \n" +
"       );                                                                                                                                                                 \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_on_insert_text)                                                                                                                                             \n" +
"   end                                                                                                                                                                    \n" );

        
        sqlTemplates.put("updateTriggerTemplate" ,
"create trigger $(triggerName) for $(schemaName)$(tableName) after update position 5 as                                                                                                                            \n" +
"   begin                                                                                                                                                                  \n" +
"     $(custom_before_update_text) \n" +
"     if ($(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               \n" +
"     begin                                                                                                                                                                \n" +
"       insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"       (data_id, table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)            \n" +
"       values(                                                                                                                                                            \n" +
"         gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1),                                                                                                                                                             \n" +
"         '$(targetTableName)',                                                                                                                                            \n" +
"         'U',                                                                                                                                                             \n" +
"         $(triggerHistoryId),                                                                                                                                             \n" +
"         $(oldKeys),                                                                                                                                                      \n" +
"         $(columns),                                                                                                                                                      \n" +
"         $(oldColumns),                                                                                                                                                   \n" +
"         $(channelExpression),                                                                                                                                                \n" +
"         $(txIdExpression),                                                                                                                                               \n" +
"         rdb$get_context('USER_SESSION', 'sync_node_disabled'),                                                                                                           \n" +
"         $(externalSelect),                                                                                                                                               \n" +
"         CURRENT_TIMESTAMP                                                                                                                                                \n" +
"       );                                                                                                                                                                 \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_on_update_text)                                                                                                                                             \n" +
"   end                                                                                                                                                                    \n" );

        sqlTemplates.put("updateReloadTriggerTemplate" ,
"create trigger $(triggerName) for $(schemaName)$(tableName) after update position 5 as                                                                                                                            \n" +
"   begin                                                                                                                                                                  \n" +
"     $(custom_before_update_text) \n" +
"     if ($(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               \n" +
"     begin                                                                                                                                                                \n" +
"       insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"       (data_id, table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)            \n" +
"       values(                                                                                                                                                            \n" +
"         gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1),                                                                                                                                                             \n" +
"         '$(targetTableName)',                                                                                                                                            \n" +
"         'R',                                                                                                                                                             \n" +
"         $(triggerHistoryId),                                                                                                                                             \n" +
"         $(oldKeys),                                                                                                                                                      \n" +
"         $(channelExpression),                                                                                                                                                \n" +
"         $(txIdExpression),                                                                                                                                               \n" +
"         rdb$get_context('USER_SESSION', 'sync_node_disabled'),                                                                                                           \n" +
"         $(externalSelect),                                                                                                                                               \n" +
"         CURRENT_TIMESTAMP                                                                                                                                                \n" +
"       );                                                                                                                                                                 \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_on_update_text)                                                                                                                                             \n" +
"   end                                                                                                                                                                    \n" );

        
        sqlTemplates.put("deleteTriggerTemplate" ,
"create trigger  $(triggerName) for $(schemaName)$(tableName) after delete position 5 as                                                                                                                           \n" +
"   begin                                                                                                                                                                  \n" +
"     $(custom_before_delete_text) \n" +
"     if ($(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               \n" +
"     begin                                                                                                                                                                \n" +
"       insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"       (data_id, table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)                      \n" +
"       values(                                                                                                                                                            \n" +
"         gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1),                                                                                                                                                             \n" +
"         '$(targetTableName)',                                                                                                                                            \n" +
"         'D',                                                                                                                                                             \n" +
"         $(triggerHistoryId),                                                                                                                                             \n" +
"         $(oldKeys),                                                                                                                                                      \n" +
"         $(oldColumns),                                                                                                                                                   \n" +
"         $(channelExpression),                                                                                                                                                \n" +
"         $(txIdExpression),                                                                                                                                               \n" +
"         rdb$get_context('USER_SESSION', 'sync_node_disabled'),                                                                                                           \n" +
"         $(externalSelect),                                                                                                                                               \n" +
"         CURRENT_TIMESTAMP                                                                                                                                                \n" +
"       );                                                                                                                                                                 \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_on_delete_text)                                                                                                                                             \n" +
"   end                                                                                                                                                                    \n" );

        sqlTemplates.put("initialLoadSqlTemplate" ,
"select $(columns) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                                " );
    }

    @Override
    public boolean useTriggerTemplateForColumnTemplatesDuringInitialLoad() {
        return false;
    }

    @Override
    protected boolean useTriggerTemplateForColumnTemplatesDuringInitialLoad(Column column) {
        return false;
    }
}