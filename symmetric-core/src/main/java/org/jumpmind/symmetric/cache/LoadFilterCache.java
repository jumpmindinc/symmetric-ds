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
package org.jumpmind.symmetric.cache;

import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.LoadFilter.LoadFilterType;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.service.ILoadFilterService;
import org.jumpmind.symmetric.service.IParameterService;

public class LoadFilterCache {
    private IParameterService parameterService;
    private ILoadFilterService loadFilterService;
    volatile private Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> loadFilterCache;
    volatile private long loadFilterCacheTime = 0;
    volatile private Object loadFilterCacheLock = new Object();

    public LoadFilterCache(ISymmetricEngine engine) {
        this.parameterService = engine.getParameterService();
        this.loadFilterService = engine.getLoadFilterService();
    }

    public Map<NodeGroupLink, Map<LoadFilterType, Map<String, List<LoadFilter>>>> findLoadFilters(NodeGroupLink nodeGroupLink,
            boolean useCache) {
        long cacheTimeoutInMs = parameterService
                .getLong(ParameterConstants.CACHE_TIMEOUT_LOAD_FILTER_IN_MS);
        if (System.currentTimeMillis() - loadFilterCacheTime >= cacheTimeoutInMs
                || loadFilterCache == null) {
            synchronized (loadFilterCacheLock) {
                if (System.currentTimeMillis() - loadFilterCacheTime >= cacheTimeoutInMs
                        || loadFilterCache == null) {
                    loadFilterCache = loadFilterService.findLoadFiltersFromDb();
                    loadFilterCacheTime = System.currentTimeMillis();
                }
            }
        }
        return loadFilterCache;
    }

    public void flushLoadFilterCache() {
        synchronized (loadFilterCacheLock) {
            loadFilterCacheTime = 0l;
        }
    }
}
