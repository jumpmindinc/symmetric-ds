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
package org.jumpmind.symmetric.db.raima;

import java.util.HashMap;

import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class RaimaTriggerTemplate extends AbstractTriggerTemplate {
    public RaimaTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''";
        stringColumnTemplate = "if($(tableAlias).$(columnName) is null, '', '\"' || replace(replace($(tableAlias).$(columnName), '\\\\','\\\\\\\\'), '\"','\\\"') || '\"')";
        numberColumnTemplate = "if($(tableAlias).$(columnName) is null, '', convert($(tableAlias).$(columnName), char))";
        datetimeColumnTemplate = "if($(tableAlias).$(columnName) is null, '', '\"' || convert($(tableAlias).$(columnName), char) || '\"')";
        clobColumnTemplate = stringColumnTemplate;
        blobColumnTemplate = "''";
        booleanColumnTemplate = "if($(tableAlias).$(columnName) is null, '', convert($(tableAlias).$(columnName), char))";
        triggerConcatCharacter = "||";
        newTriggerValue = "new_row";
        oldTriggerValue = "old_row";
        oldColumnPrefix = "";
        newColumnPrefix = "";
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate",
                "create trigger $(triggerName) after insert on $(schemaName)$(tableName) \n" +
                        "referencing new row as new_row \n" +
                        "for each row begin atomic \n" +
                        "$(custom_before_insert_text) \n" +
                        "if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then \n" +
                        "   insert into $(defaultSchema)$(prefixName)_data \n" +
                        "   (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
                        "   values('$(targetTableName)', 'I', $(triggerHistoryId), \n" +
                        "   $(columns), \n" +
                        "   $(channelExpression), $(txIdExpression), sync_node_disabled, \n" +
                        "   $(externalSelect), \n" +
                        "   current_timestamp); \n" +
                        "end if; \n" +
                        "$(custom_on_insert_text) \n" +
                        "end");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "create trigger $(triggerName) after insert on $(schemaName)$(tableName) \n" +
                        "referencing new row as new_row \n" +
                        "for each row begin atomic \n" +
                        "$(custom_before_insert_text) \n" +
                        "if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then \n" +
                        "   insert into $(defaultSchema)$(prefixName)_data \n" +
                        "   (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
                        "   values('$(targetTableName)', 'R', $(triggerHistoryId), \n" +
                        "   $(newKeys), \n" +
                        "   $(channelExpression), $(txIdExpression), sync_node_disabled, \n" +
                        "   $(externalSelect), \n" +
                        "   current_timestamp); \n" +
                        "end if; \n" +
                        "$(custom_on_insert_text) \n" +
                        "end");
        sqlTemplates.put("updateTriggerTemplate",
                "create trigger $(triggerName) after update on $(schemaName)$(tableName) \n" +
                        "referencing new row as new_row old row as old_row  \n" +
                        "for each row begin atomic \n" +
                        "$(custom_before_update_text) \n" +
                        "if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then \n" +
                        "   insert into $(defaultSchema)$(prefixName)_data \n" +
                        "   (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n"
                        +
                        "   values('$(targetTableName)', 'U', $(triggerHistoryId), \n" +
                        "   $(oldKeys), \n" +
                        "   $(columns), \n" +
                        "   $(oldColumns), \n" +
                        "   $(channelExpression), $(txIdExpression), sync_node_disabled, \n" +
                        "   $(externalSelect), \n" +
                        "   current_timestamp); \n" +
                        "end if; \n" +
                        "$(custom_on_update_text) \n" +
                        "end");
        sqlTemplates.put("deleteTriggerTemplate",
                "create trigger $(triggerName) after delete on $(schemaName)$(tableName) \n" +
                        "referencing old row as old_row " +
                        "for each row begin atomic \n" +
                        "$(custom_before_delete_text) \n" +
                        "if $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) then \n" +
                        "   insert into $(defaultSchema)$(prefixName)_data \n" +
                        "   (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n"
                        +
                        "   values('$(targetTableName)', 'D', $(triggerHistoryId), \n" +
                        "   $(oldKeys), \n" +
                        "   $(oldColumns), \n" +
                        "   $(channelExpression), $(txIdExpression), sync_node_disabled, \n" +
                        "   $(externalSelect), \n" +
                        "   current_timestamp); \n" +
                        "end if; \n" +
                        "$(custom_on_delete_text) \n" +
                        "end");
        sqlTemplates.put("initialLoadSqlTemplate", "select $(columns) from $(schemaName)$(tableName) as t where $(whereClause) ");
    }
}