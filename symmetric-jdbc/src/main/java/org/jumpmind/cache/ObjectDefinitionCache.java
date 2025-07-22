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
package org.jumpmind.cache;

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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.AbstractJdbcDdlReader;

public class ObjectDefinitionCache {
    private AbstractJdbcDdlReader ddlReader;
    private Object tableNameCacheLock = new Object();
    volatile private Map<TableNameCacheKey, List<String>> tableNameCache = new HashMap<TableNameCacheKey, List<String>>();
    volatile private long tableNameCacheTime;

    public ObjectDefinitionCache(AbstractJdbcDdlReader ddlReader) {
        this.ddlReader = ddlReader;
    }

    public List<String> getTableNames(String catalog, String schema, String[] tableTypes) {
        TableNameCacheKey cacheKey = new TableNameCacheKey(catalog, schema, tableTypes);
        long cacheTimeoutInMs = ddlReader.getPlatform().getClearCacheModelTimeoutInMs();
        List<String> tableNames;
        synchronized (tableNameCacheLock) {
            boolean timedOut = System.currentTimeMillis() - tableNameCacheTime >= cacheTimeoutInMs;
            tableNames = tableNameCache.get(cacheKey);
            if (timedOut || tableNames == null) {
                if (timedOut) {
                    clearTableNameCache();
                }
                tableNames = ddlReader.getTableNamesFromDatabase(catalog, schema, tableTypes);
                tableNameCache.put(cacheKey, tableNames);
            }
        }
        return tableNames;
    }

    public void clearTableNameCache() {
        tableNameCache.clear();
        tableNameCacheTime = System.currentTimeMillis();
    }

    private class TableNameCacheKey {
        private String catalog;
        private String schema;
        private String[] tableTypes;

        public TableNameCacheKey(String catalog, String schema, String[] tableTypes) {
            this.catalog = catalog;
            this.schema = schema;
            this.tableTypes = tableTypes;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((catalog == null) ? 0 : catalog.hashCode());
            result = prime * result + ((schema == null) ? 0 : schema.hashCode());
            result = prime * result + Arrays.hashCode(tableTypes);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TableNameCacheKey)) {
                return false;
            }
            TableNameCacheKey other = (TableNameCacheKey) obj;
            if (catalog == null) {
                if (other.catalog != null) {
                    return false;
                }
            } else if (!catalog.equals(other.catalog)) {
                return false;
            }
            if (schema == null) {
                if (other.schema != null) {
                    return false;
                }
            } else if (!schema.equals(other.schema)) {
                return false;
            }
            if (!Arrays.equals(tableTypes, other.tableTypes)) {
                return false;
            }
            return true;
        }
    }
}
