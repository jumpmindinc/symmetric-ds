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
package org.jumpmind.symmetric.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.util.FormatUtils;

/**
 * Utility SQL container that should be sub-classed in order to populate with SQL statements from the subclasses constructor.
 */
abstract public class AbstractSqlMap implements ISqlMap {
    private IDatabasePlatform platform;
    private Map<String, String> sql = new HashMap<String, String>();
    protected Map<String, String> replacementTokens;

    public AbstractSqlMap(IDatabasePlatform platform, String tablePrefix) {
        this.platform = platform;
        this.replacementTokens = mergeSqlReplacementTokens(null, tablePrefix);
    }

    public AbstractSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        this.platform = platform;
        this.replacementTokens = replacementTokens;
    }

    protected void putSql(String key, String sql) {
        if (replacementTokens != null) {
            sql = FormatUtils.replaceTokens(sql, this.replacementTokens, true);
        }
        sql = sql.replaceAll("\\s+", " ");
        this.sql.put(key, this.platform != null ? this.platform.scrubSql(sql) : sql);
    }

    public String getSql(String... keys) {
        StringBuilder sqlBuffer = new StringBuilder();
        if (keys != null) {
            if (keys.length > 1) {
                for (String key : keys) {
                    if (key != null) {
                        String value = sql.get(key);
                        sqlBuffer.append(value == null ? key : value);
                        sqlBuffer.append(" ");
                    }
                }
            } else if (keys.length == 1) {
                sqlBuffer.append(sql.get(keys[0]));
            }
        }
        return sqlBuffer.toString();
    }

    public static Map<String, String> mergeSqlReplacementTokens(Map<String, String> replacementTokens, String tablePrefix) {
        Map<String, String> map = new HashMap<String, String>();
        Set<String> tables = TableConstants.getTablesWithoutPrefix();
        for (String table : tables) {
            map.put(table, String.format("%s%s%s", tablePrefix, StringUtils.isNotBlank(tablePrefix) ? "_" : "", table));
        }
        if (replacementTokens != null) {
            map.putAll(replacementTokens);
        }
        return map;
    }
}
