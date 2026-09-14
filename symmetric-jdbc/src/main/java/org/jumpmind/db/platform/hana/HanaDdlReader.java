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
package org.jumpmind.db.platform.hana;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.Row;

public class HanaDdlReader extends AbstractJdbcDdlReader implements IDdlReader {
    protected static final List<String> SUPPORTED_AUTO_INCREMENT_CLAUSES = List.of("BY DEFAULT AS IDENTITY", "ALWAYS AS IDENTITY");

    public HanaDdlReader(IDatabasePlatform platform) {
        super(platform);
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        Relation relation = super.readRelation(connection, metaData, values);
        if (relation != null) {
            determineExtraColumnInfo(relation);
        }
        return relation;
    }

    protected void determineExtraColumnInfo(Relation relation) {
        String sql = "SELECT column_name, generation_type FROM sys.table_columns WHERE schema_name = ? AND table_name = ?";
        List<Row> rows = platform.getSqlTemplateDirty().query(sql, new Object[] { relation.getSchema(), relation.getName() });
        for (Row row : rows) {
            String columnName = row.getString("column_name");
            String generationType = row.getString("generation_type");
            if (StringUtils.isNotBlank(generationType)) {
                Column column = relation.findColumn(columnName);
                if (column != null) {
                    if (SUPPORTED_AUTO_INCREMENT_CLAUSES.stream().anyMatch(clause -> clause.equalsIgnoreCase(generationType))) {
                        log.info("Setting auto-increment to true for column: {}, on table: {}", column.getName(), relation.getName());
                        column.setAutoIncrement(true);
                        log.info("Setting generated to false to match schema for table: {}, column: {}", relation.getName(), column.getName());
                        column.setGenerated(false);
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            log.warn("Could not find extra column info for table {}", relation.getFullyQualifiedName());
        }
    }
}
