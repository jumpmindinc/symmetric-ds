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
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;

public class TransformCache {
    private IParameterService parameterService;
    private ITransformService transformService;
    volatile private Map<NodeGroupLink, Map<TransformPoint, List<TransformTableNodeGroupLink>>> transformsCacheByNodeGroupLinkByTransformPoint;
    volatile private long transformCacheTimeInMs;
    volatile private Object transformCacheLock = new Object();

    public TransformCache(ISymmetricEngine engine) {
        this.parameterService = engine.getParameterService();
        this.transformService = engine.getTransformService();
    }

    public Map<NodeGroupLink, Map<TransformPoint, List<TransformTableNodeGroupLink>>> getTransformsCacheByNodeGroupLinkByTransformPoint() {
        long cacheTimeoutInMs = parameterService
                .getLong(ParameterConstants.CACHE_TIMEOUT_TRANSFORM_IN_MS);
        if (System.currentTimeMillis() - transformCacheTimeInMs >= cacheTimeoutInMs
                || transformsCacheByNodeGroupLinkByTransformPoint == null) {
            synchronized (transformCacheLock) {
                if (System.currentTimeMillis() - transformCacheTimeInMs >= cacheTimeoutInMs
                        || transformsCacheByNodeGroupLinkByTransformPoint == null) {
                    transformsCacheByNodeGroupLinkByTransformPoint = transformService.readInCacheIfExpiredFromDb();
                    transformCacheTimeInMs = System.currentTimeMillis();
                }
            }
        }
        return transformsCacheByNodeGroupLinkByTransformPoint;
    }

    public void flushTransformCache() {
        synchronized (transformCacheLock) {
            this.transformCacheTimeInMs = 0l;
        }
    }
}
