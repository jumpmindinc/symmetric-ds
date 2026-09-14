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
package org.jumpmind.symmetric.model;

import static org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus.ERROR;
import static org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus.EXTRACTING;
import static org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus.LOADING;
import static org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus.OK;
import static org.jumpmind.symmetric.model.ProcessType.PULL_HANDLER_EXTRACT;
import static org.jumpmind.symmetric.model.ProcessType.PUSH_JOB_EXTRACT;
import static org.jumpmind.symmetric.model.ProcessType.INITIAL_LOAD_EXTRACT_JOB;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.NestedDataWriter;

public class ProcessInfoDataWriter extends NestedDataWriter {
    private ProcessInfo processInfo;
    private static Class<?> abstractBulkDatabaseWriter;
    static {
        try {
            abstractBulkDatabaseWriter = Class.forName("org.jumpmind.symmetric.io.AbstractBulkDatabaseWriter");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ProcessInfoDataWriter(IDataWriter targetWriter, ProcessInfo processInfo) {
        super(targetWriter);
        this.processInfo = processInfo;
    }

    public void open(DataContext context) {
        super.open(context);
        processInfo.setTotalBatchCount(0);
    }

    public void start(Batch batch) {
        if (batch != null) {
            ProcessType type = processInfo.getProcessType();
            if (type == PULL_HANDLER_EXTRACT || type == PUSH_JOB_EXTRACT || type == INITIAL_LOAD_EXTRACT_JOB) {
                processInfo.setStatus(EXTRACTING);
            } else {
                processInfo.setStatus(LOADING);
            }
            processInfo.setCurrentBatchId(batch.getBatchId());
            processInfo.setCurrentChannelId(batch.getChannelId());
            processInfo.incrementBatchCount();
            processInfo.setCurrentDataCount(0);
            if (abstractBulkDatabaseWriter != null
                    && abstractBulkDatabaseWriter.isAssignableFrom(nestedWriter.getClass())) {
                processInfo.setBulkLoadFlag(true);
            } else {
                processInfo.setBulkLoadFlag(false);
            }
        }
        super.start(batch);
    }

    public boolean start(Table table) {
        if (table != null) {
            processInfo.setCurrentTableName(table.getFullyQualifiedName());
        }
        return super.start(table);
    }

    @Override
    public void end(Batch batch, boolean inError) {
        processInfo.setStatus(!inError ? OK : ERROR);
        super.end(batch, inError);
    }

    public void write(CsvData data) {
        if (data != null) {
            processInfo.incrementCurrentDataCount();
        }
        super.write(data);
    }
}
