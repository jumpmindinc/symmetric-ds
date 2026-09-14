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
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;

public class DataGapRouteCursor extends AbstractDataGapRouteCursor {
    protected static Map<String, Boolean> lastSelectUsedGreaterThanQueryByEngineName = new HashMap<String, Boolean>();
    protected String engineName;

    public DataGapRouteCursor(ChannelRouterContext context, ISymmetricEngine engine) {
        super(context, engine);
        engineName = parameterService.getEngineName();
        if (lastSelectUsedGreaterThanQueryByEngineName.get(engineName) == null) {
            lastSelectUsedGreaterThanQueryByEngineName.put(engineName, Boolean.FALSE);
        }
        cursor = prepareCursor();
    }

    protected ISqlReadCursor<Data> prepareCursor() {
        int numberOfGapsToQualify = parameterService.getInt(ParameterConstants.ROUTING_MAX_GAPS_TO_QUALIFY_IN_SQL, 100);
        int maxGapsBeforeGreaterThanQuery = parameterService.getInt(ParameterConstants.ROUTING_DATA_READER_THRESHOLD_GAPS_TO_USE_GREATER_QUERY, 100);
        boolean useGreaterThanDataId = false;
        if (maxGapsBeforeGreaterThanQuery > 0 && dataGaps.size() > maxGapsBeforeGreaterThanQuery) {
            useGreaterThanDataId = true;
        }
        isEachGapQueried = !useGreaterThanDataId && dataGaps.size() <= numberOfGapsToQualify;
        String channelId = context.getChannel().getChannelId();
        String sql = null;
        Boolean lastSelectUsedGreaterThanQuery = lastSelectUsedGreaterThanQueryByEngineName.get(engineName);
        if (useGreaterThanDataId) {
            sql = getSql("selectDataUsingStartDataId");
            if (!lastSelectUsedGreaterThanQuery) {
                log.info("Switching to select from the data table where data_id >= start gap because there were {} gaps found "
                        + "which was more than the configured threshold of {}", dataGaps.size(), maxGapsBeforeGreaterThanQuery);
                lastSelectUsedGreaterThanQueryByEngineName.put(engineName, Boolean.TRUE);
            }
        } else {
            sql = qualifyUsingDataGaps(dataGaps, numberOfGapsToQualify, getSql("selectDataUsingGapsSql"));
            if (lastSelectUsedGreaterThanQuery) {
                log.info("Switching to select from the data table where data_id between gaps");
                lastSelectUsedGreaterThanQueryByEngineName.put(engineName, Boolean.FALSE);
            }
        }
        sql = qualifyForOrder(sql);
        Object[] args = null;
        int[] types = null;
        int dataIdSqlType = engine.getSymmetricDialect().getSqlTypeForIds();
        if (useGreaterThanDataId) {
            args = new Object[] { channelId, dataGaps.get(0).getStartId() };
            types = new int[] { Types.VARCHAR, dataIdSqlType };
        } else {
            int numberOfArgs = 1 + (2 * (numberOfGapsToQualify < dataGaps.size() ? numberOfGapsToQualify : dataGaps.size()));
            args = new Object[numberOfArgs];
            types = new int[numberOfArgs];
            args[0] = channelId;
            types[0] = Types.VARCHAR;
            for (int i = 1, j = 0; i + 1 < numberOfArgs && j < dataGaps.size(); i += 2, j++) {
                DataGap gap = dataGaps.get(j);
                args[i] = gap.getStartId();
                types[i] = dataIdSqlType;
                if (i + 2 == numberOfArgs) {
                    /*
                     * there were more gaps than we are going to use in the SQL. use the last gap as the end data id for the last range
                     */
                    args[i + 1] = dataGaps.get(dataGaps.size() - 1).getEndId();
                } else {
                    args[i + 1] = gap.getEndId();
                }
                types[i + 1] = dataIdSqlType;
            }
        }
        return executeCursor(sql, args, types);
    }
}
