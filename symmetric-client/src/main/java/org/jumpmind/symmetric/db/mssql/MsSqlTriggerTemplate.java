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
package org.jumpmind.symmetric.db.mssql;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.sql.Types;
import java.util.HashMap;

import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.jumpmind.util.FormatUtils;

public class MsSqlTriggerTemplate extends AbstractTriggerTemplate {
    boolean castToNVARCHAR;
    String delimiter;

    public MsSqlTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        castToNVARCHAR = symmetricDialect.getParameterService().is(ParameterConstants.MSSQL_USE_NTYPES_FOR_SYNC);
        delimiter = symmetricDialect.getParameterService().getString(ParameterConstants.TRIGGER_CAPTURE_DDL_DELIMITER, "$");
        String triggerExecuteAs = symmetricDialect.getParameterService().getString(ParameterConstants.MSSQL_TRIGGER_EXECUTE_AS, "self");
        String defaultCatalog = symmetricDialect.getParameterService().is(ParameterConstants.MSSQL_INCLUDE_CATALOG_IN_TRIGGERS, true) ? "$(defaultCatalog)"
                : "";
        boolean ddlSendTable = symmetricDialect.getParameterService().is(ParameterConstants.TRIGGER_CAPTURE_DDL_SEND_TABLE);
        // @formatter:off
        emptyColumnTemplate = "''" ;
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(convert("+
        (castToNVARCHAR ? "n" : "")
        +"varchar($(columnSizeOrMax)),$(tableAlias).\"$(columnName)\") $(masterCollation),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        geometryColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(convert("+(castToNVARCHAR ? "n" : "")+"varchar(max),$(tableAlias).\"$(columnName)\".STAsText()) $(masterCollation),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        geographyColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(convert("+(castToNVARCHAR ? "n" : "")+"varchar(max),$(tableAlias).\"$(columnName)\".STAsText()) $(masterCollation),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else ('\"' + convert(varchar(40), $(tableAlias).\"$(columnName)\",2) + '\"') end" ;
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else ('\"' + convert(varchar,$(tableAlias).\"$(columnName)\",121) + '\"') end" ;
        clobColumnTemplate = "case when $(origTableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(cast($(origTableAlias).\"$(columnName)\" as "+(castToNVARCHAR ? "n" : "")+"varchar(max)),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        blobColumnTemplate =   "case when $(origTableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(" + defaultCatalog + "dbo.$(prefixName)_base64_encode(CONVERT(VARBINARY(max), $(origTableAlias).\"$(columnName)\")),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        binaryColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(" + defaultCatalog + "dbo.$(prefixName)_base64_encode(CONVERT(VARBINARY(max), $(tableAlias).\"$(columnName)\")),'\\','\\\\'),'\"','\\\"') + '\"' end" ;
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when $(tableAlias).\"$(columnName)\" = 1 then '\"1\"' else '\"0\"' end" ;
        dateTimeWithTimeZoneColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else ('\"' + convert(varchar,cast($(tableAlias).\"$(columnName)\" as datetime2), 121) + ' ' + case when datepart(tz, $(tableAlias).\"$(columnName)\") > 0 then '+' else '-' end + RIGHT('0' + cast(abs(datepart(tz, $(tableAlias).\"$(columnName)\") / 60) as varchar), 2) + ':' +  RIGHT('0' + cast(datepart(tz, $(tableAlias).\"$(columnName)\") % 60 as varchar), 2) + '\"') end";
        sqlVariantColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' + replace(replace(replace(CONVERT(VARCHAR(MAX), $(tableAlias).\"$(columnName)\","
                + "case when SQL_VARIANT_PROPERTY($(tableAlias).\"$(columnName)\",'BaseType') in ('datetime2','datetime','smalldatetime','datetimeoffset') then 121 "
                + " when SQL_VARIANT_PROPERTY($(tableAlias).\"$(columnName)\",'BaseType') = 'date' then 23 "
                + " when SQL_VARIANT_PROPERTY($(tableAlias).\"$(columnName)\",'BaseType') = 'time' then 108 "
                + " when SQL_VARIANT_PROPERTY($(tableAlias).\"$(columnName)\",'BaseType') in ('money','smallmoney') then 2 "
                + " when SQL_VARIANT_PROPERTY($(tableAlias).\"$(columnName)\",'BaseType') = 'xml' then 1 "
                + " else 0 end),'\\','\\\\'),'\"','\\\"'),'|','\\|') + '|' + CONVERT(VARCHAR(MAX), SQL_VARIANT_PROPERTY($(tableAlias).\"$(columnName)\",'BaseType')) + '|' + CONVERT(VARCHAR(MAX), SQL_VARIANT_PROPERTY($(tableAlias).\"$(columnName)\",'Precision')) + '|' + CONVERT(VARCHAR(MAX), SQL_VARIANT_PROPERTY($(tableAlias).\"$(columnName)\",'Scale')) + '\"' end";
        triggerConcatCharacter = "+" ;
        newTriggerValue = "inserted" ;
        oldTriggerValue = "deleted" ;
        oldColumnPrefix = "" ;
        newColumnPrefix = "" ;

        sqlTemplates = new HashMap<String,String>();
        sqlTemplates.put("insertTriggerTemplate" ,
getCreateTriggerString() + " $(schemaNameOnly)$(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after insert as                                                                                                \n" +
"   begin                                                                                                                                                                  \n" +
"     declare @NCT int \n" +
"     set @NCT = @@OPTIONS & 512 \n" +
"     set nocount on                                                                                                                                                       \n" +
"     declare @TransactionId varchar(1000)                                                                                                                                 \n" +
"     if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
"       select @TransactionId = convert(VARCHAR(1000),transaction_id) from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                     \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_before_insert_text) \n" +
"     if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
"         insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data \n" +
"           (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
"          select '$(targetTableName)','I', $(triggerHistoryId), $(oracleToClob)$(columns), \n" +
"                  $(channelExpression), $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(), $(externalSelect), " + getCreateTimeExpression() + " \n" +
"       $(if:containsBlobClobColumns)                                                                                                                                      \n" +
"          from inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) \n" +
"       $(else:containsBlobClobColumns)                                                                                                                                    \n" +
"          from inserted                                                                                   \n" +
"       $(end:containsBlobClobColumns)                                                                                                                                     \n" +
"          where $(syncOnInsertCondition)               \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_on_insert_text)                                                                                                                                             \n" +
"     if (@NCT = 0) set nocount off                                                                                                                                        \n" +
"   end                                                                                                                                                                    \n" +
"---- go");
        
        sqlTemplates.put("insertReloadTriggerTemplate" ,
getCreateTriggerString() + " $(schemaNameOnly)$(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after insert as                                                                                                \n" +
"   begin                                                                                                                                                                  \n" +
"     declare @NCT int \n" +
"     set @NCT = @@OPTIONS & 512 \n" +
"     set nocount on                                                                                                                                                       \n" +
"     declare @TransactionId varchar(1000)                                                                                                                                 \n" +
"     if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
"       select @TransactionId = convert(VARCHAR(1000),transaction_id) from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                     \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_before_insert_text) \n" +
"     if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
"         insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data \n" +
"           (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
"          select '$(targetTableName)','R', $(triggerHistoryId), $(oracleToClob)$(newKeys), \n" +
"                  $(channelExpression), $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(), $(externalSelect), " + getCreateTimeExpression() + " \n" +
"       $(if:containsBlobClobColumns)                                                                                                                                      \n" +
"          from inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) \n" +
"       $(else:containsBlobClobColumns)                                                                                                                                    \n" +
"          from inserted                                                                                   \n" +
"       $(end:containsBlobClobColumns)                                                                                                                                     \n" +
"          where $(syncOnInsertCondition)               \n" +
"     end                                                                                                                                                                  \n" +
"     $(custom_on_insert_text)                                                                                                                                             \n" +
"     if (@NCT = 0) set nocount off                                                                                                                                        \n" +
"   end                                                                                                                                                                    \n" +
"---- go");


        // TODO Unable to get old data for delete during primary key update trigger because the delete record
        // has the old keys, but the record has already been updated with the new keys so there is no way to
        // join the deleted record with the original record from the table.
        // This occurs for column types like blob, clob, varbinary and binary because those are captured using blobTemplate
        // and need access to the original data
        IParameterService parameterService = symmetricDialect.getParameterService();
        if (parameterService.is(ParameterConstants.TRIGGER_USE_INSERT_DELETE_FOR_PRIMARY_KEY_CHANGES, true)) {
            sqlTemplates.put("updateTriggerTemplate" ,
getCreateTriggerString() + " $(schemaNameOnly)$(triggerName) on $(schemaName)$(tableName)                                                     \n" +
"       with execute as "+triggerExecuteAs+" after update as                                                    \n" +
"begin                                                                                                          \n" +
"  declare @LOCALROWCOUNT int                                                                                   \n" + 
"  declare @LOCALPKCHANGED int                                                                                  \n" +
"  set @LOCALROWCOUNT=@@ROWCOUNT                                                                                \n" +
"  set @LOCALPKCHANGED = 0                                                                                      \n" +
"  if ($(hasPrimaryKeysDefined))                                                                                \n" +
"  begin                                                                                                        \n" +
"    select @LOCALPKCHANGED = count(*) from inserted, deleted where $(oldNewPrimaryKeyJoin)                     \n" +
"  end                                                                                                          \n" +
"  declare @NCT int                                                                                             \n" +
"  set @NCT = @@OPTIONS & 512                                                                                   \n" +
"  set nocount on                                                                                               \n" +
"  declare @TransactionId varchar(1000)                                                                         \n" +
"  if (@@TRANCOUNT > 0)                                                                                         \n" +
"  begin                                                                                                        \n" +
"    select @TransactionId = convert(VARCHAR(1000),transaction_id)                                              \n" +
"    from sys.dm_exec_requests                                                                                  \n" +
"    where session_id=@@SPID and open_transaction_count > 0                                                     \n" +
"  end                                                                                                          \n" +
"  $(custom_before_update_text)                                                                                 \n" +
"  if ($(syncOnIncomingBatchCondition))                                                                         \n" +
"  begin                                                                                                        \n" +
"    if ($(hasPrimaryKeysDefined) $(primaryKeysUpdated) AND @LOCALROWCOUNT <> @LOCALPKCHANGED)                  \n" +
"    begin                                                                                                      \n" +
"      if (@LOCALROWCOUNT = 1)                                                                                  \n" +
"      begin                                                                                                    \n" +
"         insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data                                 \n" +
"            (table_name, event_type, trigger_hist_id, row_data, pk_data, old_data, channel_id,                 \n" +
"             transaction_id, source_node_id,                                                                   \n" +
"             external_data, create_time)                                                                       \n" +
"          select '$(targetTableName)','U', $(triggerHistoryId), $(oracleToClob)$(columns), $(oracleToClob)$(oldKeys), $(oracleToClob)$(oldColumns),         \n" +
"                   $(channelExpression),                                                                       \n" +
"               $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(), $(externalSelect), \n" +
"                " + getCreateTimeExpression() + "                                                              \n" +
"       $(if:containsBlobClobColumns)                                                                           \n" +
"          from inserted                                                                                        \n" +
"          inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin)                  \n" +
"          inner join deleted on 1=1                                                                            \n" +
"       $(else:containsBlobClobColumns)                                                                         \n" +
"          from inserted                                                                                        \n" +
"          inner join deleted on 1=1                                                                            \n" +
"       $(end:containsBlobClobColumns)                                                                          \n" +
"          where $(syncOnUpdateCondition) and ($(dataHasChangedCondition))                                      \n" +
"      end                                                                                                      \n" +
"      else                                                                                                     \n" +
"      begin                                                                                                    \n" +
"        insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data                                  \n" +
"           (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id,                      \n" +
"            source_node_id, external_data, create_time)                                                        \n" +
"          select '$(targetTableName)','D', $(triggerHistoryId), $(oracleToClob)$(oldKeys),                                    \n" +
"            $(specialSqlServerSybaseChannelExpression), $(txIdExpression),                                     \n" +
             defaultCatalog + "dbo.$(prefixName)_node_disabled(),                                               \n" +
"            $(externalSelectForDelete), " + getCreateTimeExpression() + "                                               \n" +
"          from deleted                                                                                         \n" +
"          where $(syncOnDeleteCondition)                                                                       \n" +
"        insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data                                  \n" +
"           (table_name, event_type, trigger_hist_id, row_data, channel_id, transaction_id,                     \n" +
"            source_node_id, external_data, create_time)                                                        \n" +
"          select '$(targetTableName)','I', $(triggerHistoryId), $(oracleToClob)$(columns),                                    \n" +
"            $(channelExpression), $(txIdExpression),                                                            \n" +
            defaultCatalog + "dbo.$(prefixName)_node_disabled(),                                                \n" +
"           $(externalSelectForInsert), " + getCreateTimeExpression() + "                                                \n" +
"       $(if:containsBlobClobColumns)                                                                           \n" +
"          from inserted                                                                                        \n" +
"          inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin)                  \n" +
"       $(else:containsBlobClobColumns)                                                                         \n" +
"          from inserted                                                                                        \n" +
"       $(end:containsBlobClobColumns)                                                                          \n" +
"          where $(syncOnInsertCondition)                                                                       \n" +
"      end                                                                                                      \n" +
"    end                                                                                                        \n" +
"    else                                                                                                       \n" +
"    begin                                                                                                      \n" +
"         insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data                                 \n" +
"            (table_name, event_type, trigger_hist_id, row_data, pk_data, old_data, channel_id,                 \n" +
"             transaction_id, source_node_id,                                                                   \n" +
"             external_data, create_time)                                                                       \n" +
"          select '$(targetTableName)','U', $(triggerHistoryId), $(oracleToClob)$(columns), $(oracleToClob)$(oldKeys), $(oracleToClob)$(oldColumns),         \n" +
"                   $(channelExpression),                                                                       \n" +
"               $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(), $(externalSelect), \n" +
"                " + getCreateTimeExpression() + "                                                              \n" +
"       $(if:containsBlobClobColumns)                                                                           \n" +
"          from inserted                                                                                        \n" +
"          inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin)                  \n" +
"          inner join deleted on $(oldNewPrimaryKeyJoin)                                                        \n" +
"       $(else:containsBlobClobColumns)                                                                         \n" +
"          from inserted                                                                                        \n" +
"          inner join deleted on $(oldNewPrimaryKeyJoin)                                                        \n" +
"       $(end:containsBlobClobColumns)                                                                          \n" +
"          where $(syncOnUpdateCondition) and ($(dataHasChangedCondition))                                      \n" +
"    end                                                                                                        \n" +
"  end                                                                                                          \n" +
"  $(custom_on_update_text)                                                                                     \n" +
"  if (@NCT = 0) set nocount off                                                                                \n" +
"end                                                                                                            \n" +
"---- go");
        
            sqlTemplates.put("updateReloadTriggerTemplate" ,
getCreateTriggerString() + " $(schemaNameOnly)$(triggerName) on $(schemaName)$(tableName)                                                     \n" +
"       with execute as "+triggerExecuteAs+" after update as                                                    \n" +
"begin                                                                                                          \n" +
"  declare @LOCALROWCOUNT int                                                                                   \n" + 
"  set @LOCALROWCOUNT=@@ROWCOUNT                                                                                \n" +
"  declare @LOCALPKCHANGED int                                                                                  \n" +
"  set @LOCALPKCHANGED = 0                                                                                      \n" +
"  if ($(hasPrimaryKeysDefined))                                                                                \n" +
"  begin                                                                                                        \n" +
"    select @LOCALPKCHANGED = count(*) from inserted, deleted where $(oldNewPrimaryKeyJoin)                     \n" +
"  end                                                                                                          \n" +
"  declare @NCT int                                                                                             \n" +
"  set @NCT = @@OPTIONS & 512                                                                                   \n" +
"  set nocount on                                                                                               \n" +
"  declare @TransactionId varchar(1000)                                                                         \n" +
"  if (@@TRANCOUNT > 0)                                                                                         \n" +
"  begin                                                                                                        \n" +
"    select @TransactionId = convert(VARCHAR(1000),transaction_id)                                              \n" +
"    from sys.dm_exec_requests                                                                                  \n" +
"    where session_id=@@SPID and open_transaction_count > 0                                                     \n" +
"  end                                                                                                          \n" +
"  $(custom_before_update_text)                                                                                 \n" +
"  if ($(syncOnIncomingBatchCondition))                                                                         \n" +
"  begin                                                                                                        \n" +
"    if ($(hasPrimaryKeysDefined) $(primaryKeysUpdated) AND @LOCALROWCOUNT <> @LOCALPKCHANGED )                                                       \n" +
"    begin                                                                                                      \n" +
"      if (@LOCALROWCOUNT = 1)                                                                                  \n" +
"      begin                                                                                                    \n" +
"         insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data                                 \n" +
"            (table_name, event_type, trigger_hist_id, row_data, pk_data, old_data, channel_id,                 \n" +
"             transaction_id, source_node_id,                                                                   \n" +
"             external_data, create_time)                                                                       \n" +
"          select '$(targetTableName)','U', $(triggerHistoryId), $(oracleToClob)$(columns), $(oracleToClob)$(oldKeys), $(oracleToClob)$(oldColumns),         \n" +
"                   $(channelExpression),                                                                       \n" +
"               $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(), $(externalSelect), \n" +
"                " + getCreateTimeExpression() + "                                                              \n" +
"       $(if:containsBlobClobColumns)                                                                           \n" +
"          from inserted                                                                                        \n" +
"          inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin)                  \n" +
"          inner join deleted on 1=1                                                                            \n" +
"       $(else:containsBlobClobColumns)                                                                         \n" +
"          from inserted                                                                                        \n" +
"          inner join deleted on 1=1                                                                            \n" +
"       $(end:containsBlobClobColumns)                                                                          \n" +
"          where $(syncOnUpdateCondition) and ($(dataHasChangedCondition))                                      \n" +
"      end                                                                                                      \n" +
"      else                                                                                                     \n" +
"      begin                                                                                                    \n" +
"        insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data                                  \n" +
"           (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id,                      \n" +
"            source_node_id, external_data, create_time)                                                        \n" +
"          select '$(targetTableName)','D', $(triggerHistoryId), $(oracleToClob)$(oldKeys),                                    \n" +
"              $(specialSqlServerSybaseChannelExpression),                                                      \n" +
"              $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(),                     \n" +
"              $(externalSelectForDelete), " + getCreateTimeExpression() + "                                             \n" +
"          from deleted                                                                                         \n" +
"          where $(syncOnDeleteCondition)                                                                       \n" +
"        insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data                                  \n" +
"           (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id,                      \n" +
"            source_node_id, external_data, create_time)                                                        \n" +
"          select '$(targetTableName)','R', $(triggerHistoryId), $(oracleToClob)$(newKeys),                                    \n" +
"                  $(channelExpression), $(txIdExpression),                                                     \n" + 
                   defaultCatalog + "dbo.$(prefixName)_node_disabled(),                                         \n" +
"                  $(externalSelectForInsert), " + getCreateTimeExpression() + "                                         \n" +
"       $(if:containsBlobClobColumns)                                                                           \n" +
"          from inserted                                                                                        \n" +
"          inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin)                  \n" +
"       $(else:containsBlobClobColumns)                                                                         \n" +
"          from inserted                                                                                        \n" +
"       $(end:containsBlobClobColumns)                                                                          \n" +
"          where $(syncOnInsertCondition)                                                                       \n" +
"      end                                                                                                      \n" +
"    end                                                                                                        \n" +
"    else                                                                                                       \n" +
"    begin                                                                                                      \n" +
"         insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data                                 \n" +
"            (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id,                     \n" +
"             source_node_id, external_data, create_time)                                                       \n" +
"           select '$(targetTableName)','R', $(triggerHistoryId), $(oracleToClob)$(oldKeys), $(channelExpression),             \n" +
"               $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(),                    \n" +
"               $(externalSelect), " + getCreateTimeExpression() + "                                            \n" +
"       $(if:containsBlobClobColumns)                                                                           \n" +
"           from inserted                                                                                       \n" +
"           inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin)                 \n" +
"           inner join deleted on $(oldNewPrimaryKeyJoin)                                                       \n" +
"       $(else:containsBlobClobColumns)                                                                         \n" +
"           from inserted                                                                                       \n" +
"           inner join deleted on $(oldNewPrimaryKeyJoin)                                                       \n" +
"       $(end:containsBlobClobColumns)                                                                          \n" +
"          where $(syncOnUpdateCondition) and ($(dataHasChangedCondition))                                      \n" +
"    end                                                                                                        \n" +
"  end                                                                                                          \n" +
"  $(custom_on_update_text)                                                                                     \n" +
"  if (@NCT = 0) set nocount off                                                                                \n" +
"end                                                                                                            \n" +
"---- go");
        } else {
            sqlTemplates.put("updateTriggerTemplate" ,
                    getCreateTriggerString() + " $(schemaNameOnly)$(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after update as                                                                                                                             \n" +
                    "   begin                                                                                                                                                                  \n" +
                    "     declare @NCT int \n" +
                    "     set @NCT = @@OPTIONS & 512 \n" +
                    "     set nocount on                                                                                                                                                       \n" +
                    "     declare @TransactionId varchar(1000)                                                                                                                                 \n" +
                    "                                                                                                                                                                          \n" +
                    "     if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
                    "       select @TransactionId = convert(VARCHAR(1000),transaction_id) from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                                            \n" +
                    "     end                                                                                                                                                                  \n" +
                    "     $(custom_before_update_text) \n" +
                    "     if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
                    "         insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, row_data, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
                    "             select '$(targetTableName)','U', $(triggerHistoryId), $(columns), $(oldKeys), $(oldColumns), $(channelExpression), "+
                    "               $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(), $(externalSelect), current_timestamp\n" +
                    "       $(if:containsBlobClobColumns)                                                                                                                                      \n" +
                    "          from (select $(nonBlobColumns), row_number() over (order by (select 1)) as __row_num from inserted) inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) inner join (select $(nonBlobColumns), row_number() over (order by (select 1)) as __row_num from deleted)deleted on (inserted.__row_num = deleted.__row_num)\n" +
                    "       $(else:containsBlobClobColumns)                                                                                                                                    \n" +
                    "          from (select *, row_number() over (order by (select 1)) as __row_num from inserted) inserted inner join (select *, row_number() over (order by (select 1)) as __row_num from deleted) deleted on (inserted.__row_num = deleted.__row_num)                                    \n" +
                    "       $(end:containsBlobClobColumns)                                                                                                                                     \n" +
                    "          where $(syncOnUpdateCondition) and ($(dataHasChangedCondition))                                                                     \n" +
                    "       end                                                                                                                                                                \n" +
                    "       $(custom_on_update_text)                                                                                                                                             \n" +
                    "     if (@NCT = 0) set nocount off                                                                                                                                        \n" +
                    "   end                                                                                                                                                                    \n" +
                    "---- go");

            sqlTemplates.put("updateReloadTriggerTemplate" ,
                    getCreateTriggerString() + " $(schemaNameOnly)$(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after update as                                                                                                \n" +
                    "   begin                                                                                                                                                                  \n" +
                    "     declare @NCT int \n" +
                    "     set @NCT = @@OPTIONS & 512 \n" +
                    "     set nocount on                                                                                                                                                       \n" +
                    "     declare @TransactionId varchar(1000)                                                                                                                                 \n" +
                    "     if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
                    "       select @TransactionId = convert(VARCHAR(1000),transaction_id) from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                     \n" +
                    "     end                                                                                                                                                                  \n" +
                    "     $(custom_before_update_text) \n" +
                    "     if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
                    "         insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
                    "             select '$(targetTableName)','R', $(triggerHistoryId), $(oldKeys), $(channelExpression), "+
                    "               $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(), $(externalSelect), current_timestamp\n" +
                    "       $(if:containsBlobClobColumns)                                                                                                                                      \n" +
                    "          from inserted inner join $(schemaName)$(tableName) $(origTableAlias) on $(tableNewPrimaryKeyJoin) inner join deleted on $(oldNewPrimaryKeyJoin) \n" +
                    "       $(else:containsBlobClobColumns)                                                                                                                                    \n" +
                    "          from inserted inner join deleted on $(oldNewPrimaryKeyJoin)                                   \n" +
                    "       $(end:containsBlobClobColumns)                                                                                                                                     \n" +
                    "          where $(syncOnUpdateCondition) and ($(dataHasChangedCondition))                                                    \n" +
                    "       end                                                                                                                                                                \n" +
                    "       $(custom_on_update_text)                                                                                                                                             \n" +
                    "     if (@NCT = 0) set nocount off                                                                                                                                        \n" +
                    "   end                                                                                                                                                                    \n" +
                    "---- go");
        }

        sqlTemplates.put("deleteTriggerTemplate" ,
getCreateTriggerString() + " $(schemaNameOnly)$(triggerName) on $(schemaName)$(tableName) with execute as "+triggerExecuteAs+" after delete as                                                                                                                             \n" +
"  begin                                                                                                                                                                  \n" +
"    declare @NCT int \n" +
"    set @NCT = @@OPTIONS & 512 \n" +
"    set nocount on                                                                                                                                                       \n" +
"    declare @TransactionId varchar(1000)                                                                                                                                 \n" +
"    if (@@TRANCOUNT > 0) begin                                                                                                                                           \n" +
"       select @TransactionId = convert(VARCHAR(1000),transaction_id)    from sys.dm_exec_requests where session_id=@@SPID and open_transaction_count > 0                                           \n" +
"    end                                                                                                                                                                  \n" +
"    $(custom_before_delete_text) \n" +
"    if ($(syncOnIncomingBatchCondition)) begin                                                                                                                           \n" +
"        insert into  " + defaultCatalog + "$(defaultSchema)$(prefixName)_data (table_name, event_type, trigger_hist_id, pk_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) \n" +
"        select '$(targetTableName)','D', $(triggerHistoryId), $(oracleToClob)$(oldKeys), $(oracleToClob)$(oldColumns), $(channelExpression), \n" +
"              $(txIdExpression),  " + defaultCatalog + "dbo.$(prefixName)_node_disabled(), $(externalSelect), " + getCreateTimeExpression() + "\n" +
"        from deleted where $(syncOnDeleteCondition)                                                                      \n" +
"    end                                                                                                                                                                  \n" +
"    $(custom_on_delete_text)                                                                                                                                              \n" +
"    if (@NCT = 0) set nocount off                                                                                                                                         \n" +
"   end                                                                                                                                                                    \n" +
"---- go");

        sqlTemplates.put("filteredDdlTriggerTemplate",
getCreateTriggerString() + " $(triggerName) on database\n" + 
"for create_table, drop_table, alter_table, rename,\n" +
"create_view, drop_view, alter_view,\n" +
"create_function, drop_function, alter_function,\n" +
"create_procedure, drop_procedure, alter_procedure,\n" +
"create_trigger, drop_trigger, alter_trigger,\n" +
"create_type, drop_type, \n" +
"create_schema, drop_schema, \n" +
"create_index, drop_index, alter_index as\n" +
"declare @data xml\n" +
"declare @eventType nvarchar(128)\n" +
"declare @tableName nvarchar(255)\n" +
"declare @histId int\n" +
"declare @channelId nvarchar(128)\n" +
"declare @ddlSendTable nvarchar(10)\n" + 
"declare @isTableChange nvarchar(10)\n" + 
"set @ddlSendTable = '" + ddlSendTable + "'\n" +
"set @data = eventdata()\n" +
"if (@data.value('(/EVENT_INSTANCE/ObjectName)[1]', 'nvarchar(128)') not like '$(prefixName)%') begin\n" +
"  set @eventType = @data.value('(/EVENT_INSTANCE/EventType)[1]', 'nvarchar(128)')\n" +
"  set @tableName = '$(prefixName)_node'\n" +
"  if (@eventType like '%_TABLE') begin\n" +
"    if (@ddlSendTable = 'true')\n" +
"		set @isTableChange = 'true'\n" +
"    set @tableName = @data.value('(/EVENT_INSTANCE/ObjectName)[1]', 'nvarchar(128)')\n" +
"  end\n" +
"  if (@eventType like '%_TRIGGER' or @eventType like '%_INDEX')\n" +
"    set @tableName = @data.value('(/EVENT_INSTANCE/TargetObjectName)[1]', 'nvarchar(128)')\n" +
"  select @histId = max(trigger_hist_id) from " + defaultCatalog + "$(defaultSchema)$(prefixName)_trigger_hist where source_table_name = @tableName and inactive_time is null\n" +
"  if (@histId is not null) begin\n" +
"    select @channelId = channel_id from " + defaultCatalog + "$(defaultSchema)$(prefixName)_trigger where source_table_name = @tableName\n" +
"    if (@channelId is null)\n" +
"      set @channelId = 'config'\n" +
"    if (@isTableChange = 'true')\n" +
"    		insert into " + defaultCatalog + "$(defaultSchema)$(prefixName)_data\n" +
"    		(table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"    		values (@tableName, '" + DataEventType.CREATE.getCode() + "', @histId,\n" +
"    		'" + AbstractTriggerTemplate.CREATE_EVENT_DDL_GENERATED + "',\n" +
"    		@channelId, " + defaultCatalog + "$(defaultSchema)$(prefixName)_node_disabled(), " + getCreateTimeExpression() + ")\n" +
"    else\n" +
"    		insert into " + defaultCatalog + "$(defaultSchema)$(prefixName)_data\n" +
"    		(table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"    		values (@tableName, '" + DataEventType.SQL.getCode() + "', @histId,\n" +
"    		'\"delimiter " + delimiter + ";' + CHAR(13) + char(10) + replace(replace(@data.value('(/EVENT_INSTANCE/TSQLCommand/CommandText)[1]', 'nvarchar(max)'),'\\','\\\\'),'\"','\\\"') + '\",ddl',\n" +
"    		@channelId, " + defaultCatalog + "$(defaultSchema)$(prefixName)_node_disabled(), " + getCreateTimeExpression() + ")\n" +
"  end\n" +
"end\n" + "---- go");
        
        sqlTemplates.put("allDdlTriggerTemplate",
getCreateTriggerString() + " $(triggerName) on database\n" + 
"for create_table, drop_table, alter_table,rename,\n" +
"create_view, drop_view, alter_view,\n" +
"create_function, drop_function, alter_function,\n" +
"create_procedure, drop_procedure, alter_procedure,\n" +
"create_trigger, drop_trigger, alter_trigger,\n" +
"create_type, drop_type, \n" +
"create_schema, drop_schema, \n" +
"create_index, drop_index, alter_index as\n" +
"declare @data xml\n" +
"declare @eventType nvarchar(128)\n" +
"declare @tableName nvarchar(255)\n" +
"declare @histId int\n" +
"declare @channelId nvarchar(128)\n" +
"set @data = eventdata()\n" +
"if (@data.value('(/EVENT_INSTANCE/ObjectName)[1]', 'nvarchar(128)') not like '$(prefixName)%') begin\n" +
"  set @eventType = @data.value('(/EVENT_INSTANCE/EventType)[1]', 'nvarchar(128)')\n" +
"  if (@eventType like '%_TABLE')\n" +
"    set @tableName = @data.value('(/EVENT_INSTANCE/ObjectName)[1]', 'nvarchar(128)')\n" +
"  if (@eventType like '%_TRIGGER' or @eventType like '%_INDEX')\n" +
"    set @tableName = @data.value('(/EVENT_INSTANCE/TargetObjectName)[1]', 'nvarchar(128)')\n" +
"  if (@tableName is not null)\n" +
"    select @histId = max(trigger_hist_id) from " + defaultCatalog + "$(defaultSchema)$(prefixName)_trigger_hist where source_table_name = @tableName and inactive_time is null\n" +
"  if (@histId is null)\n" +
"    set @tableName = '$(prefixName)_node';\n" +
"    select @histId = max(trigger_hist_id) from " + defaultCatalog + "$(defaultSchema)$(prefixName)_trigger_hist where source_table_name = @tableName and inactive_time is null\n" +
"  select @channelId = channel_id from " + defaultCatalog + "$(defaultSchema)$(prefixName)_trigger where source_table_name = @tableName\n" +
"  if (@channelId is null)\n" +
"    set @channelId = 'config'\n" +
"  insert into " + defaultCatalog + "$(defaultSchema)$(prefixName)_data\n" +
"  (table_name, event_type, trigger_hist_id, row_data, channel_id, source_node_id, create_time)\n" +
"  values (@tableName, '" + DataEventType.SQL.getCode() + "', @histId,\n" +
"  '\"delimiter " + delimiter + ";' + CHAR(13) + char(10) + replace(replace(@data.value('(/EVENT_INSTANCE/TSQLCommand/CommandText)[1]', 'nvarchar(max)'),'\\','\\\\'),'\"','\\\"') + '\",ddl',\n" +
"  @channelId, " + defaultCatalog + "$(defaultSchema)$(prefixName)_node_disabled(), " + getCreateTimeExpression() + ")\n" +
"end\n" + "---- go");
        
        sqlTemplates.put("initialLoadSqlTemplate" ,
"select $(columns) from $(schemaName)$(tableName) t where $(whereClause) " );

    }

    @Override
    protected String replaceTemplateVariables(DataEventType dml, Trigger trigger,
            TriggerHistory history, Channel channel, String tablePrefix, Table originalTable, Table table,
            String defaultCatalog, String defaultSchema, String ddl)
    {
        ddl =  super.replaceTemplateVariables(dml, trigger, history, channel, tablePrefix, originalTable, table,
                defaultCatalog, defaultSchema, ddl);
        ddl = FormatUtils.replace("anyNonBlobColumnChanged",
                buildNonLobColumnsAreNotEqualString(table, newTriggerValue, oldTriggerValue), ddl);
        
        ddl = FormatUtils.replace("nonBlobColumns", buildNonLobColumnsString(table), ddl);
        return ddl;
    }
    
    protected boolean isNotComparable(Column column) {
        String columnType = column.getJdbcTypeName();
        return Strings.CI.equals(columnType, "IMAGE") || Strings.CI.equals(columnType, "TEXT") || Strings.CI.equals(columnType, "NTEXT");
    }    
    
    private String buildNonLobColumnsAreNotEqualString(Table table, String table1Name, String table2Name){
        StringBuilder builder = new StringBuilder();
        
        for(Column column : table.getColumns()){
            if (isNotComparable(column)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" or ");
            }

            builder.append(String.format("((%1$s.\"%2$s\" IS NOT NULL AND %3$s.\"%2$s\" IS NOT NULL AND %1$s.\"%2$s\"<>%3$s.\"%2$s\") or "
                            + "(%1$s.\"%2$s\" IS NULL AND %3$s.\"%2$s\" IS NOT NULL) or "
                            + "(%1$s.\"%2$s\" IS NOT NULL AND %3$s.\"%2$s\" IS NULL))", table1Name, column.getName(), table2Name));
        }
        if (builder.length() == 0) {
            builder.append("1=1");
        }
        return builder.toString();
    }
    
    private String buildNonLobColumnsString(Table table){
        StringBuilder builder = new StringBuilder();
        
        for(Column column : table.getColumns()){
            if(isNotComparable(column)){
                continue;
            }
            if(builder.length() > 0){
                builder.append(",");
            }
            builder.append('"');
            builder.append(column.getName());
            builder.append('"');
        }
        
        return builder.toString();
    }
    
    @Override
    protected String getSourceTablePrefix(TriggerHistory triggerHistory) {
        String prefix = (isNotBlank(triggerHistory.getSourceSchemaName()) ? SymmetricUtils.quote(
                symmetricDialect, triggerHistory.getSourceSchemaName()) + symmetricDialect.getPlatform().getDatabaseInfo().getSchemaSeparator() : "");
        prefix = (isNotBlank(triggerHistory.getSourceCatalogName()) ? SymmetricUtils.quote(
                symmetricDialect, triggerHistory.getSourceCatalogName()) + symmetricDialect.getPlatform().getDatabaseInfo().getCatalogSeparator() : "")
                + prefix;
        if (isBlank(prefix)) {
            prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultSchema()) ? SymmetricUtils
                    .quote(symmetricDialect, symmetricDialect.getPlatform().getDefaultSchema())
                    + "." : "");
            
            if (symmetricDialect.getParameterService().is(ParameterConstants.MSSQL_INCLUDE_CATALOG_IN_TRIGGERS, true)) {
                prefix = (isNotBlank(symmetricDialect.getPlatform().getDefaultCatalog()) ? SymmetricUtils
                        .quote(symmetricDialect, symmetricDialect.getPlatform().getDefaultCatalog())
                        + "." : "") + prefix;
            }
        }
        return prefix;
    }
    
    protected String getCreateTimeExpression() {
        return "current_timestamp";
    }

    @Override
    protected String getColumnSize(Table table, Column column) {
        int totalSize = 0;
        for (Column c : table.getColumns()) {
            totalSize += c.getSizeAsInt();
        }
        if (castToNVARCHAR && totalSize > 8000) {
            return "max";
        }
        
        if (castToNVARCHAR && column.getSizeAsInt() > 4000) {
            return "max";
        }
        return column.getSize();
    }

    @Override
    protected boolean useTriggerTemplateForColumnTemplatesDuringInitialLoad(Column column) {
        boolean result = super.useTriggerTemplateForColumnTemplatesDuringInitialLoad(column);
        if (column != null) {
            int type = column.getJdbcTypeCode();
            if (type == Types.DECIMAL || type == Types.FLOAT || type == Types.DOUBLE) {
                // always use template for decimal to avoid conversion to scientific notation
                result = true;
            }
        }
        return result;
    }

    @Override
    protected boolean requiresEmptyLobTemplateForDeletes() {
        return true;
    }
    
    @Override
    protected String toClobExpression(Table table) {
        if (castToNVARCHAR) {
            return "cast(N'' as nvarchar(max)) +";
        } else {
            return "cast('' as varchar(max)) +";
        }
    }
}
