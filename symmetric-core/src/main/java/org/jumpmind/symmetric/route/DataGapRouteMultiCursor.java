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
package org.jumpmind.symmetric.route;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;

public class DataGapRouteMultiCursor extends AbstractDataGapRouteCursor {
    protected int maxGapsToQualify;
    protected int gapIndex;
    protected int dataCount;
    protected int queryCount;
    protected int queryMillis;

    public DataGapRouteMultiCursor(ChannelRouterContext context, ISymmetricEngine engine) {
        super(context, engine);
        isEachGapQueried = true;
        maxGapsToQualify = parameterService.getInt(ParameterConstants.ROUTING_MAX_GAPS_TO_QUALIFY_IN_SQL, 100);
        cursor = prepareCursor();
    }

    @Override
    public Data next() {
        Data data = cursor.next();
        while (data == null && dataGaps.size() - gapIndex > 0) {
            cursor.close();
            cursor = prepareCursor();
            data = cursor.next();
        }
        if (data != null) {
            dataCount++;
        }
        return data;
    }

    protected ISqlReadCursor<Data> prepareCursor() {
        int gapsRemaining = dataGaps.size() - gapIndex;
        int numberOfGaps = gapsRemaining > maxGapsToQualify ? maxGapsToQualify : gapsRemaining;
        int numberOfArgs = 1 + (2 * numberOfGaps);
        Object[] args = new Object[numberOfArgs];
        int[] types = new int[numberOfArgs];
        int dataIdSqlType = engine.getSymmetricDialect().getSqlTypeForIds();
        List<DataGap> gapsToQualify = new ArrayList<DataGap>();
        args[0] = context.getChannel().getChannelId();
        types[0] = Types.VARCHAR;
        log.trace("Querying for gaps {} through {} of total {}", gapIndex + 1, gapIndex + numberOfGaps, dataGaps.size());
        for (int i = 1; i + 1 < numberOfArgs && gapIndex < dataGaps.size(); i += 2, gapIndex++) {
            DataGap gap = dataGaps.get(gapIndex);
            args[i] = gap.getStartId();
            types[i] = dataIdSqlType;
            args[i + 1] = gap.getEndId();
            types[i + 1] = dataIdSqlType;
            gapsToQualify.add(gap);
        }
        String sql = qualifyForOrder(qualifyUsingDataGaps(gapsToQualify, numberOfGaps, getSql("selectDataUsingGapsSql")));
        long ts = System.currentTimeMillis();
        ISqlReadCursor<Data> cursor = executeCursor(sql, args, types);
        queryMillis += (System.currentTimeMillis() - ts);
        queryCount++;
        context.incrementStat(1, ChannelRouterContext.STAT_MULTI_QUERY_COUNT);
        return cursor;
    }

    @Override
    public void close() {
        super.close();
        String message = "Queried for {} gaps using {} queries with {} ms spent waiting on query executions";
        if (log.isDebugEnabled()) {
            log.debug(message, dataGaps.size(), queryCount, queryMillis);
        } else if (queryCount > 1 && queryMillis > 60000) {
            log.warn(message, dataGaps.size(), queryCount, queryMillis);
        }
    }
}
