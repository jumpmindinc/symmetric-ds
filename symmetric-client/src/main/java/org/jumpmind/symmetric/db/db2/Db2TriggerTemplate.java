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

import java.util.HashMap;

import org.jumpmind.db.model.Relation;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class Db2TriggerTemplate extends AbstractTriggerTemplate {
    public Db2TriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''";
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then $(toClob)'' else $(toClob)'\"' || replace(replace($(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"') || '\"' end";
        arrayColumnTemplate = null;
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else $(toClob)'\"' || trim(char($(tableAlias).\"$(columnName)\")) || '\"' end";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else $(toClob)'\"' || lpad(rtrim(char(year(timestamp_iso($(tableAlias).\"$(columnName)\")))),4,'0') ||'-'||substr(digits(month(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||'-'||substr(digits(day(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||' '||substr(digits(hour(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||':'||substr(digits(minute(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||':'||substr(digits(second(timestamp_iso($(tableAlias).\"$(columnName)\"))),9)||'.'||RIGHT(REPEAT('0',6)||rtrim(char(microsecond(timestamp_iso($(tableAlias).\"$(columnName)\")))),6) || '\"' end";
        timeColumnTemplate = null;
        dateColumnTemplate = null;
        clobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when length($(tableAlias).\"$(columnName)\") > 32672 then '\b' else '\"' || replace(replace(cast($(tableAlias).\"$(columnName)\" as varchar(32672)),'\\','\\\\'),'\"','\\\"') || '\"' end";
        blobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when length($(tableAlias).\"$(columnName)\") > 16336 then hex('\b') else '\"' || hex(cast($(tableAlias).\"$(columnName)\" as varchar(16336) for bit data)) || '\"' end";
        xmlColumnTemplate = "'\"\b\"'";
        wrappedBlobColumnTemplate = null;
        booleanColumnTemplate = null;
        triggerConcatCharacter = "||";
        newTriggerValue = "new";
        oldTriggerValue = "old";
        oldColumnPrefix = "";
        newColumnPrefix = "";
        otherColumnTemplate = stringColumnTemplate;
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER INSERT ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING NEW AS NEW                                                                                                                                                 \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL                                                                                                                                               \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                    $(custom_before_insert_text) \n" +
                        "                                    IF $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                               \n"
                        +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 \n"
                        +
                        "                                            (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)                                \n"
                        +
                        "                                        VALUES('$(targetTableName)', 'I', $(triggerHistoryId),                                                                                                         \n"
                        +
                        "                                            $(toClob)$(columns),                                                                                                                                 \n"
                        +
                        "                                            $(channelExpression), $(txIdExpression), $(sourceNodeExpression),                                                                                              \n"
                        +
                        "                                            $(externalSelect),                                                                                                                                         \n"
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        \n"
                        +
                        "                                    END IF;                                                                                                                                                            \n"
                        +
                        "                                    $(custom_on_insert_text)                                                                                                                                           \n"
                        +
                        "                                END                                                                                                                                                                    ");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER INSERT ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING NEW AS NEW                                                                                                                                                 \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL                                                                                                                                               \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                    $(custom_before_insert_text) \n" +
                        "                                    IF $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                               \n"
                        +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 \n"
                        +
                        "                                            (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)                                \n"
                        +
                        "                                        VALUES('$(targetTableName)', 'R', $(triggerHistoryId),                                                                                                         \n"
                        +
                        "                                            $(toClob)$(newKeys),                                                                                                                                 \n"
                        +
                        "                                            $(channelExpression), $(txIdExpression), $(sourceNodeExpression),                                                                                              \n"
                        +
                        "                                            $(externalSelect),                                                                                                                                         \n"
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        \n"
                        +
                        "                                    END IF;                                                                                                                                                            \n"
                        +
                        "                                    $(custom_on_insert_text)                                                                                                                                           \n"
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
                        "                                    DECLARE var_row_data VARCHAR(16336);                                                                                                                               \n"
                        +
                        "                                    DECLARE var_old_data VARCHAR(16336);                                                                                                                               \n"
                        +
                        "                                    $(custom_before_update_text) \n" +
                        "                                    IF $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                               \n"
                        +
                        "                                        SET var_row_data = $(toClob)$(columns);                                                                                                                                 \n"
                        +
                        "                                        SET var_old_data = $(toClob)$(oldColumns);                                                                                                                              \n"
                        +
                        "                                        IF $(dataHasChangedCondition) THEN                                                                                                                             \n"
                        +
                        "                                            INSERT into $(defaultSchema)$(prefixName)_data                                                                                                             \n"
                        +
                        "                                                (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)         \n"
                        +
                        "                                            VALUES('$(targetTableName)', 'U', $(triggerHistoryId),                                                                                                     \n"
                        +
                        "                                                $(toClob)$(oldKeys),                                                                                                                             \n"
                        +
                        "                                                var_row_data,                                                                                                                                          \n"
                        +
                        "                                                var_old_data,                                                                                                                                          \n"
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
                        "                                        END IF;                                                                                                                                                        \n"
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
                        "                                    IF $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                               \n"
                        +
                        "                                            INSERT into $(defaultSchema)$(prefixName)_data                                                                                                             \n"
                        +
                        "                                                (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)         \n"
                        +
                        "                                            VALUES('$(targetTableName)', 'R', $(triggerHistoryId),                                                                                                     \n"
                        +
                        "                                                $(toClob)$(newKeys),                                                                                                                             \n"
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
                "CREATE TRIGGER $(schemaName)$(triggerName)                                                                                                                                                             \n"
                        +
                        "                                AFTER DELETE ON $(schemaName)$(tableName)                                                                                                                              \n"
                        +
                        "                                REFERENCING OLD AS OLD                                                                                                                                                 \n"
                        +
                        "                                FOR EACH ROW MODE DB2SQL                                                                                                                                               \n"
                        +
                        "                                BEGIN ATOMIC                                                                                                                                                           \n"
                        +
                        "                                    $(custom_before_delete_text) \n" +
                        "                                    IF $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) then                                                                                               \n"
                        +
                        "                                        INSERT into $(defaultSchema)$(prefixName)_data                                                                                                                 \n"
                        +
                        "                                            (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)                       \n"
                        +
                        "                                        VALUES ('$(targetTableName)', 'D', $(triggerHistoryId),                                                                                                        \n"
                        +
                        "                                            $(toClob)$(oldKeys),                                                                                                                                 \n"
                        +
                        "                                            $(toClob)$(oldColumns),                                                                                                                              \n"
                        +
                        "                                            $(channelExpression),                                                                                                                                          \n"
                        +
                        "                                            $(txIdExpression),                                                                                                                                         \n"
                        +
                        "                                            $(sourceNodeExpression),                                                                                                                                   \n"
                        +
                        "                                            $(externalSelect),                                                                                                                                         \n"
                        +
                        "                                            CURRENT_TIMESTAMP);                                                                                                                                        \n"
                        +
                        "                                    END IF;                                                                                                                                                            \n"
                        +
                        "                                    $(custom_on_delete_text)                                                                                                                                           \n"
                        +
                        "                                END                                                                                                                                                                    ");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select $(toClob)$(columns) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                                ");
    }

    protected String toClobExpression(Relation relation) {
        if (relation.hasNTypeColumns()) {
            return "nclob('')||";
        } else {
            return "clob('')||";
        }
    }
}