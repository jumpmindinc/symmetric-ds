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
package org.jumpmind.symmetric.io;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.writer.Conflict;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterSettings;

public class JdbcBatchBulkDatabaseWriter extends AbstractBulkDatabaseWriter {
    private int lastRowCount = 0;
    private int expectedRowCount = 0;

    public JdbcBatchBulkDatabaseWriter(IDatabasePlatform symmetricPlatform, IDatabasePlatform targetPlatform,
            String tablePrefix, DatabaseWriterSettings writerSettings) {
        super(symmetricPlatform, targetPlatform, tablePrefix, writerSettings);
    }

    @Override
    public void start(Batch batch) {
        super.start(batch);
        if (context.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE) == null || !context.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE).equals("default")) {
            getTransaction().setInBatchMode(true);
            ((JdbcSqlTransaction) getTransaction()).setBatchSize(((JdbcSqlTemplate) getPlatform()
                    .getSqlTemplate()).getSettings().getBatchBulkLoaderSize());
        }
    }

    @Override
    protected void bulkWrite(CsvData data) {
        writeDefault(data);
    }

    @Override
    protected LoadStatus delete(CsvData data, boolean useConflictDetection) {
        LoadStatus loadStatus = super.delete(data, useConflictDetection);
        if (!getTransaction().isInBatchMode()) {
            return loadStatus;
        }
        checkForConflict();
        return LoadStatus.SUCCESS;
    }

    @Override
    protected LoadStatus insert(CsvData data) {
        LoadStatus loadStatus = super.insert(data);
        if (!getTransaction().isInBatchMode()) {
            return loadStatus;
        }
        checkForConflict();
        return LoadStatus.SUCCESS;
    }

    @Override
    protected LoadStatus update(CsvData data, boolean applyChangesOnly, boolean useConflictDetection) {
        LoadStatus loadStatus = super.update(data, applyChangesOnly, useConflictDetection);
        if (!getTransaction().isInBatchMode()) {
            return loadStatus;
        }
        checkForConflict();
        return LoadStatus.SUCCESS;
    }

    protected void checkForConflict() {
        if (getTransaction().getUnflushedMarkers(false).size() == 0) {
            if (expectedRowCount != lastRowCount) {
                throw new SymmetricException("JdbcBatchBulkDataWriter was in conflict, will attempt to fallback using default writer.");
            }
            expectedRowCount = 0;
            lastRowCount = 0;
        }
    }

    @Override
    protected void prepare() {
        if (getTransaction().isInBatchMode()) {
            lastRowCount = getTransaction().flush();
            checkForConflict();
        }
        super.prepare();
    }

    @Override
    protected int execute(CsvData data, String[] values) {
        lastRowCount = super.execute(data, values);
        expectedRowCount++;
        return lastRowCount;
    }

    @Override
    public void end(Batch batch, boolean inError) {
        if (getTransaction().isInBatchMode()) {
            lastRowCount = getTransaction().flush();
            checkForConflict();
        }
        super.end(batch, inError);
    }

    @Override
    protected boolean requireNewStatement(DmlType currentType, CsvData data,
            boolean applyChangesOnly, boolean useConflictDetection,
            Conflict.DetectConflict detectType) {
        if (currentType == DmlType.DELETE) {
            applyChangesOnly = false;
        }
        return super.requireNewStatement(currentType, data, applyChangesOnly, useConflictDetection, detectType);
    }
}
