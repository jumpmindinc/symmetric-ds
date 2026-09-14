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
package org.jumpmind.symmetric.io;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.TableNotFoundException;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterSettings;
import org.jumpmind.symmetric.io.data.writer.DynamicDefaultDatabaseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBulkDatabaseWriter extends DynamicDefaultDatabaseWriter {
    private static final Logger log = LoggerFactory.getLogger(AbstractBulkDatabaseWriter.class);

    public AbstractBulkDatabaseWriter(IDatabasePlatform symmetricPlatform, IDatabasePlatform targetPlatform, String tablePrefix) {
        super(symmetricPlatform, targetPlatform, tablePrefix);
    }

    public AbstractBulkDatabaseWriter(IDatabasePlatform symmetricPlatform, IDatabasePlatform targetPlatform,
            String tablePrefix, DatabaseWriterSettings settings) {
        super(symmetricPlatform, targetPlatform, tablePrefix, settings);
    }

    @Override
    public void start(Batch batch) {
        super.start(batch);
        batch.setBulkLoaderFlag(true);
        if (isFallBackToDefault()) {
            batch.setBulkLoaderFlag(false);
            getTransaction().setInBatchMode(false);
            getTransaction().clearBatch();
            log.debug("Writing batch " + batch.getBatchId() + " on channel " + batch.getChannelId() + " to node " + batch.getTargetNodeId()
                    + " using DEFAULT loader");
        } else {
            log.debug("Writing batch " + batch.getBatchId() + " on channel " + batch.getChannelId() + " to node " + batch.getTargetNodeId()
                    + " using BULK loader");
        }
    }

    public final void write(CsvData data) {
        if (data.requiresTable() && data.getDataEventType() != DataEventType.SQL) {
            if (sourceTable == null) {
                throw new SqlException("The target table was not specified");
            } else if (targetTable == null) {
                String qualifiedName = sourceTable.getFullyQualifiedName();
                if (writerSettings.isIgnoreMissingTables() || batch.getBatchId() == IoConstants.IGNORE_TABLES_BATCH) {
                    if (missingTables.add(qualifiedName)) {
                        log.info("Did not find the {} table in the target database", qualifiedName);
                    }
                } else {
                    statistics.get(batch).increment(DataWriterStatisticConstants.ROWCOUNT);
                    statistics.get(batch).increment(DataWriterStatisticConstants.LINENUMBER);
                    throw new TableNotFoundException(String.format("Could not find the target table '%s'", qualifiedName));
                }
            }
        }
        if (isFallBackToDefault()) {
            writeDefault(data);
        } else {
            context.put(ContextConstants.CONTEXT_BULK_WRITER_TO_USE, "bulk");
            bulkWrite(data);
        }
    }

    @Override
    public void end(Batch batch, boolean inError) {
        super.end(batch, inError);
        if (!inError) {
            context.put(ContextConstants.CONTEXT_BULK_WRITER_TO_USE, null);
        }
    }

    public boolean isFallBackToDefault() {
        return context.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE) != null && context.get(ContextConstants.CONTEXT_BULK_WRITER_TO_USE).equals("default");
    }

    protected final void writeDefault(CsvData data) {
        super.write(data);
    }

    protected abstract void bulkWrite(CsvData data);
}
