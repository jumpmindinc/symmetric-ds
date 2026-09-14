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

import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.TransformedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultTransformWriterConflictResolver extends DefaultDatabaseWriterConflictResolver {
    private static final Logger log = LoggerFactory.getLogger(DefaultTransformWriterConflictResolver.class);
    protected TransformWriter transformWriter;

    public DefaultTransformWriterConflictResolver(TransformWriter transformWriter) {
        this.transformWriter = transformWriter;
    }

    @Override
    protected void performFallbackToInsert(AbstractDatabaseWriter writer, CsvData data, Conflict conflict, boolean retransform) {
        TransformedData transformedData = data.getAttribute(TransformedData.class.getName());
        if (transformedData != null && retransform) {
            List<TransformedData> newlyTransformedDatas = transformWriter.transform(
                    DataEventType.INSERT, writer.getContext(), transformedData.getTransformation(),
                    transformedData.getSourceKeyValues(), transformedData.getOldSourceValues(),
                    transformedData.getSourceValues());
            if (newlyTransformedDatas.size() > 0) {
                boolean matchedTransform = false;
                for (TransformedData newlyTransformedData : newlyTransformedDatas) {
                    /*
                     * If there is only one transform, then process it. Otherwise, we need to attempt to match the key values to choose the correct transform.
                     */
                    if (newlyTransformedDatas.size() == 1
                            || newlyTransformedData
                                    .hasSameKeyValues(transformedData.getKeyValues())
                            || newlyTransformedData.isGeneratedIdentityNeeded()) {
                        matchedTransform = true;
                        Table table = newlyTransformedData.buildTargetTable();
                        CsvData newData = newlyTransformedData.buildTargetCsvData(data.getAttributes());
                        if (newlyTransformedData.isGeneratedIdentityNeeded()) {
                            if (log.isDebugEnabled()) {
                                log.debug("Enabling generation of identity for {}",
                                        newlyTransformedData.getTableName());
                            }
                            writer.allowInsertIntoAutoIncrementColumns(false, table);
                        } else if (table.hasAutoIncrementColumn()) {
                            writer.allowInsertIntoAutoIncrementColumns(true, table);
                        }
                        transformWriter.lastTransformedTable = table;
                        writer.start(table);
                        super.performFallbackToInsert(writer, newData, conflict, retransform);
                        writer.end(table);
                    }
                }
                if (!matchedTransform) {
                    log.warn("The attempt to retransform resulted in more than one transform.  We tried to choose one "
                            + "by matching on the ordered key values, but could not find a match.  Please check that the "
                            + "transformation is configured so that it will return keys in the same order regardless of DML type.  "
                            + "The original key values that we tried to match on were: {}"
                            + ArrayUtils.toString(transformedData.getKeyValues()));
                }
            }
        } else {
            super.performFallbackToInsert(writer, data, conflict, retransform);
        }
    }

    @Override
    protected void performFallbackToUpdate(AbstractDatabaseWriter writer, CsvData data, Conflict conflict, boolean retransform) {
        TransformedData transformedData = data.getAttribute(TransformedData.class.getName());
        if (transformedData != null && retransform) {
            List<TransformedData> newlyTransformedDatas = transformWriter.transform(
                    DataEventType.UPDATE, writer.getContext(), transformedData.getTransformation(),
                    transformedData.getSourceKeyValues(), transformedData.getOldSourceValues(),
                    transformedData.getSourceValues());
            for (TransformedData newlyTransformedData : newlyTransformedDatas) {
                if (newlyTransformedData.hasSameKeyValues(transformedData.getKeyValues())) {
                    Table table = newlyTransformedData.buildTargetTable();
                    transformWriter.lastTransformedTable = table;
                    writer.start(table);
                    super.performFallbackToUpdate(writer, newlyTransformedData.buildTargetCsvData(data.getAttributes()), conflict, retransform);
                    writer.end(table);
                }
            }
        } else {
            super.performFallbackToUpdate(writer, data, conflict, retransform);
        }
    }
}
