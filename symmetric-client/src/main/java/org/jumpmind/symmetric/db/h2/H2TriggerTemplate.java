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
package org.jumpmind.symmetric.db.h2;

import java.util.HashMap;

import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class H2TriggerTemplate extends AbstractTriggerTemplate {
    public H2TriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''''";
        stringColumnTemplate = "case when $(tableAlias)\"$(columnName)\" is null then '''' else ''\"''||replace(replace($(tableAlias)\"$(columnName)\",''\\'',''\\\\''),''\"'',''\\\"'')||''\"'' end";
        numberColumnTemplate = "case when $(tableAlias)\"$(columnName)\" is null then '''' else ''\"''||cast($(tableAlias)\"$(columnName)\" as varchar(50))||''\"'' end";
        datetimeColumnTemplate = "case when $(tableAlias)\"$(columnName)\" is null then '''' else ''\"''||formatdatetime($(tableAlias)\"$(columnName)\", ''yyyy-MM-dd HH:mm:ss.SSS'')||''\"'' end";
        clobColumnTemplate = "case when $(tableAlias)\"$(columnName)\" is null then '''' else ''\"''||replace(replace($(tableAlias)\"$(columnName)\",''\\'',''\\\\''),''\"'',''\\\"'')||''\"'' end";
        blobColumnTemplate = "case when $(tableAlias)\"$(columnName)\" is null then '''' else ''\"''||replace(replace(sym_BASE64_ENCODE($(tableAlias)\"$(columnName)\"),''\\'',''\\\\''),''\"'',''\\\"'')||''\"'' end";
        booleanColumnTemplate = "case when $(tableAlias)\"$(columnName)\" is null then '''' when $(tableAlias)\"$(columnName)\" then ''\"1\"'' else ''\"0\"'' end";
        if (!Version.isOlderThanVersion(symmetricDialect.getProductVersion(), "2.0.202")) {
            timeColumnTemplate = "case when $(tableAlias)\"$(columnName)\" is null then '''' else ''\"''||to_char($(tableAlias)\"$(columnName)\", ''HH:MI:SSXFF3'')||''\"'' end";
        }
        triggerConcatCharacter = "||";
        newTriggerValue = "";
        oldTriggerValue = "";
        oldColumnPrefix = "OLD_";
        newColumnPrefix = "NEW_";
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate",
                "CREATE TABLE $(schemaName)$(triggerName)_CONFIG (CONDITION_SQL CLOB, INSERT_DATA_SQL CLOB);                                                                                                                         "
                        +
                        "                                INSERT INTO $(schemaName)$(triggerName)_CONFIG values(                                                                                                                              "
                        +
                        "                                'select count(*) from $(virtualOldNewTable) where $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition)',                                                       "
                        +
                        "                                'insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)"
                        +
                        "                                  (select ''$(targetTableName)'',''I'',$(triggerHistoryId),$(columns), $(channelExpression), $(txIdExpression), @node_value, $(externalSelect), CURRENT_TIMESTAMP from $(virtualOldNewTable))' "
                        +
                        "                                ); COMMIT;                                                                                                                                                                    "
                        +
                        "                                CREATE TRIGGER $(schemaName)$(triggerName) AFTER INSERT ON $(schemaName)$(tableName) FOR EACH ROW CALL \"org.jumpmind.symmetric.db.h2.H2Trigger\";                                               ");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "CREATE TABLE $(schemaName)$(triggerName)_CONFIG (CONDITION_SQL CLOB, INSERT_DATA_SQL CLOB);                                                                                                                         "
                        +
                        "                                INSERT INTO $(schemaName)$(triggerName)_CONFIG values(                                                                                                                              "
                        +
                        "                                'select count(*) from $(virtualOldNewTable) where $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition)',                                                       "
                        +
                        "                                'insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)"
                        +
                        "                                  (select ''$(targetTableName)'',''R'',$(triggerHistoryId),$(newKeys), $(channelExpression), $(txIdExpression), @node_value, $(externalSelect), CURRENT_TIMESTAMP from $(virtualOldNewTable))' "
                        +
                        "                                ); COMMIT;                                                                                                                                                                    "
                        +
                        "                                CREATE TRIGGER $(schemaName)$(triggerName) AFTER INSERT ON $(schemaName)$(tableName) FOR EACH ROW CALL \"org.jumpmind.symmetric.db.h2.H2Trigger\";                                               ");
        sqlTemplates.put("updateTriggerTemplate",
                "CREATE TABLE $(schemaName)$(triggerName)_CONFIG (CONDITION_SQL CLOB, INSERT_DATA_SQL CLOB);                                                                                                                         "
                        +
                        "                                INSERT INTO $(schemaName)$(triggerName)_CONFIG values(                                                                                                                              "
                        +
                        "                                  'select count(*) from $(virtualOldNewTable) where $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition)',                                                     "
                        +
                        "                                  'insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)"
                        +
                        "                                    (select ''$(targetTableName)'',''U'',$(triggerHistoryId),$(oldKeys),$(columns),$(oldColumns), $(channelExpression), $(txIdExpression), @node_value, $(externalSelect), CURRENT_TIMESTAMP from $(virtualOldNewTable))'"
                        +
                        "                                ); COMMIT;                                                                                                                                                                    "
                        +
                        "                                CREATE TRIGGER $(schemaName)$(triggerName) AFTER UPDATE ON $(schemaName)$(tableName) FOR EACH ROW CALL \"org.jumpmind.symmetric.db.h2.H2Trigger\";                                               ");
        sqlTemplates.put("updateReloadTriggerTemplate",
                "CREATE TABLE $(schemaName)$(triggerName)_CONFIG (CONDITION_SQL CLOB, INSERT_DATA_SQL CLOB);                                                                                                                         "
                        +
                        "                                INSERT INTO $(schemaName)$(triggerName)_CONFIG values(                                                                                                                              "
                        +
                        "                                  'select count(*) from $(virtualOldNewTable) where $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition)',                                                     "
                        +
                        "                                  'insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)"
                        +
                        "                                    (select ''$(targetTableName)'',''R'',$(triggerHistoryId),$(oldKeys), $(channelExpression), $(txIdExpression), @node_value, $(externalSelect), CURRENT_TIMESTAMP from $(virtualOldNewTable))'"
                        +
                        "                                ); COMMIT;                                                                                                                                                                    "
                        +
                        "                                CREATE TRIGGER $(schemaName)$(triggerName) AFTER UPDATE ON $(schemaName)$(tableName) FOR EACH ROW CALL \"org.jumpmind.symmetric.db.h2.H2Trigger\";                                               ");
        sqlTemplates.put("deleteTriggerTemplate",
                "CREATE TABLE $(schemaName)$(triggerName)_CONFIG (CONDITION_SQL CLOB, INSERT_DATA_SQL CLOB);                                                                                                                         "
                        +
                        "                                INSERT INTO $(schemaName)$(triggerName)_CONFIG values(                                                                                                                              "
                        +
                        "                                  'select count(*) from $(virtualOldNewTable) where $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition)',                                                     "
                        +
                        "                                  'insert into $(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)"
                        +
                        "                                    (select ''$(targetTableName)'',''D'',$(triggerHistoryId),$(oldKeys),$(oldColumns),$(channelExpression), $(txIdExpression), @node_value, $(externalSelect), CURRENT_TIMESTAMP from $(virtualOldNewTable))'"
                        +
                        "                                ); COMMIT;                                                                                                                                                                    "
                        +
                        "                                CREATE TRIGGER $(schemaName)$(triggerName) AFTER DELETE ON $(schemaName)$(tableName) FOR EACH ROW CALL \"org.jumpmind.symmetric.db.h2.H2Trigger\";                                               ");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select $(columns) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                                ");
    }
}