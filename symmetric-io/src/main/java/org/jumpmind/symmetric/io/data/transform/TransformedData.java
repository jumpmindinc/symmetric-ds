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
package org.jumpmind.symmetric.io.data.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;

public class TransformedData implements Cloneable {
    protected boolean generatedIdentityNeeded = false;
    protected TargetDmlAction targetAction = null;
    protected DataEventType targetDmlType;
    protected DataEventType sourceDmlType;
    protected Map<TransformColumn.IncludeOnType, LinkedHashMap<String, String>> targetNewValueByIncludeOnType;
    protected Map<TransformColumn.IncludeOnType, LinkedHashMap<String, String>> targetNewKeysByIncludeOnType;
    protected Map<TransformColumn.IncludeOnType, LinkedHashMap<String, String>> targetOldValuesByIncludeOnType;
    protected TransformTable transformation;
    protected Map<String, String> sourceKeyValues;
    protected Map<String, String> oldSourceValues;
    protected Map<String, String> sourceValues;

    public TransformedData(TransformTable transformation, DataEventType sourceDmlType,
            Map<String, String> sourceKeyValues, Map<String, String> oldSourceValues,
            Map<String, String> sourceValues) {
        this.transformation = transformation;
        this.targetDmlType = sourceDmlType;
        this.sourceDmlType = sourceDmlType;
        this.sourceKeyValues = sourceKeyValues;
        this.oldSourceValues = oldSourceValues;
        this.sourceValues = sourceValues;
    }

    public String getFullyQualifiedTableName() {
        return transformation.getFullyQualifiedTargetTableName();
    }

    public DataEventType getTargetDmlType() {
        return targetDmlType;
    }

    public void setTargetDmlType(DataEventType dmlType) {
        this.targetDmlType = dmlType;
    }

    public String getTableName() {
        return transformation.getTargetTableName();
    }

    public String getCatalogName() {
        return transformation.getTargetCatalogName();
    }

    public String getSchemaName() {
        return transformation.getTargetSchemaName();
    }

    public void put(TransformColumn column, String columnValue, String oldValue, boolean recordAsKey) {
        if (recordAsKey) {
            if (targetNewKeysByIncludeOnType == null) {
                targetNewKeysByIncludeOnType = new HashMap<TransformColumn.IncludeOnType, LinkedHashMap<String, String>>(
                        2);
            }
            LinkedHashMap<String, String> keyValues = targetNewKeysByIncludeOnType.get(column.getIncludeOn());
            if (keyValues == null) {
                keyValues = new LinkedHashMap<String, String>();
                targetNewKeysByIncludeOnType.put(column.getIncludeOn(), keyValues);
            }
            keyValues.put(column.getTargetColumnName(), oldValue != null ? oldValue : columnValue);
        }
        if (targetNewValueByIncludeOnType == null) {
            targetNewValueByIncludeOnType = new HashMap<TransformColumn.IncludeOnType, LinkedHashMap<String, String>>(2);
        }
        LinkedHashMap<String, String> columnValues = targetNewValueByIncludeOnType.get(column.getIncludeOn());
        if (columnValues == null) {
            columnValues = new LinkedHashMap<String, String>();
            targetNewValueByIncludeOnType.put(column.getIncludeOn(), columnValues);
        }
        columnValues.put(column.getTargetColumnName(), columnValue);
        if (targetOldValuesByIncludeOnType == null) {
            targetOldValuesByIncludeOnType = new HashMap<TransformColumn.IncludeOnType, LinkedHashMap<String, String>>(2);
        }
        LinkedHashMap<String, String> oldColumnValues = targetOldValuesByIncludeOnType.get(column.getIncludeOn());
        if (oldColumnValues == null) {
            oldColumnValues = new LinkedHashMap<String, String>();
            targetOldValuesByIncludeOnType.put(column.getIncludeOn(), oldColumnValues);
        }
        oldColumnValues.put(column.getTargetColumnName(), oldValue);
    }

    protected Map<String, String> retrieve(
            Map<TransformColumn.IncludeOnType, LinkedHashMap<String, String>> source) {
        Map<String, String> list = new LinkedHashMap<String, String>(source == null ? 0
                : source.size());
        if (source != null) {
            LinkedHashMap<String, String> values = source.get(IncludeOnType.ALL);
            if (values != null) {
                list.putAll(values);
            }
            IncludeOnType type = IncludeOnType.DELETE;
            if (targetDmlType == DataEventType.UPDATE && sourceDmlType != DataEventType.DELETE) {
                type = IncludeOnType.UPDATE;
            } else if (targetDmlType == DataEventType.INSERT) {
                type = IncludeOnType.INSERT;
            }
            values = source.get(type);
            if (values != null) {
                list.putAll(values);
            }
        }
        return list;
    }

    protected List<String> retrieve(
            Map<TransformColumn.IncludeOnType, LinkedHashMap<String, String>> source,
            boolean getColumnNames) {
        Map<String, String> values = retrieve(source);
        if (getColumnNames) {
            return new ArrayList<String>(values.keySet());
        } else {
            return new ArrayList<String>(values.values());
        }
    }

    public Map<String, String> getTargetKeyValues() {
        return retrieve(targetNewKeysByIncludeOnType);
    }

    public Map<String, String> getTargetValues() {
        return retrieve(targetNewValueByIncludeOnType);
    }

    public String[] getKeyNames() {
        List<String> list = retrieve(targetNewKeysByIncludeOnType, true);
        return list.toArray(new String[list.size()]);
    }

    public String[] getKeyValues() {
        List<String> list = retrieve(targetNewKeysByIncludeOnType, false);
        return list.toArray(new String[list.size()]);
    }

    public String[] getColumnNames() {
        List<String> list = retrieve(targetNewValueByIncludeOnType, true);
        return list.toArray(new String[list.size()]);
    }

    public String[] getColumnValues() {
        List<String> list = retrieve(targetNewValueByIncludeOnType, false);
        return list.toArray(new String[list.size()]);
    }

    public DataEventType getSourceDmlType() {
        return sourceDmlType;
    }

    public TransformedData copy() {
        try {
            TransformedData clone = (TransformedData) this.clone();
            clone.targetNewValueByIncludeOnType = copy(targetNewValueByIncludeOnType);
            clone.targetNewKeysByIncludeOnType = copy(targetNewKeysByIncludeOnType);
            clone.targetOldValuesByIncludeOnType = copy(targetOldValuesByIncludeOnType);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public TransformTable getTransformation() {
        return transformation;
    }

    public void setGeneratedIdentityNeeded(boolean generatedIdentityNeeded) {
        this.generatedIdentityNeeded = generatedIdentityNeeded;
    }

    public boolean isGeneratedIdentityNeeded() {
        return generatedIdentityNeeded;
    }

    protected Map<TransformColumn.IncludeOnType, LinkedHashMap<String, String>> copy(
            Map<TransformColumn.IncludeOnType, LinkedHashMap<String, String>> toCopy) {
        Map<TransformColumn.IncludeOnType, LinkedHashMap<String, String>> newMap = new HashMap<TransformColumn.IncludeOnType, LinkedHashMap<String, String>>(
                toCopy.size());
        for (TransformColumn.IncludeOnType key : toCopy.keySet()) {
            LinkedHashMap<String, String> value = toCopy.get(key);
            newMap.put(key, new LinkedHashMap<String, String>(value));
        }
        return newMap;
    }

    public Map<String, String> getSourceKeyValues() {
        return sourceKeyValues;
    }

    public Map<String, String> getOldSourceValues() {
        return oldSourceValues;
    }

    public Map<String, String> getSourceValues() {
        return sourceValues;
    }

    public void setTargetAction(TargetDmlAction targetAction) {
        this.targetAction = targetAction;
    }

    public TargetDmlAction getTargetAction() {
        return targetAction;
    }

    public boolean hasSameKeyValues(String[] otherKeyValues) {
        String[] keyValues = getKeyValues();
        if (otherKeyValues != null) {
            if (keyValues != null) {
                if (keyValues.length != otherKeyValues.length) {
                    return false;
                }
                for (int i = 0; i < otherKeyValues.length; i++) {
                    if (!(keyValues[i] == null && otherKeyValues[i] == null) &&
                            ((keyValues[i] == null && otherKeyValues[i] != null) ||
                                    (otherKeyValues[i] == null && keyValues[i] != null) ||
                                    (!keyValues[i].equals(otherKeyValues[i])))) {
                        return false;
                    }
                }
            } else {
                return false;
            }
        } else if (keyValues != null) {
            return false;
        }
        return true;
    }

    public Table buildTargetTable() {
        Table table = null;
        String[] columnNames = getColumnNames();
        String[] keyNames = getKeyNames();
        if (columnNames != null && columnNames.length > 0) {
            table = new Table(transformation.getTargetCatalogName(),
                    transformation.getTargetSchemaName(), transformation.getTargetTableName());
            for (String colName : columnNames) {
                Column col = new Column(colName);
                table.addColumn(col);
                if (keyNames != null) {
                    for (String keyName : keyNames) {
                        if (keyName.equals(colName)) {
                            col.setPrimaryKey(true);
                        }
                    }
                }
            }
        }
        return table;
    }

    public CsvData buildTargetCsvData(Map<String, Object> attributes) {
        CsvData data = new CsvData(this.targetDmlType);
        data.setAttributes(attributes);
        if (transformation != null) {
            data.putAttribute(CsvData.ATTRIBUTE_TABLE_NAME, transformation.getSourceTableName());
        }
        if (targetDmlType != DataEventType.DELETE) {
            data.putParsedData(CsvData.ROW_DATA, getColumnValues());
        }
        if (targetDmlType == DataEventType.UPDATE || targetDmlType == DataEventType.DELETE) {
            data.putParsedData(CsvData.OLD_DATA, getOldColumnValues());
            data.putParsedData(CsvData.PK_DATA, getKeyValues());
        }
        data.putAttribute(getClass().getName(), this);
        return data;
    }

    public String[] getOldColumnValues() {
        List<String> list = retrieve(targetOldValuesByIncludeOnType, false);
        boolean use = false;
        for (String string : list) {
            use |= string != null;
        }
        if (use) {
            return list.toArray(new String[list.size()]);
        } else {
            return null;
        }
    }
}
