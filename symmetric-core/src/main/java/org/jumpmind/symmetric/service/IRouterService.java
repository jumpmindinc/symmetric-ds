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
package org.jumpmind.symmetric.service;

import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.model.ChannelDataCreateTimeRange;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.route.IDataRouter;
import org.jumpmind.symmetric.route.SimpleRouterContext;

/**
 * This service is responsible for routing data to specific nodes and managing the batching of data to be delivered to each node.
 * 
 * @since 2.0
 */
public interface IRouterService extends IService {
    public long routeData(boolean force);

    public long getMaxDataIdAlreadyRouted();

    public long getUnroutedDataCount();

    public List<ChannelDataCreateTimeRange> findUnroutedDataCreateTimeRangeByChannel();

    public boolean shouldDataBeRouted(SimpleRouterContext context, DataMetaData dataMetaData,
            Node node, boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter);

    /**
     * Get a list of available batch algorithms that can be used for the different channels
     */
    public List<String> getAvailableBatchAlgorithms();

    public Map<String, IDataRouter> getRouters();

    public List<DataGap> getDataGaps();

    public void stop();

    public void flushCache();
}