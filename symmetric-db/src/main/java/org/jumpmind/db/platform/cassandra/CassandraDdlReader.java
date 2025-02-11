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
package org.jumpmind.db.platform.cassandra;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.PlatformTrigger;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.db.util.TableRow;

public class CassandraDdlReader implements IDdlReader {
    protected CassandraPlatform platform;

    public CassandraDdlReader(CassandraPlatform platform) {
        this.platform = platform;
    }

    @Override
    public Database readTables(String catalog, String schema, String[] tableTypes) {
        return null;
    }

    @Override
    public Table readTable(String catalog, String schema, String tableName) {
        Map<String, Table> tables = platform.getMetaData()
                .get(catalog == null ? schema : catalog);
        return tables.get(tableName.toLowerCase());
    }

    @Override
    public Table readTable(ISqlTransaction transaction, String catalog, String schema, String table) {
        return readTable(catalog, schema, table);
    }

    @Override
    public List<String> getTableTypes() {
        return null;
    }

    @Override
    public List<String> getCatalogNames() {
        return null;
    }

    @Override
    public List<String> getSchemaNames(String catalog) {
        return null;
    }

    @Override
    public List<String> getTableNames(String catalog, String schema, String[] tableTypes) {
        return null;
    }

    @Override
    public List<String> getColumnNames(String catalog, String schema, String tableName) {
        return null;
    }

    @Override
    public List<Trigger> getTriggers(String catalog, String schema, String tableName) {
        return null;
    }

    @Override
    public Trigger getTriggerFor(Table table, String name) {
        return null;
    }

    @Override
    public Collection<ForeignKey> getExportedKeys(Table table) {
        return null;
    }

    @Override
    public List<TableRow> getExportedForeignTableRows(ISqlTransaction transaction, List<TableRow> tableRows, Set<TableRow> visited, BinaryEncoding encoding) {
        return null;
    }

    @Override
    public List<TableRow> getImportedForeignTableRows(List<TableRow> tableRows, Set<TableRow> visited, BinaryEncoding encoding) {
        return null;
    }

    @Override
    public List<Trigger> getApplicationTriggersForModel(String catalog, String schema, String tableName, String triggerPrefix) {
        return new ArrayList<Trigger>(0);
    }

    @Override
    public PlatformTrigger getPlatformTrigger(IDatabasePlatform platform, Trigger trigger) {
        return new PlatformTrigger(platform.getName(), trigger.getSource());
    }
}
