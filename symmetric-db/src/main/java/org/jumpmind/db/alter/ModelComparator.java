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
package org.jumpmind.db.alter;

import java.math.BigDecimal;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Function;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.PlatformFunction;
import org.jumpmind.db.model.PlatformTrigger;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.platform.ase.AseDdlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares two database models and creates change objects that express how to adapt the first model so that it becomes the second one. Neither of the models
 * are changed in the process, however, it is also assumed that the models do not change in between.
 */
public class ModelComparator {
    /** The log for this comparator. */
    private final Logger log = LoggerFactory.getLogger(ModelComparator.class);
    /** The platform information. */
    protected DatabaseInfo platformInfo;
    /** Whether comparison is case sensitive. */
    protected boolean caseSensitive;
    protected IDdlBuilder ddlBuilder;

    /**
     * Creates a new model comparator object.
     * 
     * @param platformInfo
     *            The platform info
     * @param caseSensitive
     *            Whether comparison is case sensitive
     */
    public ModelComparator(IDdlBuilder ddlBuilder, DatabaseInfo platformInfo, boolean caseSensitive) {
        this.ddlBuilder = ddlBuilder;
        this.platformInfo = platformInfo;
        this.caseSensitive = caseSensitive;
    }

    private boolean supportsDefaultValues() {
        if (ddlBuilder instanceof AseDdlBuilder && ((AseDdlBuilder) ddlBuilder).isUsingJtds()) {
            return false;
        }
        return true;
    }

    /**
     * Compares the two models and returns the changes necessary to create the second model from the first one.
     * 
     * @param sourceModel
     *            The source model
     * @param targetModel
     *            The target model
     * @return The changes
     */
    public List<IModelChange> compare(Database sourceModel, Database targetModel) {
        ArrayList<IModelChange> changes = new ArrayList<IModelChange>();
        for (int tableIdx = 0; tableIdx < targetModel.getTableCount(); tableIdx++) {
            Table targetTable = targetModel.getTable(tableIdx);
            Table sourceTable = sourceModel.findTable(targetTable.getName(), caseSensitive);
            if (sourceTable == null) {
                log.info("Table {} needs to be added", targetTable.getName());
                changes.add(new AddTableChange(targetTable));
                if (platformInfo.isForeignKeysSupported()) {
                    for (int fkIdx = 0; fkIdx < targetTable.getForeignKeyCount(); fkIdx++) {
                        // we have to use target table's definition here because the
                        // complete table is new
                        changes.add(new AddForeignKeyChange(targetTable, targetTable
                                .getForeignKey(fkIdx)));
                    }
                }
            } else {
                changes.addAll(compareTables(sourceModel, sourceTable, targetModel, targetTable));
            }
        }
        for (int tableIdx = 0; tableIdx < sourceModel.getTableCount(); tableIdx++) {
            Table sourceTable = sourceModel.getTable(tableIdx);
            Table targetTable = targetModel.findTable(sourceTable.getName(), caseSensitive);
            if ((targetTable == null) && (sourceTable.getName() != null)
                    && (sourceTable.getName().length() > 0)) {
                log.info("Table {} needs to be removed", sourceTable.getName());
                changes.add(new RemoveTableChange(sourceTable));
                /*
                 * we assume that the target model is sound, ie. that there are no longer any foreign keys to this table in the target model; thus we already
                 * have removeFK changes for these from the compareTables method and we only need to create changes for the fks originating from this table
                 */
                if (platformInfo.isForeignKeysSupported()) {
                    for (int fkIdx = 0; fkIdx < sourceTable.getForeignKeyCount(); fkIdx++) {
                        changes.add(new RemoveForeignKeyChange(sourceTable, sourceTable
                                .getForeignKey(fkIdx)));
                    }
                }
            }
        }
        return changes;
    }

    /**
     * Compares the two tables and returns the changes necessary to create the second table from the first one.
     * 
     * @param sourceModel
     *            The source model which contains the source table
     * @param sourceTable
     *            The source table
     * @param targetModel
     *            The target model which contains the target table
     * @param targetTable
     *            The target table
     * @return The changes
     */
    public List<IModelChange> compareTables(Database sourceModel, Table sourceTable,
            Database targetModel, Table targetTable) {
        ArrayList<IModelChange> changes = new ArrayList<IModelChange>();
        detectLoggingChanges(sourceModel, sourceTable, targetModel, targetTable, changes);
        detectForeignKeyChanges(sourceModel, sourceTable, targetModel, targetTable, changes);
        detectIndexChanges(sourceModel, sourceTable, targetModel, targetTable, changes);
        detectColumnChanges(sourceModel, sourceTable, targetModel, targetTable, changes);
        detectPrimaryKeyChanges(sourceModel, sourceTable, targetModel, targetTable, changes);
        detectTriggerChanges(sourceModel, sourceTable, targetModel, targetTable, changes);
        return changes;
    }

    /**
     * Compares tables and appends detected logging mode changes (necessary to create the targetTable from the sourceTable) to specified list.
     */
    public void detectLoggingChanges(Database sourceModel, Table sourceTable,
            Database targetModel, Table targetTable, ArrayList<IModelChange> changes) {
        if (!platformInfo.isTableLevelLoggingSupported()) {
            return;
        }
        if (sourceTable.getLogging() == targetTable.getLogging()) {
            log.debug("Logging mode remains unchanged for table {}", sourceTable.getName());
            return;
        }
        if (!sourceTable.getLogging() && targetTable.getLogging()) {
            log.debug("Logging needs to be added to table {}", sourceTable.getName());
            changes.add(new AddTableLoggingChange(sourceTable));
            return;
        }
        log.debug("Logging needs to be removed from table {}", sourceTable.getName());
        changes.add(new RemoveTableLoggingChange(sourceTable));
    }

    /**
     * Compares tables and appends detected ForeignKey changes (necessary to create the targetTable from the sourceTable) to specified list.
     */
    public void detectForeignKeyChanges(Database sourceModel, Table sourceTable,
            Database targetModel, Table targetTable, ArrayList<IModelChange> changes) {
        if (!platformInfo.isForeignKeysSupported()) {
            return;
        }
        for (int fkIdx = 0; fkIdx < sourceTable.getForeignKeyCount(); fkIdx++) {
            ForeignKey sourceFk = sourceTable.getForeignKey(fkIdx);
            ForeignKey targetFk = findCorrespondingForeignKey(targetTable, sourceFk);
            if (targetFk == null) {
                log.info("{} needs to be removed from table {}", sourceFk, sourceTable.getName());
                changes.add(new RemoveForeignKeyChange(sourceTable, sourceFk));
            }
        }
        for (int fkIdx = 0; fkIdx < targetTable.getForeignKeyCount(); fkIdx++) {
            ForeignKey targetFk = targetTable.getForeignKey(fkIdx);
            ForeignKey sourceFk = findCorrespondingForeignKey(sourceTable, targetFk);
            if (sourceFk == null) {
                log.info("{} needs to be created for table {}", targetFk, sourceTable.getName());
                /*
                 * we have to use the target table here because the foreign key might reference a new column
                 */
                changes.add(new AddForeignKeyChange(targetTable, targetFk));
            }
        }
    }

    /**
     * Compares tables and appends detected index changes (necessary to create the targetTable from the sourceTable) to specified list.
     */
    public void detectIndexChanges(Database sourceModel, Table sourceTable,
            Database targetModel, Table targetTable, ArrayList<IModelChange> changes) {
        if (!platformInfo.isIndicesSupported()) {
            return;
        }
        for (int indexIdx = 0; indexIdx < sourceTable.getIndexCount(); indexIdx++) {
            IIndex sourceIndex = sourceTable.getIndex(indexIdx);
            IIndex targetIndex = findCorrespondingIndex(targetTable, sourceIndex);
            if (targetIndex == null) {
                log.info("Index {} needs to be removed from table {}", sourceIndex.getName(), sourceTable.getName());
                changes.add(new RemoveIndexChange(sourceTable, sourceIndex));
            }
        }
        for (int indexIdx = 0; indexIdx < targetTable.getIndexCount(); indexIdx++) {
            IIndex targetIndex = targetTable.getIndex(indexIdx);
            IIndex sourceIndex = findCorrespondingIndex(sourceTable, targetIndex);
            if (sourceIndex == null) {
                log.info("Index {} needs to be created for table {}", targetIndex.getName(), sourceTable.getName());
                // we have to use the target table here because the index might
                // reference a new column
                changes.add(new AddIndexChange(targetTable, targetIndex));
            } else {
                if (!new EqualsBuilder().append(new HashSet<IIndex>(Arrays.asList(targetIndex)), new HashSet<IIndex>(Arrays.asList(sourceIndex))).isEquals()) {
                    log.info("Index {} needs to be modified (removed/created) for table {}", targetIndex.getName(), sourceTable.getName());
                    changes.add(new RemoveIndexChange(sourceTable, sourceIndex));
                    changes.add(new AddIndexChange(targetTable, targetIndex));
                }
            }
        }
    }

    /**
     * Compares tables and appends detected column changes (necessary to create the targetTable from the sourceTable) to specified list.
     */
    public void detectColumnChanges(Database sourceModel, Table sourceTable,
            Database targetModel, Table targetTable, ArrayList<IModelChange> changes) {
        HashMap<Column, TableChange> addColumnChanges = new HashMap<Column, TableChange>();
        for (int columnIdx = 0; columnIdx < targetTable.getColumnCount(); columnIdx++) {
            Column targetColumn = targetTable.getColumn(columnIdx);
            Column sourceColumn = sourceTable.findColumn(targetColumn.getName(), caseSensitive);
            if (sourceColumn == null) {
                log.info("Column {} needs to be created for table {}", targetColumn.getName(), sourceTable.getName());
                AddColumnChange change = new AddColumnChange(sourceTable, targetColumn,
                        columnIdx > 0 ? targetTable.getColumn(columnIdx - 1) : null,
                        columnIdx < targetTable.getColumnCount() - 1 ? targetTable
                                .getColumn(columnIdx + 1) : null);
                changes.add(change);
                addColumnChanges.put(targetColumn, change);
            } else {
                changes.addAll(compareColumns(sourceTable, sourceColumn, targetTable, targetColumn));
            }
        }
        // if the last columns in the target table are added, then we note this
        // at the changes
        for (int columnIdx = targetTable.getColumnCount() - 1; columnIdx >= 0; columnIdx--) {
            Column targetColumn = targetTable.getColumn(columnIdx);
            AddColumnChange change = (AddColumnChange) addColumnChanges.get(targetColumn);
            if (change == null) {
                // column was not added, so we can ignore any columns before it
                // that were added
                break;
            } else {
                change.setAtEnd(true);
            }
        }
    }

    /**
     * Compares tables and appends detected primary key & related column changes (necessary to create the targetTable from the sourceTable) to specified list.
     */
    public void detectPrimaryKeyChanges(Database sourceModel, Table sourceTable,
            Database targetModel, Table targetTable, ArrayList<IModelChange> changes) {
        Column[] sourcePK = sourceTable.getPrimaryKeyColumnsInIndexOrder();
        Column[] targetPK = targetTable.getPrimaryKeyColumnsInIndexOrder();
        if ((sourcePK.length == 0) && (targetPK.length > 0)) {
            log.info("A primary key needs to be added to the table {}", sourceTable.getName());
            // we have to use the target table here because the primary key
            // might
            // reference a new column
            changes.add(new AddPrimaryKeyChange(targetTable, targetPK));
        } else if ((targetPK.length == 0) && (sourcePK.length > 0)) {
            log.info("The primary key needs to be removed from the table {}", sourceTable.getName());
            changes.add(new RemovePrimaryKeyChange(sourceTable, sourcePK));
        } else if ((sourcePK.length > 0) && (targetPK.length > 0)) {
            boolean changePK = false;
            if (sourcePK.length != targetPK.length) {
                changePK = true;
            } else {
                for (int pkColumnIdx = 0; (pkColumnIdx < sourcePK.length) && !changePK; pkColumnIdx++) {
                    if ((caseSensitive && !sourcePK[pkColumnIdx].getName().equals(
                            targetPK[pkColumnIdx].getName()))
                            || (!caseSensitive && !sourcePK[pkColumnIdx].getName()
                                    .equalsIgnoreCase(targetPK[pkColumnIdx].getName()))) {
                        changePK = true;
                    }
                }
            }
            if (changePK) {
                log.info("The primary key of table {} needs to be changed", sourceTable.getName());
                changes.add(new PrimaryKeyChange(sourceTable, sourcePK, targetPK));
            }
        }
        for (int columnIdx = 0; columnIdx < sourceTable.getColumnCount(); columnIdx++) {
            Column sourceColumn = sourceTable.getColumn(columnIdx);
            Column targetColumn = targetTable.findColumn(sourceColumn.getName(), caseSensitive);
            if (targetColumn == null) {
                log.info("Column {} needs to be removed from table {}", sourceColumn.getName(), sourceTable.getName());
                changes.add(new RemoveColumnChange(sourceTable, sourceColumn));
            }
        }
    }

    public void detectTriggerChanges(Database sourceModel, Table sourceTable,
            Database targetModel, Table targetTable, ArrayList<IModelChange> changes) {
        Map<String, Trigger> sourceTriggersMap = new HashMap<String, Trigger>();
        Map<String, Trigger> targetTriggersMap = new HashMap<String, Trigger>();
        String platformName = targetModel.getName();
        if (sourceTable.getTriggers() != null) {
            for (Trigger trigger : sourceTable.getTriggers()) {
                sourceTriggersMap.put(trigger.getName(), trigger);
            }
        }
        if (targetTable.getTriggers() != null) {
            for (Trigger trigger : targetTable.getTriggers()) {
                targetTriggersMap.put(trigger.getName(), trigger);
            }
        }
        for (Map.Entry<String, Trigger> entry : sourceTriggersMap.entrySet()) {
            Trigger sourceTrigger = entry.getValue();
            if (targetTriggersMap.containsKey(entry.getKey())) {
                Trigger targetTrigger = targetTriggersMap.get(entry.getKey());
                if (sourceTrigger.getPlatformTriggers().containsKey(platformName)) {
                    PlatformTrigger targetPlatformTrigger = targetTrigger.findPlatformTrigger(platformName);
                    PlatformTrigger sourcePlatformTrigger = sourceTrigger.findPlatformTrigger(platformName);
                    boolean triggerTextChanged = !StringUtils.equals(targetPlatformTrigger.getTriggerText(), sourcePlatformTrigger.getTriggerText());
                    String targetFunctionText = getFunctionText(targetPlatformTrigger, platformName);
                    String sourceFunctionText = getFunctionText(sourcePlatformTrigger, platformName);
                    boolean functionTextChanged = !StringUtils.equals(targetFunctionText, sourceFunctionText);
                    if (triggerTextChanged || functionTextChanged) {
                        changes.add(new RemoveTriggerChange(sourceTable, sourceTrigger));
                        changes.add(new RemoveFunctionChange(sourceTable, sourceTrigger, sourcePlatformTrigger.getFunction()));
                        changes.add(new AddFunctionChange(targetTable, targetTrigger, targetPlatformTrigger.getFunction()));
                        changes.add(new AddTriggerChange(targetTable, targetTrigger));
                    }
                }
            } else {
                changes.add(new RemoveTriggerChange(sourceTable, sourceTrigger));
                if (sourceTrigger.getPlatformTriggers().containsKey(platformName)) {
                    PlatformTrigger sourcePlatformTrigger = sourceTrigger.findPlatformTrigger(platformName);
                    Function sourceFunction = sourcePlatformTrigger.getFunction();
                    if (sourceFunction != null) {
                        changes.add(new RemoveFunctionChange(sourceTable, sourceTrigger, sourceFunction));
                    }
                }
            }
        }
        for (Map.Entry<String, Trigger> entry : targetTriggersMap.entrySet()) {
            if (!sourceTriggersMap.containsKey(entry.getKey())) {
                Trigger targetTrigger = entry.getValue();
                if (targetTrigger.getPlatformTriggers().containsKey(platformName)) {
                    PlatformTrigger platformTrigger = targetTrigger.getPlatformTriggers().get(platformName);
                    Function targetFunction = platformTrigger.getFunction();
                    if (targetFunction != null) {
                        changes.add(new AddFunctionChange(targetTable, targetTrigger, targetFunction));
                    }
                }
                changes.add(new AddTriggerChange(targetTable, entry.getValue()));
            }
        }
    }

    private String getFunctionText(PlatformTrigger platformTrigger, String databaseName) {
        String functionText = null;
        if (platformTrigger != null && platformTrigger.getFunction() != null) {
            Function function = platformTrigger.getFunction();
            if (function.getPlatformFunctions().containsKey(databaseName)) {
                PlatformFunction platformFunction = function.getPlatformFunctions().get(databaseName);
                if (platformFunction != null) {
                    functionText = platformFunction.getFunctionText();
                }
            }
        }
        return functionText;
    }

    /**
     * Compares the two columns and returns the changes necessary to create the second column from the first one.
     * 
     * @param sourceTable
     *            The source table which contains the source column
     * @param sourceColumn
     *            The source column
     * @param targetTable
     *            The target table which contains the target column
     * @param targetColumn
     *            The target column
     * @return The changes
     */
    public List<TableChange> compareColumns(Table sourceTable, Column sourceColumn,
            Table targetTable, Column targetColumn) {
        ArrayList<TableChange> changes = new ArrayList<TableChange>();
        int actualTypeCode = sourceColumn.getMappedTypeCode();
        int desiredTypeCode = targetColumn.getMappedTypeCode();
        boolean sizeMatters = platformInfo.hasSize(targetColumn.getMappedTypeCode());
        boolean scaleMatters = platformInfo.hasPrecisionAndScale(targetColumn.getMappedTypeCode());
        boolean compatible = (actualTypeCode == Types.NUMERIC || actualTypeCode == Types.DECIMAL) &&
                (desiredTypeCode == Types.INTEGER || desiredTypeCode == Types.BIGINT);
        if (sourceColumn.isAutoIncrement() && targetColumn.isAutoIncrement() &&
                (desiredTypeCode == Types.NUMERIC || desiredTypeCode == Types.DECIMAL) &&
                (actualTypeCode == Types.INTEGER || actualTypeCode == Types.BIGINT)) {
            compatible = true;
            // This is the rare case where size doesnt matter!
            sizeMatters = false;
            scaleMatters = false;
        }
        if (sourceColumn.getMappedTypeCode() == Types.BLOB && targetColumn.getMappedTypeCode() == Types.LONGVARCHAR) {
            // This is probably a conversion from CLOB to BLOB because the database collation is set to binary
            compatible = true;
        }
        if (!compatible && !ddlBuilder.areMappedTypesTheSame(sourceColumn, targetColumn)
                && platformInfo.getTargetJdbcType(targetColumn.getMappedTypeCode()) != platformInfo.getTargetJdbcType(sourceColumn.getMappedTypeCode())) {
            log.info("The {} column on the {} table changed type codes from {} to {} ", sourceColumn.getName(), sourceTable.getName(),
                    sourceColumn.getMappedTypeCode(), targetColumn.getMappedTypeCode());
            changes.add(new ColumnDataTypeChange(sourceTable, sourceColumn, targetColumn
                    .getMappedTypeCode()));
        }
        if (!ddlBuilder.areColumnSizesTheSame(sourceColumn, targetColumn)) {
            if (sizeMatters) {
                int targetSize = targetColumn.getSizeAsInt();
                if (targetColumn.getSize() == null) {
                    Integer defaultSize = platformInfo.getDefaultSize(platformInfo.getTargetJdbcType(targetColumn.getMappedTypeCode()));
                    if (defaultSize != null) {
                        targetSize = defaultSize;
                    } else {
                        targetSize = 0;
                    }
                }
                log.info("The {} column on the {} table changed size from ({}) to ({})", sourceColumn.getName(),
                        sourceTable.getName(), sourceColumn.getSizeAsInt(), targetSize);
                changes.add(new ColumnSizeChange(sourceTable, sourceColumn, targetSize, targetColumn.getScale()));
            } else if (scaleMatters) {
                log.info("The {} column on the {} table changed scale from ({},{}) to ({},{})", sourceColumn.getName(), sourceTable.getName(),
                        sourceColumn.getSizeAsInt(), sourceColumn.getScale(), targetColumn.getSizeAsInt(), targetColumn.getScale());
                changes.add(new ColumnSizeChange(sourceTable, sourceColumn, targetColumn.getSizeAsInt(), targetColumn.getScale()));
            }
        }
        if (supportsDefaultValues() && !defaultValuesAreEqual(sourceColumn, targetColumn)) {
            log.info("The {} column on the {} table changed default value from {} to {} ", sourceColumn.getName(), sourceTable.getName(),
                    sourceColumn.getDefaultValue(), targetColumn.getDefaultValue());
            changes.add(new ColumnDefaultValueChange(sourceTable, sourceColumn, targetColumn.getDefaultValue()));
        }
        if (!targetColumn.isGenerated() && sourceColumn.isRequired() != targetColumn.isRequired()) {
            log.info("The {} column on the {} table changed required status from {} to {}", sourceColumn.getName(), sourceTable.getName(),
                    sourceColumn.isRequired(), targetColumn.isRequired());
            changes.add(new ColumnRequiredChange(sourceTable, sourceColumn));
        }
        if (sourceColumn.isAutoIncrement() != targetColumn.isAutoIncrement()) {
            log.info("The {} column on the {} table changed auto increment status from {} to {} ", sourceColumn.getName(), sourceTable.getName(),
                    sourceColumn.isAutoIncrement(), targetColumn.isAutoIncrement());
            changes.add(new ColumnAutoIncrementChange(sourceTable, sourceColumn));
        }
        if (sourceColumn.isAutoUpdate() != targetColumn.isAutoUpdate()) {
            log.info("The {} column on the {} table changed auto update status from {} to {} ", sourceColumn.getName(), sourceTable.getName(),
                    sourceColumn.isAutoUpdate(), targetColumn.isAutoUpdate());
            changes.add(new ColumnAutoUpdateChange(sourceTable, sourceColumn));
        }
        if (sourceColumn.isGenerated() != targetColumn.isGenerated()) {
            log.info("The {} column on the {} table changed generated status from {} to {} ", sourceColumn.getName(), sourceTable.getName(),
                    sourceColumn.isGenerated(), targetColumn.isGenerated());
            changes.add(new ColumnGeneratedChange(sourceTable, sourceColumn, targetColumn.getDefaultValue()));
        } else if (Boolean.valueOf(System.getProperty("compare.generated.column.definitions", "true"))
                && sourceColumn.isGenerated() && sourceColumn.getDefaultValue() != null
                && !sourceColumn.getDefaultValue().equals(targetColumn.getDefaultValue())) {
            log.info("The {} generated column on the {} table changed definition from {} to {} ", sourceColumn.getName(), sourceTable.getName(),
                    sourceColumn.getDefaultValue(), targetColumn.getDefaultValue());
            changes.add(new GeneratedColumnDefinitionChange(sourceTable, sourceColumn, targetColumn.getDefaultValue()));
        }
        return changes;
    }

    /**
     * Searches in the given table for a corresponding foreign key. If the given key has no name, then a foreign key to the same table with the same columns
     * (but not necessarily in the same order) is searched. If the given key has a name, then the corresponding key also needs to have the same name, or no name
     * at all, but not a different one.
     * 
     * @param table
     *            The table to search in
     * @param fk
     *            The original foreign key
     * @return The corresponding foreign key if found
     */
    private ForeignKey findCorrespondingForeignKey(Table table, ForeignKey fk) {
        for (int fkIdx = 0; fkIdx < table.getForeignKeyCount(); fkIdx++) {
            ForeignKey curFk = table.getForeignKey(fkIdx);
            if ((caseSensitive && fk.equals(curFk))
                    || (!caseSensitive && fk.equalsIgnoreCase(curFk))) {
                return curFk;
            }
        }
        return null;
    }

    /**
     * Searches in the given table for a corresponding index. If the given index has no name, then a index to the same table with the same columns in the same
     * order is searched. If the given index has a name, then the a corresponding index also needs to have the same name, or no name at all, but not a different
     * one.
     * 
     * @param table
     *            The table to search in
     * @param index
     *            The original index
     * @return The corresponding index if found
     */
    private IIndex findCorrespondingIndex(Table table, IIndex index) {
        for (int indexIdx = 0; indexIdx < table.getIndexCount(); indexIdx++) {
            IIndex curIndex = table.getIndex(indexIdx);
            if ((caseSensitive && index.equals(curIndex))
                    || (!caseSensitive && index.equalsIgnoreCase(curIndex))) {
                return curIndex;
            }
        }
        return null;
    }

    private boolean defaultValuesAreEqual(Column sourceColumn, Column targetColumn) {
        Object sourceDefaultValue = sourceColumn.getParsedDefaultValue();
        Object targetDefaultValue = targetColumn.getParsedDefaultValue();
        if (!targetColumn.isGenerated() && !(sourceDefaultValue == null && targetDefaultValue == null)) {
            if ((sourceDefaultValue == null && targetDefaultValue != null)
                    || (sourceDefaultValue != null && targetDefaultValue == null)) {
                return false;
            }
            boolean isBigDecimal = sourceDefaultValue instanceof BigDecimal && targetDefaultValue instanceof BigDecimal;
            if ((isBigDecimal && ((BigDecimal) sourceDefaultValue).compareTo((BigDecimal) targetDefaultValue) != 0)) {
                return false;
            }
            if (!isBigDecimal) {
                String sourceDefaultValueString = sourceDefaultValue.toString();
                String targetDefaultValueString = ddlBuilder.mapDefaultValue(targetDefaultValue, targetColumn).toString();
                if (!sourceDefaultValueString.equals(targetDefaultValueString)) {
                    int typeCode = targetColumn.getMappedTypeCode();
                    if (typeCode == Types.TIMESTAMP || typeCode == ColumnTypes.TIMESTAMPTZ || typeCode == ColumnTypes.TIMESTAMPLTZ) {
                        if (targetColumn.anyPlatformColumnNameContains("mysql") || targetColumn.anyPlatformColumnNameContains("maria")) {
                            while (targetDefaultValueString.startsWith("(") && targetDefaultValueString.endsWith(")")) {
                                targetDefaultValueString = targetDefaultValueString.substring(1, targetDefaultValueString.length() - 1);
                            }
                        }
                        if (targetColumn.anyPlatformColumnNameContains("postgres")) {
                            sourceDefaultValueString = sourceDefaultValueString.replace("::text", "");
                        }
                        return sourceDefaultValueString.equalsIgnoreCase(targetDefaultValueString);
                    } else if (sourceColumn.isOfNumericType() && targetColumn.isOfNumericType()) {
                        if (sourceColumn.anyPlatformColumnNameContains("postgres")) {
                            sourceDefaultValueString = sourceDefaultValueString.replace("'", "").replace("::integer", "");
                        }
                        return sourceDefaultValueString.equalsIgnoreCase(targetDefaultValueString);
                    }
                    return false;
                }
            }
        }
        return true;
    }
}
