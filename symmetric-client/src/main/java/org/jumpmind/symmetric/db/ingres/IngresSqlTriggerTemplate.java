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
package org.jumpmind.symmetric.db.ingres;

import java.util.HashMap;

import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class IngresSqlTriggerTemplate extends AbstractTriggerTemplate {
    // create table t2(id varchar(255) not null, description varchar(4000), constraint t1_pk primary key(id));
    //
    // declare global temporary table sym_temp_context (name varchar(255), context_value varchar(255)) on commit preserve rows with norecovery;
    //
    // select * from temporary_table;
    //
    // insert into temporary_table values('a','a');
    //
    // select 1;
    //
    // select concat('1','2');
    //
    // create procedure replace(sourcestring varchar, findstring varchar, replacestring varchar) AS
    // DECLARE
    // continuereplacing boolean;
    // finalstring varchar;
    // workingstring varchar;
    // BEGIN
    // continuereplacing = TRUE;
    // workingstring = sourcestring;
    // finalstring = '';
    // while(continuereplacing = TRUE) DO
    // if(locate(workingstring, findstring) < size(workingstring)+1) THEN
    // finalstring = concat(finalstring,substring(workingstring from 1 for locate(workingstring, findstring) + 1);
    // workingstring = substring(workingstring from locate(workingstring, findstring) + 1);
    // ELSE
    // finalstring = concat(finalstring, workingstring);
    // continuereplacing = FALSE;
    // ENDIF;
    // ENDWHILE;
    // return :finalstring;
    // END;
    //
    // select * from iiprocedure;
    //
    // select replace('hello world', 'l', 'll');
    //
    // select replace(varchar('"hello world"',1024), '"', '""');
    //
    // select case when 'null' is null then '' else 'null' end;
    //
    // select case when '"he\\o, world"' is null then '' else concat(concat('"', replace(replace(varchar('"he\\o, world"',1024),'\','\\'),'"','\"')),'"') end;
    //
    // SELECT TO_CHAR('2013-07-30 23:42:00.290533-07:00','YEAR-MONTH-DAY HH24:MI:SSXFF6 tzh:tzm');
    //
    // SELECT TO_CHAR(sysdate, 'yyyy-mm-dd hh:mi:ss.ff');
    //
    // SELECT TO_CHAR(local_timestamp, 'yyyy-mm-dd hh:mi:ss.ff tzh:tzm');
    //
    // select 'a' + 'b';
    //
    // select 'a' || 'b';
    //
    // select local_timestamp;
    //
    // SELECT REPLACE(varchar('The prior was in the next prior',1024),'prior','church');
    //
    // create procedure replace(sourcestring varchar(100), findstring varchar(100), replacestring varchar(100)) AS
    // declare counter integer;
    // BEGIN
    // select 1 into :counter;
    // END;
    //
    // create table t2_audit (id varchar(255), description varchar(4000), create_time timestamp, old_data varchar(16000), transaction_id varchar(255));
    //
    // create procedure t2_proc(IN id varchar(255), IN description varchar(4000), IN old_data varchar(16000)) AS
    // declare
    // var_transaction_id char(255);
    // BEGIN
    // select DBMSINFO('db_tran_id') into :var_transaction_id;
    // insert into t2_audit (id, description, old_data, transaction_id, create_time) values(:id, :description, :old_data, :var_transaction_id,
    // current_timestamp);
    // END;
    //
    // create trigger t2_trig after insert on t2 for each row execute procedure t2_proc(new.id, new.description,
    // old_data =
    // case when old.id is null then '' else '"' || replace(replace(varchar(old.id,255),'\','\\'),'"','\"') || '"' end || ',' ||
    // case when old.description is null then '' else '"' || replace(replace(varchar(old.description,255),'\','\\'),'"','\"') || '"' end
    // );
    //
    // insert into t2 values('2','2');
    //
    // insert into t2 values('3','3');
    //
    // select * from t2;
    //
    // select * from t2_audit;
    //
    // drop trigger t2_trig;
    //
    // drop procedure t2_proc;
    //
    // drop table t2_audit;
    //
    // delete from t2;
    //
    // create procedure t2_proc AS
    // BEGIN
    // insert into t2_audit (id, description, create_time) values(:new.id, :new.description, current_timestamp)
    // END;
    //
    // select DBMSINFO('db_tran_id'), DBMSINFO('transaction_state');
    protected IngresSqlTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''";
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(varchar($(tableAlias).\"$(columnName)\",$(columnSize)),'\\','\\\\'),'\"','\\\"') || '\"' end";
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || varchar($(tableAlias).\"$(columnName)\") || '\"' end";
        geometryColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || varchar($(tableAlias).\"$(columnName)\") || '\"' end";
        geographyColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || varchar($(tableAlias).\"$(columnName)\") || '\"' end";
        xmlColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || varchar($(tableAlias).\"$(columnName)\") || '\"' end";
        arrayColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || varchar($(tableAlias).\"$(columnName)\") || '\"' end";
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when $(tableAlias).\"$(columnName)\" is true then '\"1\"' else '\"0\"' end";
        dateColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || to_char($(tableAlias).\"$(columnName)\", 'yyyy-mm-dd hh:mi:ss') || '\"' end";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || to_char($(tableAlias).\"$(columnName)\", 'yyyy-mm-dd hh:mi:ss.ff') || '\"' end";
        timeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || to_char($(tableAlias).\"$(columnName)\") || '\"' end";
        dateTimeWithTimeZoneColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || to_char($(tableAlias).\"$(columnName)\", 'hh:mi:ss.ff tzh:tzm') || '\"' end";
        clobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(varchar($(tableAlias).\"$(columnName)\"),'\\','\\\\'),'\"','\\\"') || '\"' end";
        blobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || hex(varbinary($(tableAlias).\"$(columnName)\")) || '\"' end";
        triggerConcatCharacter = "||";
        newTriggerValue = "new";
        oldTriggerValue = "old";
        oldColumnPrefix = "";
        newColumnPrefix = "";
        otherColumnTemplate = stringColumnTemplate;
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate",
                "create or replace procedure $(schemaName)f$(triggerName)(" +
                        " IN columns varchar(16000), IN oldcolumns varchar(16000), IN oldkeys varchar(16000), IN newkeys varchar(16000), IN channelid varchar(16000), IN syncOnInsertCondition boolean "
                        +
                        ") AS \n" +
                        "DECLARE \n" +
                        "var_transaction_id varchar(255); \n" +
                        "var_sync_triggers_disabled varchar(255); \n" +
                        "var_source_node varchar(255); \n" +
                        "BEGIN \n" +
                        "select DBMSINFO('db_tran_id') into :var_transaction_id; \n" +
                        "select varchar(context_value) into :var_source_node from $(prefixName)_context where name = DBMSINFO('session_id') || ':sourcenode'; \n"
                        +
                        "select varchar(context_value) into :var_sync_triggers_disabled from $(prefixName)_context where name = DBMSINFO('session_id') || ':synctriggersdisabled'; \n"
                        +
                        "$(custom_before_insert_text) \n" +
                        "if :syncOnInsertCondition is true and $(syncOnIncomingBatchCondition) THEN \n" +
                        "            insert into $(defaultSchema)$(prefixName)_data \n" +
                        "            (table_name, event_type, trigger_hist_id, row_data, channel_id, \n" +
                        "             transaction_id, source_node_id, external_data, create_time) \n" +
                        "            values( \n" +
                        "             '$(targetTableName)', \n" +
                        "             'I', \n" +
                        "             $(triggerHistoryId), \n" +
                        "             :columns, \n" +
                        "             :channelid, \n" +
                        "             :var_transaction_id, \n" +
                        "             :var_source_node, \n" +
                        "             $(externalSelect), \n" +
                        "             current_timestamp \n" +
                        "             ); \n" +
                        "ENDIF; \n" +
                        "$(custom_on_insert_text)" +
                        "END \n");
        sqlTemplates.put("insertReloadTriggerTemplate",
                "create or replace procedure $(schemaName)f$(triggerName)(" +
                        " IN columns varchar(16000), IN oldcolumns varchar(16000), IN oldkeys varchar(16000), IN newkeys varchar(16000), IN channelid varchar(16000), IN syncOnInsertCondition boolean "
                        +
                        ") AS \n" +
                        "DECLARE \n" +
                        "var_transaction_id varchar(255); \n" +
                        "var_sync_triggers_disabled varchar(255); \n" +
                        "var_source_node varchar(255); \n" +
                        "BEGIN \n" +
                        "select DBMSINFO('db_tran_id') into :var_transaction_id; \n" +
                        "select varchar(context_value) into :var_source_node from $(prefixName)_context where name = DBMSINFO('session_id') || ':sourcenode'; \n"
                        +
                        "select varchar(context_value) into :var_sync_triggers_disabled from $(prefixName)_context where name = DBMSINFO('session_id') || ':synctriggersdisabled'; \n"
                        +
                        "$(custom_before_insert_text) \n" +
                        "if :syncOnInsertCondition is true and $(syncOnIncomingBatchCondition) THEN \n" +
                        "            insert into $(defaultSchema)$(prefixName)_data \n" +
                        "            (table_name, event_type, trigger_hist_id, pk_data, channel_id, \n" +
                        "             transaction_id, source_node_id, external_data, create_time) \n" +
                        "            values( \n" +
                        "             '$(targetTableName)', \n" +
                        "             'R', \n" +
                        "             $(triggerHistoryId), \n" +
                        "             :newkeys, \n" +
                        "             :channelid, \n" +
                        "             :var_transaction_id, \n" +
                        "             :var_source_node, \n" +
                        "             $(externalSelect), \n" +
                        "             current_timestamp \n" +
                        "             ); \n" +
                        "ENDIF; \n" +
                        "$(custom_on_insert_text)" +
                        "END \n");
        sqlTemplates.put("insertPostTriggerTemplate",
                "create trigger $(schemaName)$(triggerName) after insert of $(schemaName)$(tableName) for each row \n" +
                        "execute procedure $(schemaName)f$(triggerName)( \n" +
                        "columns = $(columns) , \n" +
                        "oldcolumns = NULL, \n" +
                        "oldkeys = NULL, \n" +
                        "newkeys = $(newKeys) , \n" +
                        "channelid = $(channelExpression), \n" +
                        "syncOnInsertCondition = case when $(syncOnInsertCondition) then true else false end \n" +
                        ")");
        sqlTemplates.put("updateTriggerTemplate",
                "create or replace procedure $(schemaName)f$(triggerName)(" +
                        " IN columns varchar(16000), IN oldcolumns varchar(16000), IN oldkeys varchar(16000), IN newkeys varchar(16000), IN channelid varchar(16000), IN syncOnUpdateCondition boolean "
                        +
                        ") AS \n" +
                        "DECLARE \n" +
                        "var_transaction_id varchar(255); \n" +
                        "var_sync_triggers_disabled varchar(255); \n" +
                        "var_source_node varchar(255); \n" +
                        "BEGIN \n" +
                        "select DBMSINFO('db_tran_id') into :var_transaction_id; \n" +
                        "select varchar(context_value) into :var_source_node from $(prefixName)_context where name = DBMSINFO('session_id') || ':sourcenode'; \n"
                        +
                        "select varchar(context_value) into :var_sync_triggers_disabled from $(prefixName)_context where name = DBMSINFO('session_id') || ':synctriggersdisabled'; \n"
                        +
                        "$(custom_before_update_text) \n" +
                        "if :syncOnUpdateCondition is true and $(syncOnIncomingBatchCondition) THEN \n" +
                        "          if $(dataHasChangedCondition) THEN \n" +
                        "            insert into $(defaultSchema)$(prefixName)_data \n" +
                        "            (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, \n" +
                        "             transaction_id, source_node_id, external_data, create_time) \n" +
                        "            values( \n" +
                        "             '$(targetTableName)', \n" +
                        "             'U', \n" +
                        "             $(triggerHistoryId), \n" +
                        "             :oldkeys, \n" +
                        "             :columns, \n" +
                        "             :oldcolumns, \n" +
                        "             :channelid, \n" +
                        "             :var_transaction_id, \n" +
                        "             :var_source_node, \n" +
                        "             $(externalSelect), \n" +
                        "             current_timestamp \n" +
                        "             ); \n" +
                        "           ENDIF; \n" +
                        "ENDIF; \n" +
                        "$(custom_on_update_text)" +
                        "END \n");
        sqlTemplates.put("updateReloadTriggerTemplate",
                "create or replace procedure $(schemaName)f$(triggerName)(" +
                        " IN columns varchar(16000), IN oldcolumns varchar(16000), IN oldkeys varchar(16000), IN newkeys varchar(16000), IN channelid varchar(16000), IN syncOnUpdateCondition boolean "
                        +
                        ") AS \n" +
                        "DECLARE \n" +
                        "var_transaction_id varchar(255); \n" +
                        "var_sync_triggers_disabled varchar(255); \n" +
                        "var_source_node varchar(255); \n" +
                        "BEGIN \n" +
                        "select DBMSINFO('db_tran_id') into :var_transaction_id; \n" +
                        "select varchar(context_value) into :var_source_node from $(prefixName)_context where name = DBMSINFO('session_id') || ':sourcenode'; \n"
                        +
                        "select varchar(context_value) into :var_sync_triggers_disabled from $(prefixName)_context where name = DBMSINFO('session_id') || ':synctriggersdisabled'; \n"
                        +
                        "$(custom_before_update_text) \n" +
                        "if :syncOnUpdateCondition is true and $(syncOnIncomingBatchCondition) THEN \n" +
                        "          if $(dataHasChangedCondition) THEN \n" +
                        "            insert into $(defaultSchema)$(prefixName)_data \n" +
                        "            (table_name, event_type, trigger_hist_id, pk_data, channel_id, \n" +
                        "             transaction_id, source_node_id, external_data, create_time) \n" +
                        "            values( \n" +
                        "             '$(targetTableName)', \n" +
                        "             'R', \n" +
                        "             $(triggerHistoryId), \n" +
                        "             :oldkeys, \n" +
                        "             :channelid, \n" +
                        "             :var_transaction_id, \n" +
                        "             :var_source_node, \n" +
                        "             $(externalSelect), \n" +
                        "             current_timestamp \n" +
                        "             ); \n" +
                        "           ENDIF; \n" +
                        "ENDIF; \n" +
                        "$(custom_on_update_text)" +
                        "END \n");
        sqlTemplates.put("updatePostTriggerTemplate",
                "create trigger $(schemaName)$(triggerName) after update of $(schemaName)$(tableName) for each row \n" +
                        "execute procedure $(schemaName)f$(triggerName)( \n" +
                        "columns = $(columns) , \n" +
                        "oldcolumns = $(oldColumns), \n" +
                        "oldkeys = $(oldKeys), \n" +
                        "newkeys = $(newKeys), \n" +
                        "channelid = $(channelExpression), " +
                        "syncOnUpdateCondition = case when $(syncOnUpdateCondition) then true else false end \n" +
                        ")");
        sqlTemplates.put("deleteTriggerTemplate",
                "create or replace procedure $(schemaName)f$(triggerName)(" +
                        " IN columns varchar(16000), IN oldcolumns varchar(16000), IN oldkeys varchar(16000), IN newkeys varchar(16000), IN channelid varchar(16000), IN syncOnDeleteCondition boolean "
                        +
                        ") AS \n" +
                        "DECLARE \n" +
                        "var_transaction_id varchar(255); \n" +
                        "var_sync_triggers_disabled varchar(255); \n" +
                        "var_source_node varchar(255); \n" +
                        "BEGIN \n" +
                        "select DBMSINFO('db_tran_id') into :var_transaction_id; \n" +
                        "select varchar(context_value) into :var_source_node from $(prefixName)_context where name = DBMSINFO('session_id') || ':sourcenode'; \n"
                        +
                        "select varchar(context_value) into :var_sync_triggers_disabled from $(prefixName)_context where name = DBMSINFO('session_id') || ':synctriggersdisabled'; \n"
                        +
                        "$(custom_before_delete_text) \n" +
                        "if :syncOnDeleteCondition is true and $(syncOnIncomingBatchCondition) THEN \n" +
                        "          if $(dataHasChangedCondition) THEN \n" +
                        "            insert into $(defaultSchema)$(prefixName)_data \n" +
                        "            (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, \n" +
                        "             transaction_id, source_node_id, external_data, create_time) \n" +
                        "            values( \n" +
                        "             '$(targetTableName)', \n" +
                        "             'D', \n" +
                        "             $(triggerHistoryId), \n" +
                        "             :oldkeys, \n" +
                        "             :oldcolumns, \n" +
                        "             :channelid, \n" +
                        "             :var_transaction_id, \n" +
                        "             :var_source_node, \n" +
                        "             $(externalSelect), \n" +
                        "             current_timestamp \n" +
                        "             ); \n" +
                        "           ENDIF; \n" +
                        "ENDIF; \n" +
                        "$(custom_on_delete_text)" +
                        "END \n");
        sqlTemplates.put("deletePostTriggerTemplate",
                "create trigger $(schemaName)$(triggerName) after delete of $(schemaName)$(tableName) for each row \n" +
                        "execute procedure $(schemaName)f$(triggerName)( \n" +
                        "columns = NULL , \n" +
                        "oldcolumns = $(oldColumns), \n" +
                        "oldkeys = $(oldKeys), \n" +
                        "newkeys = NULL, \n" +
                        "channelid = $(channelExpression), " +
                        "syncOnDeleteCondition = case when $(syncOnDeleteCondition) then true else false end \n" +
                        ")");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select $(columns) from $(schemaName)$(tableName) t where $(whereClause)");
    }
}
