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
package org.jumpmind.symmetric.io.data.writer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.util.Statistics;

public class MockDataWriter implements IDataWriter {
    protected boolean closeCalled = false;
    protected Map<String, List<CsvData>> writtenDatas = new HashMap<String, List<CsvData>>();
    protected Table currentTable;
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();

    public void open(DataContext context) {
    }

    public void close() {
        closeCalled = true;
    }

    public void start(Batch batch) {
        statistics.put(batch, new Statistics());
    }

    public boolean start(Table table) {
        this.currentTable = table;
        if (!writtenDatas.containsKey(table.getFullyQualifiedName())) {
            writtenDatas.put(table.getFullyQualifiedName(), new ArrayList<CsvData>());
        }
        return true;
    }

    public void write(CsvData data) {
        writtenDatas.get(this.currentTable.getFullyQualifiedName()).add(data);
    }

    public void end(Table table) {
    }

    public void end(Batch batch, boolean inError) {
    }

    protected void reset() {
        writtenDatas.clear();
    }

    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }
}
