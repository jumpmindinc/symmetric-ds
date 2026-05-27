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
package org.jumpmind.symmetric.io.data.transform;

import java.util.*;

import bsh.Interpreter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.SchemaObject;
import org.jumpmind.symmetric.io.data.*;
import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;
import org.jumpmind.util.Context;
import org.slf4j.*;

public class TransformTable implements Cloneable {
    final String INTERPRETER_KEY = String.format("%s.BshInterpreter", getClass().getName());
    transient protected final Logger log = LoggerFactory.getLogger(getClass());
    /*
     * Static context object used to maintain objects in memory for reference between BSH transforms.
     */
    private static Map<String, Object> bshContext = new HashMap<String, Object>();
    protected String transformId;
    protected String sourceCatalogName;
    protected String sourceSchemaName;
    protected String sourceTableName;
    protected String targetCatalogName;
    protected String targetSchemaName;
    protected String targetTableName;
    protected TransformPoint transformPoint;
    protected List<TransformColumn> transformColumns;
    protected List<TransformColumn> primaryKeyColumns;
    protected String updateAction = TargetDmlAction.UPDATE_COL.name();
    protected TargetDmlAction deleteAction = TargetDmlAction.DEL_ROW;
    protected ColumnPolicy columnPolicy = ColumnPolicy.IMPLIED;
    protected boolean updateFirst = false;
    protected int transformOrder = 0;
    protected Date createTime;
    protected Date lastUpdateTime;
    protected String lastUpdateBy;

    public TransformTable(String sourceTableName, String targetTableName,
            TransformPoint transformPoint, TransformColumn... columns) {
        this.sourceTableName = sourceTableName;
        this.targetTableName = targetTableName;
        this.transformPoint = transformPoint;
        this.transformColumns = new ArrayList<TransformColumn>();
        this.primaryKeyColumns = new ArrayList<TransformColumn>();
        if (columns != null) {
            for (TransformColumn transformColumn : columns) {
                if (transformColumn.isPk()) {
                    primaryKeyColumns.add(transformColumn);
                }
                transformColumns.add(transformColumn);
            }
        }
    }

    public TransformTable() {
    }

    public String getFullyQualifiedSourceTableName() {
        return SchemaObject.getFullyQualifiedName(sourceCatalogName, sourceSchemaName,
                sourceTableName);
    }

    public String getFullyQualifiedTargetTableName() {
        return SchemaObject.getFullyQualifiedName(targetCatalogName, targetSchemaName,
                targetTableName);
    }

    public String getTransformId() {
        return transformId;
    }

    public void setTransformId(String transformId) {
        this.transformId = transformId;
    }

    public String getSourceCatalogName() {
        return sourceCatalogName;
    }

    public void setSourceCatalogName(String sourceCatalogName) {
        this.sourceCatalogName = sourceCatalogName;
    }

    public String getSourceSchemaName() {
        return sourceSchemaName;
    }

    public void setSourceSchemaName(String sourceSchemaName) {
        this.sourceSchemaName = sourceSchemaName;
    }

    public String getSourceTableName() {
        return sourceTableName;
    }

    public void setSourceTableName(String sourceTableName) {
        this.sourceTableName = sourceTableName;
    }

    public String getTargetCatalogName() {
        return targetCatalogName;
    }

    public void setTargetCatalogName(String targetCatalogName) {
        this.targetCatalogName = targetCatalogName;
    }

    public String getTargetSchemaName() {
        return targetSchemaName;
    }

    public void setTargetSchemaName(String targetSchemaName) {
        this.targetSchemaName = targetSchemaName;
    }

    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
    }

    public void setTransformPoint(TransformPoint transformPoint) {
        this.transformPoint = transformPoint;
    }

    public TransformPoint getTransformPoint() {
        return transformPoint;
    }

    public void setTransformColumns(List<TransformColumn> transformColumns) {
        this.transformColumns = transformColumns;
        this.primaryKeyColumns = new ArrayList<TransformColumn>();
        if (transformColumns != null) {
            for (TransformColumn transformColumn : transformColumns) {
                if (transformColumn.isPk()) {
                    this.primaryKeyColumns.add(transformColumn);
                }
            }
        }
    }

    public List<TransformColumn> getTransformColumns() {
        return transformColumns;
    }

    public List<TransformColumn> getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    public List<TransformColumn> getTransformColumnFor(String columnName) {
        List<TransformColumn> columns = new ArrayList<TransformColumn>(2);
        if (transformColumns != null) {
            for (TransformColumn column : transformColumns) {
                if (Strings.CI.equals(column.getSourceColumnName(), columnName)) {
                    columns.add(column);
                }
            }
        }
        return columns;
    }

    public List<TransformColumn> getTransformColumnTo(String targetColumn) {
        List<TransformColumn> columns = new ArrayList<TransformColumn>(2);
        if (transformColumns != null) {
            for (TransformColumn column : transformColumns) {
                if (Strings.CI.equals(column.getTargetColumnName(), targetColumn)) {
                    columns.add(column);
                }
            }
        }
        return columns;
    }

    public TransformColumn getTransformColumn(String targetColumn, IncludeOnType includeOn) {
        if (transformColumns != null) {
            for (TransformColumn column : transformColumns) {
                if (Strings.CI.equals(targetColumn, column.getTargetColumnName()) && includeOn == column.getIncludeOn()) {
                    return column;
                }
            }
        }
        return null;
    }

    public void addTransformColumn(TransformColumn column) {
        if (transformColumns == null) {
            transformColumns = new ArrayList<TransformColumn>();
        }
        if (primaryKeyColumns == null) {
            primaryKeyColumns = new ArrayList<TransformColumn>();
        }
        transformColumns.add(column);
        if (column.isPk()) {
            primaryKeyColumns.add(column);
        }
    }

    public void setUpdateAction(String updateAction) {
        this.updateAction = updateAction;
    }

    public String getUpdateAction() {
        return updateAction;
    }

    public TargetDmlAction evaluateTargetDmlAction(DataContext dataContext, TransformedData transformedData) {
        TargetDmlAction action = null;
        try {
            action = TargetDmlAction.valueOf(updateAction);
        } catch (Exception ex) {
        }
        if (action == null) {
            Interpreter interpreter = getInterpreter(dataContext);
            Map<String, String> sourceValues = transformedData.getSourceValues();
            try {
                interpreter.set("sourceDmlType", transformedData.getSourceDmlType());
                interpreter.set("sourceDmlTypeString", transformedData.getSourceDmlType().toString());
                interpreter.set("transformedData", transformedData);
                CsvData csvData = dataContext.getData();
                if (csvData != null) {
                    interpreter.set("externalData", csvData.getAttribute("externalData"));
                } else {
                    interpreter.set("externalData", null);
                }
                for (String columnName : sourceValues.keySet()) {
                    interpreter.set(columnName.toUpperCase(), sourceValues.get(columnName));
                    interpreter.set(columnName, sourceValues.get(columnName));
                }
                if (transformedData.getOldSourceValues() != null) {
                    for (Map.Entry<String, String> oldColumn : transformedData.getOldSourceValues().entrySet()) {
                        interpreter.set("OLD_" + oldColumn.getKey(), oldColumn.getValue());
                        interpreter.set("OLD_" + oldColumn.getKey().toUpperCase(), oldColumn.getValue());
                    }
                }
                String transformExpression = updateAction;
                String methodName = String.format("transform_%d()", Math.abs((long) transformExpression.hashCode()));
                if (dataContext.get(methodName) == null) {
                    // create BSH-Method if not exists in Context
                    interpreter.set("context", dataContext);
                    interpreter.set("bshContext", bshContext);
                    interpreter.eval(String.format("%s {\n%s\n}", methodName, transformExpression));
                    dataContext.put(methodName, Boolean.TRUE);
                }
                // call BSH-Method
                Object result = interpreter.eval(methodName);
                // evaluate Result of BSH-Script
                action = TargetDmlAction.valueOf((String) result);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return action;
    }

    protected Interpreter getInterpreter(Context context) {
        Interpreter interpreter = (Interpreter) context.get(INTERPRETER_KEY);
        if (interpreter == null) {
            interpreter = new Interpreter();
            context.put(INTERPRETER_KEY, interpreter);
        }
        return interpreter;
    }

    public void setDeleteAction(TargetDmlAction deleteAction) {
        this.deleteAction = deleteAction;
    }

    public TargetDmlAction getDeleteAction() {
        return deleteAction;
    }

    public void setTransformOrder(int transformOrder) {
        this.transformOrder = transformOrder;
    }

    public int getTransformOrder() {
        return transformOrder;
    }

    public void setUpdateFirst(boolean updateFirst) {
        this.updateFirst = updateFirst;
    }

    public boolean isUpdateFirst() {
        return updateFirst;
    }

    public ColumnPolicy getColumnPolicy() {
        return columnPolicy;
    }

    public void setColumnPolicy(ColumnPolicy columnPolicy) {
        this.columnPolicy = columnPolicy;
    }

    @Override
    public int hashCode() {
        if (transformId != null) {
            return transformId.hashCode();
        } else {
            return super.hashCode();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (transformId != null) {
            if (obj instanceof TransformTable) {
                return transformId.equals(((TransformTable) obj).transformId);
            } else {
                return false;
            }
        } else {
            return super.equals(obj);
        }
    }

    @Override
    public String toString() {
        if (transformId != null) {
            return transformId;
        } else {
            return super.toString();
        }
    }

    public TransformTable enhanceWithImpliedColumns(String[] keyNames, String[] columnNames) {
        TransformTable copiedVersion;
        try {
            copiedVersion = (TransformTable) this.clone();
            if (transformColumns != null) {
                copiedVersion.transformColumns = new ArrayList<TransformColumn>(transformColumns);
            } else {
                copiedVersion.transformColumns = new ArrayList<TransformColumn>();
            }
            if (primaryKeyColumns != null) {
                copiedVersion.primaryKeyColumns = new ArrayList<TransformColumn>(primaryKeyColumns);
            } else {
                copiedVersion.primaryKeyColumns = new ArrayList<TransformColumn>();
            }
            if (columnPolicy == ColumnPolicy.IMPLIED) {
                for (String column : keyNames) {
                    boolean hasInsert = false;
                    boolean hasUpdate = false;
                    boolean hasDelete = false;
                    String columnLowerCase = column.toLowerCase();
                    if (primaryKeyColumns != null) {
                        for (TransformColumn xCol : transformColumns) {
                            if ((StringUtils.isNotBlank(xCol.getSourceColumnName()) && columnLowerCase.equals(xCol.getSourceColumnNameLowerCase())) ||
                                    StringUtils.isNotBlank(xCol.getTargetColumnName()) && columnLowerCase.equals(xCol.getTargetColumnNameLowerCase())) {
                                if (xCol.includeOn == IncludeOnType.ALL) {
                                    hasInsert = hasUpdate = hasDelete = true;
                                    break;
                                } else if (xCol.includeOn == IncludeOnType.INSERT) {
                                    hasInsert = true;
                                } else if (xCol.includeOn == IncludeOnType.UPDATE) {
                                    hasUpdate = true;
                                } else if (xCol.includeOn == IncludeOnType.DELETE) {
                                    hasDelete = true;
                                }
                            }
                        }
                    }
                    if (!hasInsert && !hasUpdate && !hasDelete) {
                        TransformColumn newCol = createImplicitTransformColumn(column, IncludeOnType.ALL, true);
                        copiedVersion.primaryKeyColumns.add(newCol);
                        copiedVersion.transformColumns.add(newCol);
                    } else {
                        if (!hasInsert) {
                            TransformColumn newCol = createImplicitTransformColumn(column, IncludeOnType.INSERT, true);
                            copiedVersion.primaryKeyColumns.add(newCol);
                            copiedVersion.transformColumns.add(newCol);
                        }
                        if (!hasUpdate) {
                            TransformColumn newCol = createImplicitTransformColumn(column, IncludeOnType.UPDATE, true);
                            copiedVersion.primaryKeyColumns.add(newCol);
                            copiedVersion.transformColumns.add(newCol);
                        }
                        if (!hasDelete) {
                            TransformColumn newCol = createImplicitTransformColumn(column, IncludeOnType.DELETE, true);
                            copiedVersion.primaryKeyColumns.add(newCol);
                            copiedVersion.transformColumns.add(newCol);
                        }
                    }
                }
                for (String column : columnNames) {
                    boolean hasInsert = false;
                    boolean hasUpdate = false;
                    boolean hasDelete = false;
                    String columnLowerCase = column.toLowerCase();
                    for (TransformColumn xCol : copiedVersion.transformColumns) {
                        if ((StringUtils.isNotBlank(xCol.getSourceColumnName()) && columnLowerCase.equals(xCol.getSourceColumnNameLowerCase())) ||
                                (StringUtils.isNotBlank(xCol.getTargetColumnName()) && columnLowerCase.equals(xCol.getTargetColumnNameLowerCase()))) {
                            if (xCol.includeOn == IncludeOnType.ALL) {
                                hasInsert = hasUpdate = hasDelete = true;
                                break;
                            } else if (xCol.includeOn == IncludeOnType.INSERT) {
                                hasInsert = true;
                            } else if (xCol.includeOn == IncludeOnType.UPDATE) {
                                hasUpdate = true;
                            } else if (xCol.includeOn == IncludeOnType.DELETE) {
                                hasDelete = true;
                            }
                        }
                    }
                    if (!hasInsert && !hasUpdate && !hasDelete) {
                        TransformColumn newCol = createImplicitTransformColumn(column, IncludeOnType.ALL, false);
                        copiedVersion.transformColumns.add(newCol);
                    } else {
                        if (!hasInsert) {
                            TransformColumn newCol = createImplicitTransformColumn(column, IncludeOnType.INSERT, false);
                            copiedVersion.transformColumns.add(newCol);
                        }
                        if (!hasUpdate) {
                            TransformColumn newCol = createImplicitTransformColumn(column, IncludeOnType.UPDATE, false);
                            copiedVersion.transformColumns.add(newCol);
                        }
                        if (!hasDelete) {
                            TransformColumn newCol = createImplicitTransformColumn(column, IncludeOnType.DELETE, false);
                            copiedVersion.transformColumns.add(newCol);
                        }
                    }
                }
            }
            return copiedVersion;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    private TransformColumn createImplicitTransformColumn(String column, IncludeOnType includeOnType, boolean pk) {
        TransformColumn newCol = new TransformColumn();
        newCol.setTransformId(transformId);
        newCol.setPk(pk);
        newCol.setIncludeOn(includeOnType);
        newCol.setTransformType(CopyColumnTransform.NAME);
        newCol.setSourceColumnName(column);
        newCol.setTargetColumnName(column);
        return newCol;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
}
