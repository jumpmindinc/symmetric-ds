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
package org.jumpmind.db.platform.generic;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;

public class GenericJdbcSqlDdlReader extends AbstractJdbcDdlReader {
    public GenericJdbcSqlDdlReader(IDatabasePlatform platform) {
        super(platform);
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        return "PRIMARY".equals(index.getName());
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        try {
            return super.readRelation(connection, metaData, values);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Relation readRelation(String catalog, String schema, String table) {
        try {
            return super.readRelation(catalog, schema, table);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Database readRelations(String catalog, String schema, String[] relationTypes) {
        try {
            return super.readRelations(catalog, schema, relationTypes);
        } catch (Exception e) {
            return null;
        }
    }
}
