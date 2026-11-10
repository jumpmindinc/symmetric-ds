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
package org.jumpmind.db.platform.clickhouse;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;

/**
 * Reads ClickHouse metadata through JDBC. The driver reports the database as the schema, and column type names include wrappers such as
 * <code>Nullable(Decimal(10, 2))</code> that are removed here.
 */
public class ClickHouseDdlReader extends AbstractJdbcDdlReader {
    private static final String[] WRAPPERS = { "Nullable", "LowCardinality" };

    public ClickHouseDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern(null);
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        Column column = super.readColumn(metaData, values);
        if (ClickHouseDdlBuilder.BINARY_COLUMN_COMMENT.equals(values.get(getName("REMARKS")))) {
            // The column was created for binary data, which ClickHouse stores in a String
            column.setMappedTypeCode(Types.VARBINARY);
            column.setJdbcTypeCode(Types.VARBINARY);
        }
        readDefault(column);
        String typeName = stripWrappers(column.getJdbcTypeName());
        column.setJdbcTypeName(typeName);
        PlatformColumn platformColumn = column.findPlatformColumn(platform.getName());
        if (platformColumn != null) {
            platformColumn.setType(baseTypeName(typeName));
        }
        return column;
    }

    /**
     * A <code>generateSerialID</code> default is turned back into an auto-increment column, and an empty default is dropped because the driver reports it the
     * same as no default.
     */
    protected void readDefault(Column column) {
        String value = column.getDefaultValue();
        if (StringUtils.isEmpty(value)) {
            column.setDefaultValue(null);
        } else if (value.startsWith(ClickHouseDdlBuilder.SERIAL_FUNCTION + "(")) {
            column.setAutoIncrement(true);
            column.setDefaultValue(null);
        }
    }

    /**
     * The base reader removes the quotes around a text or date default, which leaves the ClickHouse escapes in place. They are removed here and the value is
     * quoted again as a SQL literal.
     */
    @Override
    protected Relation postprocessRelationFromDatabase(Relation relation) {
        Relation result = super.postprocessRelationFromDatabase(relation);
        if (result != null) {
            for (int i = 0; i < result.getColumnCount(); i++) {
                Column column = result.getColumn(i);
                String value = column.getDefaultValue();
                int type = column.getMappedTypeCode();
                if (value != null && (TypeMap.isTextType(type) || TypeMap.isDateTimeType(type))) {
                    column.setDefaultValue("'" + unescape(value).replace("'", "''") + "'");
                }
            }
        }
        return result;
    }

    public static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n':
                        result.append('\n');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 'b':
                        result.append('\b');
                        break;
                    case 'f':
                        result.append('\f');
                        break;
                    case '0':
                        result.append('\0');
                        break;
                    default:
                        result.append(next);
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * The driver reports the key of a table declared as <code>ORDER BY (id)</code> as "(id)", so the wrapping is removed to get the column name.
     */
    @Override
    protected String readPrimaryKeyName(DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        return cleanKeyName(super.readPrimaryKeyName(metaData, values));
    }

    public static String cleanKeyName(String name) {
        if (name == null) {
            return null;
        }
        String result = name.trim();
        while (result.length() > 1 && result.startsWith("(") && result.endsWith(")")) {
            result = result.substring(1, result.length() - 1).trim();
        }
        if (result.length() > 1 && (result.startsWith("`") && result.endsWith("`") || result.startsWith("\"") && result.endsWith("\""))) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    public static String stripWrappers(String typeName) {
        String result = typeName;
        boolean isStripped = true;
        while (result != null && isStripped) {
            isStripped = false;
            for (String wrapper : WRAPPERS) {
                if (result.startsWith(wrapper + "(") && result.endsWith(")")) {
                    result = result.substring(wrapper.length() + 1, result.length() - 1).trim();
                    isStripped = true;
                }
            }
        }
        return result;
    }

    public static String baseTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        int paren = typeName.indexOf('(');
        return paren > 0 ? typeName.substring(0, paren).trim() : typeName;
    }
}
