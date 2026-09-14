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

import java.util.Map;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.util.Statistics;

public class NestedDataWriter implements IDataWriter {
    protected IDataWriter nestedWriter;
    protected DataContext context;

    public NestedDataWriter(IDataWriter nestedWriter) {
        this.nestedWriter = nestedWriter;
    }

    public void open(DataContext context) {
        this.context = context;
        this.nestedWriter.open(context);
    }

    public void close() {
        this.nestedWriter.close();
    }

    public Map<Batch, Statistics> getStatistics() {
        return this.nestedWriter.getStatistics();
    }

    public void start(Batch batch) {
        this.nestedWriter.start(batch);
    }

    public boolean start(Table table) {
        return this.nestedWriter.start(table);
    }

    public void write(CsvData data) {
        this.nestedWriter.write(data);
    }

    public void end(Table table) {
        this.nestedWriter.end(table);
    }

    public void end(Batch batch, boolean inError) {
        this.nestedWriter.end(batch, inError);
    }

    public void setNestedWriter(IDataWriter nestedWriter) {
        this.nestedWriter = nestedWriter;
    }

    public IDataWriter getNestedWriter() {
        return nestedWriter;
    }

    @SuppressWarnings("unchecked")
    public <T extends IDataWriter> T getNestedWriterOfType(Class<?> clazz) {
        IDataWriter writer = this;
        while (writer != null && !clazz.isInstance(writer) && writer instanceof NestedDataWriter) {
            writer = ((NestedDataWriter) writer).getNestedWriter();
        }
        if (writer != null && clazz.isInstance(writer)) {
            return (T) writer;
        } else {
            return null;
        }
    }
}
