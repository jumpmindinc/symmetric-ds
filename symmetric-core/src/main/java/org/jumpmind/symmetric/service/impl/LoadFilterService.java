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
package org.jumpmind.symmetric.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.SchemaObject;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.LoadFilter.LoadFilterType;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.ILoadFilterService;
import org.jumpmind.util.FormatUtils;

public class LoadFilterService extends AbstractService implements ILoadFilterService {
    private Date lastUpdateTime;
    private IConfigurationService configurationService;
    private ICacheManager cacheManager;

    public LoadFilterService(ISymmetricEngine engine, ISymmetricDialect symmetricDialect) {
        super(engine.getParameterService(), symmetricDialect);
        this.configurationService = engine.getConfigurationService();
        this.cacheManager = engine.getCacheManager();
        setSqlMap(new LoadFilterServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
    }

    @Override
    public Map<LoadFilterType, Map<String, List<LoadFilter>>> findLoadFiltersFor(NodeGroupLink nodeGroupLink,
            boolean useCache) {
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> data = cacheManager.findLoadFilters(nodeGroupLink, useCache);
        if (data != null) {
            return data.get(nodeGroupLink);
        }
        return null;
    }

    @Override
    public Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> findLoadFiltersFromDb() {
        Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> data = new HashMap<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>>();
        List<LoadFilterNodeGroupLink> loadFilters = getLoadFiltersFromDB();
        boolean ignoreCase = this.parameterService
                .is(ParameterConstants.DB_METADATA_IGNORE_CASE);
        for (LoadFilterNodeGroupLink loadFilter : loadFilters) {
            NodeGroupLink nodeGroupLink = loadFilter.getNodeGroupLink();
            if (nodeGroupLink != null) {
                Map<LoadFilterType, Map<String, List<LoadFilter>>> loadFiltersByType = data
                        .get(nodeGroupLink);
                if (loadFiltersByType == null) {
                    loadFiltersByType = new HashMap<LoadFilterType, Map<String, List<LoadFilter>>>();
                    data.put(nodeGroupLink, loadFiltersByType);
                }
                Map<String, List<LoadFilter>> loadFiltersByTable = loadFiltersByType.get(loadFilter.getLoadFilterType());
                if (loadFiltersByTable == null) {
                    loadFiltersByTable = new HashMap<String, List<LoadFilter>>();
                    loadFiltersByType.put(loadFilter.getLoadFilterType(), loadFiltersByTable);
                }
                String tableName = loadFilter.getTargetTableName();
                if (StringUtils.isBlank(tableName)) {
                    tableName = FormatUtils.WILDCARD;
                } else if (ignoreCase) {
                    tableName = tableName.toUpperCase();
                }
                String schemaName = loadFilter.getTargetSchemaName();
                if (StringUtils.isBlank(schemaName)) {
                    schemaName = FormatUtils.WILDCARD;
                } else if (ignoreCase) {
                    schemaName = schemaName.toUpperCase();
                }
                String catalogName = loadFilter.getTargetCatalogName();
                if (StringUtils.isBlank(catalogName)) {
                    catalogName = FormatUtils.WILDCARD;
                } else if (ignoreCase) {
                    catalogName = catalogName.toUpperCase();
                }
                String qualifiedName = SchemaObject.getFullyQualifiedName(
                        catalogName, schemaName, tableName);
                List<LoadFilter> loadFiltersForTable = loadFiltersByTable.get(qualifiedName);
                if (loadFiltersForTable == null) {
                    loadFiltersForTable = new ArrayList<LoadFilter>();
                    loadFiltersByTable.put(qualifiedName, loadFiltersForTable);
                }
                loadFiltersForTable.add(loadFilter);
            }
        }
        return data;
    }

    private List<LoadFilterNodeGroupLink> getLoadFiltersFromDB() {
        return sqlTemplate.query(getSql("selectLoadFilterTable", "orderByLoadFilterOrderSql"), new LoadFilterMapper());
    }

    class LoadFilterMapper implements ISqlRowMapper<LoadFilterNodeGroupLink> {
        public LoadFilterNodeGroupLink mapRow(Row rs) {
            LoadFilterNodeGroupLink loadFilter = new LoadFilterNodeGroupLink();
            loadFilter.setLoadFilterId(rs.getString("load_filter_id"));
            loadFilter.setNodeGroupLink(configurationService.getNodeGroupLinkFor(
                    rs.getString("source_node_group_id"), rs.getString("target_node_group_id"), false));
            loadFilter.setTargetCatalogName(rs.getString("target_catalog_name"));
            loadFilter.setTargetSchemaName(rs.getString("target_schema_name"));
            loadFilter.setTargetTableName(rs.getString("target_table_name"));
            loadFilter.setFilterOnInsert(rs.getBoolean("filter_on_insert"));
            loadFilter.setFilterOnUpdate(rs.getBoolean("filter_on_update"));
            loadFilter.setFilterOnDelete(rs.getBoolean("filter_on_delete"));
            loadFilter.setBeforeWriteScript(rs.getString("before_write_script"));
            loadFilter.setAfterWriteScript(rs.getString("after_write_script"));
            loadFilter.setBatchCompleteScript(rs.getString("batch_complete_script"));
            loadFilter.setBatchCommitScript(rs.getString("batch_commit_script"));
            loadFilter.setBatchRollbackScript(rs.getString("batch_rollback_script"));
            loadFilter.setHandleErrorScript(rs.getString("handle_error_script"));
            loadFilter.setCreateTime(rs.getDateTime("create_time"));
            loadFilter.setLastUpdateBy(rs.getString("last_update_by"));
            loadFilter.setLastUpdateTime(rs.getDateTime("last_update_time"));
            loadFilter.setLoadFilterOrder(rs.getInt("load_filter_order"));
            loadFilter.setFailOnError(rs.getBoolean("fail_on_error"));
            try {
                loadFilter.setLoadFilterType(LoadFilter.LoadFilterType.valueOf(rs.getString(
                        "load_filter_type").toUpperCase()));
            } catch (RuntimeException ex) {
                log.warn(
                        "Invalid value provided for load_filter_type of '{}.'  Valid values are: {}",
                        rs.getString("load_filter_type"),
                        Arrays.toString(LoadFilter.LoadFilterType.values()));
                throw ex;
            }
            return loadFilter;
        }
    }

    public List<LoadFilterNodeGroupLink> getLoadFilterNodeGroupLinks() {
        return getLoadFiltersFromDB();
    }

    public void saveLoadFilter(LoadFilterNodeGroupLink loadFilter) {
        loadFilter.setLastUpdateTime(new Date());
        if (sqlTemplate.update(getSql("updateLoadFilterSql"), new Object[] { loadFilter.getAfterWriteScript(),
                loadFilter.getBatchCommitScript(), loadFilter.getBatchCompleteScript(),
                loadFilter.getBatchRollbackScript(), loadFilter.getBeforeWriteScript(),
                loadFilter.getHandleErrorScript(), loadFilter.getLoadFilterOrder(),
                loadFilter.getLoadFilterType().name(), loadFilter.getNodeGroupLink().getSourceNodeGroupId(),
                loadFilter.getNodeGroupLink().getTargetNodeGroupId(), loadFilter.getTargetCatalogName(),
                loadFilter.getTargetSchemaName(), loadFilter.getTargetTableName(),
                loadFilter.isFilterOnInsert() ? 1 : 0, loadFilter.isFilterOnUpdate() ? 1 : 0,
                loadFilter.isFilterOnDelete() ? 1 : 0, loadFilter.isFailOnError() ? 1 : 0, loadFilter.getLastUpdateBy(),
                loadFilter.getLastUpdateTime(), loadFilter.getLoadFilterId() }) <= 0) {
            sqlTemplate.update(getSql("insertLoadFilterSql"),
                    new Object[] { loadFilter.getAfterWriteScript(), loadFilter.getBatchCommitScript(),
                            loadFilter.getBatchCompleteScript(), loadFilter.getBatchRollbackScript(),
                            loadFilter.getBeforeWriteScript(), loadFilter.getHandleErrorScript(),
                            loadFilter.getLoadFilterOrder(), loadFilter.getLoadFilterType().name(),
                            loadFilter.getNodeGroupLink().getSourceNodeGroupId(),
                            loadFilter.getNodeGroupLink().getTargetNodeGroupId(), loadFilter.getTargetCatalogName(),
                            loadFilter.getTargetSchemaName(), loadFilter.getTargetTableName(),
                            loadFilter.isFilterOnInsert() ? 1 : 0, loadFilter.isFilterOnUpdate() ? 1 : 0,
                            loadFilter.isFilterOnDelete() ? 1 : 0, loadFilter.isFailOnError() ? 1 : 0,
                            loadFilter.getLastUpdateBy(), loadFilter.getLastUpdateTime(), new Date(),
                            loadFilter.getLoadFilterId() });
        }
        clearCache();
    }

    public void saveLoadFilterAsCopy(LoadFilterNodeGroupLink loadFilter) {
        String newId = loadFilter.getLoadFilterId();
        List<LoadFilterNodeGroupLink> loadFilters = sqlTemplate
                .query(getSql("selectLoadFilterTable", "whereLoadFilterIdLikeSql"), new LoadFilterMapper(), newId + "%");
        List<String> ids = loadFilters.stream().map(LoadFilterNodeGroupLink::getLoadFilterId).collect(Collectors.toList());
        String suffix = "";
        for (int i = 2; ids.contains(newId + suffix); i++) {
            suffix = "_" + i;
        }
        loadFilter.setLoadFilterId(newId + suffix);
        saveLoadFilter(loadFilter);
    }

    public void renameLoadFilter(String oldId, LoadFilterNodeGroupLink loadFilter) {
        deleteLoadFilter(oldId);
        saveLoadFilter(loadFilter);
    }

    public void deleteLoadFilter(String loadFilterId) {
        sqlTemplate.update(getSql("deleteLoadFilterSql"), loadFilterId);
        clearCache();
    }

    public void deleteAllLoadFilters() {
        sqlTemplate.update(getSql("deleteAllLoadFiltersSql"));
        clearCache();
    }

    public boolean refreshFromDatabase() {
        Date date = sqlTemplate.queryForObject(getSql("selectMaxLastUpdateTime"), Date.class);
        if (date != null) {
            if (lastUpdateTime == null || lastUpdateTime.before(date)) {
                if (lastUpdateTime != null) {
                    log.info("Newer filter settings were detected");
                }
                lastUpdateTime = date;
                clearCache();
                return true;
            }
        }
        return false;
    }

    public void clearCache() {
        cacheManager.flushLoadFilters();
    }

    public static class LoadFilterNodeGroupLink extends LoadFilter {
        private static final long serialVersionUID = 1L;
        protected NodeGroupLink nodeGroupLink;

        public void setNodeGroupLink(NodeGroupLink nodeGroupLink) {
            this.nodeGroupLink = nodeGroupLink;
        }

        public NodeGroupLink getNodeGroupLink() {
            return nodeGroupLink;
        }
    }
}
