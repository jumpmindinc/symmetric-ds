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
package org.jumpmind.symmetric.extract;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.SchemaObject;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.transform.ColumnPolicy;
import org.jumpmind.symmetric.io.data.transform.RemoveColumnTransform;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformTable;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.jumpmind.symmetric.util.SymmetricUtils;

public class ColumnsAccordingToTriggerHistory {
    private static Map<String, Map<String, Relation>> cacheByEngine = new ConcurrentHashMap<>();
    private Map<CacheKey, Relation> cache = new HashMap<>();
    private ISymmetricEngine engine;
    private Node sourceNode;
    private Node targetNode;
    private ITriggerRouterService triggerRouterService;
    private ITransformService transformService;
    private ISymmetricDialect symmetricDialect;
    private String tablePrefix;
    private boolean isUsingTargetExternalId;

    public ColumnsAccordingToTriggerHistory(ISymmetricEngine engine, Node sourceNode, Node targetNode) {
        this.engine = engine;
        triggerRouterService = engine.getTriggerRouterService();
        transformService = engine.getTransformService();
        symmetricDialect = engine.getSymmetricDialect();
        tablePrefix = engine.getTablePrefix().toLowerCase();
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }

    public Relation lookup(String routerId, TriggerHistory triggerHistory, boolean setTargetTableName,
            boolean useDatabaseDefinition, boolean useTransforms, boolean addMissingColumns) {
        CacheKey key = new CacheKey(routerId, triggerHistory.getTriggerHistoryId(), setTargetTableName,
                useDatabaseDefinition, useTransforms, addMissingColumns);
        Relation relation = cache.get(key);
        if (relation == null) {
            relation = lookupAndOrderColumnsAccordingToTriggerHistory(routerId, triggerHistory, setTargetTableName,
                    useDatabaseDefinition, useTransforms, addMissingColumns);
            cache.put(key, relation);
        }
        return relation;
    }

    protected Relation lookupAndOrderColumnsAccordingToTriggerHistory(String routerId, TriggerHistory triggerHistory,
            boolean setTargetTableName, boolean useDatabaseDefinition, boolean useTransforms, boolean addMissingColumns) {
        String catalogName = triggerHistory.getSourceCatalogName();
        String schemaName = triggerHistory.getSourceSchemaName();
        String tableName = triggerHistory.getSourceTableName();
        String tableNameLowerCase = triggerHistory.getSourceTableNameLowerCase();
        Relation relation = lookupRelationForTriggerHistory(triggerHistory, tableName, tableNameLowerCase, catalogName,
                schemaName, useDatabaseDefinition, addMissingColumns);
        Router router = triggerRouterService.getRouterById(routerId, false);
        if (router != null && setTargetTableName) {
            applyRouterTargetNames(relation, router, catalogName, schemaName);
        }
        if (useTransforms) {
            applyTransforms(relation, TransformPoint.EXTRACT);
            applyTransforms(relation, TransformPoint.LOAD);
        }
        return relation;
    }

    private Relation lookupRelationForTriggerHistory(TriggerHistory triggerHistory, String tableName, String tableNameLowerCase,
            String catalogName, String schemaName, boolean useDatabaseDefinition, boolean addMissingColumns) {
        if (!useDatabaseDefinition) {
            Relation relation = new Table(tableName);
            relation.addColumns(triggerHistory.getParsedColumnNames());
            relation.setPrimaryKeys(triggerHistory.getParsedPkColumnNames());
            return relation;
        }
        if (isUsingTargetExternalId && !tableName.startsWith(tablePrefix)) {
            return lookupRelationExpanded(getTargetPlatform(tableNameLowerCase), catalogName, schemaName, tableName, triggerHistory, addMissingColumns);
        }
        return lookupRelation(getTargetPlatform(tableNameLowerCase), catalogName, schemaName, tableName, triggerHistory, addMissingColumns);
    }

    private void applyRouterTargetNames(Relation relation, Router router, String catalogName, String schemaName) {
        if (router.isUseSourceCatalogSchema()) {
            relation.setCatalog(catalogName);
            relation.setSchema(schemaName);
        } else {
            relation.setCatalog(null);
            relation.setSchema(null);
        }
        applyRouterTargetCatalog(relation, router, catalogName, schemaName);
        applyRouterTargetSchema(relation, router, catalogName, schemaName);
        if (StringUtils.isNotBlank(router.getTargetTableName())) {
            relation.setName(router.getTargetTableName());
        }
    }

    private void applyRouterTargetCatalog(Relation relation, Router router, String catalogName, String schemaName) {
        if (Strings.CS.equals(Constants.NONE_TOKEN, router.getTargetCatalogName())) {
            relation.setCatalog(null);
        } else if (StringUtils.isNotBlank(router.getTargetCatalogName())) {
            relation.setCatalog(SymmetricUtils.replaceCatalogSchemaVariables(catalogName,
                    symmetricDialect.getTargetPlatform().getDefaultCatalog(),
                    schemaName,
                    symmetricDialect.getTargetPlatform().getDefaultSchema(),
                    router.getTargetCatalogName()));
        }
    }

    private void applyRouterTargetSchema(Relation relation, Router router, String catalogName, String schemaName) {
        if (Strings.CS.equals(Constants.NONE_TOKEN, router.getTargetSchemaName())) {
            relation.setSchema(null);
        } else if (StringUtils.isNotBlank(router.getTargetSchemaName())) {
            relation.setSchema(SymmetricUtils.replaceCatalogSchemaVariables(catalogName,
                    symmetricDialect.getTargetPlatform().getDefaultCatalog(),
                    schemaName,
                    symmetricDialect.getTargetPlatform().getDefaultSchema(),
                    router.getTargetSchemaName()));
        }
    }

    private void applyTransforms(Relation relation, TransformPoint transformPoint) {
        TransformTable transform = getTransform(relation, transformPoint, Integer.MIN_VALUE);
        while (transform != null) {
            applyTransform(relation, transform);
            transform = getTransform(relation, transformPoint, transform.getTransformOrder() + 1);
        }
    }

    protected IDatabasePlatform getTargetPlatform(String tableName) {
        return tableName.startsWith(tablePrefix) ? symmetricDialect.getPlatform() : symmetricDialect.getTargetDialect().getPlatform();
    }

    protected Relation lookupRelation(IDatabasePlatform platform, String catalogName, String schemaName, String relationName, TriggerHistory triggerHistory,
            boolean addMissingColumns) {
        Relation relation = platform.getRelationFromCache(catalogName, schemaName, relationName, false);
        if (relation != null && relation.getColumnCount() < triggerHistory.getParsedColumnNames().length) {
            /*
             * If the column count is less than what trigger history reports, then chances are the table cache is out of date.
             */
            relation = platform.getRelationFromCache(catalogName, schemaName, relationName, true);
        }
        if (relation != null) {
            relation = relation.copyAndFilterColumns(triggerHistory.getParsedColumnNames(),
                    triggerHistory.getParsedPkColumnNames(), true, addMissingColumns);
        } else {
            throw new SymmetricException("Could not find the following table.  It might have been dropped: %s",
                    SchemaObject.getFullyQualifiedName(catalogName, schemaName, relationName));
        }
        return relation;
    }

    protected Relation lookupRelationExpanded(IDatabasePlatform platform, String catalogName, String schemaName, String relationName,
            TriggerHistory triggerHistory, boolean addMissingColumns) {
        Relation relation = null;
        if (!relationName.contains(targetNode.getExternalId())) {
            relation = lookupRelation(platform, catalogName, schemaName, relationName, triggerHistory, addMissingColumns);
        } else {
            String baseTableName = relationName.replace(targetNode.getExternalId(), "") + (addMissingColumns ? "-t" : "-f");
            Map<String, Relation> sourceRelationMap = getSourceRelationMap(engine.getEngineName());
            relation = sourceRelationMap.get(baseTableName);
            if (relation == null) {
                relation = lookupRelation(platform, catalogName, schemaName, relationName, triggerHistory, addMissingColumns);
                sourceRelationMap.put(baseTableName, relation);
            } else {
                relation = relation.copyAndFilterColumns(triggerHistory.getParsedColumnNames(),
                        triggerHistory.getParsedPkColumnNames(), true, addMissingColumns);
            }
            if (relation != null) {
                relation.setName(relationName);
            }
        }
        return relation;
    }

    protected Map<String, Relation> getSourceRelationMap(String engineName) {
        return cacheByEngine.computeIfAbsent(engineName, k -> new ConcurrentHashMap<>());
    }

    protected TransformTable getTransform(Relation relation, TransformPoint transformPoint, int order) {
        List<TransformTableNodeGroupLink> transforms = transformService.findTransformsFor(sourceNode.getNodeGroupId(),
                targetNode.getNodeGroupId(), relation.getName(), relation.getSchema(), relation.getCatalog());
        if (transforms != null) {
            for (TransformTableNodeGroupLink transform : transforms) {
                if (Strings.CS.equals(transform.getSourceCatalogName(), relation.getCatalog())
                        && Strings.CS.equals(transform.getSourceSchemaName(), relation.getSchema())
                        && transform.getTransformPoint().equals(transformPoint) && transform.getTransformOrder() >= order) {
                    return transform;
                }
            }
        }
        return null;
    }

    protected void applyTransform(Relation relation, TransformTable transform) {
        List<String> columnNamesToRemoveList = new ArrayList<>();
        if (transform.getColumnPolicy().equals(ColumnPolicy.SPECIFIED)) {
            columnNamesToRemoveList.addAll(Arrays.asList(relation.getColumnNames()));
        }
        List<TransformColumn> transformColumns = transform.getTransformColumns();
        if (transformColumns != null) {
            for (TransformColumn transformColumn : transformColumns) {
                applyTransformColumn(relation, transformColumn, columnNamesToRemoveList);
            }
        }
        for (String columnName : columnNamesToRemoveList) {
            relation.removeColumn(relation.getColumnIndex(columnName));
        }
        relation.setCatalog(transform.getTargetCatalogName());
        relation.setSchema(transform.getTargetSchemaName());
        relation.setName(transform.getTargetTableName());
    }

    private void applyTransformColumn(Relation relation, TransformColumn transformColumn, List<String> columnNamesToRemoveList) {
        if (StringUtils.isNotBlank(transformColumn.getSourceColumnName())) {
            Column column = relation.getColumnWithName(transformColumn.getSourceColumnName());
            if (column != null) {
                columnNamesToRemoveList.remove(column.getName());
                column.setName(transformColumn.getTargetColumnName());
                if (RemoveColumnTransform.NAME.equals(transformColumn.getTransformType())) {
                    columnNamesToRemoveList.add(column.getName());
                } else {
                    columnNamesToRemoveList.remove(column.getName());
                }
                column.setPrimaryKey(transformColumn.isPk());
            }
        } else {
            Column column = new Column(transformColumn.getTargetColumnName());
            column.setPrimaryKey(transformColumn.isPk());
            column.setTypeCode(Types.VARCHAR);
            column.setJdbcTypeCode(Types.VARCHAR);
            column.setJdbcTypeName("VARCHAR");
            column.setSize("100");
            relation.addColumn(column);
            columnNamesToRemoveList.remove(column.getName());
        }
    }

    static class CacheKey {
        private String routerId;
        private int triggerHistoryId;
        private boolean setTargetTableName;
        private boolean useDatabaseDefinition;
        private boolean useTransforms;
        private boolean addMissingColumns;

        public CacheKey(String routerId, int triggerHistoryId, boolean setTargetTableName,
                boolean useDatabaseDefinition, boolean useTransforms, boolean addMissingColumns) {
            this.routerId = routerId;
            this.triggerHistoryId = triggerHistoryId;
            this.setTargetTableName = setTargetTableName;
            this.useDatabaseDefinition = useDatabaseDefinition;
            this.useTransforms = useTransforms;
            this.addMissingColumns = addMissingColumns;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((routerId == null) ? 0 : routerId.hashCode());
            result = prime * result + (setTargetTableName ? 1231 : 1237);
            result = prime * result + triggerHistoryId;
            result = prime * result + (useDatabaseDefinition ? 1231 : 1237);
            result = prime * result + (useTransforms ? 1231 : 1237);
            result = prime * result + (addMissingColumns ? 1231 : 1237);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            if (routerId == null) {
                if (other.routerId != null) {
                    return false;
                }
            } else if (!routerId.equals(other.routerId)) {
                return false;
            }
            if (setTargetTableName != other.setTargetTableName) {
                return false;
            }
            if (triggerHistoryId != other.triggerHistoryId) {
                return false;
            }
            if (useDatabaseDefinition != other.useDatabaseDefinition) {
                return false;
            }
            if (useTransforms != other.useTransforms) {
                return false;
            }
            if (addMissingColumns != other.addMissingColumns) {
                return false;
            }
            return true;
        }
    }
}
