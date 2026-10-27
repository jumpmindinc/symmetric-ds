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
package org.jumpmind.db.platform.duckdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;

public class DuckDbDdlReader extends AbstractJdbcDdlReader {
    public DuckDbDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
    }

    @Override
    public Collection<IIndex> readIndices(Connection connection, DatabaseMetaDataWrapper metaData,
            String tableName) throws SQLException {
        Map<String, IIndex> indices = new ListOrderedMap<String, IIndex>();
        StringBuilder query = new StringBuilder();
        query.append("select index_name as INDEX_NAME, table_name as TABLE_NAME, is_unique as NON_UNIQUE, ");
        query.append("unnest(expressions::varchar[]) as COLUMN_NAME, ");
        query.append("generate_subscripts(expressions::varchar[], 1) as ORDINAL_POSITION ");
        query.append("from duckdb_indexes() where table_name = ? and schema_name = ?");
        try (PreparedStatement stmt = connection.prepareStatement(query.toString())) {
            stmt.setString(1, tableName);
            stmt.setString(2, metaData.getSchemaPattern());
            try (ResultSet indexData = stmt.executeQuery()) {
                while (indexData.next()) {
                    Map<String, Object> values = readMetaData(indexData, getColumnsForIndex());
                    // we have to reverse the meaning of the unique flag
                    values.put("NON_UNIQUE",
                            Boolean.FALSE.equals(values.get("NON_UNIQUE")) ? Boolean.TRUE
                                    : Boolean.FALSE);
                    // and trim the names
                    values.put("INDEX_NAME", ((String) values.get("INDEX_NAME")).trim());
                    values.put("TABLE_NAME", ((String) values.get("TABLE_NAME")).trim());
                    values.put("COLUMN_NAME", ((String) values.get("COLUMN_NAME")).trim());
                    readIndex(metaData, values, indices);
                }
            }
        }
        return indices.values();
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        Column column = super.readColumn(metaData, values);
        String defaultValue = column.getDefaultValue();
        if ((defaultValue != null) && (defaultValue.startsWith("nextval("))) {
            column.setAutoIncrement(true);
            defaultValue = null;
            column.setDefaultValue(defaultValue);
        }
        return column;
    }
}
