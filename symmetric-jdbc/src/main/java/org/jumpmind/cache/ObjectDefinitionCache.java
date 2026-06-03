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

import org.jumpmind.db.model.CatalogSchema;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;

public class ObjectDefinitionCache {
    private AbstractJdbcDdlReader ddlReader;
    private Object relationNameCacheLock = new Object();
    volatile private Map<RelationNameCacheKey, List<String>> relationNameCache = new HashMap<RelationNameCacheKey, List<String>>();
    volatile private long relationNameCacheTime;

    public ObjectDefinitionCache(AbstractJdbcDdlReader ddlReader) {
        this.ddlReader = ddlReader;
    }

    public List<String> getRelationNames(CatalogSchema catalogSchema, String[] tableTypes) {
        RelationNameCacheKey cacheKey = new RelationNameCacheKey(catalogSchema, tableTypes);
        long cacheTimeoutInMs = ddlReader.getPlatform().getClearCacheModelTimeoutInMs();
        List<String> relationNames;
        synchronized (relationNameCacheLock) {
            boolean timedOut = System.currentTimeMillis() - relationNameCacheTime >= cacheTimeoutInMs;
            relationNames = relationNameCache.get(cacheKey);
            if (timedOut || relationNames == null) {
                if (timedOut) {
                    clearRelationNameCache();
                }
                relationNames = ddlReader.getRelationNamesFromDatabase(catalogSchema.getCatalog(), catalogSchema.getSchema(), tableTypes);
                relationNameCache.put(cacheKey, relationNames);
            }
        }
        return relationNames;
    }

    public void clearRelationNameCache() {
        synchronized (relationNameCacheLock) {
            relationNameCache.clear();
            relationNameCacheTime = System.currentTimeMillis();
        }
    }

    private class RelationNameCacheKey {
        private CatalogSchema catalogSchema;
        private String[] tableTypes;

        public RelationNameCacheKey(CatalogSchema catalogSchema, String[] tableTypes) {
            this.catalogSchema = catalogSchema;
            this.tableTypes = tableTypes;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((catalogSchema == null) ? 0 : catalogSchema.hashCode());
            result = prime * result + Arrays.hashCode(tableTypes);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RelationNameCacheKey)) {
                return false;
            }
            RelationNameCacheKey other = (RelationNameCacheKey) obj;
            if (catalogSchema == null) {
                if (other.catalogSchema != null) {
                    return false;
                }
            } else if (!catalogSchema.equals(other.catalogSchema)) {
                return false;
            }
            if (!Arrays.equals(tableTypes, other.tableTypes)) {
                return false;
            }
            return true;
        }
    }
}
