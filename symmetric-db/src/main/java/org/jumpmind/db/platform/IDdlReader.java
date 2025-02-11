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
package org.jumpmind.db.platform;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.PlatformTrigger;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.db.util.TableRow;

public interface IDdlReader {
    public Database readTables(String catalog, String schema, String[] tableTypes);

    public Table readTable(String catalog, String schema, String tableName);

    public Table readTable(ISqlTransaction transaction, String catalog, String schema, String table);

    public List<String> getTableTypes();

    public List<String> getCatalogNames();

    public List<String> getSchemaNames(String catalog);

    public List<String> getTableNames(String catalog, String schema, String[] tableTypes);

    public List<String> getColumnNames(String catalog, String schema, String tableName);

    public List<Trigger> getTriggers(String catalog, String schema, String tableName);

    public List<Trigger> getApplicationTriggersForModel(String catalog, String schema, String tableName, String triggerPrefix);

    public Trigger getTriggerFor(Table table, String name);

    public PlatformTrigger getPlatformTrigger(IDatabasePlatform platform, Trigger trigger);

    public Collection<ForeignKey> getExportedKeys(Table table);

    public List<TableRow> getExportedForeignTableRows(ISqlTransaction transaction, List<TableRow> tableRows, Set<TableRow> visited, BinaryEncoding encoding);

    public List<TableRow> getImportedForeignTableRows(List<TableRow> tableRows, Set<TableRow> visited, BinaryEncoding encoding);
}
