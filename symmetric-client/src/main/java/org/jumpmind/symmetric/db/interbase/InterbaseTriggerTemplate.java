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
package org.jumpmind.symmetric.db.interbase;

import java.util.HashMap;

import org.jumpmind.db.model.Column;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class InterbaseTriggerTemplate extends AbstractTriggerTemplate {
    public InterbaseTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''";
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || sym_escape($(tableAlias).\"$(columnName)\") || '\"' end";
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || $(tableAlias).\"$(columnName)\" || '\"' end";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || $(tableAlias).\"$(columnName)\" || '\"' end";
        clobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || sym_escape($(tableAlias).\"$(columnName)\") || '\"' end";
        blobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || sym_hex($(tableAlias).\"$(columnName)\") || '\"' end";
        triggerConcatCharacter = "||";
        newTriggerValue = "new";
        oldTriggerValue = "old";
        oldColumnPrefix = "";
        newColumnPrefix = "";
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate",
                "create trigger $(triggerName) for $(schemaName)$(tableName) after insert as                                                                                                                            "
                        +
                        "                                declare variable name NUMERIC(18,0);                                                                                                                                           "
                        +
                        "                                declare variable sync_triggers_disabled varchar(30);                                                                                                                   "
                        +
                        "                                declare variable sync_node_disabled varchar(50);                                                                                                                       "
                        +
                        "                                begin                                                                                                                                                                  "
                        +
                        "                                  select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_triggers_disabled' into :sync_triggers_disabled;                                                    " +
                        "                                  $(custom_before_insert_text) \n" +
                        "                                  if ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               "
                        +
                        "                                  begin                                                                                                                                                                "
                        +
                        "                                    select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_node_disabled' into :sync_node_disabled;                                                          " +
                        "                                    select gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1) from rdb$database into :name;                                                                       "
                        +
                        "                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     "
                        +
                        "                                    (data_id, table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)                               "
                        +
                        "                                    values(                                                                                                                                                            "
                        +
                        "                                      :name,                                                                                                                                                             "
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            "
                        +
                        "                                      'I',                                                                                                                                                             "
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             "
                        +
                        "                                      $(columns),                                                                                                                                                      "
                        +
                        "                                      $(channelExpression),                                                                                                                                                "
                        +
                        "                                      $(txIdExpression),                                                                                                                                               "
                        +
                        "                                      :sync_node_disabled,                                                                                                                                             "
                        +
                        "                                      $(externalSelect),                                                                                                                                               "
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                "
                        +
                        "                                    );                                                                                                                                                                 "
                        +
                        "                                  end                                                                                                                                                                  "
                        +
                        "                                  $(custom_on_insert_text)                                                                                                                                             "
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "create trigger $(triggerName) for $(schemaName)$(tableName) after insert as                                                                                                                            "
                        +
                        "                                declare variable name NUMERIC(18,0);                                                                                                                                           "
                        +
                        "                                declare variable sync_triggers_disabled varchar(30);                                                                                                                   "
                        +
                        "                                declare variable sync_node_disabled varchar(50);                                                                                                                       "
                        +
                        "                                begin                                                                                                                                                                  "
                        +
                        "                                  select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_triggers_disabled' into :sync_triggers_disabled;                                                    " +
                        "                                  $(custom_before_insert_text) \n" +
                        "                                  if ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               "
                        +
                        "                                  begin                                                                                                                                                                "
                        +
                        "                                    select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_node_disabled' into :sync_node_disabled;                                                          " +
                        "                                    select gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1) from rdb$database into :name;                                                                       "
                        +
                        "                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     "
                        +
                        "                                    (data_id, table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)                               "
                        +
                        "                                    values(                                                                                                                                                            "
                        +
                        "                                      :name,                                                                                                                                                             "
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            "
                        +
                        "                                      'R',                                                                                                                                                             "
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             "
                        +
                        "                                      $(newKeys),                                                                                                                                                      "
                        +
                        "                                      $(channelExpression),                                                                                                                                                "
                        +
                        "                                      $(txIdExpression),                                                                                                                                               "
                        +
                        "                                      :sync_node_disabled,                                                                                                                                             "
                        +
                        "                                      $(externalSelect),                                                                                                                                               "
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                "
                        +
                        "                                    );                                                                                                                                                                 "
                        +
                        "                                  end                                                                                                                                                                  "
                        +
                        "                                  $(custom_on_insert_text)                                                                                                                                             "
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("updateTriggerTemplate",
                "create trigger $(triggerName) for $(schemaName)$(tableName) after update as                                                                                                                            "
                        +
                        "                                declare variable name NUMERIC(18,0);                                                                                                                                           "
                        +
                        "                                declare variable sync_triggers_disabled varchar(30);                                                                                                                   "
                        +
                        "                                declare variable sync_node_disabled varchar(50);                                                                                                                       "
                        +
                        "                                begin                                                                                                                                                                  "
                        +
                        "                                  select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_triggers_disabled' into :sync_triggers_disabled;                                                    " +
                        "                                  $(custom_before_update_text) \n" +
                        "                                  if ($(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               "
                        +
                        "                                  begin                                                                                                                                                                "
                        +
                        "                                    select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_node_disabled' into :sync_node_disabled;                                                          " +
                        "                                    select gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1) from rdb$database into :name;                                                                       "
                        +
                        "                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     "
                        +
                        "                                    (data_id, table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)            "
                        +
                        "                                    values(                                                                                                                                                            "
                        +
                        "                                      :name,                                                                                                                                                             "
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            "
                        +
                        "                                      'U',                                                                                                                                                             "
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             "
                        +
                        "                                      $(oldKeys),                                                                                                                                                      "
                        +
                        "                                      $(columns),                                                                                                                                                      "
                        +
                        "                                      $(oldColumns),                                                                                                                                                   "
                        +
                        "                                      $(channelExpression),                                                                                                                                                "
                        +
                        "                                      $(txIdExpression),                                                                                                                                               "
                        +
                        "                                      :sync_node_disabled,                                                                                                                                             "
                        +
                        "                                      $(externalSelect),                                                                                                                                               "
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                "
                        +
                        "                                    );                                                                                                                                                                 "
                        +
                        "                                  end                                                                                                                                                                  "
                        +
                        "                                  $(custom_on_update_text)                                                                                                                                             "
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("updateReloadTriggerTemplate",
                "create trigger $(triggerName) for $(schemaName)$(tableName) after update as                                                                                                                            "
                        +
                        "                                declare variable name NUMERIC(18,0);                                                                                                                                           "
                        +
                        "                                declare variable sync_triggers_disabled varchar(30);                                                                                                                   "
                        +
                        "                                declare variable sync_node_disabled varchar(50);                                                                                                                       "
                        +
                        "                                begin                                                                                                                                                                  "
                        +
                        "                                  select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_triggers_disabled' into :sync_triggers_disabled;                                                    " +
                        "                                  $(custom_before_update_text) \n" +
                        "                                  if ($(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               "
                        +
                        "                                  begin                                                                                                                                                                "
                        +
                        "                                    select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_node_disabled' into :sync_node_disabled;                                                          " +
                        "                                    select gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1) from rdb$database into :name;                                                                       "
                        +
                        "                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     "
                        +
                        "                                    (data_id, table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)            "
                        +
                        "                                    values(                                                                                                                                                            "
                        +
                        "                                      :name,                                                                                                                                                             "
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            "
                        +
                        "                                      'R',                                                                                                                                                             "
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             "
                        +
                        "                                      $(oldKeys),                                                                                                                                                      "
                        +
                        "                                      $(channelExpression),                                                                                                                                                "
                        +
                        "                                      $(txIdExpression),                                                                                                                                               "
                        +
                        "                                      :sync_node_disabled,                                                                                                                                             "
                        +
                        "                                      $(externalSelect),                                                                                                                                               "
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                "
                        +
                        "                                    );                                                                                                                                                                 "
                        +
                        "                                  end                                                                                                                                                                  "
                        +
                        "                                  $(custom_on_update_text)                                                                                                                                             "
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("deleteTriggerTemplate",
                "create trigger  $(triggerName) for $(schemaName)$(tableName) after delete as                                                                                                                           "
                        +
                        "                                declare variable name NUMERIC(18,0);                                                                                                                                           "
                        +
                        "                                declare variable sync_triggers_disabled varchar(30);                                                                                                                   "
                        +
                        "                                declare variable sync_node_disabled varchar(50);                                                                                                                       "
                        +
                        "                                begin                                                                                                                                                                  "
                        +
                        "                                  select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_triggers_disabled' into :sync_triggers_disabled;                                                    " +
                        "                                  $(custom_before_delete_text) \n" +
                        "                                  if ($(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition)) then                                                                                               "
                        +
                        "                                  begin                                                                                                                                                                "
                        +
                        "                                    select context_value from $(prefixName)_" + InterbaseSymmetricDialect.CONTEXT_TABLE_NAME
                        + " where name = 'sync_node_disabled' into :sync_node_disabled;                                                          " +
                        "                                    select gen_id($(defaultSchema)GEN_$(prefixName)_data_data_id, 1) from rdb$database into :name;                                                                       "
                        +
                        "                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     "
                        +
                        "                                    (data_id, table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)                      "
                        +
                        "                                    values(                                                                                                                                                            "
                        +
                        "                                      :name,                                                                                                                                                             "
                        +
                        "                                      '$(targetTableName)',                                                                                                                                            "
                        +
                        "                                      'D',                                                                                                                                                             "
                        +
                        "                                      $(triggerHistoryId),                                                                                                                                             "
                        +
                        "                                      $(oldKeys),                                                                                                                                                      "
                        +
                        "                                      $(oldColumns),                                                                                                                                                   "
                        +
                        "                                      $(channelExpression),                                                                                                                                                "
                        +
                        "                                      $(txIdExpression),                                                                                                                                               "
                        +
                        "                                      :sync_node_disabled,                                                                                                                                             "
                        +
                        "                                      $(externalSelect),                                                                                                                                               "
                        +
                        "                                      CURRENT_TIMESTAMP                                                                                                                                                "
                        +
                        "                                    );                                                                                                                                                                 "
                        +
                        "                                  end                                                                                                                                                                  "
                        +
                        "                                  $(custom_on_delete_text)                                                                                                                                             "
                        +
                        "                                end                                                                                                                                                                    ");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select sym_rtrim($(columns))||'' from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                 ");
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