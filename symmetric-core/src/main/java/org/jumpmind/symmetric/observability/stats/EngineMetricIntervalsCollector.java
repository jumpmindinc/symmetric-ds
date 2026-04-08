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
package org.jumpmind.symmetric.observability.stats;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.observability.models.MetricInterval;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists completed {@link MetricInterval} records to two database tables.
 *
 * <p>{@code {prefix}_metric_attributes} stores the mapping from a compact {@code BIGINT} surrogate
 * key to the {@link MetricKey} identity (hostname, engine name, metric ID). This avoids repeating
 * the string-valued identity in every interval row.
 *
 * <p>{@code {prefix}_metric_intervals} stores one row per completed 5-minute window, keyed by
 * {@code (metric_attr_id, interval_start)}.
 *
 * <p>The attribute ID cache is loaded from the database on {@link #setupTables()} so the mapping
 * survives engine restarts without re-inserting existing rows.
 */
public class EngineMetricIntervalsCollector {
    private static final Logger log = LoggerFactory.getLogger(EngineMetricIntervalsCollector.class);

    private final IDatabasePlatform platform;
    private final ISqlTemplate sqlTemplate;
    private final String attrTable;
    private final String intervalsTable;

    /** In-memory cache of MetricKey → surrogate metric_attr_id. */
    private final Map<MetricKey, Long> attrIdCache = new ConcurrentHashMap<>();

    /** Next available surrogate ID; seeded from MAX(metric_attr_id) on startup. */
    private final AtomicLong nextAttrId = new AtomicLong(1);

    public EngineMetricIntervalsCollector(ISymmetricEngine engine) {
        this.platform = engine.getDatabasePlatform();
        this.sqlTemplate = engine.getSqlTemplate();
        String prefix = engine.getParameterService().getTablePrefix();
        this.attrTable = platform.alterCaseToMatchDatabaseDefaultCase(
                TableConstants.getTableName(prefix, TableConstants.SYM_METRIC_KEY));
        this.intervalsTable = platform.alterCaseToMatchDatabaseDefaultCase(
                TableConstants.getTableName(prefix, TableConstants.SYM_METRIC_INTERVAL));
    }

    /**
     * Creates the two metric tables if they do not already exist and loads the attribute ID cache
     * from any existing rows. Must be called after the engine's main database setup completes.
     */
    public void setupTables() {
        Table attrT = new Table(attrTable);
        attrT.addColumn(new Column("metric_attr_id", true, Types.BIGINT, 0, 0));
        attrT.addColumn(new Column("hostname", false, Types.VARCHAR, 255, 0));
        attrT.addColumn(new Column("engine_name", false, Types.VARCHAR, 255, 0));
        attrT.addColumn(new Column("metric_id", false, Types.VARCHAR, 255, 0));
        platform.alterCaseToMatchDatabaseDefaultCase(attrT);

        Table intervalsT = new Table(intervalsTable);
        intervalsT.addColumn(new Column("metric_attr_id", true, Types.BIGINT, 0, 0));
        intervalsT.addColumn(new Column("interval_start", true, Types.BIGINT, 0, 0));
        intervalsT.addColumn(new Column("interval_end", false, Types.BIGINT, 0, 0));
        intervalsT.addColumn(new Column("avg_value", false, Types.DOUBLE, 0, 0));
        intervalsT.addColumn(new Column("min_value", false, Types.DOUBLE, 0, 0));
        intervalsT.addColumn(new Column("max_value", false, Types.DOUBLE, 0, 0));
        intervalsT.addColumn(new Column("std_dev", false, Types.DOUBLE, 0, 0));
        intervalsT.addColumn(new Column("observation_count", false, Types.INTEGER, 0, 0));
        platform.alterCaseToMatchDatabaseDefaultCase(intervalsT);

        platform.alterTables(false, attrT, intervalsT);
        log.info("Metric storage tables verified: {}, {}", attrTable, intervalsTable);

        loadAttributeCache();
    }

    private void loadAttributeCache() {
        long[] maxId = {0};
        sqlTemplate.query(
                "SELECT metric_attr_id, hostname, engine_name, metric_id FROM " + attrTable,
                row -> {
                    long id = row.getLong("metric_attr_id");
                    attrIdCache.put(
                            new MetricKey(row.getString("hostname"), row.getString("engine_name"), row.getString("metric_id")),
                            id);
                    if (id > maxId[0]) {
                        maxId[0] = id;
                    }
                    return null;
                });
        nextAttrId.set(maxId[0] + 1);
        log.debug("Loaded {} metric attribute mappings from database (next id={})", attrIdCache.size(), nextAttrId.get());
    }

    /**
     * Persists a batch of newly completed intervals within a single transaction.
     * Any {@link MetricKey} not yet present in {@code metric_attributes} is inserted first.
     *
     * @param intervals entries produced by the aggregation cycle, newest-first order is acceptable
     */
    public void save(List<Map.Entry<MetricKey, MetricInterval>> intervals) {
        if (intervals.isEmpty()) {
            return;
        }
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();

            // Phase 1: ensure every MetricKey has a surrogate ID row in metric_attributes.
            for (Map.Entry<MetricKey, MetricInterval> entry : intervals) {
                ensureAttrId(transaction, entry.getKey());
            }

            // Phase 2: batch-insert the interval rows.
            String insertSql = "INSERT INTO " + intervalsTable
                    + " (metric_attr_id, interval_start, interval_end, avg_value, min_value, max_value, std_dev, observation_count)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            transaction.prepare(insertSql);
            for (Map.Entry<MetricKey, MetricInterval> entry : intervals) {
                MetricInterval iv = entry.getValue();
                long attrId = attrIdCache.get(entry.getKey());
                transaction.addRow(null,
                        new Object[] { attrId, iv.intervalStart(), iv.intervalEnd(),
                                iv.avg(), iv.min(), iv.max(), iv.stdDev(), iv.observationCount() },
                        new int[] { Types.BIGINT, Types.BIGINT, Types.BIGINT,
                                Types.DOUBLE, Types.DOUBLE, Types.DOUBLE, Types.DOUBLE,
                                Types.INTEGER });
            }
            transaction.flush();
            transaction.commit();
            log.debug("Saved {} metric intervals to database", intervals.size());
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            log.error("Failed to save {} metric intervals to database", intervals.size(), e);
        } finally {
            close(transaction);
        }
    }

    /**
     * If this {@link MetricKey} has no surrogate ID yet, allocates one and inserts a row into
     * {@code metric_attributes} within the supplied transaction. Updates the in-memory cache.
     */
    private void ensureAttrId(ISqlTransaction transaction, MetricKey key) {
        if (attrIdCache.containsKey(key)) {
            return;
        }
        long id = nextAttrId.getAndIncrement();
        transaction.prepareAndExecute(
                "INSERT INTO " + attrTable + " (metric_attr_id, hostname, engine_name, metric_id) VALUES (?, ?, ?, ?)",
                new Object[] { id, key.hostname(), key.engineName(), key.metricId() },
                new int[] { Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR });
        attrIdCache.put(key, id);
    }

    private void close(ISqlTransaction transaction) {
        if (transaction != null) {
            try {
                transaction.close();
            } catch (Exception ignored) {
            }
        }
    }
}
