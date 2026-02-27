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
package org.jumpmind.symmetric.db.db2;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.util.FormatUtils;

public class Db2zOsTriggerTemplate extends Db2TriggerTemplate {
    public Db2zOsTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null or $(tableAlias).\"$(columnName)\" = '' then $(toClob)'' else '\"' || $(toClob)replace(replace($(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"') || '\"' end";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || lpad(rtrim(char(year(timestamp_iso($(tableAlias).\"$(columnName)\")))),4,'0') ||'-'||substr(digits(month(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||'-'||substr(digits(day(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||' '||substr(digits(hour(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||':'||substr(digits(minute(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||':'||substr(digits(second(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||'.'||RIGHT(REPEAT('0',6)||rtrim(char(microsecond(timestamp_iso($(tableAlias).\"$(columnName)\")))),6) || '\"' end";
        sqlTemplates.put("insertTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER INSERT ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING NEW AS NEW                                                                                                                                                 \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL $(isAccessControlled)                                                                                                                         \n"
                        +
                        "                                WHEN ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition))                                                                                                    \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                        $(custom_before_insert_text) \n" +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 \n"
                        +
                        "                                            (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)                                \n"
                        +
                        "                                        VALUES('$(targetTableName)', 'I', $(triggerHistoryId),                                                                                                         \n"
                        +
                        "                                            $(columns),                                                                                                                                                \n"
                        +
                        "                                            $(channelExpression), $(txIdExpression), $(sourceNodeExpression),                                                                                          \n"
                        +
                        "                                            $(externalSelect),                                                                                                                                         \n"
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        \n"
                        +
                        "                                    $(custom_on_insert_text)                                                                                                                                           \n"
                        +
                        "                                END                                                                                                                                                                    \n");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER INSERT ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING NEW AS NEW                                                                                                                                                 \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL $(isAccessControlled)                                                                                                                         \n"
                        +
                        "                                WHEN ($(syncOnInsertCondition) and $(syncOnIncomingBatchCondition))                                                                                                    \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                        $(custom_before_insert_text) \n" +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 \n"
                        +
                        "                                            (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)                                \n"
                        +
                        "                                        VALUES('$(targetTableName)', 'R', $(triggerHistoryId),                                                                                                         \n"
                        +
                        "                                            $(newKeys),                                                                                                                                                \n"
                        +
                        "                                            $(channelExpression), $(txIdExpression), $(sourceNodeExpression),                                                                                          \n"
                        +
                        "                                            $(externalSelect),                                                                                                                                         \n"
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        \n"
                        +
                        "                                    $(custom_on_insert_text)                                                                                                                                           \n"
                        +
                        "                                END                                                                                                                                                                    \n");
        sqlTemplates.put("updateTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER UPDATE ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING OLD AS OLD NEW AS NEW                                                                                                                                      \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL $(isAccessControlled)                                                                                                                        \n"
                        +
                        "                                WHEN ($(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition))                                                                                                    \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                            $(custom_before_update_text) \n" +
                        "                                            INSERT into $(defaultSchema)$(prefixName)_data                                                                                                             \n"
                        +
                        "                                                (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)         \n"
                        +
                        "                                            VALUES('$(targetTableName)', 'U', $(triggerHistoryId),                                                                                                     \n"
                        +
                        "                                                $(oldKeys),                                                                                                                                            \n"
                        +
                        "                                                $(columns),                                                                                                                                            \n"
                        +
                        "                                                $(oldColumns),                                                                                                                                         \n"
                        +
                        "                                                $(channelExpression),                                                                                                                                  \n"
                        +
                        "                                                $(txIdExpression),                                                                                                                                     \n"
                        +
                        "                                                $(sourceNodeExpression),                                                                                                                               \n"
                        +
                        "                                                $(externalSelect),                                                                                                                                     \n"
                        +
                        "                                                CURRENT_TIMESTAMP);                                                                                                                                    \n"
                        +
                        "                                    $(custom_on_update_text)                                                                                                                                           \n"
                        +
                        "                                END                                                                                                                                                                    ");
        sqlTemplates.put("updateReloadTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER UPDATE ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING OLD AS OLD NEW AS NEW                                                                                                                                      \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL $(isAccessControlled)                                                                                                                        \n"
                        +
                        "                                WHEN ($(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition))                                                                                                    \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                            $(custom_before_update_text) \n" +
                        "                                            INSERT into $(defaultSchema)$(prefixName)_data                                                                                                             \n"
                        +
                        "                                                (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)         \n"
                        +
                        "                                            VALUES('$(targetTableName)', 'R', $(triggerHistoryId),                                                                                                     \n"
                        +
                        "                                                $(newKeys),                                                                                                                                            \n"
                        +
                        "                                                $(channelExpression),                                                                                                                                  \n"
                        +
                        "                                                $(txIdExpression),                                                                                                                                     \n"
                        +
                        "                                                $(sourceNodeExpression),                                                                                                                               \n"
                        +
                        "                                                $(externalSelect),                                                                                                                                     \n"
                        +
                        "                                                CURRENT_TIMESTAMP);                                                                                                                                    \n"
                        +
                        "                                    $(custom_on_update_text)                                                                                                                                           \n"
                        +
                        "                                END                                                                                                                                                                    ");
        sqlTemplates.put("deleteTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER DELETE ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING OLD AS OLD                                                                                                                                                 \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL $(isAccessControlled)                                                                                                                         \n"
                        +
                        "                                WHEN ($(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition))                                                                                                    \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                        $(custom_before_delete_text) \n" +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 \n"
                        +
                        "                                            (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)                       \n"
                        +
                        "                                        VALUES ('$(targetTableName)', 'D', $(triggerHistoryId),                                                                                                        \n"
                        +
                        "                                            $(oldKeys),                                                                                                                                                \n"
                        +
                        "                                            $(oldColumns),                                                                                                                                             \n"
                        +
                        "                                            $(channelExpression),                                                                                                                                      \n"
                        +
                        "                                            $(txIdExpression),                                                                                                                                         \n"
                        +
                        "                                            $(sourceNodeExpression),                                                                                                                                   \n"
                        +
                        "                                            $(externalSelect),                                                                                                                                         \n"
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        \n"
                        +
                        "                                    $(custom_on_delete_text)                                                                                                                                           \n"
                        +
                        "                                END                                                                                                                                                                    \n");
    }

    @Override
    protected String replaceTemplateVariables(DataEventType dml, Trigger trigger,
            TriggerHistory history, Channel channel, String tablePrefix, Table originalTable, Table table,
            String defaultCatalog, String defaultSchema, String ddl) {
        ddl = super.replaceTemplateVariables(dml, trigger, history, channel, tablePrefix,
                originalTable, table, defaultCatalog, defaultSchema, ddl);
        ddl = FormatUtils.replace("isAccessControlled", table.isAccessControlled() ? " SECURED" : "", ddl);
        return ddl;
    }
}