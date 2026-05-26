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
package org.jumpmind.db.platform.voltdb;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;

public class VoltDbDdlReader extends AbstractJdbcDdlReader {
    public final static String VOLT_DB_SYSTEM_INDEX_PREFIX = "VOLTDB_AUTOGEN_IDX_";

    public VoltDbDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern(null);
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) throws SQLException {
        return true;
    }

    @Override
    protected void removeSystemIndices(Connection connection, DatabaseMetaDataWrapper metaData,
            Table table) throws SQLException {
        super.removeSystemIndices(connection, metaData, table);
        for (int indexIdx = 0; indexIdx < table.getIndexCount();) {
            IIndex index = table.getIndex(indexIdx);
            if (index.getName() != null && index.getName().startsWith(VOLT_DB_SYSTEM_INDEX_PREFIX)) {
                table.removeIndex(indexIdx);
            } else {
                indexIdx++;
            }
        }
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        Relation relation = super.readRelation(connection, metaData, values);
        disableAutoIncrement(relation);
        fixIndexKeyOrder(relation);
        return relation;
    }

    protected void fixIndexKeyOrder(Relation relation) {
    }

    protected void disableAutoIncrement(Relation relation) {
        for (Column column : relation.getColumns()) {
            column.setAutoIncrement(false);
        }
    }

    @Override
    protected void readForeignKeyUpdateRule(Map<String, Object> values, ForeignKey fk) {
        // VoltDB does not support cascade actions
        fk.setOnUpdateAction(ForeignKeyAction.NOACTION);
    }

    @Override
    protected void readForeignKeyDeleteRule(Map<String, Object> values, ForeignKey fk) {
        // VoltDB does not support cascade actions
        fk.setOnDeleteAction(ForeignKeyAction.NOACTION);
    }
}
