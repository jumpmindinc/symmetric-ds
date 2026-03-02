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
package org.jumpmind.symmetric.route;

import java.util.Comparator;
import java.util.List;

import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractDataGapRouteCursor implements IDataGapRouteCursor {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ISymmetricEngine engine;
    protected IParameterService parameterService;
    protected ChannelRouterContext context;
    protected List<DataGap> dataGaps;
    protected ISqlReadCursor<Data> cursor;
    protected boolean isSortInMemory;
    protected boolean isEachGapQueried;

    public AbstractDataGapRouteCursor(ChannelRouterContext context, ISymmetricEngine engine) {
        this.engine = engine;
        this.context = context;
        parameterService = engine.getParameterService();
        isSortInMemory = parameterService.is(ParameterConstants.ROUTING_DATA_READER_INTO_MEMORY_ENABLED, false);
        dataGaps = context.getDataGaps();
    }

    @Override
    public Data next() {
        return cursor.next();
    }

    @Override
    public void close() {
        cursor.close();
    }

    protected ISqlReadCursor<Data> executeCursor(String sql, Object[] args, int[] types) {
        ISqlTemplate sqlTemplate = engine.getSymmetricDialect().getPlatform().getSqlTemplate();
        ISqlRowMapper<Data> dataMapper = engine.getDataService().getDataMapper();
        ISqlReadCursor<Data> cursor = null;
        long ts = System.currentTimeMillis();
        try {
            cursor = sqlTemplate.queryForCursor(sql, dataMapper, args, types);
        } catch (RuntimeException e) {
            log.info("Failed to execute query, but will try again,", e);
            AppUtils.sleep(1000);
            cursor = sqlTemplate.queryForCursor(sql, dataMapper, args, types);
        }
        if (isSortInMemory) {
            cursor = getDataMemoryCursor(cursor);
        }
        context.incrementStat(System.currentTimeMillis() - ts, ChannelRouterContext.STAT_QUERY_EXEC_TIME_MS);
        return cursor;
    }

    protected ISqlReadCursor<Data> getDataMemoryCursor(ISqlReadCursor<Data> cursor) {
        Comparator<Data> comparator = null;
        if (parameterService.is(ParameterConstants.ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED, true)) {
            comparator = DataMemoryCursor.SORT_BY_ID;
        }
        return new DataMemoryCursor(cursor, context, comparator);
    }

    protected String getSql(String sqlName) {
        String select = engine.getRouterService().getSql(sqlName);
        Channel channel = context.getChannel().getChannel();
        if (!channel.isUseOldDataToRoute() || context.isOnlyDefaultRoutersAssigned()) {
            select = select.replace("d.old_data", "''");
        }
        if (!channel.isUseRowDataToRoute() || (context.isOnlyDefaultRoutersAssigned() && !channel.isFileSyncFlag())) {
            select = select.replace("d.row_data", "''");
        }
        if (!channel.isUsePkDataToRoute() || context.isOnlyDefaultRoutersAssigned()) {
            select = select.replace("d.pk_data", "''");
        }
        return engine.getSymmetricDialect().massageDataExtractionSql(select, context.isOverrideContainsBigLob() || channel.isContainsBigLob());
    }

    protected String qualifyUsingDataGaps(List<DataGap> dataGaps, int numberOfGapsToQualify, String sql) {
        StringBuilder gapClause = new StringBuilder();
        for (int i = 0; i < numberOfGapsToQualify && i < dataGaps.size(); i++) {
            if (i == 0) {
                gapClause.append(" and (");
            } else {
                gapClause.append(" or ");
            }
            gapClause.append("(d.data_id between ? and ?)");
        }
        gapClause.append(")");
        return FormatUtils.replace("dataRange", gapClause.toString(), sql);
    }

    protected String qualifyForOrder(String sql) {
        if (!isSortInMemory && parameterService.is(ParameterConstants.ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED, true)) {
            sql = String.format("%s %s", sql, engine.getRouterService().getSql("orderByDataId"));
        }
        return sql;
    }

    @Override
    public boolean isEachGapQueried() {
        return isEachGapQueried;
    }
}
