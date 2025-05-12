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
package org.jumpmind.symmetric.db.oracle;

import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;

public class OracleTriggerTemplate extends AbstractTriggerTemplate {
    String delimiter;

    public OracleTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        delimiter = symmetricDialect.getParameterService().getString(ParameterConstants.TRIGGER_CAPTURE_DDL_DELIMITER, "$");
        // @formatter:off

        emptyColumnTemplate = "''" ;
        stringColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", '\"'||replace(replace($(oracleToClob)$(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"')||'\"', '')";
        geometryColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", '\"'||replace(replace(SDO_UTIL.TO_WKTGEOMETRY($(tableAlias).\"$(columnName)\"),'\\','\\\\'),'\"','\\\"')||'\"', '')";
        numberColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", $(oracleToClob)'\"'||" + getNumberConversionString() + "||'\"', '')";
        datetimeColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", $(oracleToClob)concat(concat('\"',to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS.FF9')),'\"'), '')";
        dateTimeWithTimeZoneColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", $(oracleToClob)concat(concat('\"',to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM')),'\"'), '')";
        dateTimeWithLocalTimeZoneColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", $(oracleToClob)concat(concat('\"',to_char(cast($(tableAlias).\"$(columnName)\" as timestamp), 'YYYY-MM-DD HH24:MI:SS.FF9')),'\"'), '')";
        timeColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", $(oracleToClob)concat(concat('\"',to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS','NLS_CALENDAR=''GREGORIAN''')),'\"'), '')";
        dateColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", $(oracleToClob)concat(concat('\"',to_char($(tableAlias).\"$(columnName)\", 'YYYY-MM-DD HH24:MI:SS','NLS_CALENDAR=''GREGORIAN''')),'\"'), '')";
        clobColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", '\"'||replace(replace($(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"')||'\"', '')";
        blobColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", '\"'||$(prefixName)_blob2clob($(tableAlias).\"$(columnName)\")||'\"', '')";
        longColumnTemplate = "$(oracleToClob)'\"\\b\"'";
        booleanColumnTemplate = "nvl2($(tableAlias).\"$(columnName)\", '\"'||cast($(tableAlias).\"$(columnName)\" as number("+symmetricDialect.getTemplateNumberPrecisionSpec()+"))||'\"', '')";
        xmlColumnTemplate = "nvl2(extract($(tableAlias).\"$(columnName)\", '/').getclobval(), '\"'||replace(replace(extract($(tableAlias).\"$(columnName)\", '/').getclobval(),'\\','\\\\'),'\"','\\\"')||'\"', '')";
        binaryColumnTemplate = blobColumnTemplate;
        triggerConcatCharacter = "||" ;
        newTriggerValue = ":new" ;
        oldTriggerValue = ":old" ;
        oldColumnPrefix = "" ;
        newColumnPrefix = "" ;

        sqlTemplates = new HashMap<String,String>();
        sqlTemplates.put("insertTriggerTemplate",
"create or replace trigger $(triggerName)\n" +
"after insert on $(schemaName)$(tableName)\n" +
"for each row\n" +
"begin\n" +
"    $(custom_before_insert_text)\n" +
"    if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then\n" +
"        begin\n" +
"            insert into $(defaultSchema)$(prefixName)_data\n" +
"            (table_name, event_type, trigger_hist_id, row_data, channel_id,\n" +
"            transaction_id, source_node_id, external_data, create_time)\n" +
"            values(\n" +
"            '$(targetTableName)',\n" +
"            'I',\n" +
"            $(triggerHistoryId),\n" +
"            $(oracleToClob)$(columns),\n" +
"            $(channelExpression),\n" +
"            $(txIdExpression),\n" +
"            $(prefixName)_pkg.disable_node_id,\n" +
"            $(externalSelect),\n" +
"            " + getCreateTimeExpression(symmetricDialect) + "\n" +
"            );\n" +
"        exception\n" +
"        when others then\n" +
"            insert into $(defaultSchema)$(prefixName)_data\n" +
"            (table_name, event_type, trigger_hist_id, row_data, channel_id,\n" +
"            transaction_id, source_node_id, external_data, create_time)\n" +
"            values(\n" +
"            '$(targetTableName)',\n" +
"            'I',\n" +
"            $(triggerHistoryId),\n" +
"            $(oracleToClobAlways)$(columnsClobAlways),\n" +
"            $(channelExpression),\n" +
"            $(txIdExpression),\n" +
"            $(prefixName)_pkg.disable_node_id,\n" +
"            $(externalSelect),\n" +
"            " + getCreateTimeExpression(symmetricDialect) + "\n" +
"            );\n" +
"        end;\n" +
"    end if;\n" +
"    $(custom_on_insert_text)\n" +
"end;\n");

        sqlTemplates.put("insertReloadTriggerTemplate",
"create or replace trigger $(triggerName)\n" +
"after insert on $(schemaName)$(tableName)\n" +
"for each row\n" +
"begin\n" +
"    $(custom_before_insert_text)\n" +
"    if $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, pk_data, channel_id,\n" +
"        transaction_id, source_node_id, external_data, create_time)\n" +
"        values(\n" +
"        '$(targetTableName)',\n" +
"        'R',\n" +
"        $(triggerHistoryId),\n" +
"        $(newKeys),\n" +
"        $(channelExpression),\n" +
"        $(txIdExpression),\n" +
"        $(prefixName)_pkg.disable_node_id,\n" +
"        $(externalSelect),\n" +
"        " + getCreateTimeExpression(symmetricDialect) + "\n" +
"        );\n" +
"    end if;\n" +
"    $(custom_on_insert_text)\n" +
"end;\n");

        sqlTemplates.put("updateTriggerTemplate",
"create or replace trigger $(triggerName) after update on $(schemaName)$(tableName)\n" +
"for each row\n" +
"begin\n" +
"    $(custom_before_update_text)\n" +
"    if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then\n" +
"        declare\n" +
"            var_row_data $(oracleLobType);\n" +
"            var_old_data $(oracleLobType);\n" +
"        begin\n" +
"            select $(oracleToClob)$(columns) into var_row_data from dual;\n" +
"            select $(oracleToClob)$(oldColumns) into var_old_data from dual;\n" +
"            if $(dataHasChangedCondition) then\n" +
"                insert into $(defaultSchema)$(prefixName)_data\n" +
"                (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"                values(\n" +
"                '$(targetTableName)',\n" +
"                'U',\n" +
"                $(triggerHistoryId),\n" +
"                $(oldKeys),\n" +
"                var_row_data,\n" +
"                var_old_data,\n" +
"                $(channelExpression),\n" +
"                $(txIdExpression),\n" +
"                $(prefixName)_pkg.disable_node_id,\n" +
"                $(externalSelect),\n" +
"                " + getCreateTimeExpression(symmetricDialect) + "\n" +
"                );\n" +
"            end if;\n" +
"        exception\n" +
"        when others then\n" +
"            declare\n" +
"                var_row_data $(oracleLobTypeClobAlways);\n" +
"                var_old_data $(oracleLobTypeClobAlways);\n" +
"            begin\n" +
"                select $(oracleToClobAlways)$(columnsClobAlways) into var_row_data from dual;\n" +
"                select $(oracleToClobAlways)$(oldColumnsClobAlways) into var_old_data from dual;\n" +
"                if $(dataHasChangedConditionClobAlways) then\n" +
"                    insert into $(defaultSchema)$(prefixName)_data\n" +
"                    (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"                    values(\n" +
"                    '$(targetTableName)',\n" +
"                    'U',\n" +
"                    $(triggerHistoryId),\n" +
"                    $(oldKeys),\n" +
"                    var_row_data,\n" +
"                    var_old_data,\n" +
"                    $(channelExpression),\n" +
"                    $(txIdExpression),\n" +
"                    $(prefixName)_pkg.disable_node_id,\n" +
"                    $(externalSelect),\n" +
"                    " + getCreateTimeExpression(symmetricDialect) + "\n" +
"                    );\n" +
"                end if;\n" +
"            end;\n" +
"        end;\n" +
"    end if;\n" +
"    $(custom_on_update_text)\n" +
"end;\n");

        sqlTemplates.put("updateReloadTriggerTemplate",
"create or replace trigger $(triggerName) after update on $(schemaName)$(tableName)\n" +
"for each row\n" +
"begin\n" +
"    $(custom_before_update_text)\n" +
"    if $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"        values(\n" +
"        '$(targetTableName)',\n" +
"        'R',\n" +
"        $(triggerHistoryId),\n" +
"        $(oldKeys),\n" +
"        $(channelExpression),\n" +
"        $(txIdExpression),\n" +
"        $(prefixName)_pkg.disable_node_id,\n" +
"        $(externalSelect),\n" +
"        " + getCreateTimeExpression(symmetricDialect) + "\n" +
"        );\n" +
"    end if;\n" +
"    $(custom_on_update_text)\n" +
"end;\n");

        sqlTemplates.put("deleteTriggerTemplate",
"create or replace trigger  $(triggerName) after delete on $(schemaName)$(tableName)\n" +
"for each row\n" +
"begin\n" +
"    $(custom_before_delete_text)\n" +
"    if $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) then\n" +
"        begin\n" +
"            insert into $(defaultSchema)$(prefixName)_data\n" +
"            (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"            values(\n" +
"            '$(targetTableName)',\n" +
"            'D',\n" +
"            $(triggerHistoryId),\n" +
"            $(oldKeys),\n" +
"            $(oracleToClob)$(oldColumns),\n" +
"            $(channelExpression),\n" +
"            $(txIdExpression),\n" +
"            $(prefixName)_pkg.disable_node_id,\n" +
"            $(externalSelect),\n" +
"            " + getCreateTimeExpression(symmetricDialect) + "\n" +
"            );\n" +
"        exception\n" +
"        when others then\n" +
"            insert into $(defaultSchema)$(prefixName)_data\n" +
"            (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time)\n" +
"            values(\n" +
"            '$(targetTableName)',\n" +
"            'D',\n" +
"            $(triggerHistoryId),\n" +
"            $(oldKeys),\n" +
"            $(oracleToClobAlways)$(oldColumnsClobAlways),\n" +
"            $(channelExpression),\n" +
"            $(txIdExpression),\n" +
"            $(prefixName)_pkg.disable_node_id,\n" +
"            $(externalSelect),\n" +
"            " + getCreateTimeExpression(symmetricDialect) + "\n" +
"            );\n" +
"        end;\n" +
"    end if;\n" +
"    $(custom_on_delete_text)\n" +
"end;\n");

        sqlTemplates.put("initialLoadSqlTemplate" ,
"select $(oracleQueryHint) $(oracleToClob)$(columns) from $(schemaName)$(tableName) t where $(whereClause)");

        sqlTemplates.put("filteredDdlTriggerTemplate",
"create or replace trigger $(triggerName) after ddl or truncate on schema\n" +
"declare\n" +
"tableName varchar(255);\n" +
"histId number(22);\n" +
"channelId varchar(128);\n" +
"n number;\n" +
"sqlText ora_name_list_t;\n" +
"rowData varchar2(4000);\n" +
"begin\n" +
"if (ora_dict_obj_name not like upper('$(prefixName)%')) then\n" +
"    n := ora_sql_txt(sqlText);\n" + 
"    for i in 1..n loop\n" + 
"        rowData := rowData || sqlText(i);\n" + 
"    end loop;\n" +
"    tableName := '$(prefixName)_node';\n" +
"    if (ora_dict_obj_type like '%TABLE%' and upper(rowData) not like '%DROP%TABLE%') then\n" +
"        tableName := ora_dict_obj_name;\n" +
"    end if;\n" +
"    if (ora_dict_obj_type like '%TRIGGER%') then\n" +
"        if (upper(rowData) like '%CREATE%TRIGGER%') then\n" +
"            select regexp_substr(rowData, '\\son\\s([[:alnum:]_$#\".]+)', 1, 1, 'i', 1) into tableName from dual;\n" +
"            select regexp_replace(tableName, '$(defaultSchema)', '', 1, 1, 'i') into tableName from dual;\n" +
"            if (upper(tableName) = 'DATABASE' or upper(tableName) = 'SCHEMA' or upper(tableName) = 'NESTED') then\n" +
"                tableName := '$(prefixName)_node';\n" +
"            end if;\n" +
"        else\n" +
"            select table_name into tableName from all_triggers where trigger_name = ora_dict_obj_name;\n" +
"        end if;\n" +
"    end if;\n" +
"    if (ora_dict_obj_type like '%INDEX%') then\n" +
"        if (upper(rowData) like '%CREATE%INDEX%') then\n" +
"            select regexp_substr(rowData, '\\son\\s([[:alnum:]_$#\".]+)', 1, 1, 'i', 1) into tableName from dual;\n" +
"            select regexp_replace(tableName, '$(defaultSchema)', '', 1, 1, 'i') into tableName from dual;\n" +
"            if (upper(tableName) = 'CLUSTER') then\n" +
"                tableName := '$(prefixName)_node';\n" +
"            end if;\n" +
"        else\n" +
"            select table_name into tableName from all_indexes where index_name = ora_dict_obj_name;\n" +
"        end if;\n" +
"    end if;\n" +
"    select regexp_replace(tableName, '$(defaultSchema)', '', 1, 1, 'i') into tableName from dual;\n" +
"    begin\n" +
"        select trigger_hist_id, source_table_name into histId, tableName from sym_trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"    exception when no_data_found then\n" +
"        histId := '';\n" +
"    end;\n" +
"    if (histId is not null) then\n" +
"        begin\n" +
"            select channel_id into channelId from sym_trigger where upper(source_table_name) = upper(tableName);\n" +
"        exception when no_data_found then\n" +
"            channelId := 'config';\n" +
"        end;\n" +
"        select regexp_replace(rowData, '$(defaultSchema)', '', 1, 0, 'i') into rowData from dual;\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"        values (tableName, '" + DataEventType.SQL.getCode() + "', histId,\n" +
"        '\"delimiter " + delimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"        channelId, $(prefixName)_pkg.disable_node_id, " + getCreateTimeExpression(symmetricDialect) + ");\n" +
"    end if;\n" +
"end if;\n" +
"end;\n");
        
        sqlTemplates.put("allDdlTriggerTemplate",
"create or replace trigger $(triggerName) after ddl or truncate on schema\n" +
"declare\n" +
"tableName varchar(255);\n" +
"histId number(22);\n" +
"channelId varchar(128);\n" +
"n number;\n" +
"sqlText ora_name_list_t;\n" +
"rowData varchar2(4000);\n" +
"begin\n" +
"if (ora_dict_obj_name not like upper('$(prefixName)%')) then\n" +
"    n := ora_sql_txt(sqlText);\n" + 
"    for i in 1..n loop\n" + 
"        rowData := rowData || sqlText(i);\n" + 
"    end loop;\n" +
"    if (ora_dict_obj_name not like 'SYS_C%' and rowData not like 'CREATE UNIQUE INDEX%') then\n" +
"        if (ora_dict_obj_type like '%TABLE%' and upper(rowData) not like '%DROP%TABLE%') then\n" +
"            tableName := ora_dict_obj_name;\n" +
"        end if;\n" +
"        if (ora_dict_obj_type like '%TRIGGER%') then\n" +
"            if (upper(rowData) like '%CREATE%TRIGGER%') then\n" +
"                select regexp_substr(rowData, '\\son\\s([[:alnum:]_$#\".]+)', 1, 1, 'i', 1) into tableName from dual;\n" +
"                select regexp_replace(tableName, '$(defaultSchema)', '', 1, 1, 'i') into tableName from dual;\n" +
"                if (upper(tableName) = 'DATABASE' or upper(tableName) = 'SCHEMA' or upper(tableName) = 'NESTED') then\n" +
"                    tableName := '$(prefixName)_node';\n" +
"                end if;\n" +
"            else\n" +
"                select table_name into tableName from all_triggers where trigger_name = ora_dict_obj_name;\n" +
"            end if;\n" +
"        end if;\n" +
"        if (ora_dict_obj_type like '%INDEX%') then\n" +
"            if (upper(rowData) like '%CREATE%INDEX%') then\n" +
"                select regexp_substr(rowData, '\\son\\s([[:alnum:]_$#\".]+)', 1, 1, 'i', 1) into tableName from dual;\n" +
"                select regexp_replace(tableName, '$(defaultSchema)', '', 1, 1, 'i') into tableName from dual;\n" +
"                if (upper(tableName) = 'CLUSTER') then\n" +
"                    tableName := '$(prefixName)_node';\n" +
"                end if;\n" +
"            else\n" +
"                select table_name into tableName from all_indexes where index_name = ora_dict_obj_name;\n" +
"            end if;\n" +
"        end if;\n" +
"        if (tableName is not null) then\n" +
"            select regexp_replace(tableName, '$(defaultSchema)', '', 1, 1, 'i') into tableName from dual;\n" +
"            begin\n" +
"                select trigger_hist_id, source_table_name into histId, tableName from sym_trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"            exception when no_data_found then\n" +
"                histId := '';\n" +
"            end;\n" +
"        end if;\n" +
"        if (histId is null) then\n" +
"            tableName := '$(prefixName)_node';\n" +
"            select trigger_hist_id into histId from sym_trigger_hist where upper(source_table_name) = upper(tableName) and inactive_time is null;\n" +
"        end if;\n" +
"        begin\n" +
"            select channel_id into channelId from sym_trigger where upper(source_table_name) = upper(tableName);\n" +
"        exception when no_data_found then\n" +
"            channelId := 'config';\n" +
"        end;\n" +
"        select regexp_replace(rowData, '$(defaultSchema)', '', 1, 0, 'i') into rowData from dual;\n" +
"        insert into $(defaultSchema)$(prefixName)_data\n" +
"        (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"        values (tableName, '" + DataEventType.SQL.getCode() + "', histId,\n" +
"        '\"delimiter " + delimiter + ";' || chr(13) || chr(10) || replace(replace(rowData,'\\','\\\\'),'\"','\\\"') || '\",ddl',\n" +
"        channelId, $(prefixName)_pkg.disable_node_id, " + getCreateTimeExpression(symmetricDialect) + ");\n" +
"    end if;\n" +
"end if;\n" +
"end;\n");
    }

    protected final String getNumberConversionString() {
        if (symmetricDialect.getParameterService().is(ParameterConstants.DBDIALECT_ORACLE_TEMPLATE_NUMBER_TEXT_MINIMUM)) {
            return "to_char($(tableAlias).\"$(columnName)\", 'TM')";
        } else {
            return "cast($(tableAlias).\"$(columnName)\" as number(" + symmetricDialect.getTemplateNumberPrecisionSpec() + "))";
        }
    }
    protected final String getCreateTimeExpression(ISymmetricDialect symmetricDialect) {
        String timezone = symmetricDialect.getParameterService().getString(ParameterConstants.DATA_CREATE_TIME_TIMEZONE);
        if (StringUtils.isEmpty(timezone)) {
            return "CURRENT_TIMESTAMP";
        } else {
            return String.format("CURRENT_TIMESTAMP AT TIME ZONE '%s'", timezone);
        }
    }

    @Override
    protected String toClobExpression(Table table) {
        if (symmetricDialect.getParameterService().is(ParameterConstants.DBDIALECT_ORACLE_USE_NTYPES_FOR_SYNC)) {
            return "to_nclob('')||";
        } else {
            return "to_clob('')||";
        }
    }

}
