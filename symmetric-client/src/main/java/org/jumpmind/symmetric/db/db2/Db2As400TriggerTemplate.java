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
package org.jumpmind.symmetric.db.db2;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Relation;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;

public class Db2As400TriggerTemplate extends Db2TriggerTemplate {
    public Db2As400TriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace($(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"') || '\"' end";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || rtrim(char(year(timestamp_iso($(tableAlias).\"$(columnName)\"))))||'-'||substr(digits(month(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||'-'||substr(digits(day(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||' '||substr(digits(hour(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||':'||substr(digits(minute(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||':'||substr(digits(second(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||'.'||RIGHT(REPEAT('0',6)||rtrim(char(microsecond(timestamp_iso($(tableAlias).\"$(columnName)\")))),6) || '\"' end";
        String castClobTo = symmetricDialect.getParameterService().getString(ParameterConstants.AS400_CAST_CLOB_TO, "DCLOB");
        clobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(cast($(tableAlias).\"$(columnName)\" as "
                + castClobTo + "),'\\','\\\\'),'\"','\\\"') || '\"' end";
        sqlTemplates.put("insertTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             "
                        +
                        "                                AFTER INSERT ON $(schemaName)$(tableName)                                                                                                                              "
                        +
                        "                                REFERENCING NEW AS NEW                                                                                                                                                 "
                        +
                        "                                FOR EACH ROW MODE DB2SQL                                                                                                                                               "
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           "
                        +
                        "                                    $(custom_before_insert_text) \n" +
                        "                                    IF $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                               "
                        +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 "
                        +
                        "                                            (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)                                "
                        +
                        "                                        VALUES('$(targetTableName)', 'I', $(triggerHistoryId),                                                                                                         "
                        +
                        "                                            $(columns),                                                                                                                                                "
                        +
                        "                                            $(channelExpression), $(txIdExpression), $(sourceNodeExpression),                                                                                          "
                        +
                        "                                            $(externalSelect),                                                                                                                                         "
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        "
                        +
                        "                                    END IF;                                                                                                                                                            "
                        +
                        "                                    $(custom_on_insert_text)                                                                                                                                           "
                        +
                        "                                END                                                                                                                                                                    ");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             "
                        +
                        "                                AFTER INSERT ON $(schemaName)$(tableName)                                                                                                                              "
                        +
                        "                                REFERENCING NEW AS NEW                                                                                                                                                 "
                        +
                        "                                FOR EACH ROW MODE DB2SQL                                                                                                                                               "
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           "
                        +
                        "                                    $(custom_before_insert_text) \n" +
                        "                                    IF $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                               "
                        +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 "
                        +
                        "                                            (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)                                "
                        +
                        "                                        VALUES('$(targetTableName)', 'R', $(triggerHistoryId),                                                                                                         "
                        +
                        "                                            $(newKeys),                                                                                                                                                "
                        +
                        "                                            $(channelExpression), $(txIdExpression), $(sourceNodeExpression),                                                                                          "
                        +
                        "                                            $(externalSelect),                                                                                                                                         "
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        "
                        +
                        "                                    END IF;                                                                                                                                                            "
                        +
                        "                                    $(custom_on_insert_text)                                                                                                                                           "
                        +
                        "                                END                                                                                                                                                                    ");
        sqlTemplates.put("updateTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER UPDATE ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING OLD AS OLD NEW AS NEW                                                                                                                                      \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL                                                                                                                                               \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                    $(custom_before_update_text) \n" +
                        "                                    IF $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                             \n"
                        +
                        "                                            INSERT into $(defaultSchema)$(prefixName)_data                                                                                                             \n"
                        +
                        "                                                (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)         \n"
                        +
                        "                                            VALUES('$(targetTableName)', 'U', $(triggerHistoryId),                                                                                                     \n"
                        +
                        "                                                $(oldKeys),                                                                                                                                            \n"
                        +
                        "                                                $(columns),                                                                                                                                          \n"
                        +
                        "                                                $(oldColumns),                                                                                                                                          \n"
                        +
                        "                                                $(channelExpression),                                                                                                                                      \n"
                        +
                        "                                                $(txIdExpression),                                                                                                                                     \n"
                        +
                        "                                                $(sourceNodeExpression),                                                                                                                               \n"
                        +
                        "                                                $(externalSelect),                                                                                                                                     \n"
                        +
                        "                                                CURRENT_TIMESTAMP);                                                                                                                                    \n"
                        +
                        "                                    END IF;                                                                                                                                                            \n"
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
                        "                                FOR EACH ROW MODE DB2SQL                                                                                                                                               \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                    $(custom_before_update_text) \n" +
                        "                                    IF $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                             \n"
                        +
                        "                                            INSERT into $(defaultSchema)$(prefixName)_data                                                                                                             \n"
                        +
                        "                                                (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)         \n"
                        +
                        "                                            VALUES('$(targetTableName)', 'R', $(triggerHistoryId),                                                                                                     \n"
                        +
                        "                                                $(oldKeys),                                                                                                                                            \n"
                        +
                        "                                                $(channelExpression),                                                                                                                                      \n"
                        +
                        "                                                $(txIdExpression),                                                                                                                                     \n"
                        +
                        "                                                $(sourceNodeExpression),                                                                                                                               \n"
                        +
                        "                                                $(externalSelect),                                                                                                                                     \n"
                        +
                        "                                                CURRENT_TIMESTAMP);                                                                                                                                    \n"
                        +
                        "                                    END IF;                                                                                                                                                            \n"
                        +
                        "                                    $(custom_on_update_text)                                                                                                                                           \n"
                        +
                        "                                END                                                                                                                                                                    ");
        sqlTemplates.put("deleteTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             "
                        +
                        "                                AFTER DELETE ON $(schemaName)$(tableName)                                                                                                                              "
                        +
                        "                                REFERENCING OLD AS OLD                                                                                                                                                 "
                        +
                        "                                FOR EACH ROW MODE DB2SQL                                                                                                                                               "
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           "
                        +
                        "                                    $(custom_before_delete_text) \n" +
                        "                                    IF $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) then                                                                                               "
                        +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 "
                        +
                        "                                            (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)                       "
                        +
                        "                                        VALUES ('$(targetTableName)', 'D', $(triggerHistoryId),                                                                                                        "
                        +
                        "                                            $(oldKeys),                                                                                                                                                "
                        +
                        "                                            $(oldColumns),                                                                                                                                             "
                        +
                        "                                            $(channelExpression),                                                                                                                                          "
                        +
                        "                                            $(txIdExpression),                                                                                                                                         "
                        +
                        "                                            $(sourceNodeExpression),                                                                                                                                   "
                        +
                        "                                            $(externalSelect),                                                                                                                                         "
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        "
                        +
                        "                                    END IF;                                                                                                                                                            "
                        +
                        "                                    $(custom_on_delete_text)                                                                                                                                           "
                        +
                        "                                END                                                                                                                                                                    ");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select $(columns) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                                ");
    }

    public boolean useTriggerTemplateForColumnTemplatesDuringInitialLoad() {
        return false;
    }

    @Override
    protected boolean useTriggerTemplateForColumnTemplatesDuringInitialLoad(Column column) {
        return false;
    }

    @Override
    public String createInitalLoadSql(Node node, TriggerRouter triggerRouter, Relation originalRelation, TriggerHistory triggerHistory, Channel channel,
            String overrideSelectSql) {
        String sql = super.createInitalLoadSql(node, triggerRouter, originalRelation, triggerHistory, channel, overrideSelectSql);
        boolean includeRRN = this.symmetricDialect.getParameterService().is(ParameterConstants.INCLUDE_ROWIDENTIFIER_AS_COLUMN);
        if (includeRRN) {
            sql = sql.replace("\"RRN\"", "RRN(t)");
        }
        return sql;
    }
}
