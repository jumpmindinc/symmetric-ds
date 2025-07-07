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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.config.IParameterFilter;
import org.jumpmind.symmetric.config.IParameterSaveFilter;
import org.jumpmind.symmetric.model.DatabaseParameter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IParameterService;

/**
 * @see IParameterService
 */
public class ParameterService extends AbstractParameterService implements IParameterService {
    String tablePrefix;
    private ITypedPropertiesFactory factory;
    private ParameterServiceSqlMap sql;
    private ISqlTemplate sqlTemplate;
    private Date lastUpdateTime;
    private List<DatabaseParameter> offlineParameters;

    public ParameterService(IDatabasePlatform platform, ITypedPropertiesFactory factory, String tablePrefix) {
        this.tablePrefix = tablePrefix;
        this.factory = factory;
        this.sql = new ParameterServiceSqlMap(platform, tablePrefix);
        this.sqlTemplate = platform.getSqlTemplate();
    }

    public String getTablePrefix() {
        return this.tablePrefix;
    }

    public boolean refreshFromDatabase() {
        Date date = sqlTemplate.queryForObject(sql.getSql("selectMaxLastUpdateTime"), Date.class);
        if (date != null) {
            if (lastUpdateTime == null || lastUpdateTime.before(date)) {
                if (lastUpdateTime != null) {
                    log.info("Newer database parameters were detected");
                }
                lastUpdateTime = date;
                rereadParameters();
                return true;
            }
        }
        return false;
    }

    /**
     * Save a parameter that applies to {@link ParameterConstants#ALL} external ids and all node groups.
     */
    public void saveParameter(String key, Object paramValue, String lastUpdateBy) {
        this.saveParameter(ParameterConstants.ALL, ParameterConstants.ALL, key, paramValue, lastUpdateBy);
    }

    public void saveParameter(String externalId, String nodeGroupId, String key, Object paramValue, String lastUpdateBy) {
        paramValue = paramValue != null ? paramValue.toString() : null;
        if (extensionService != null) {
            for (IParameterSaveFilter filter : extensionService.getExtensionPointList(IParameterSaveFilter.class)) {
                paramValue = filter.filterSaveParameter(key, (String) paramValue);
            }
        }
        int count = sqlTemplate.update(sql.getSql("updateParameterSql"), new Object[] { paramValue, lastUpdateBy,
                new Date(), externalId, nodeGroupId, key });
        if (count <= 0) {
            sqlTemplate.update(sql.getSql("insertParameterSql"), new Object[] { externalId,
                    nodeGroupId, key, paramValue, lastUpdateBy, new Date(), new Date() });
        }
        rereadParameters();
    }

    @Override
    public void deleteParameter(String key) {
        sqlTemplate.update(sql.getSql("deleteParameterByKeySql"), key);
        rereadParameters();
    }

    @Override
    public void deleteParameter(String externalId, String nodeGroupId, String key) {
        sqlTemplate.update(sql.getSql("deleteParameterSql"), externalId, nodeGroupId, key);
        rereadParameters();
    }

    public void deleteParameterWithUpdate(String externalId, String nodeGroupId, String key) {
        String oldSql = sql.getSql("deleteParameterSql");
        String newSql = "";
        int j = 0;
        for (int i = 0; i < oldSql.length(); i++) {
            if (oldSql.charAt(i) == '?') {
                if (j == 0) {
                    newSql += "'" + externalId + "'";
                    ;
                } else if (j == 1) {
                    newSql += "'" + nodeGroupId + "'";
                } else {
                    newSql += "'" + key + "'";
                }
                j++;
            } else {
                newSql += oldSql.charAt(i);
            }
        }
        sqlTemplate.update(newSql);
    }

    @Override
    public void deleteAllParameters() {
        sqlTemplate.update(sql.getSql("deleteAllParametersSql"));
        rereadParameters();
    }

    public void saveParameters(String externalId, String nodeGroupId, Map<String, Object> parameters, String lastUpdateBy) {
        Set<String> keys = parameters.keySet();
        for (String key : keys) {
            saveParameter(externalId, nodeGroupId, key, parameters.get(key), lastUpdateBy);
        }
    }

    protected TypedProperties readParametersFromDatabase(String sqlKey, Object... values) {
        final TypedProperties properties = new TypedProperties();
        final IParameterFilter filter = extensionService != null ? extensionService.getExtensionPoint(IParameterFilter.class) : null;
        sqlTemplate.query(sql.getSql(sqlKey), new ISqlRowMapper<Object>() {
            public Object mapRow(Row row) {
                String key = row.getString("param_key");
                String value = row.getString("param_value");
                if (filter != null) {
                    value = filter.filterParameter(key, value);
                }
                if (value != null) {
                    properties.setProperty(key, value);
                }
                return row;
            }
        }, values);
        return properties;
    }

    public boolean isRegistrationServer() {
        return StringUtils.isBlank(getRegistrationUrl())
                || getRegistrationUrl().equalsIgnoreCase(getSyncUrl());
    }

    public boolean isRemoteNodeRegistrationServer(Node remoteNode) {
        return getRegistrationUrl().equalsIgnoreCase(remoteNode.getSyncUrl());
    }

    protected TypedProperties rereadApplicationParameters() {
        TypedProperties p = this.factory.reload();
        p.putAll(systemProperties);
        p.putAll(rereadDatabaseParameters(p));
        rereadOfflineNodeParameters();
        return p;
    }

    protected synchronized void rereadOfflineNodeParameters() {
        if (databaseHasBeenInitialized) {
            offlineParameters = getDatabaseParametersFor(ParameterConstants.NODE_OFFLINE);
        }
    }

    public List<DatabaseParameter> getDatabaseParametersForAll() {
        return sqlTemplate.query(sql.getSql("selectParametersSql"), new DatabaseParameterMapper());
    }

    public List<DatabaseParameter> getDatabaseParametersFor(String paramKey) {
        return sqlTemplate.query(sql.getSql("selectParametersByKeySql"),
                new DatabaseParameterMapper(), paramKey);
    }

    public TypedProperties getDatabaseParameters(String externalId, String nodeGroupId) {
        return readParametersFromDatabase("selectParametersByNodeGroupAndExternalIdSql", externalId, nodeGroupId);
    }

    public List<DatabaseParameter> getOfflineNodeParameters() {
        if (offlineParameters == null) {
            rereadOfflineNodeParameters();
        }
        return offlineParameters;
    }

    class DatabaseParameterMapper implements ISqlRowMapper<DatabaseParameter> {
        IParameterFilter filter = extensionService != null ? extensionService.getExtensionPoint(IParameterFilter.class) : null;

        public DatabaseParameter mapRow(Row row) {
            String key = row.getString("param_key");
            String value = row.getString("param_value");
            if (filter != null) {
                value = filter.filterParameter(key, value);
            }
            return new DatabaseParameter(key, value, row.getString("external_id"), row.getString("node_group_id"));
        }
    }
}
