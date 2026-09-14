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
package org.jumpmind.symmetric.load;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterFilterAdapter;

/**
 * An extension that prefixes the table name with a schema name that is equal to the incoming node_id.
 */
public class SchemaPerNodeDataLoaderFilter extends DatabaseWriterFilterAdapter {
    private String tablePrefix;
    private String schemaPrefix;

    @Override
    public boolean beforeWrite(
            DataContext context, Table table, CsvData data) {
        if (!table.getName().startsWith(tablePrefix)) {
            Batch batch = context.getBatch();
            String sourceNodeId = batch.getSourceNodeId();
            table.setSchema(schemaPrefix != null ? schemaPrefix + sourceNodeId : sourceNodeId);
        }
        return true;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public void setSchemaPrefix(String schemaPrefix) {
        this.schemaPrefix = schemaPrefix;
    }
}