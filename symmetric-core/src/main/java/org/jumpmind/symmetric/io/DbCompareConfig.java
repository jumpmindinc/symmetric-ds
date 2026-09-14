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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbCompareConfig {
    final Logger log = LoggerFactory.getLogger(getClass());
    public final static String WHERE_CLAUSE = "where_clause";
    public final static String ORDER_BY_SUFFIX = "order_by_suffix";
    public final static String EXCLUDED_COLUMN = "exclude_columns";
    private List<String> sourceTableNames;
    private List<String> targetTableNames;
    private List<String> excludedTableNames;
    private boolean useSymmetricConfig = true;
    private int numericScale = 3;
    private String dateTimeFormat;
    private Map<String, String> whereClauses = new LinkedHashMap<String, String>();
    private Map<String, String> orderBySuffixes = new LinkedHashMap<String, String>();
    private Map<String, List<String>> tablesToExcludedColumns = new LinkedHashMap<String, List<String>>();
    private String outputSql;
    private boolean continueAfterError = false;
    private Map<String, String> configSources = new HashMap<String, String>();

    public DbCompareConfig() {
        configSources.put("sourceTableNames", "default");
        configSources.put("includedTableNames", "default");
        configSources.put("targetTableNames", "default");
        configSources.put("excludedTableNames", "default");
        configSources.put("useSymmetricConfig", "default");
        configSources.put("numericScale", "default");
        configSources.put("whereClauses", "default");
        configSources.put("orderBySuffixes", "default");
        configSources.put("tablesToExcludedColumns", "default");
        configSources.put("sqlDiffFileName", "default");
        configSources.put("outputSql", "default");
        configSources.put("continueAfterError", "default");
    }

    public String getSourceWhereClause(String tableName) {
        String whereClause = getWhereClause(tableName, "source");
        if (StringUtils.isEmpty(whereClause)) {
            whereClause = "1=1";
        }
        return whereClause;
    }

    public String getTargetWhereClause(String tableName) {
        String whereClause = getWhereClause(tableName, "target");
        if (StringUtils.isEmpty(whereClause)) {
            String simpleTableName = DbCompareUtil.getUnqualifiedTableName(tableName);
            whereClause = getWhereClause(simpleTableName, "target");
        }
        if (StringUtils.isEmpty(whereClause)) {
            whereClause = "1=1";
        }
        return whereClause;
    }

    protected String getWhereClause(String tableName, String sourceOrTarget) {
        String tableNameLower = tableName.toLowerCase();
        String[] keys = {
                tableNameLower + "." + sourceOrTarget + "." + WHERE_CLAUSE,
                tableNameLower + "." + WHERE_CLAUSE,
                WHERE_CLAUSE
        };
        for (String key : keys) {
            if (whereClauses.containsKey(key)) {
                return whereClauses.get(key);
            }
        }
        return null;
    }

    public String getSourceOrderBySuffix(String tableName, String columnName) {
        return getOrderBySuffix(tableName, columnName, "source");
    }

    public String getTargetOrderBySuffix(String tableName, String columnName) {
        String orderBySuffix = getOrderBySuffix(tableName, columnName, "target");
        if (StringUtils.isEmpty(orderBySuffix)) {
            String simpleTableName = DbCompareUtil.getUnqualifiedTableName(tableName);
            orderBySuffix = getOrderBySuffix(simpleTableName, columnName, "target");
        }
        return orderBySuffix;
    }

    protected String getOrderBySuffix(String tableName, String columnName, String sourceOrTarget) {
        String tableNameLower = tableName.toLowerCase();
        String columnNameLower = columnName.toLowerCase();
        String[] keys = {
                tableNameLower + "." + columnNameLower + "." + sourceOrTarget + "." + ORDER_BY_SUFFIX,
                tableNameLower + "." + columnNameLower + "." + ORDER_BY_SUFFIX,
                tableNameLower + "." + sourceOrTarget + "." + ORDER_BY_SUFFIX,
                tableNameLower + "." + ORDER_BY_SUFFIX,
                ORDER_BY_SUFFIX
        };
        for (String key : keys) {
            if (orderBySuffixes.containsKey(key)) {
                return orderBySuffixes.get(key);
            }
        }
        return "";
    }

    protected boolean shouldIncludeColumn(String tableName, String columnName) {
        String tableNameLower = tableName.toLowerCase();
        String columnNameLower = columnName.toLowerCase();
        String[] keys = {
                tableNameLower + "." + EXCLUDED_COLUMN,
                EXCLUDED_COLUMN
        };
        for (String key : keys) {
            if (tablesToExcludedColumns.containsKey(key)) {
                List<String> exludedColumnNames = tablesToExcludedColumns.get(key);
                return !exludedColumnNames.contains(columnNameLower);
            }
        }
        return true;
    }

    public List<String> getExcludedTableNames() {
        return excludedTableNames;
    }

    public void setExcludedTableNames(List<String> excludedTableNames) {
        this.excludedTableNames = excludedTableNames;
    }

    public boolean isUseSymmetricConfig() {
        return useSymmetricConfig;
    }

    public void setUseSymmetricConfig(boolean useSymmetricConfig) {
        this.useSymmetricConfig = useSymmetricConfig;
    }

    public int getNumericScale() {
        return numericScale;
    }

    public void setNumericScale(int numericScale) {
        this.numericScale = numericScale;
    }

    public String getDateTimeFormat() {
        return dateTimeFormat;
    }

    public void setDateTimeFormat(String format) {
        this.dateTimeFormat = format;
    }

    public Map<String, String> getWhereClauses() {
        return whereClauses;
    }

    public void setConfigSource(String configName, String configSource) {
        configSources.put(configName, configSource);
    }

    public void setWhereClauses(Map<String, String> whereClauses) {
        this.whereClauses = new CaseInsensitiveMap<String, String>(whereClauses);
    }

    public Map<String, String> getOrderBySuffixes() {
        return orderBySuffixes;
    }

    public void setOrderBySuffixes(Map<String, String> orderBySuffixes) {
        this.orderBySuffixes = new CaseInsensitiveMap<String, String>(orderBySuffixes);
    }

    public List<String> getSourceTableNames() {
        return sourceTableNames;
    }

    public void setSourceTableNames(List<String> sourceTableNames) {
        this.sourceTableNames = sourceTableNames;
    }

    public List<String> getTargetTableNames() {
        return targetTableNames;
    }

    public void setTargetTableNames(List<String> targetTableNames) {
        this.targetTableNames = targetTableNames;
    }

    public Map<String, List<String>> getTablesToExcludedColumns() {
        return tablesToExcludedColumns;
    }

    public void setTablesToExcludedColumns(Map<String, List<String>> tablesToExcludedColumns) {
        this.tablesToExcludedColumns = tablesToExcludedColumns;
    }

    public String getOutputSql() {
        return outputSql;
    }

    public void setOutputSql(String outputSql) {
        this.outputSql = outputSql;
    }

    public void setContinueAfterError(boolean continueAfterError) {
        this.continueAfterError = continueAfterError;
    }

    public boolean isContinueAfterError() {
        return continueAfterError;
    }

    public String report() {
        StringBuilder buff = new StringBuilder(128);
        buff.append("\tsourceTableNames=").append(sourceTableNames).append(" @").append(configSources.get("sourceTableNames")).append("\n");
        buff.append("\ttargetTableNames=").append(targetTableNames).append(" @").append(configSources.get("targetTableNames")).append("\n");
        buff.append("\texcludedTableNames=").append(excludedTableNames).append(" @").append(configSources.get("excludedTableNames")).append("\n");
        buff.append("\tuseSymmetricConfig=").append(useSymmetricConfig).append(" @").append(configSources.get("useSymmetricConfig")).append("\n");
        buff.append("\tnumericScale=").append(numericScale).append(" @").append(configSources.get("numericScale")).append("\n");
        buff.append("\twhereClauses=").append(whereClauses).append("@").append(configSources.get("whereClauses")).append("\n");
        buff.append("\torderBySuffixes=").append(orderBySuffixes).append("@").append(configSources.get("orderBySuffixes")).append("\n");
        buff.append("\ttablesToExcludedColumns=").append(tablesToExcludedColumns).append(" @").append(configSources.get("tablesToExcludedColumns")).append(
                "\n");
        buff.append("\toutputSql=").append(outputSql).append(" @").append(configSources.get("outputSql")).append("\n");
        buff.append("\tcontinueAfterError=").append(continueAfterError).append(" @").append(configSources.get("continueAfterError")).append("\n");
        return buff.toString();
    }
}
