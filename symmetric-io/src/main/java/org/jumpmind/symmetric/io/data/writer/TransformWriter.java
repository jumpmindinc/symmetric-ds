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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.transform.IColumnTransform;
import org.jumpmind.symmetric.io.data.transform.IgnoreColumnException;
import org.jumpmind.symmetric.io.data.transform.IgnoreRowException;
import org.jumpmind.symmetric.io.data.transform.NewAndOldValue;
import org.jumpmind.symmetric.io.data.transform.TargetDmlAction;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;
import org.jumpmind.symmetric.io.data.transform.TransformColumnException;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformTable;
import org.jumpmind.symmetric.io.data.transform.TransformedData;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedRuntimeException;

public class TransformWriter extends NestedDataWriter {
    private static final Logger log = LoggerFactory.getLogger(TransformWriter.class);
    protected TransformPoint transformPoint;
    protected IDatabasePlatform platform;
    protected Map<String, List<TransformTable>> transformsBySourceTable;
    protected Table sourceTable;
    protected List<TransformTable> activeTransforms;
    protected Batch batch;
    protected Map<String, IColumnTransform<?>> columnTransforms;
    protected Table lastTransformedTable;

    public TransformWriter(IDatabasePlatform platform, TransformPoint transformPoint,
            IDataWriter targetWriter, Map<String, IColumnTransform<?>> columnTransforms,
            TransformTable... transforms) {
        super(targetWriter);
        this.columnTransforms = columnTransforms;
        this.platform = platform;
        this.transformPoint = transformPoint == null ? TransformPoint.LOAD : transformPoint;
        this.transformsBySourceTable = toMap(transforms);
    }

    protected final Map<String, List<TransformTable>> toMap(TransformTable[] transforms) {
        Map<String, List<TransformTable>> transformsByTable = new HashMap<String, List<TransformTable>>();
        if (transforms != null) {
            for (TransformTable transformTable : transforms) {
                if (transformPoint == transformTable.getTransformPoint()) {
                    String sourceTableName = transformTable.getFullyQualifiedSourceTableName().toLowerCase();
                    List<TransformTable> tables = transformsByTable.get(sourceTableName);
                    if (tables == null) {
                        tables = new ArrayList<TransformTable>();
                        transformsByTable.put(sourceTableName, tables);
                    }
                    tables.add(transformTable);
                }
            }
        }
        return transformsByTable;
    }

    @Override
    public void start(Batch batch) {
        this.batch = batch;
        super.start(batch);
    }

    @Override
    public boolean start(Table table) {
        List<TransformTable> activeTransformsTemp = transformsBySourceTable.get(table.getFullyQualifiedNameLowerCase());
        if (activeTransformsTemp != null && activeTransformsTemp.size() > 0) {
            this.sourceTable = table;
            activeTransforms = new ArrayList<TransformTable>(activeTransformsTemp.size());
            for (TransformTable transformation : activeTransformsTemp) {
                activeTransforms.add(transformation.enhanceWithImpliedColumns(
                        this.sourceTable.getPrimaryKeyColumnNames(),
                        this.sourceTable.getColumnNames()));
            }
            return true;
        } else {
            this.sourceTable = null;
            return super.start(table);
        }
    }

    protected boolean isTransformable(DataEventType eventType) {
        return eventType != null
                && (eventType == DataEventType.INSERT || eventType == DataEventType.UPDATE || eventType == DataEventType.DELETE
                        || eventType == DataEventType.SQL);
    }

    public void write(CsvData data) {
        DataEventType eventType = data.getDataEventType();
        if (activeTransforms != null && activeTransforms.size() > 0 && isTransformable(eventType)) {
            if (data.requiresTable() && sourceTable == null &&
                    context.getLastParsedRelation() != null) {
                // if we cross batches and the table isn't specified, then
                // use the last table we used
                start((Table) context.getLastParsedRelation());
            }
            if (eventType == DataEventType.SQL) {
                List<TransformTable> transformTables = activeTransforms;
                for (TransformTable transformation : transformTables) {
                    Table transformedTable = new Table(transformation.getTargetCatalogName(),
                            transformation.getTargetSchemaName(), transformation.getTargetTableName(),
                            getTargetColumnNames(transformation.getTransformColumns()), getTargetColumnNames(transformation.getPrimaryKeyColumns()));
                    callWriter(transformedTable, data);
                }
                return;
            }
            Map<String, String> sourceValues = data.toColumnNameValuePairs(this.sourceTable.getColumnNames(),
                    CsvData.ROW_DATA);
            Map<String, String> oldSourceValues = null;
            if (data.contains(CsvData.OLD_DATA)) {
                oldSourceValues = data.toColumnNameValuePairs(this.sourceTable.getColumnNames(),
                        CsvData.OLD_DATA);
            }
            Map<String, String> sourceKeyValues = null;
            if (data.contains(CsvData.PK_DATA)) {
                sourceKeyValues = data.toKeyColumnValuePairs(this.sourceTable);
            }
            if (eventType == DataEventType.DELETE) {
                sourceValues = oldSourceValues;
                if (sourceValues == null || sourceValues.size() == 0) {
                    sourceValues = sourceKeyValues;
                }
            }
            if (log.isDebugEnabled()) {
                log.debug(
                        "{} transformation(s) started because of {} on {}.  The original row data was: {}",
                        new Object[] { activeTransforms.size(), eventType.toString(),
                                this.sourceTable.getFullyQualifiedName(), sourceValues });
            }
            List<TransformTable> transformTables = activeTransforms;
            if (eventType == DataEventType.DELETE) {
                transformTables = new ArrayList<TransformTable>(activeTransforms);
                Collections.reverse(transformTables);
            }
            for (TransformTable transformation : transformTables) {
                DataEventType localEventType = eventType;
                if (localEventType == DataEventType.INSERT && transformation.isUpdateFirst()) {
                    localEventType = DataEventType.UPDATE;
                }
                List<TransformedData> dataThatHasBeenTransformed = transform(localEventType, context, transformation, sourceKeyValues, oldSourceValues,
                        sourceValues);
                for (TransformedData transformedData : dataThatHasBeenTransformed) {
                    Table transformedTable = transformedData.buildTargetTable();
                    CsvData csvData = transformedData.buildTargetCsvData(data.getAttributes());
                    callWriter(transformedTable, csvData);
                }
            }
        } else {
            if (sourceTable != null) {
                super.start(sourceTable);
            }
            super.write(data);
            if (sourceTable != null) {
                super.end(sourceTable);
            }
        }
    }

    protected void callWriter(Table transformedTable, CsvData csvData) {
        boolean processData = true;
        if (lastTransformedTable == null || transformedTable == null || !lastTransformedTable.equalsByName(transformedTable)) {
            if (lastTransformedTable != null) {
                this.nestedWriter.end(lastTransformedTable);
            }
            processData = this.nestedWriter.start(transformedTable);
            if (!processData) {
                lastTransformedTable = null;
            } else {
                lastTransformedTable = transformedTable;
            }
        }
        if (processData || !csvData.requiresTable()) {
            this.nestedWriter.write(csvData);
        }
    }

    protected String[] getTargetColumnNames(List<TransformColumn> columns) {
        String[] names = new String[columns.size()];
        int i = 0;
        for (TransformColumn column : columns) {
            names[i++] = column.getTargetColumnName();
        }
        return names;
    }

    protected List<TransformedData> transform(DataEventType eventType, DataContext context,
            TransformTable transformation, Map<String, String> sourceKeyValues,
            Map<String, String> oldSourceValues, Map<String, String> sourceValues) {
        long ts = System.currentTimeMillis();
        try {
            List<TransformedData> dataToTransform = create(context, eventType, transformation,
                    sourceKeyValues, oldSourceValues, sourceValues);
            List<TransformedData> dataThatHasBeenTransformed = new ArrayList<TransformedData>(
                    dataToTransform.size());
            if (log.isDebugEnabled()) {
                log.debug(
                        "{} target data was created for the {} transformation.  The target table is {}",
                        new Object[] { dataToTransform.size(), transformation.getTransformId(),
                                transformation.getFullyQualifiedTargetTableName() });
            }
            int transformNumber = 0;
            for (TransformedData targetData : dataToTransform) {
                transformNumber++;
                if (perform(context, targetData, transformation, sourceValues, oldSourceValues)) {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Data has been transformed to a {} for the #{} transform.  The mapped target columns are: {}. The mapped target values are: {}",
                                new Object[] { targetData.getTargetDmlType().toString(),
                                        transformNumber,
                                        ArrayUtils.toString(targetData.getColumnNames()),
                                        ArrayUtils.toString(targetData.getColumnValues()) });
                    }
                    dataThatHasBeenTransformed.add(targetData);
                } else {
                    log.debug("Data has not been transformed for the #{} transform",
                            transformNumber);
                }
            }
            return dataThatHasBeenTransformed;
        } catch (IgnoreRowException ex) {
            // ignore this row
            if (log.isDebugEnabled()) {
                log.debug(
                        "Transform indicated that the target row should be ignored with a target key of: {}",
                        "unknown.  Transformation aborted during transformation of key");
            }
            return new ArrayList<TransformedData>(0);
        } finally {
            long transformTimeInMs = System.currentTimeMillis() - ts;
            Statistics stats = this.nestedWriter.getStatistics().get(batch);
            if (stats != null) {
                stats.increment(DataWriterStatisticConstants.TRANSFORMMILLIS, transformTimeInMs);
            }
            ts = System.currentTimeMillis();
        }
    }

    protected boolean perform(DataContext context, TransformedData data,
            TransformTable transformation, Map<String, String> sourceValues,
            Map<String, String> oldSourceValues) throws IgnoreRowException {
        boolean persistData = false;
        try {
            DataEventType eventType = data.getSourceDmlType();
            for (TransformColumn transformColumn : transformation.getTransformColumns()) {
                if (!transformColumn.isPk()) {
                    IncludeOnType includeOn = transformColumn.getIncludeOn();
                    if (includeOn == IncludeOnType.ALL || (includeOn == IncludeOnType.INSERT && eventType == DataEventType.INSERT)
                            || (includeOn == IncludeOnType.UPDATE && eventType == DataEventType.UPDATE)
                            || (includeOn == IncludeOnType.DELETE && eventType == DataEventType.DELETE)) {
                        if (StringUtils.isBlank(transformColumn.getSourceColumnName())
                                || sourceValues.containsKey(transformColumn.getSourceColumnName())) {
                            try {
                                Object value = transformColumn(context, data, transformColumn, sourceValues, oldSourceValues);
                                if (value instanceof NewAndOldValue) {
                                    data.put(transformColumn, ((NewAndOldValue) value).getNewValue(),
                                            oldSourceValues != null ? ((NewAndOldValue) value).getOldValue() : null, false);
                                } else if (value == null || value instanceof String) {
                                    data.put(transformColumn, (String) value, null, false);
                                } else if (value instanceof List) {
                                    throw new IllegalStateException(String.format(
                                            "Column transform failed %s.%s. Transforms that multiply rows must be marked as part of the primary key",
                                            transformColumn.getTransformId(), transformColumn.getTargetColumnName()));
                                } else {
                                    throw new IllegalStateException(
                                            String.format("Column transform failed %s.%s. It returned an unexpected type of %s",
                                                    transformColumn.getTransformId(), transformColumn.getTargetColumnName(),
                                                    value.getClass().getSimpleName()));
                                }
                            } catch (IgnoreColumnException e) {
                                // Do nothing. We are ignoring the column
                                if (log.isDebugEnabled()) {
                                    log.debug("A transform indicated we should ignore the target column {}",
                                            transformColumn.getTargetColumnName());
                                }
                            }
                        } else {
                            if (eventType != DataEventType.DELETE) {
                                log.warn("Could not find a source column of {} for the transformation: {}",
                                        transformColumn.getSourceColumnName(), transformation.getTransformId());
                            } else {
                                log.debug(
                                        "Could not find a source column of {} for the transformation: {}.  This is probably because this was a DELETE event and no old data was captured.",
                                        transformColumn.getSourceColumnName(), transformation.getTransformId());
                            }
                        }
                    }
                }
            }
            TargetDmlAction targetAction = data.getTargetAction();
            if (targetAction != null && data.getColumnNames().length > 0 && targetAction != TargetDmlAction.NONE) {
                persistData = true;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("The {} transformation is not configured to delete row.  Not sending the delete through.",
                            transformation.getTransformId());
                }
            }
        } catch (IgnoreRowException ex) {
            // ignore this row
            if (log.isDebugEnabled()) {
                log.debug("Transform indicated that the target row should be ignored with a target key of: {}",
                        ArrayUtils.toString(data.getKeyValues()));
            }
        }
        return persistData;
    }

    protected List<TransformedData> create(DataContext context, DataEventType dataEventType,
            TransformTable transformation, Map<String, String> sourceKeyValues,
            Map<String, String> oldSourceValues, Map<String, String> sourceValues)
            throws IgnoreRowException {
        List<TransformColumn> columns = transformation.getPrimaryKeyColumns();
        if (columns == null || columns.size() == 0) {
            log.error("No primary key defined for the transformation: {}",
                    transformation.getTransformId());
            return new ArrayList<TransformedData>(0);
        } else {
            List<TransformedData> datas = new ArrayList<TransformedData>();
            TransformedData data = new TransformedData(transformation, dataEventType,
                    sourceKeyValues, oldSourceValues, sourceValues);
            datas.add(data);
            DataEventType eventType = data.getSourceDmlType();
            switch (data.getTargetDmlType()) {
                case INSERT:
                    data.setTargetAction(TargetDmlAction.INS_ROW);
                    break;
                case UPDATE:
                    data.setTargetAction(transformation.evaluateTargetDmlAction(context, data));
                    break;
                case DELETE:
                    data.setTargetAction(transformation.getDeleteAction());
                    break;
                default:
                    data.setTargetAction(TargetDmlAction.OTHER);
            }
            // how to handle the update/delete action on target..
            switch (data.getTargetAction()) {
                case DEL_ROW:
                    data.setTargetDmlType(DataEventType.DELETE);
                    break;
                case UPDATE_COL:
                case UPD_ROW:
                    data.setTargetDmlType(DataEventType.UPDATE);
                    break;
                case INS_ROW:
                    data.setTargetDmlType(DataEventType.INSERT);
                    break;
                case NONE:
                default:
                    break;
            }
            for (TransformColumn transformColumn : columns) {
                IncludeOnType includeOn = transformColumn.getIncludeOn();
                if (includeOn == IncludeOnType.ALL
                        || (includeOn == IncludeOnType.INSERT && eventType == DataEventType.INSERT)
                        || (includeOn == IncludeOnType.UPDATE && eventType == DataEventType.UPDATE)
                        || (includeOn == IncludeOnType.DELETE && eventType == DataEventType.DELETE)) {
                    List<TransformedData> newDatas = null;
                    try {
                        Object columnValue = transformColumn(context, data, transformColumn,
                                sourceValues, oldSourceValues);
                        if (columnValue instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> values = (List<String>) columnValue;
                            if (values.size() > 0) {
                                data.put(transformColumn, values.get(0), oldSourceValues != null ? values.get(0) : null, true);
                                if (values.size() > 1) {
                                    if (newDatas == null) {
                                        newDatas = new ArrayList<TransformedData>(values.size() - 1);
                                    }
                                    for (int i = 1; i < values.size(); i++) {
                                        TransformedData newData = data.copy();
                                        newData.put(transformColumn, values.get(i), oldSourceValues != null ? values.get(i) : null, true);
                                        newDatas.add(newData);
                                    }
                                }
                            } else {
                                throw new IgnoreRowException();
                            }
                        } else if (columnValue instanceof NewAndOldValue) {
                            data.put(transformColumn, ((NewAndOldValue) columnValue).getNewValue(),
                                    oldSourceValues != null ? ((NewAndOldValue) columnValue).getOldValue() : null, true);
                        } else {
                            data.put(transformColumn, (String) columnValue, oldSourceValues != null ? (String) columnValue : null, true);
                        }
                    } catch (IgnoreColumnException e) {
                        // Do nothing. We are suppose to ignore the column.
                    }
                    if (newDatas != null) {
                        datas.addAll(newDatas);
                        newDatas = null;
                    }
                }
            }
            return datas;
        }
    }

    protected Object transformColumn(DataContext context, TransformedData data,
            TransformColumn transformColumn, Map<String, String> sourceValues,
            Map<String, String> oldSourceValues) throws IgnoreRowException, IgnoreColumnException {
        Object returnValue = null;
        String value = transformColumn.getSourceColumnName() != null ? sourceValues
                .get(transformColumn.getSourceColumnName()) : null;
        returnValue = value;
        IColumnTransform<?> transform = columnTransforms != null ? columnTransforms
                .get(transformColumn.getTransformType()) : null;
        if (transform != null) {
            try {
                String oldValue = null;
                if (oldSourceValues != null) {
                    oldValue = oldSourceValues.get(transformColumn.getSourceColumnName());
                }
                returnValue = transform.transform(platform, context, transformColumn, data,
                        sourceValues, value, oldValue);
            } catch (NestedRuntimeException nestedRuntimeException) {
                Throwable rootCause = nestedRuntimeException.getRootCause();
                if (rootCause instanceof IgnoreColumnException) {
                    throw (IgnoreColumnException) rootCause;
                } else if (rootCause instanceof IgnoreRowException) {
                    throw (IgnoreRowException) rootCause;
                } else {
                    throw nestedRuntimeException;
                }
            } catch (RuntimeException ex) {
                log.warn("Column transform failed {}.{} ({}) for source values of {}", new Object[] { transformColumn.getTransformId(), transformColumn
                        .getTargetColumnName(), transformColumn.getIncludeOn().name(), sourceValues.toString() });
                throw ex;
            }
        } else {
            throw new TransformColumnException(String.format("Could not locate a column transform of type '%s'", transformColumn.getTransformType()));
        }
        return returnValue;
    }

    public void end(Table table) {
        if (this.lastTransformedTable != null) {
            this.nestedWriter.end(lastTransformedTable);
            this.lastTransformedTable = null;
        }
        if (activeTransforms != null && activeTransforms.size() > 0) {
            activeTransforms = null;
        } else {
            super.end(table);
        }
    }
}
