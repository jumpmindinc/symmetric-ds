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

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.db.SequenceIdentifier;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IParameterService;

/*
 * A dialect that is specific to Oracle databases
 */
public class OracleSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    static final String ORACLE_OBJECT_TYPE = "FUNCTION";
    static final String SQL_SELECT_TRIGGERS = "from ALL_TRIGGERS where owner = sys_context('USERENV', 'CURRENT_SCHEMA') and trigger_name = upper(?) and table_name = upper(?) and table_owner = upper(?)";
    static final String SQL_SELECT_DDL_TRIGGERS = "from ALL_TRIGGERS where owner = sys_context('USERENV', 'CURRENT_SCHEMA') and trigger_name = upper(?) and table_name is null and table_owner = upper(?)";
    static final String SQL_SELECT_TRANSACTIONS = "select min(start_time) from gv$transaction where status = 'ACTIVE'";
    static final String SQL_OBJECT_INSTALLED = "select count(*) from user_source where line = 1 and (((type = 'FUNCTION' or type = 'PACKAGE') and name=upper('$(functionName)')) or (name||'_'||type=upper('$(functionName)')))";
    static final String SQL_DROP_FUNCTION = "DROP FUNCTION $(functionName)";

    public OracleSymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new OracleTriggerTemplate(this);
        this.supportsDdlTriggers = true;
        if (parameterService.is(ParameterConstants.DBDIALECT_ORACLE_USE_TRANSACTION_VIEW)) {
            try {
                areDatabaseTransactionsPendingSince(System.currentTimeMillis());
                supportsTransactionViews = true;
            } catch (Exception ex) {
                log.warn("Was not able to enable the use of transaction views.  You might not have access to select from gv$transaction", ex);
            }
        }
        platform.getDatabaseInfo().setGeneratedColumnsSupported(databaseMajorVersion >= 11);
    }

    @Override
    public Database readSymmetricSchemaFromXml() {
        Database db = super.readSymmetricSchemaFromXml();
        if (parameterService.is(ParameterConstants.DBDIALECT_ORACLE_USE_NTYPES_FOR_SYNC)) {
            Table table = db.findTable(TableConstants.getTableName(getTablePrefix(),
                    TableConstants.SYM_DATA));
            table.getColumnWithName("row_data").setMappedType(TypeMap.NCLOB);
            table.getColumnWithName("old_data").setMappedType(TypeMap.NCLOB);
            table.getColumnWithName("pk_data").setMappedType(TypeMap.NCLOB);
        }
        return db;
    }

    @Override
    protected void buildSqlReplacementTokens() {
        super.buildSqlReplacementTokens();
        if (parameterService.is(ParameterConstants.DBDIALECT_ORACLE_USE_HINTS, true)) {
            sqlReplacementTokens.put("selectDataUsingGapsSqlHint", "/*+ index(d " + parameterService.getTablePrefix() + "_IDX_D_CHANNEL_ID) */");
        }
        if (parameterService.is(ParameterConstants.DBDIALECT_ORACLE_USE_SELECT_START_DATA_ID_HINT, false)) {
            sqlReplacementTokens.put("selectDataUsingStartDataIdHint", "/*+ full (d) */");
        }
    }

    @Override
    protected boolean doesTriggerExistOnPlatform(StringBuilder sqlBuffer, String catalog, String schema, String tableName,
            String triggerName) {
        if (isBlank(schema)) {
            schema = platform.getDefaultSchema();
        }
        return platform.getSqlTemplate().queryForInt("select count(*) " + SQL_SELECT_TRIGGERS,
                new Object[] { triggerName, tableName, schema }) > 0;
    }

    @Override
    protected String getDropTriggerSql(StringBuilder sqlBuffer, String catalogName,
            String schemaName, String triggerName, String tableName) {
        return "drop trigger " + triggerName;
    }

    @Override
    public void createTrigger(StringBuilder sqlBuffer, DataEventType dml, Trigger trigger,
            TriggerHistory history, Channel channel, String tablePrefix, Table table, ISqlTransaction transaction) {
        try {
            super.createTrigger(sqlBuffer, dml, trigger, history, channel,
                    parameterService.getTablePrefix(), table, transaction);
        } catch (SqlException ex) {
            if (ex.getErrorCode() == 4095) {
                try {
                    // a trigger of the same name exists on another table
                    Map<String, Object> map = platform.getSqlTemplate().queryForMap("select * " + SQL_SELECT_TRIGGERS,
                            new Object[] { history.getTriggerNameForDmlType(dml), history.getSourceTableName(), history.getSourceSchemaName() });
                    if (map != null) {
                        log.warn("Trigger named {} already exists on table {}.{} and it cannot be replaced",
                                history.getTriggerNameForDmlType(dml), map.get("TABLE_OWNER"), map.get("TABLE_NAME"));
                    } else {
                        log.warn("Trigger named {} already exists on another table and it cannot be replaced", history.getTriggerNameForDmlType(dml));
                    }
                } catch (SqlException e) {
                }
            }
            throw ex;
        }
    }

    @Override
    public boolean doesDdlTriggerExist(final String catalogName, final String schema, final String triggerName) {
        if (isBlank(schema)) {
            return platform.getSqlTemplate().queryForInt("select count(*) " + SQL_SELECT_DDL_TRIGGERS,
                    new Object[] { triggerName, platform.getDefaultSchema() }) > 0;
        } else {
            return platform.getSqlTemplate().queryForInt("select count(*) " + SQL_SELECT_DDL_TRIGGERS,
                    new Object[] { triggerName, schema }) > 0;
        }
    }

    @Override
    public void removeDdlTrigger(StringBuilder sqlBuffer, String catalogName, String schemaName, String triggerName) {
        String sql = "drop trigger " + triggerName;
        logSql(sql, sqlBuffer);
        if (parameterService.is(ParameterConstants.AUTO_SYNC_TRIGGERS) && sqlBuffer == null) {
            try {
                log.info("Removing DDL trigger " + triggerName);
                platform.getSqlTemplate().update(sql);
            } catch (Exception e) {
                log.warn("Tried to remove DDL trigger using: {} and failed because: {}", sql, e.getMessage());
            }
        }
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String blobToClob = this.parameterService.getTablePrefix() + "_" + "blob2clob";
        if (!installed(SQL_OBJECT_INSTALLED, blobToClob)) {
            String sql = "CREATE OR REPLACE FUNCTION $(functionName) (blob_in IN BLOB)                                                                                                                                           "
                    + "     RETURN CLOB                                                                                                                                                          "
                    + "   AS                                                                                                                                                                     "
                    + "       v_clob    CLOB := null;                                                                                                                                            "
                    + "       v_varchar VARCHAR2(32767);                                                                                                                                         "
                    + "       v_start   PLS_INTEGER := 1;                                                                                                                                        "
                    + "       v_buffer  PLS_INTEGER := 999;                                                                                                                                      "
                    + "   BEGIN                                                                                                                                                                  "
                    + "       IF blob_in IS NOT NULL THEN                                                                                                                                        "
                    + "           IF DBMS_LOB.GETLENGTH(blob_in) > 0 THEN                                                                                                                        "
                    + "               DBMS_LOB.CREATETEMPORARY(v_clob, TRUE);                                                                                                                    "
                    + "               FOR i IN 1..CEIL(DBMS_LOB.GETLENGTH(blob_in) / v_buffer)                                                                                                   "
                    + "               LOOP                                                                                                                                                       "
                    + "                   v_varchar := UTL_RAW.CAST_TO_VARCHAR2(UTL_ENCODE.base64_encode(DBMS_LOB.SUBSTR(blob_in, v_buffer, v_start)));                                          "
                    + "                   v_varchar := REPLACE(v_varchar,CHR(13)||CHR(10));                                                                                                      "
                    + "                   DBMS_LOB.WRITEAPPEND(v_clob, LENGTH(v_varchar), v_varchar);                                                                                            "
                    + "                   v_start := v_start + v_buffer;                                                                                                                         "
                    + "               END LOOP;                                                                                                                                                  "
                    + "           END IF;                                                                                                                                                        "
                    + "       END IF;                                                                                                                                                            "
                    + "       RETURN v_clob;                                                                                                                                                     "
                    + "   END $(functionName);                                                                                                                                                   ";
            install(sql, blobToClob, ddl);
        }
        String transactionId = this.parameterService.getTablePrefix() + "_" + "transaction_id";
        if (!installed(SQL_OBJECT_INSTALLED, transactionId)) {
            String sql = "CREATE OR REPLACE function $(functionName)                                                                                                                                                             "
                    + "   return varchar is                                                                                                                                                  "
                    + "   begin                                                                                                                                                              "
                    + "      return DBMS_TRANSACTION.local_transaction_id(false);                                                                                                            "
                    + "   end;                                                                                                                                                               ";
            install(sql, transactionId, ddl);
        }
        String triggerDisabled = this.parameterService.getTablePrefix() + "_" + "trigger_disabled";
        if (!installed(SQL_OBJECT_INSTALLED, triggerDisabled)) {
            String sql = "CREATE OR REPLACE function $(functionName) return varchar is                                                                                                                                           "
                    + "   begin                                                                                                                                                                "
                    + "      return " + getSymmetricPackageName()
                    + ".disable_trigger;                                                                                                                                   "
                    + "   end;                                                                                                                                                                 ";
            install(sql, triggerDisabled, ddl);
        }
        String pkgPackage = this.parameterService.getTablePrefix() + "_" + "pkg";
        if (!installed(SQL_OBJECT_INSTALLED, pkgPackage)) {
            String sql = "CREATE OR REPLACE package $(functionName) as                                                                                                                                                                   "
                    + "      disable_trigger pls_integer;                                                                                                                                       "
                    + "      disable_node_id varchar(50);                                                                                                                                       "
                    + "      procedure setValue (a IN number);                                                                                                                                  "
                    + "      procedure setNodeValue (node_id IN varchar);                                                                                                                       "
                    + "  end " + getSymmetricPackageName()
                    + ";                                                                                                                                                           ";
            install(sql, pkgPackage, ddl);
            sql = "CREATE OR REPLACE package body $(functionName) as                                                                                                                                                              "
                    + "     procedure setValue(a IN number) is                                                                                                                                 "
                    + "     begin                                                                                                                                                              "
                    + "         $(functionName).disable_trigger:=a;                                                                                                                                   "
                    + "     end;                                                                                                                                                               "
                    + "     procedure setNodeValue(node_id IN varchar) is                                                                                                                      "
                    + "     begin                                                                                                                                                              "
                    + "         $(functionName).disable_node_id := node_id;                                                                                                                           "
                    + "     end;                                                                                                                                                               "
                    + " end " + getSymmetricPackageName()
                    + ";                                                                                                                                                           ";
            install(sql, pkgPackage, ddl);
        }
        String wkt2geom = this.parameterService.getTablePrefix() + "_" + "wkt2geom";
        if (!installed(SQL_OBJECT_INSTALLED, wkt2geom) && this.isSpatialTypesEnabled) {
            String sql = "  CREATE OR REPLACE FUNCTION $(functionName) (  \r\n"
                    + "        clob_in IN CLOB,                           \r\n"
                    + "        srid_in IN INTEGER)                        \r\n"
                    + "      RETURN SDO_GEOMETRY                          \r\n"
                    + "    AS                                             \r\n"
                    + "      v_out SDO_GEOMETRY := NULL;                  \r\n"
                    + "    BEGIN                                          \r\n"
                    + "      IF clob_in IS NOT NULL THEN                  \r\n"
                    + "        IF DBMS_LOB.GETLENGTH(clob_in) > 0 THEN    \r\n"
                    + "          v_out := SDO_GEOMETRY(clob_in, srid_in); \r\n"
                    + "        END IF;                                    \r\n"
                    + "      END IF;                                      \r\n"
                    + "      RETURN v_out;                                \r\n"
                    + "    END $(functionName);                           \r\n";
            install(sql, wkt2geom, ddl);
        }
    }

    @Override
    public boolean createOrAlterTablesIfNecessary(String... tableNames) {
        boolean isAltered = super.createOrAlterTablesIfNecessary(tableNames);
        boolean isNoOrder = parameterService.is(ParameterConstants.DBDIALECT_ORACLE_SEQUENCE_NOORDER, false);
        String seqName = getSequenceName(SequenceIdentifier.DATA).toUpperCase();
        String orderFlag = platform.getSqlTemplate().queryForString(
                "select order_flag from user_sequences where sequence_name = ?", seqName);
        String sql = null;
        if (orderFlag != null && orderFlag.equals("N") && !isNoOrder) {
            sql = "alter sequence " + seqName + " order";
        } else if (orderFlag != null && orderFlag.equals("Y") && isNoOrder) {
            sql = "alter sequence " + seqName + " noorder";
        }
        if (sql != null) {
            log.info("DDL applied: " + sql);
            platform.getSqlTemplate().update(sql);
        }
        return isAltered;
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String blobToClob = this.parameterService.getTablePrefix() + "_" + "blob2clob";
        if (installed(SQL_OBJECT_INSTALLED, blobToClob)) {
            uninstall(SQL_DROP_FUNCTION, blobToClob);
        }
        String transactionId = this.parameterService.getTablePrefix() + "_" + "transaction_id";
        if (installed(SQL_OBJECT_INSTALLED, transactionId)) {
            uninstall(SQL_DROP_FUNCTION, transactionId);
        }
        String triggerDisabled = this.parameterService.getTablePrefix() + "_" + "trigger_disabled";
        if (installed(SQL_OBJECT_INSTALLED, triggerDisabled)) {
            uninstall(SQL_DROP_FUNCTION, triggerDisabled);
        }
        String wkt2geom = this.parameterService.getTablePrefix() + "_" + "wkt2geom";
        if (installed(SQL_OBJECT_INSTALLED, wkt2geom)) {
            uninstall(SQL_DROP_FUNCTION, wkt2geom);
        }
        String pkgPackage = this.parameterService.getTablePrefix() + "_" + "pkg";
        if (installed(SQL_OBJECT_INSTALLED, pkgPackage)) {
            uninstall("DROP PACKAGE $(functionName)", pkgPackage);
        }
    }

    @Override
    public BinaryEncoding getBinaryEncoding() {
        return BinaryEncoding.BASE64;
    }

    @Override
    public String getTransactionTriggerExpression(String defaultCatalog, String defaultSchema,
            Trigger trigger) {
        return parameterService.getTablePrefix() + "_" + "transaction_id()";
    }

    @Override
    public String getTransactionId(ISqlTransaction transaction) {
        if (supportsTransactionId()) {
            List<String> list = transaction.query("select DBMS_TRANSACTION.local_transaction_id() from dual", new StringMapper(), null, null);
            if (list != null && list.size() > 0) {
                return list.get(0);
            }
        }
        return null;
    }

    @Override
    public boolean supportsTransactionId() {
        return true;
    }

    @Override
    public String getSequenceName(SequenceIdentifier identifier) {
        switch (identifier) {
            case REQUEST:
                return "SEQ_" + parameterService.getTablePrefix() + "_EXTRACT_EST_REQUEST_ID";
            case DATA:
                return "SEQ_" + parameterService.getTablePrefix() + "_DATA_DATA_ID";
            case TRIGGER_HIST:
                return "SEQ_" + parameterService.getTablePrefix() + "_TRIGGER_RIGGER_HIST_ID";
        }
        return null;
    }

    @Override
    public long getCurrentSequenceValue(SequenceIdentifier identifier) {
        long id = platform.getSqlTemplate().queryForLong("select last_number from user_sequences where sequence_name = ?",
                getSequenceName(identifier).toUpperCase());
        return id < 0 ? 0 : id;
    }

    public void cleanDatabase() {
        platform.getSqlTemplate().update("purge recyclebin");
    }

    protected String getSymmetricPackageName() {
        return parameterService.getTablePrefix() + "_pkg";
    }

    public void disableSyncTriggers(ISqlTransaction transaction, String nodeId) {
        transaction.prepareAndExecute(String.format("call %s.setValue(1)", getSymmetricPackageName()));
        if (nodeId != null) {
            transaction.prepareAndExecute(String.format("call %s.setNodeValue('" + nodeId + "')",
                    getSymmetricPackageName()));
        }
    }

    public void enableSyncTriggers(ISqlTransaction transaction) {
        transaction.prepareAndExecute(String.format("call %s.setValue(null)", getSymmetricPackageName()));
        transaction.prepareAndExecute(String.format("call %s.setNodeValue(null)", getSymmetricPackageName()));
    }

    public String getSyncTriggersExpression() {
        return parameterService.getTablePrefix() + "_trigger_disabled() is null";
    }

    public String getSyncTriggersOnIncomingExpression() {
        return "nvl(" + parameterService.getTablePrefix() + "_trigger_disabled(), 0) != 2";
    }

    @Override
    public final boolean areDatabaseTransactionsPendingSince(long time) {
        String returnValue = platform.getSqlTemplate().queryForObject(SQL_SELECT_TRANSACTIONS,
                String.class);
        if (returnValue != null) {
            Date date;
            try {
                date = DateUtils.parseDate(returnValue, new String[] { "MM/dd/yy HH:mm:ss" });
                return date.getTime() < time;
            } catch (ParseException e) {
                log.error("Could not parse the pending transactions date", e);
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public Date getEarliestTransactionStartTime() {
        Date date = null;
        String returnValue = platform.getSqlTemplate().queryForObject(SQL_SELECT_TRANSACTIONS, String.class);
        if (returnValue != null) {
            try {
                date = DateUtils.parseDate(returnValue, new String[] { "MM/dd/yy HH:mm:ss" });
            } catch (ParseException e) {
                log.error("", e);
                date = new Date();
            }
        }
        return date;
    }

    @Override
    public String massageDataExtractionSql(String sql, boolean isContainsBigLob) {
        if (!isContainsBigLob) {
            sql = Strings.CS.replace(sql, "d.row_data", "dbms_lob.substr(d.row_data, 4000, 1 )");
            sql = Strings.CS.replace(sql, "d.old_data", "dbms_lob.substr(d.old_data, 4000, 1 )");
            sql = Strings.CS.replace(sql, "d.pk_data", "dbms_lob.substr(d.pk_data, 4000, 1 )");
        }
        sql = super.massageDataExtractionSql(sql, isContainsBigLob);
        return sql;
    }

    @Override
    public String massageForLob(String sql, boolean isContainsBigLob) {
        if (!isContainsBigLob) {
            return String.format("dbms_lob.substr(%s, 4000, 1)", sql);
        } else {
            return super.massageForLob(sql, isContainsBigLob);
        }
    }

    @Override
    public boolean isInitialLoadTwoPassLob(Table table) {
        boolean initialLoadTwoPassLob = false;
        if (parameterService.is(ParameterConstants.INITIAL_LOAD_EXTRACT_USE_TWO_PASS_LOB)
                && !TableConstants.getTables(parameterService.getTablePrefix()).contains(table.getNameLowerCase())) {
            for (Column column : table.getLobColumns(this.platform)) {
                if (isInitialLoadTwoPassLobColumn(column)) {
                    initialLoadTwoPassLob = true;
                    break;
                }
            }
        }
        return initialLoadTwoPassLob;
    }

    private boolean isInitialLoadTwoPassLobColumn(Column column) {
        String typeName = column.getJdbcTypeName();
        boolean isLob = typeName.equalsIgnoreCase("CLOB") || typeName.equalsIgnoreCase("BLOB");
        boolean isVariableBinary = typeName.equalsIgnoreCase("BINARY") || typeName.equalsIgnoreCase("VARBINARY");
        return isLob || (isVariableBinary && column.getSizeAsInt() > 4000);
    }

    @Override
    public String getInitialLoadTwoPassLobLengthSql(Column column, boolean isFirstPass) {
        String quote = this.platform.getDdlBuilder().getDatabaseInfo().getDelimiterToken();
        if (!isInitialLoadTwoPassLobColumn(column)) {
            return null;
        } else if (isFirstPass) {
            return "(t." + quote + column.getName() + quote + " is null or " +
                    "dbms_lob.getlength(t." + quote + column.getName() + quote + ") <= 4000)";
        } else if (parameterService.is(ParameterConstants.EXTRACT_CHECK_ROW_SIZE, false)) {
            return "dbms_lob.getlength(t." + quote + column.getName() + quote + ") between 4001 and " + parameterService.getLong(
                    ParameterConstants.EXTRACT_ROW_MAX_LENGTH, 1000000000);
        }
        return "dbms_lob.getlength(t." + quote + column.getName() + quote + ") > 4000";
    }

    @Override
    protected String getDbSpecificDataHasChangedCondition(Trigger trigger) {
        if (!trigger.isUseCaptureLobs()) {
            return "var_old_data is null or var_row_data != var_old_data";
        } else {
            return "dbms_lob.compare(nvl(var_row_data,'Null'),nvl(var_old_data,'Null')) != 0 ";
        }
    }

    public String getTemplateNumberPrecisionSpec() {
        return parameterService.getString(ParameterConstants.DBDIALECT_ORACLE_TEMPLATE_NUMBER_SPEC, "30,10");
    }

    @Override
    public PermissionType[] getSymTablePermissions() {
        PermissionType[] permissions = { PermissionType.CREATE_TABLE, PermissionType.DROP_TABLE, PermissionType.CREATE_TRIGGER, PermissionType.DROP_TRIGGER,
                PermissionType.EXECUTE };
        return permissions;
    }

    @Override
    public String getDatabaseTimeSQL() {
        return "select current_timestamp from dual";
    }
}
