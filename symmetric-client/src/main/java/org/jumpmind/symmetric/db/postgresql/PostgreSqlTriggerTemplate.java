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
package org.jumpmind.symmetric.db.postgresql;

import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;

public class PostgreSqlTriggerTemplate extends AbstractTriggerTemplate {
    String delimiter;
    String infinityDateExpression;

    public PostgreSqlTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        delimiter = symmetricDialect.getParameterService().getString(ParameterConstants.TRIGGER_CAPTURE_DDL_DELIMITER, "$");
        infinityDateExpression = symmetricDialect.getParameterService().is(ParameterConstants.POSTGRES_CONVERT_INFINITY_DATE_TO_NULL, true) ? "''"
                : "cast($(tableAlias).\"$(columnName)\" as varchar)";
        //@formatter:off        
        geometryColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(cast(ST_AsEWKT($(tableAlias).\"$(columnName)\") as varchar),$$\\$$,$$\\\\$$),'\"',$$\\\"$$) || '\"' end" ;
        geographyColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(cast(ST_AsEWKT($(tableAlias).\"$(columnName)\") as varchar),$$\\$$,$$\\\\$$),'\"',$$\\\"$$) || '\"' end" ;
        emptyColumnTemplate = "''" ;
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(cast($(tableAlias).\"$(columnName)\" as varchar),$$\\$$,$$\\\\$$),'\"',$$\\\"$$) || '\"' end" ;
        xmlColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(cast($(tableAlias).\"$(columnName)\" as varchar),$$\\$$,$$\\\\$$),'\"',$$\\\"$$) || '\"' end" ;
        arrayColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(cast($(tableAlias).\"$(columnName)\" as varchar),$$\\$$,$$\\\\$$),'\"',$$\\\"$$) || '\"' end" ;
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || cast(cast($(tableAlias).\"$(columnName)\" as numeric) as varchar) || '\"' end" ;
        dateColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when isfinite($(tableAlias).\"$(columnName)\") then '\"' || to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS') || '\"' "
                + "else " + infinityDateExpression + " end" ;
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when isfinite($(tableAlias).\"$(columnName)\") then '\"' || to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS.US') || '\"' " 
                + "else " + infinityDateExpression + " end" ;
        timeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || to_char($(tableAlias).\"$(columnName)\", 'HH24:MI:SS.US') || '\"' end" ;
        dateTimeWithTimeZoneColumnTemplate =
                "case when $(tableAlias).\"$(columnName)\" is null then '' when isfinite($(tableAlias).\"$(columnName)\") then                                                   " +
                "   case                                                                                                             " +
                "   when extract(timezone_hour from $(tableAlias).\"$(columnName)\") <= 0 and                                        " +
                "        extract(timezone_minute from $(tableAlias).\"$(columnName)\") <= 0 then                                      " +
                "     '\"' || to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS.US ')||'-'||                           " +
                "     lpad(cast(abs(round(extract(timezone_hour from $(tableAlias).\"$(columnName)\"))) as varchar),2,'0')||':'||           " +
                "     lpad(cast(abs(round(extract(timezone_minute from $(tableAlias).\"$(columnName)\"))) as varchar), 2, '0') || '\"'      " +
                "   when extract(timezone_hour from $(tableAlias).\"$(columnName)\") = 0 and                                        " +
                "        extract(timezone_minute from $(tableAlias).\"$(columnName)\") >= 0 then                                      " +
                "     '\"' || to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS.US ')||'+'||                           " +
                "     lpad(cast(round(extract(timezone_hour from $(tableAlias).\"$(columnName)\")) as varchar),2,'0')||':'||           " +
                "     lpad(cast(round(extract(timezone_minute from $(tableAlias).\"$(columnName)\")) as varchar), 2, '0') || '\"'      " +
                "   else                                                                                                             " +
                "     '\"' || to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS.US ')||'+'||                           " +
                "     lpad(cast(round(extract(timezone_hour from $(tableAlias).\"$(columnName)\")) as varchar),2,'0')||':'||                " +
                "     lpad(cast(round(extract(timezone_minute from $(tableAlias).\"$(columnName)\")) as varchar), 2, '0') || '\"'           " +
                "   end                                                                                                              " +
                "else " + infinityDateExpression + " " +
                "end                                                                                                                 ";
        clobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(cast($(tableAlias).\"$(columnName)\" as varchar),$$\\$$,$$\\\\$$),'\"',$$\\\"$$) || '\"' end" ;
        blobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || pg_catalog.encode($(tableAlias).\"$(columnName)\", 'base64') || '\"' end" ;
        wrappedBlobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || $(defaultSchema)$(prefixName)_largeobject($(tableAlias).\"$(columnName)\") || '\"' end" ;
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when $(tableAlias).\"$(columnName)\" then '\"1\"' else '\"0\"' end" ;
        triggerConcatCharacter = "||" ;
        newTriggerValue = "new" ;
        oldTriggerValue = "old" ;
        oldColumnPrefix = "" ;
        newColumnPrefix = "" ;
        otherColumnTemplate = stringColumnTemplate;
        sqlTemplates = new HashMap<String,String>();
        sqlTemplates.put("insertTriggerTemplate" ,
"create or replace function $(schemaName)f$(triggerName)() returns trigger as $function$                                                                                                                \n" +
"                                begin                                                                                                                                                                  \n" +
"                                  $(custom_before_insert_text) \n" +
"                                  if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"                                    (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time)                                        \n" +
"                                    values(                                                                                                                                                            \n" +
"                                      '$(targetTableName)',                                                                                                                                            \n" +
"                                      'I',                                                                                                                                                             \n" +
"                                      $(triggerHistoryId),                                                                                                                                             \n" +
"                                      $(columns),                                                                                                                                                      \n" +
"                                      $(channelExpression),                                                                                                                                                \n" +
"                                      $(txIdExpression),                                                                                                                                               \n" +
"                                      $(defaultSchema)$(prefixName)_node_disabled(),                                                                                                                   \n" +
"                                      $(externalSelect),                                                                                                                                               \n" +
"                                      " + getCreateTimeExpression(symmetricDialect) + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_insert_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + getSecurityClause() + ";");

        sqlTemplates.put("insertReloadTriggerTemplate" ,
"create or replace function $(schemaName)f$(triggerName)() returns trigger as $function$                                                                                                                \n" +
"                                begin                                                                                                                                                                  \n" +
"                                  $(custom_before_insert_text) \n" +
"                                  if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"                                    (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)                                        \n" +
"                                    values(                                                                                                                                                            \n" +
"                                      '$(targetTableName)',                                                                                                                                            \n" +
"                                      'R',                                                                                                                                                             \n" +
"                                      $(triggerHistoryId),                                                                                                                                             \n" +
"                                      $(newKeys),                                                                                                                                                      \n" +
"                                      $(channelExpression),                                                                                                                                                \n" +
"                                      $(txIdExpression),                                                                                                                                               \n" +
"                                      $(defaultSchema)$(prefixName)_node_disabled(),                                                                                                                   \n" +
"                                      $(externalSelect),                                                                                                                                               \n" +
"                                      " + getCreateTimeExpression(symmetricDialect) + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_insert_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + getSecurityClause() + ";");

        
        sqlTemplates.put("insertPostTriggerTemplate" ,
"create trigger $(triggerName) after insert on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row execute procedure $(schemaName)f$(triggerName)();                                                                                                         " );

        sqlTemplates.put("updateTriggerTemplate" ,
"create or replace function $(schemaName)f$(triggerName)() returns trigger as $function$                                                                                                                \n" +
"                                declare var_row_data text; \n" +        
"                                declare var_old_data text; \n" +
"                                begin\n" +
"                                  $(custom_before_update_text) \n" +
"                                  if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                    var_row_data := $(columns); \n" +
"                                    var_old_data := $(oldColumns); \n" +
"                                    if $(dataHasChangedCondition) then \n" +
"                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"                                    (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)                     \n" +
"                                    values(                                                                                                                                                            \n" +
"                                      '$(targetTableName)',                                                                                                                                            \n" +
"                                      'U',                                                                                                                                                             \n" +
"                                      $(triggerHistoryId),                                                                                                                                             \n" +
"                                      $(oldKeys),                                                                                                                                                      \n" +
"                                      var_row_data,                                                                                                                                                      \n" +
"                                      var_old_data,                                                                                                                                                   \n" +
"                                      $(channelExpression),                                                                                                                                                \n" +
"                                      $(txIdExpression),                                                                                                                                               \n" +
"                                      $(defaultSchema)$(prefixName)_node_disabled(),                                                                                                                   \n" +
"                                      $(externalSelect),                                                                                                                                               \n" +
"                                      " + getCreateTimeExpression(symmetricDialect) + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_update_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + getSecurityClause() + ";");

        sqlTemplates.put("updateReloadTriggerTemplate" ,
"create or replace function $(schemaName)f$(triggerName)() returns trigger as $function$                                                                                                                \n" +
"                                declare var_row_data text; \n" +        
"                                declare var_old_data text; \n" +
"                                begin\n" +
"                                  $(custom_before_update_text) \n" +
"                                  if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                    var_row_data := $(columns); \n" +
"                                    var_old_data := $(oldColumns); \n" +
"                                    if $(dataHasChangedCondition) then \n" +
"                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"                                    (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)                     \n" +
"                                    values(                                                                                                                                                            \n" +
"                                      '$(targetTableName)',                                                                                                                                            \n" +
"                                      'R',                                                                                                                                                             \n" +
"                                      $(triggerHistoryId),                                                                                                                                             \n" +
"                                      $(oldKeys),                                                                                                                                                      \n" +
"                                      $(channelExpression),                                                                                                                                                \n" +
"                                      $(txIdExpression),                                                                                                                                               \n" +
"                                      $(defaultSchema)$(prefixName)_node_disabled(),                                                                                                                   \n" +
"                                      $(externalSelect),                                                                                                                                               \n" +
"                                      " + getCreateTimeExpression(symmetricDialect) + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_update_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + getSecurityClause() + ";");

        sqlTemplates.put("updatePostTriggerTemplate" ,
"create trigger $(triggerName) after update on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row execute procedure $(schemaName)f$(triggerName)();                                                                                                         " );

        sqlTemplates.put("deleteTriggerTemplate" ,
"create or replace function $(schemaName)f$(triggerName)() returns trigger as $function$                                                                                                                \n" +
"                                begin                                                                                                                                                                  \n" +
"                                  $(custom_before_delete_text) \n" +
"                                  if $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) then                                                                                                 \n" +
"                                    insert into $(defaultSchema)$(prefixName)_data                                                                                                                     \n" +
"                                    (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)                               \n" +
"                                    values(                                                                                                                                                            \n" +
"                                      '$(targetTableName)',                                                                                                                                            \n" +
"                                      'D',                                                                                                                                                             \n" +
"                                      $(triggerHistoryId),                                                                                                                                             \n" +
"                                      $(oldKeys),                                                                                                                                                      \n" +
"                                      $(oldColumns),                                                                                                                                                   \n" +
"                                      $(channelExpression),                                                                                                                                                \n" +
"                                      $(txIdExpression),                                                                                                                                               \n" +
"                                      $(defaultSchema)$(prefixName)_node_disabled(),                                                                                                                   \n" +
"                                      $(externalSelect),                                                                                                                                               \n" +
"                                      " + getCreateTimeExpression(symmetricDialect) + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_delete_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + getSecurityClause() + ";");

        sqlTemplates.put("deletePostTriggerTemplate" ,
"create trigger $(triggerName) after delete on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row execute procedure $(schemaName)f$(triggerName)();                                                                                                         " );

        sqlTemplates.put("initialLoadSqlTemplate" ,
"select $(columns) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                                " );

        sqlTemplates.put("filteredDdlTriggerTemplate",
"create or replace function f$(triggerName)() returns event_trigger as\n" +
"$function$\n" +
"declare cmd record;\n" +
"declare tableName varchar(255);\n" +
"declare histId integer;\n" +
"declare channelId varchar(128);\n" +
"declare rowData text;\n" +
"begin\n" +
"rowData = current_query();\n" +
"for cmd in select * from pg_event_trigger_ddl_commands() loop\n" +
"    if (upper(cmd.object_identity) not like upper('$(prefixName)%') and upper(cmd.object_identity) not like upper('%.$(prefixName)%') and" + 
"    upper(cmd.object_identity) not like upper('f$(prefixName)%') and upper(cmd.object_identity) not like upper('%.f$(prefixName)%') and" +
"    (upper(rowData) not like '%CREATE%TABLE%(%' or cmd.command_tag like '%CREATE%TABLE%')) then\n" +
"        tableName := '$(prefixName)_node';\n" +
"        if (cmd.command_tag like '%TABLE%') then\n" +
"            tableName := cmd.object_identity;\n" +
"        end if;\n" +
"        if (cmd.command_tag like '%TRIGGER%') then\n" +
"            select c.relname into tableName from pg_trigger t join pg_class c on t.tgrelid = c.oid" +
"            where t.tgname = trim(both '\"' from split_part(cmd.object_identity, '.', 2));\n" +
"        end if;\n" +
"        if (cmd.command_tag like '%INDEX%') then\n" +
"            select ct.relname into tableName from pg_index i join pg_class ci on i.indexrelid = ci.oid join pg_class ct on i.indrelid = ct.oid" +
"            where ci.relname = trim(both '\"' from split_part(cmd.object_identity, '.', 2));\n" +
"        end if;\n" +
"        if (tableName like '%.%') then\n" +
"            tableName := split_part(tableName, '.', 2);\n" +
"        end if;\n" +
"        tableName := trim(both '\"' from tableName);\n" +
"        select trigger_hist_id, source_table_name into histId, tableName from $(defaultSchema)$(prefixName)trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"        if (histId is not null) then\n" +
"            select channel_id into channelId from $(defaultSchema)$(prefixName)trigger where upper(source_table_name) = upper(tableName);\n" +
"            if (channelId is null) then\n" +
"                channelId := 'config';\n" +
"            end if;\n" +
"            insert into $(defaultSchema)$(prefixName)_data\n" +
"            (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"            values (tableName, '" + DataEventType.SQL.getCode() + "', histId,\n" +
"            '\"delimiter " + delimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"            channelId, $(defaultSchema)$(prefixName)_node_disabled(), " + getCreateTimeExpression(symmetricDialect) + ");\n" +
"        end if;\n" +
"    end if;\n" +
"end loop;\n" +
"end;\n" +
"$function$ language plpgsql" + getSecurityClause() + ";" +
"create or replace function f$(triggerName)_drop() returns event_trigger as\n" +
"$function$\n" +
"declare cmd record;\n" +
"declare histId integer;\n" +
"declare rowData text;\n" +
"begin\n" +
"rowData = current_query();\n" +
"for cmd in select * from pg_event_trigger_dropped_objects() loop\n" +
"    if (upper(cmd.object_identity) not like upper('$(prefixName)%') and upper(cmd.object_identity) not like upper('%.$(prefixName)%') and" + 
"    upper(cmd.object_identity) not like upper('f$(prefixName)%') and upper(cmd.object_identity) not like upper('%.f$(prefixName)%') and cmd.original) then\n" +
"        select trigger_hist_id into histId from $(defaultSchema)$(prefixName)trigger_hist where upper(source_table_name) = upper('$(prefixName)_node') and inactive_time is null;\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"        values ('$(prefixName)_node', '" + DataEventType.SQL.getCode() + "', histId,\n" +
"        '\"delimiter " + delimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"        'config', $(defaultSchema)$(prefixName)_node_disabled(), " + getCreateTimeExpression(symmetricDialect) + ");\n" +
"    end if;\n" +
"end loop;\n" +
"end;\n" +
"$function$ language plpgsql" + getSecurityClause() + ";");

        sqlTemplates.put("allDdlTriggerTemplate",
"create or replace function f$(triggerName)() returns event_trigger as\n" +
"$function$\n" +
"declare cmd record;\n" +
"declare tableName varchar(255);\n" +
"declare histId integer;\n" +
"declare channelId varchar(128);\n" +
"declare rowData text;\n" +
"begin\n" +
"rowData = current_query();\n" +
"for cmd in select * from pg_event_trigger_ddl_commands() loop\n" +
"    if (upper(cmd.object_identity) not like upper('$(prefixName)%') and upper(cmd.object_identity) not like upper('%.$(prefixName)%') and" + 
"    upper(cmd.object_identity) not like upper('f$(prefixName)%') and upper(cmd.object_identity) not like upper('%.f$(prefixName)%') and" +
"    (upper(rowData) not like '%CREATE%TABLE%(%' or cmd.command_tag like '%CREATE%TABLE%')) then\n" +
"        if (cmd.command_tag like '%TABLE%') then\n" +
"            tableName := cmd.object_identity;\n" +
"        end if;\n" +
"        if (cmd.command_tag like '%TRIGGER%') then\n" +
"            select c.relname into tableName from pg_trigger t join pg_class c on t.tgrelid = c.oid" +
"            where t.tgname = trim(both '\"' from split_part(cmd.object_identity, '.', 2));\n" +
"        end if;\n" +
"        if (cmd.command_tag like '%INDEX%') then\n" +
"            select ct.relname into tableName from pg_index i join pg_class ci on i.indexrelid = ci.oid join pg_class ct on i.indrelid = ct.oid" +
"            where ci.relname = trim(both '\"' from split_part(cmd.object_identity, '.', 2));\n" +
"        end if;\n" +
"        if (tableName is not null) then\n" +
"            if (tableName like '%.%') then\n" +
"                tableName := split_part(tableName, '.', 2);\n" +
"            end if;\n" +
"            tableName := trim(both '\"' from tableName);\n" +
"            select trigger_hist_id, source_table_name into histId, tableName from $(defaultSchema)$(prefixName)trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"        end if;\n" +
"        if (histId is null) then\n" +
"            tableName := '$(prefixName)_node';\n" +
"            select trigger_hist_id into histId from $(defaultSchema)$(prefixName)trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"        end if;\n" +
"        select channel_id into channelId from $(defaultSchema)$(prefixName)trigger where upper(source_table_name) = upper(tableName);\n" +
"        if (channelId is null) then\n" +
"            channelId := 'config';\n" +
"        end if;\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"        values (tableName, '" + DataEventType.SQL.getCode() + "', histId,\n" +
"        '\"delimiter " + delimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"        channelId, $(defaultSchema)$(prefixName)_node_disabled(), " + getCreateTimeExpression(symmetricDialect) + ");\n" +
"    end if;\n" +
"end loop;\n" +
"end;\n" +
"$function$ language plpgsql" + getSecurityClause() + ";" +
"create or replace function f$(triggerName)_drop() returns event_trigger as\n" +
"$function$\n" +
"declare cmd record;\n" +
"declare histId integer;\n" +
"declare rowData text;\n" +
"begin\n" +
"rowData = current_query();\n" +
"for cmd in select * from pg_event_trigger_dropped_objects() loop\n" +
"    if (upper(cmd.object_identity) not like upper('$(prefixName)%') and upper(cmd.object_identity) not like upper('%.$(prefixName)%') and" + 
"    upper(cmd.object_identity) not like upper('f$(prefixName)%') and upper(cmd.object_identity) not like upper('%.f$(prefixName)%') and cmd.original) then\n" +
"        select trigger_hist_id into histId from $(defaultSchema)$(prefixName)trigger_hist where upper(source_table_name) = upper('$(prefixName)_node') and inactive_time is null;\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"        values ('$(prefixName)_node', '" + DataEventType.SQL.getCode() + "', histId,\n" +
"        '\"delimiter " + delimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"        'config', $(defaultSchema)$(prefixName)_node_disabled(), " + getCreateTimeExpression(symmetricDialect) + ");\n" +
"    end if;\n" +
"end loop;\n" +
"end;\n" +
"$function$ language plpgsql" + getSecurityClause() + ";");
        
        sqlTemplates.put("postDdlTriggerTemplate", "create event trigger $(triggerName) on ddl_command_end execute procedure f$(triggerName)();" + 
"create event trigger $(triggerName)_drop on sql_drop execute procedure f$(triggerName)_drop();");
    }

    @Override
    protected boolean requiresWrappedBlobTemplateForBlobType() {
        return true;
    }
    
    protected final String getCreateTimeExpression(ISymmetricDialect symmetricDialect) {
        String timezone = symmetricDialect.getParameterService().getString(ParameterConstants.DATA_CREATE_TIME_TIMEZONE);
        if (StringUtils.isEmpty(timezone)) {
            return "CURRENT_TIMESTAMP";
        } else {
            return String.format("CURRENT_TIMESTAMP AT TIME ZONE '%s'", timezone);
        }    
    }
    
    protected final String getSecurityClause() {
        if (symmetricDialect.getParameterService().is(ParameterConstants.POSTGRES_SECURITY_DEFINER, false)) {
            return " security definer";
        }
        return "";
    }

}