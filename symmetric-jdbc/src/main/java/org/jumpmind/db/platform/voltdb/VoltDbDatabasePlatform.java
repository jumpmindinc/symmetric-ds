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
package org.jumpmind.db.platform.voltdb;

import java.io.InputStream;
import java.sql.Types;

import javax.sql.DataSource;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.jumpmind.db.util.BinaryEncoding;

public class VoltDbDatabasePlatform extends AbstractJdbcDatabasePlatform {
    public VoltDbDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        getDatabaseInfo().setRequiresAutoCommitForDdl(true);
        getDatabaseInfo().setDelimiterToken("");
        getDatabaseInfo().setDelimitedIdentifiersSupported(false);
        getDatabaseInfo().setTriggersSupported(false);
        getDatabaseInfo().setForeignKeysSupported(false);
        getDatabaseInfo().setHasPrecisionAndScale(Types.DECIMAL, false);
        getDatabaseInfo().setHasPrecisionAndScale(Types.FLOAT, false);
    }

    public static final String JDBC_DRIVER = "org.voltdb.jdbc.Driver";
    public static final String JDBC_SUBPROTOCOL = "voltdb";

    @Override
    public String getName() {
        return DatabaseNamesConstants.VOLTDB;
    }

    @Override
    public String getClassName() {
        return VoltDbDatabasePlatform.class.getName();
    }

    @Override
    public Database readDatabaseFromXml(InputStream is, boolean alterCaseToMatchDatabaseDefaultCase) {
        Database database = super.readDatabaseFromXml(is, alterCaseToMatchDatabaseDefaultCase);
        for (Table table : database.getTables()) {
            for (Column column : table.getColumns())
                column.setAutoIncrement(false);
        }
        return database;
    }

    @Override
    protected Object getObjectValue(String value, Column column, BinaryEncoding encoding,
            boolean useVariableDates, boolean fitToColumn) throws DecoderException {
        Object objectValue = super.getObjectValue(value, column, encoding, useVariableDates, fitToColumn);
        if (objectValue instanceof byte[]
                && (column.getJdbcTypeCode() == Types.VARBINARY
                        || column.getJdbcTypeCode() == Types.CLOB)) {
            objectValue = new String(Hex.encodeHex((byte[]) objectValue));
        }
        return objectValue;
    }

    @Override
    public String getDefaultSchema() {
        return null;
    }

    @Override
    public String getDefaultCatalog() {
        return defaultCatalog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getDataSource() {
        return (T) dataSource;
    }

    @Override
    public ISqlTemplate getSqlTemplate() {
        return sqlTemplate;
    }

    @Override
    protected VoltDbDdlBuilder createDdlBuilder() {
        return new VoltDbDdlBuilder();
    }

    @Override
    protected VoltDbDdlReader createDdlReader() {
        return new VoltDbDdlReader(this);
    }

    @Override
    protected VoltDbJdbcSqlTemplate createSqlTemplate() {
        return new VoltDbJdbcSqlTemplate(dataSource, settings, new SymmetricLobHandler(), getDatabaseInfo());
    }

    @Override
    public PermissionResult getCreateSymTablePermission(Database database) {
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TABLE, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    public PermissionResult getDropSymTablePermission() {
        PermissionResult result = new PermissionResult(PermissionType.DROP_TABLE, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    public PermissionResult getAlterSymTablePermission(Database database) {
        PermissionResult result = new PermissionResult(PermissionType.ALTER_TABLE, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    public PermissionResult getDropSymTriggerPermission() {
        PermissionResult result = new PermissionResult(PermissionType.DROP_TRIGGER, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    protected ISqlTemplate createSqlTemplateDirty() {
        return sqlTemplate;
    }

    @Override
    public boolean supportsLimitOffset() {
        return true;
    }

    @Override
    public String massageForLimitOffset(String sql, int limit, int offset) {
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql + " limit " + limit + " offset " + offset;
    }
}
