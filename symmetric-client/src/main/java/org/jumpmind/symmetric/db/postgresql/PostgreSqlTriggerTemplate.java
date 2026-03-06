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
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.util.FormatUtils;

public class PostgreSqlTriggerTemplate extends AbstractTriggerTemplate {
    protected String sqlBatchDelimiter;
    protected String infinityDateExpression;
    protected String createTriggerCommandBeginning = "create trigger ";
    protected String sharedTruncateEventFunctionName;
    protected String currentTimestampAndZoneExpression = "CURRENT_TIMESTAMP";
    protected String invokerSecurityClause = " ";

    public PostgreSqlTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        IParameterService parameterService = symmetricDialect.getParameterService();
        PostgreSqlSymmetricDialect pgDialect = (PostgreSqlSymmetricDialect) this.symmetricDialect;
        createTriggerCommandBeginning = getCreateTriggerCommandBeginning(pgDialect);
        currentTimestampAndZoneExpression = getCurrentTimestampAndZoneExpression(pgDialect);
        invokerSecurityClause = getSecurityClause(pgDialect);
        sqlBatchDelimiter = parameterService.getString(ParameterConstants.TRIGGER_CAPTURE_DDL_DELIMITER, "$");
        infinityDateExpression = parameterService.is(ParameterConstants.POSTGRES_CONVERT_INFINITY_DATE_TO_NULL, true) ? "''"
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
        sharedTruncateEventFunctionName = "f"+this.symmetricDialect.getTablePrefix()+"_on_truncate_table_configured_for_replication";
        otherColumnTemplate = stringColumnTemplate;
        sqlTemplates = new HashMap<String,String>();
        sqlTemplates.put(sharedTruncateEventFunctionName,"CREATE OR REPLACE FUNCTION $(defaultSchema)"+sharedTruncateEventFunctionName+"() RETURNS TRIGGER AS $$ "
                + "\nDECLARE "
                + "\n    eventDdl VARCHAR(3000); "
                + "\n    argumentNo int:=0; "
                + "\n    triggersDisabled int:=0; "                
                + "\n    tableSchema varchar(255); "
                + "\n    tableName varchar(255); "
                + "\n    channelId varchar(128); "
                + "\n    triggerId varchar(128); "
                + "\n    histId int:=0; "
                + "\nBEGIN  "
                + "\n    tableSchema := TG_TABLE_SCHEMA; "
                + "\n    tableName := TG_TABLE_NAME;  "
                + "\n    if (tableSchema IS NOT NULL and LENGTH(tableSchema)>0) then "
                + "\n        eventDdl := 'TRUNCATE TABLE ' || tableSchema || '.'|| tableName; "
                + "\n    else "
                + "\n        eventDdl := 'TRUNCATE TABLE ' || tableName; "
                + "\n    end if; "
                + "\n    if(TG_NARGS > 1) THEN "
                + "\n        for argumentNo in 1..TG_NARGS-1 LOOP  "
                + "\n            eventDdl := eventDdl || ' ' ||  TG_ARGV[argumentNo ]; "
                + "\n            argumentNo := argumentNo+1; "
                + "\n        END LOOP; "
                + "\n    end if; "
                + "\n    if (tableName like '%.%') then "
                + "\n           tableSchema := split_part(tableName, '.', 1); "
                + "\n           tableName := split_part(tableName, '.', 2); "
                + "\n    end if; "
                + "\n    tableSchema := trim(both '\\\"' from tableSchema); "
                + "\n    tableName := trim(both '\\\"' from tableName); "
                + "\n    if( $(replicationEnabledCondition) ) then "
                + "\n      select tr.trigger_id, tr.channel_id, max(th.trigger_hist_id) "
                + "\n      into triggerId, channelId, histId "
                + "\n      from $(defaultSchema)$(prefixName)_trigger tr  "
                + "\n      join $(defaultSchema)$(prefixName)_trigger_hist th "
                + "\n        on th.trigger_id = tr.trigger_id and th.inactive_time is null "
                + "\n      where tr.source_schema_name = lower(tableSchema) and tr.source_table_name = lower(tableName) "
                + "\n      group by tr.trigger_id, tr.channel_id "
                + "\n      limit 1; "
                + "\n      if (channelId is null) then  "
                + "\n        RAISE EXCEPTION 'Unable to capture truncate event for table, because SymmetricDS configuration is incomplete (channel is NULL)! Schema=%, Table=%, Command=%', tableSchema, tableName, eventDdl "
                + "\n            USING MESSAGE = 'Run SyncTriggers job in SymmetricDS (Disabling this trigger will cause data to be out-of-sync!)'; "
                + "\n        RETURN NEW;  "
                + "\n      end if; "
                + "\n      INSERT INTO $(defaultSchema)$(prefixName)_data(table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, transaction_id, create_time) "
                + "\n      VALUES (tableName, 'S', histId, "
                + "\n         '\"delimiter " + sqlBatchDelimiter + ";' || chr(13) || chr(10) || replace(replace(eventDdl,'\\','\\\\'),'\"','\\\"') || chr(13) || chr(10) || '\",ddl',"
                + "\n         channelId, 'source',"
                + "\n         TO_CHAR(CURRENT_TIMESTAMP, 'truncate-YYYY-MM-DD')||'T'||TO_CHAR(CURRENT_TIMESTAMP, 'HH24:MI:MSOF'), " + currentTimestampAndZoneExpression + "); "
                + "\n      RAISE NOTICE 'Captured truncate event for table; Schema=%, Table=%, Channel=%, Command=%', tableSchema, tableName, channelId, eventDdl;"
                + "\n    else "
                + "\n      RAISE NOTICE 'Skipped truncate event for table, because replication is disabled; Schema=%, Table=%, Command=%', tableSchema, tableName, eventDdl;"
                + "\n    end if; "
                + "\n    RETURN NEW;  "
                + "\nEND $$ LANGUAGE plpgsql" + invokerSecurityClause + ";");
        
        sqlTemplates.put("post"+sharedTruncateEventFunctionName+"Template",
                createTriggerCommandBeginning + "$(triggerName) after truncate on $(sourceTableName)"
                + " for each STATEMENT execute function $(defaultSchema)$(sharedFunctionName)(); ");         
        
        sqlTemplates.put(INSERT_TRIGGER_TEMPLATE,
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
"                                      " + currentTimestampAndZoneExpression + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_insert_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + invokerSecurityClause + ";");

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
"                                      " + currentTimestampAndZoneExpression + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_insert_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + invokerSecurityClause + ";");

        
        sqlTemplates.put("insertPostTriggerTemplate" ,
createTriggerCommandBeginning + "$(triggerName) after insert on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row execute procedure $(schemaName)f$(triggerName)();                                                                                                         " );

        sqlTemplates.put(UPDATE_TRIGGER_TEMPLATE ,
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
"                                      " + currentTimestampAndZoneExpression + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_update_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + invokerSecurityClause + ";");

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
"                                      " + currentTimestampAndZoneExpression + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_update_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + invokerSecurityClause + ";");

        sqlTemplates.put("updatePostTriggerTemplate" ,
createTriggerCommandBeginning + "$(triggerName) after update on $(schemaName)$(tableName)                                                                                                                                \n" +
"                                for each row execute procedure $(schemaName)f$(triggerName)();                                                                                                         " );

        sqlTemplates.put(DELETE_TRIGGER_TEMPLATE,
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
"                                      " + currentTimestampAndZoneExpression + "                                                                                                                \n" +
"                                    );                                                                                                                                                                 \n" +
"                                  end if;                                                                                                                                                              \n" +
"                                  $(custom_on_delete_text)                                                                                                                                             \n" +
"                                  return null;                                                                                                                                                         \n" +
"                                end;                                                                                                                                                                   \n" +
"                                $function$ language plpgsql" + invokerSecurityClause + ";");

        sqlTemplates.put("deletePostTriggerTemplate" ,
createTriggerCommandBeginning + "$(triggerName) after delete on $(schemaName)$(tableName)                                                                                                                                \n" +
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
"        select trigger_hist_id, source_table_name into histId, tableName from $(defaultSchema)$(prefixName)_trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"        if (histId is not null) then\n" +
"            select channel_id into channelId from $(defaultSchema)$(prefixName)_trigger where upper(source_table_name) = upper(tableName);\n" +
"            if (channelId is null) then\n" +
"                channelId := 'config';\n" +
"            end if;\n" +
"            insert into $(defaultSchema)$(prefixName)_data\n" +
"            (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"            values (tableName, '" + DataEventType.SQL.getCode() + "', histId,\n" +
"            '\"delimiter " + sqlBatchDelimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"            channelId, $(defaultSchema)$(prefixName)_node_disabled(), " + currentTimestampAndZoneExpression + ");\n" +
"        end if;\n" +
"    end if;\n" +
"end loop;\n" +
"end;\n" +
"$function$ language plpgsql" + invokerSecurityClause + ";" +
"create or replace function f$(triggerName)_drop() returns event_trigger as\n" +
"$function$\n" +
"declare cmd record;\n" +
"declare histId integer;\n" +
"declare rowData text;\n" +
"begin\n" +
"rowData = current_query();\n" +
"for cmd in select * from pg_event_trigger_dropped_objects() loop\n" +
"    if (upper(cmd.object_identity) not like upper('$(prefixName)%') and upper(cmd.object_identity) not like upper('%.$(prefixName)%') and" + 
"    upper(cmd.object_identity) not like upper('f$(prefixName)%') and upper(cmd.object_identity) not like upper('%.f$(prefixName)%') and cmd.original and upper(rowData) not like 'ALTER%') then\n" +
"        select trigger_hist_id into histId from $(defaultSchema)$(prefixName)_trigger_hist where upper(source_table_name) = upper('$(prefixName)_node') and inactive_time is null;\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"        values ('$(prefixName)_node', '" + DataEventType.SQL.getCode() + "', histId,\n" +
"        '\"delimiter " + sqlBatchDelimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"        'config', $(defaultSchema)$(prefixName)_node_disabled(), " + currentTimestampAndZoneExpression + ");\n" +
"    end if;\n" +
"end loop;\n" +
"end;\n" +
"$function$ language plpgsql" + invokerSecurityClause + ";");

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
"            select trigger_hist_id, source_table_name into histId, tableName from $(defaultSchema)$(prefixName)_trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"        end if;\n" +
"        if (histId is null) then\n" +
"            tableName := '$(prefixName)_node';\n" +
"            select trigger_hist_id into histId from $(defaultSchema)$(prefixName)_trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"        end if;\n" +
"        select channel_id into channelId from $(defaultSchema)$(prefixName)_trigger where upper(source_table_name) = upper(tableName);\n" +
"        if (channelId is null) then\n" +
"            channelId := 'config';\n" +
"        end if;\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"        values (tableName, '" + DataEventType.SQL.getCode() + "', histId,\n" +
"        '\"delimiter " + sqlBatchDelimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"        channelId, $(defaultSchema)$(prefixName)_node_disabled(), " + currentTimestampAndZoneExpression + ");\n" +
"    end if;\n" +
"end loop;\n" +
"end;\n" +
"$function$ language plpgsql" + invokerSecurityClause + ";" +
"create or replace function f$(triggerName)_drop() returns event_trigger as\n" +
"$function$\n" +
"declare cmd record;\n" +
"declare histId integer;\n" +
"declare rowData text;\n" +
"begin\n" +
"rowData = current_query();\n" +
"for cmd in select * from pg_event_trigger_dropped_objects() loop\n" +
"    if (upper(cmd.object_identity) not like upper('$(prefixName)%') and upper(cmd.object_identity) not like upper('%.$(prefixName)%') and" + 
"    upper(cmd.object_identity) not like upper('f$(prefixName)%') and upper(cmd.object_identity) not like upper('%.f$(prefixName)%') and cmd.original and upper(rowData) not like 'ALTER%') then\n" +
"        select trigger_hist_id into histId from $(defaultSchema)$(prefixName)_trigger_hist where upper(source_table_name) = upper('$(prefixName)_node') and inactive_time is null;\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"        values ('$(prefixName)_node', '" + DataEventType.SQL.getCode() + "', histId,\n" +
"        '\"delimiter " + sqlBatchDelimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"        'config', $(defaultSchema)$(prefixName)_node_disabled(), " + currentTimestampAndZoneExpression + ");\n" +
"    end if;\n" +
"end loop;\n" +
"end;\n" +
"$function$ language plpgsql" + invokerSecurityClause + ";");
        
        sqlTemplates.put("postDdlTriggerTemplate", "create event trigger $(triggerName) on ddl_command_end execute procedure f$(triggerName)();" + 
"create event trigger $(triggerName)_drop on sql_drop execute procedure f$(triggerName)_drop();");
    }

    @Override
    protected boolean requiresWrappedBlobTemplateForBlobType() {
        return true;
    }
    
    protected final String getCurrentTimestampAndZoneExpression(PostgreSqlSymmetricDialect pgDialect ) {
        String timezone = pgDialect.getParameterService().getString(ParameterConstants.DATA_CREATE_TIME_TIMEZONE);
        if (StringUtils.isEmpty(timezone)) {
            return "CURRENT_TIMESTAMP";
        } else {
            return String.format("CURRENT_TIMESTAMP AT TIME ZONE '%s'", timezone);
        }    
    }
    
    protected final String getCreateTriggerCommandBeginning(PostgreSqlSymmetricDialect pgDialect ) {
        if (pgDialect.supportsReplaceTriggers() && pgDialect.getParameterService().is(ParameterConstants.ALLOW_TRIGGER_CREATE_OR_REPLACE, true)) {
            return "create or replace trigger ";
        }        
        return "create trigger ";
    }
    
    protected final String getSecurityClause(PostgreSqlSymmetricDialect pgDialect ) {
        if (pgDialect.getParameterService().is(ParameterConstants.POSTGRES_SECURITY_DEFINER, false)) {
            return " security definer";
        }
        return "";
    }

    @Override
    public String createDdlTrigger(String tablePrefix, String defaultCatalog, String defaultSchema, String triggerName) {
        String ddl = createSharedTruncateCaptureFunction(  tablePrefix,   defaultCatalog,   defaultSchema);
        if (ddl == null) {
            ddl = "";
        }
        return ddl + super.createDdlTrigger(  tablePrefix,   defaultCatalog,   defaultSchema,   triggerName);
    }
    
    @Override
    public String createPostTriggerDDL(DataEventType dml, Trigger trigger, TriggerHistory history,
            Channel channel, String tablePrefix, Table originalTable, String defaultCatalog,
            String defaultSchema) {
        String ddl = "";
        String tableSchema = originalTable.getSchema();       
        boolean internalTable = originalTable.getName().startsWith(tablePrefix) 
                    && ( StringUtils.isBlank(defaultSchema) == StringUtils.isBlank(tableSchema) )
                    && defaultSchema.contentEquals( tableSchema);
        PostgreSqlSymmetricDialect pgDialect = (PostgreSqlSymmetricDialect)this.symmetricDialect;        
        boolean includeTruncateTrigger = pgDialect.supportsReplaceTriggers() 
                                         && pgDialect.getParameterService().is(ParameterConstants.POSTGRES_TRIGGER_CAPTURE_TRUNCATE)
                                         && ( (!trigger.isSyncOnDelete() && dml == DataEventType.INSERT)  
                                            || (trigger.isSyncOnDelete() && dml == DataEventType.DELETE)); 
        if (includeTruncateTrigger && !internalTable) {
            ddl = createPostTriggerDDLForTruncate(  trigger, history, channel,   tablePrefix, originalTable, defaultCatalog, defaultSchema);
            if (ddl == null) {
                ddl = "";                
            }
        }
        return ddl + super.createPostTriggerDDL(dml, trigger, history, channel, tablePrefix, originalTable, defaultCatalog, defaultSchema);
    }

    public String createPostTriggerDDLForTruncate( Trigger trigger, TriggerHistory history, Channel channel, String tablePrefix, Table originalTable, String defaultCatalog,
            String defaultSchema) {
        String ddl = ""; 
        String truncateTriggerName = generateTruncateTriggerName(trigger, history);
        if(truncateTriggerName == null || truncateTriggerName.length() < 1) {
            return ddl;
        }                                                                                                   

        String truncateTriggerTemplate = "post"+sharedTruncateEventFunctionName+"Template";
        ddl = sqlTemplates.get(truncateTriggerTemplate);
        if (StringUtils.isBlank(ddl)) {
            log.warn("Missing definition of a trigger statement {} for truncate events.", truncateTriggerTemplate);
            return "";
        }
        ddl = FormatUtils.replace("triggerName", truncateTriggerName, ddl);
        ddl = FormatUtils.replace("sourceTableName", originalTable.getFullyQualifiedTableName(), ddl);
        ddl = FormatUtils.replace("sharedFunctionName", sharedTruncateEventFunctionName, ddl);
        ddl = replaceTemplateVariables(DataEventType.DELETE, trigger, history, channel, tablePrefix, originalTable, originalTable, defaultCatalog, defaultSchema, ddl);
        
        if (log.isDebugEnabled()) {
            log.debug("Injected trigger for truncate events on table={}, DDL={}", originalTable.getName(), ddl);
        }else{
            log.info("Injected trigger for truncate events on table={}.", originalTable.getName());
        }
        return ddl ;
    }
 
    protected String generateTruncateTriggerName(Trigger trigger, TriggerHistory history) {
        String truncateTriggerName = null;
        if (trigger.isSyncOnInsert()) {
            truncateTriggerName = history.getNameForInsertTrigger();
            if (!StringUtils.isBlank(truncateTriggerName)) {
                truncateTriggerName = truncateTriggerName.replace("_ON_I_FOR_", "_ON_T_FOR_");
            }
        }
        if (StringUtils.isBlank(truncateTriggerName) && trigger.isSyncOnUpdate()) {
            truncateTriggerName = history.getNameForUpdateTrigger();
            if (!StringUtils.isBlank(truncateTriggerName)) {
                truncateTriggerName = truncateTriggerName.replace("_ON_U_FOR_", "_ON_T_FOR_");
            }
        }
        if (StringUtils.isBlank(truncateTriggerName) && trigger.isSyncOnDelete()) {
            truncateTriggerName = history.getNameForDeleteTrigger();
            if (!StringUtils.isBlank(truncateTriggerName)) {
                truncateTriggerName = truncateTriggerName.replace("_ON_D_FOR_", "_ON_T_FOR_");
            }
        }
        return truncateTriggerName;
    }

    public String createSharedTruncateCaptureFunction(String tablePrefix, String defaultCatalog, String defaultSchema) {
        PostgreSqlSymmetricDialect pgDialect = (PostgreSqlSymmetricDialect)this.symmetricDialect;
        if (!(pgDialect.getParameterService().is(ParameterConstants.POSTGRES_TRIGGER_CAPTURE_TRUNCATE) || !pgDialect.supportsReplaceTriggers())){
            return "";
        }
        if (pgDialect.isFunctionInstalled(sharedTruncateEventFunctionName)) {
            log.debug("Detected shared function {} for capturing table truncate events. Name={}", sharedTruncateEventFunctionName);
            return "";
        }  
        String ddl = sqlTemplates.get(sharedTruncateEventFunctionName);
        if (StringUtils.isBlank(ddl)) {
            log.warn("Missing definition of the shared function {} for capturing table truncate events.", sharedTruncateEventFunctionName);
            return "";
        }
        ddl = FormatUtils.replace("prefixName", tablePrefix, ddl);
        ddl = FormatUtils.replace("replicationEnabledCondition", pgDialect.getSyncTriggersExpression(), ddl);
        ddl = replaceDefaultSchemaAndCatalog(ddl, defaultCatalog, defaultSchema);
        if(log.isDebugEnabled()) {
            log.debug("Injected shared function {} for capturing table truncate events. DDL={}", sharedTruncateEventFunctionName, ddl);
        }else{
            log.info("Injected shared function {} for capturing table truncate events.", sharedTruncateEventFunctionName);
        }
        return ddl;
    }
    
    public String getTruncateSharedFunctionName() {
        return sharedTruncateEventFunctionName;
    }
    
    public String getEntry(String key) {
        return sqlTemplates.get(key);
    }
}
