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
package org.jumpmind.db.platform.sqlite;

import java.sql.Connection;
import java.sql.Types;
import java.util.Map;

import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

public class SqliteDdlBuilder extends AbstractDdlBuilder {
    public SqliteDdlBuilder() {
        super(DatabaseNamesConstants.SQLITE);
        databaseInfo.setMinIsolationLevelToPreventPhantomReads(Connection.TRANSACTION_SERIALIZABLE);
        databaseInfo.setPrimaryKeyEmbedded(true);
        databaseInfo.setNonPKIdentityColumnsSupported(false);
        databaseInfo.setIdentityOverrideAllowed(false);
        databaseInfo.setSystemForeignKeyIndicesAlwaysNonUnique(true);
        databaseInfo.setNullAsDefaultValueRequired(false);
        databaseInfo.setRequiresAutoCommitForDdl(true);
        databaseInfo.addNativeTypeMapping(Types.ARRAY, "BINARY", Types.BINARY);
        databaseInfo.addNativeTypeMapping(Types.CHAR, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.BIGINT, "INTEGER", Types.INTEGER);
        databaseInfo.addNativeTypeMapping(Types.DISTINCT, "BINARY", Types.BINARY);
        databaseInfo.addNativeTypeMapping(Types.NULL, "BINARY", Types.BINARY);
        databaseInfo.addNativeTypeMapping(Types.REF, "BINARY", Types.BINARY);
        databaseInfo.addNativeTypeMapping(Types.STRUCT, "BINARY", Types.BINARY);
        databaseInfo.addNativeTypeMapping(Types.DATALINK, "BINARY", Types.BINARY);
        databaseInfo.addNativeTypeMapping(Types.TIMESTAMP, "TIMESTAMP", Types.TIMESTAMP);
        databaseInfo.addNativeTypeMapping(Types.TIME, "TIME", Types.TIME);
        databaseInfo.addNativeTypeMapping(Types.DATE, "DATETIME", Types.DATE);
        databaseInfo.addNativeTypeMapping(Types.BIT, "INTEGER", Types.INTEGER);
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "INTEGER", Types.INTEGER);
        databaseInfo.addNativeTypeMapping(Types.SMALLINT, "INTEGER", Types.INTEGER);
        databaseInfo.addNativeTypeMapping(Types.BINARY, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.BLOB, "BLOB", Types.BLOB);
        databaseInfo.addNativeTypeMapping(Types.CLOB, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "VARCHAR", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "FLOAT", Types.FLOAT);
        databaseInfo.addNativeTypeMapping(Types.JAVA_OBJECT, "OTHER");
        databaseInfo.setDefaultSize(Types.CHAR, Integer.MAX_VALUE);
        databaseInfo.setDefaultSize(Types.VARCHAR, Integer.MAX_VALUE);
        databaseInfo.setDefaultSize(Types.BINARY, Integer.MAX_VALUE);
        databaseInfo.setDefaultSize(Types.VARBINARY, Integer.MAX_VALUE);
        databaseInfo.setHasSize(Types.CHAR, false);
        databaseInfo.setHasSize(Types.VARCHAR, false);
        databaseInfo.setHasSize(Types.BINARY, false);
        databaseInfo.setHasSize(Types.VARBINARY, false);
        databaseInfo.setHasPrecisionAndScale(Types.DECIMAL, false);
        databaseInfo.setHasPrecisionAndScale(Types.NUMERIC, false);
        databaseInfo.setDateOverridesToTimestamp(false);
        databaseInfo.setEmptyStringNulled(false);
        databaseInfo.setBlankCharColumnSpacePadded(false);
        databaseInfo.setNonBlankCharColumnSpacePadded(false);
        databaseInfo.setForeignKeysSupported(true);
        databaseInfo.setForeignKeysEmbedded(true);
        databaseInfo.setEmbeddedForeignKeysNamed(false);
    }

    @Override
    protected void writeExternalForeignKeyCreateStmt(Database database, Table table,
            ForeignKey key, StringBuilder ddl) {
    }

    @Override
    protected void writeExternalForeignKeyDropStmt(Table table, ForeignKey foreignKey,
            StringBuilder ddl) {
    }

    @Override
    public void writeExternalIndexDropStmt(Table table, IIndex index, StringBuilder ddl) {
        ddl.append("DROP INDEX ");
        printIdentifier(getIndexName(index), ddl);
        printEndOfStatement(ddl);
    }

    /**
     * Prints that the column is an auto increment column.
     */
    @Override
    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
        ddl.append("AUTOINCREMENT");
    }

    @Override
    protected void writeColumnEmbeddedPrimaryKey(Table table, Column column, StringBuilder ddl) {
        if (table.getPrimaryKeyColumnCount() == 1) {
            ddl.append(" PRIMARY KEY ");
        }
    }

    @Override
    protected void writeEmbeddedPrimaryKeysStmt(Table table, StringBuilder ddl) {
        if (table.getPrimaryKeyColumnCount() > 1) {
            super.writeEmbeddedPrimaryKeysStmt(table, ddl);
        }
    }

    @Override
    protected void dropTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        ddl.append("DROP TABLE IF EXISTS ");
        ddl.append(getFullyQualifiedTableNameShorten(table));
        printEndOfStatement(ddl);
    }

    @Override
    public String getIndexName(IIndex index) {
        return super.getIndexName(index).replace("-", "_");
    }

    @Override
    public String mapDefaultValue(Object defaultValue, Column column) {
        if (defaultValue != null) {
            String defaultValueStr = defaultValue.toString();
            Map<String, String> defaultValuesToTranslate = databaseInfo.getDefaultValuesToTranslate();
            if (defaultValuesToTranslate.containsKey(defaultValueStr)) {
                return defaultValuesToTranslate.get(defaultValueStr);
            }
            if (TypeMap.isDateTimeType(column.getMappedTypeCode())) {
                if (defaultValueStr.toUpperCase().startsWith("SYSDATE")
                        || defaultValueStr.toUpperCase().startsWith("CURRENT_DATE")) {
                    return "CURRENT_DATE";
                } else if (defaultValueStr.toUpperCase().startsWith("SYSTIMESTAMP")
                        || defaultValueStr.toUpperCase().startsWith("CURRENT_TIMESTAMP")) {
                    return "CURRENT_TIMESTAMP";
                } else if (defaultValueStr.toUpperCase().startsWith("SYSTIME")
                        || defaultValueStr.toUpperCase().startsWith("CURRENT_TIME")) {
                    return "CURRENT_TIME";
                } else if (defaultValueStr.contains("('")) {
                    int beginIndex = defaultValueStr.indexOf("('");
                    int lastIndex = defaultValueStr.lastIndexOf(")");
                    if (lastIndex > beginIndex) {
                        return defaultValueStr.substring(beginIndex, lastIndex);
                    } else {
                        return defaultValueStr.substring(beginIndex);
                    }
                }
            } else {
                if (defaultValueStr.toUpperCase().startsWith("NEWID(")) {
                    return "HEX(RANDOMBLOB(4)) || '-' || HEX(RANDOMBLOB(2)) || '-' || HEX(RANDOMBLOB(2)) || '-' || HEX(RANDOMBLOB(2)) || '-'"
                            + " || HEX(RANDOMBLOB(6))";
                } else if (defaultValueStr.toUpperCase().startsWith("NEWSEQUENTIALID(")) {
                    return "HEX(RANDOMBLOB(4)) || '-' || HEX(RANDOMBLOB(2)) || '-' || HEX(RANDOMBLOB(2)) || '-' || HEX(RANDOMBLOB(2)) || '-'"
                            + " || SUBSTR(PRINTF('%014X', (STRFTIME('%s', 'now') * 1000 + SUBSTR(STRFTIME('%f','now'), 4, 3))), 3, 12)";
                }
            }
        }
        return super.mapDefaultValue(defaultValue, column);
    }

    @Override
    protected void writeColumnDefaultValueStmt(Table table, Column column, StringBuilder ddl) {
        if (!Strings.CI.contains(getNativeDefaultValue(column), "NEXT VALUE FOR")) {
            super.writeColumnDefaultValueStmt(table, column, ddl);
        }
    }

    @Override
    protected void writeColumnDefaultValue(Table table, Column column, StringBuilder ddl) {
        ddl.append("(");
        super.writeColumnDefaultValue(table, column, ddl);
        ddl.append(")");
    }

    @Override
    protected boolean shouldUseQuotes(String defaultValue, Column column) {
        String defaultValueStr = mapDefaultValue(defaultValue, column);
        while (defaultValueStr != null && defaultValueStr.startsWith("(") && defaultValueStr.endsWith(")")) {
            defaultValueStr = defaultValueStr.substring(1, defaultValueStr.length() - 1);
        }
        return super.shouldUseQuotes(defaultValue, column) && !defaultValueStr.trim().toUpperCase().startsWith("HEX(RANDOMBLOB(");
    }

    @Override
    protected void createTable(Table table, StringBuilder ddl, boolean temporary, boolean recreate) {
        // SQL Lite does not allow auto increment columns on a composite primary key. Solution is to turn off
        // Auto increment and still support composite key
        if (table.getPrimaryKeyColumnCount() > 1 && table.hasAutoIncrementColumn()) {
            for (Column column : table.getColumns()) {
                column.setAutoIncrement(false);
            }
        }
        super.createTable(table, ddl, temporary, recreate);
    }
}
