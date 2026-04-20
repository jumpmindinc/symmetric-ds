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
package org.jumpmind.symmetric.observability.repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricIntervalStatsRecord;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.jumpmind.symmetric.observability.stats.MetricSeriesSlidingWorkset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository for all database access to metrics keys and data.
 *
 * <p>
 * <b>MetricKey</b> — {@link #saveMetricKey} inserts a key if not already present; {@link #loadAllMetricKeys} returns every key stored in the database.
 *
 * <p>
 * <b>Intervals — write</b> — {@link #saveIntervals} persists a batch of completed {@link MetricIntervalStats} records in a single transaction, inserting any
 * new {@link MetricKey} dimension rows as part of the same transaction.
 *
 * <p>
 * <b>Intervals — read</b> — {@link #loadRecentIntervals} returns the most recent {@link MetricSeriesSlidingWorkset#IQR_INTERVALS_MIN} intervals for a given
 * key, ordered oldest-first, to seed a freshly created {@link MetricSeriesSlidingWorkset}.
 *
 * <p>
 * An in-memory cache of {@link MetricKey} → surrogate {@code metric_key} is loaded lazily on first use and reused across all operations to avoid redundant
 * JOINs.
 */
public class MetricsRepository {
    private static final Logger log = LoggerFactory.getLogger(MetricsRepository.class);
    public static final String METRIC_SHARED_ENGINE = "*";
    private final String engineName;
    private final String hostname;
    private final String keyTable;
    private final String intervalsTable;
    private final ISymmetricEngine engine;
    /** In-memory cache of MetricKey → surrogate metric_key. Loaded lazily on first use. */
    private final Map<Integer, MetricKey> metricKeysCache = new ConcurrentHashMap<>();
    /** Long surrogate key, allocated via database. */
    private final SurrogateLongKeyBuffer surrogateKeys = new SurrogateLongKeyBuffer();
    private volatile boolean cacheLoaded = false;

    public MetricsRepository(ISymmetricEngine engine, String hostname) {
        this.engine = engine;
        this.engineName = engine.getEngineName();
        this.hostname = hostname;
        String prefix = engine.getParameterService().getTablePrefix();
        IDatabasePlatform platform = engine.getDatabasePlatform();
        this.keyTable = platform.alterCaseToMatchDatabaseDefaultCase(
                TableConstants.getTableName(prefix, TableConstants.SYM_METRIC_KEY));
        this.intervalsTable = platform.alterCaseToMatchDatabaseDefaultCase(
                TableConstants.getTableName(prefix, TableConstants.SYM_METRIC_INTERVAL));
    }

    private Integer generateCacheKey(String metricId, String engineName, String hostname) {
        return Objects.hash(metricId, engineName, hostname);
    }

    private Integer generateCacheKey(MetricKey key) {
        return generateCacheKey(key.metricId(), key.engineName(), key.hostname());
    }

    public void initializeCache() {
        ensureMetricKeyCacheLoaded();
    }

    private void ensureMetricKeyCacheLoaded() {
        if (!cacheLoaded) {
            List<MetricKey> metricKeys = loadAllMetricKeysForHostnameFromDatabase();
            if (metricKeys != null && !metricKeys.isEmpty()) {
                populateSurrogateKeyBuffer(metricKeys);
                populateMetricKeyCache(metricKeys);
            } else {
                log.warn("Found no metric keys in database! hostname={}, engine_name={}", hostname, engineName);
            }
        }
    }

    public MetricKey getMetricKey(String metricId) {
        return getOrRegisterMetricKey(metricId, this.engineName, this.hostname);
    }

    /**
     * Fetches {@link MetricKey} record from cache or database. Ensures the surrogate ID assigned to new entries.
     */
    public MetricKey getOrRegisterMetricKey(String metricId, String engineName, String hostname) {
        ensureMetricKeyCacheLoaded();
        Integer cacheKey = generateCacheKey(metricId, engineName, hostname);
        MetricKey metricKeyRec = metricKeysCache.get(cacheKey);
        if (metricKeyRec != null) {
            log.debug("Cache hit for metric key {}", metricKeyRec);
            return metricKeyRec;
        }
        return saveMetricKeyInternal(metricId, engineName, hostname);
    }

    /**
     * Ensure MetricKey has surrogate ID assigned and stored in database, if it's new (not in cache already)
     */
    public MetricKey getOrRegisterMetricKey(MetricKey key) {
        ensureMetricKeyCacheLoaded();
        Integer cacheKey = generateCacheKey(key);
        MetricKey metricKeyRec = metricKeysCache.get(cacheKey);
        if (metricKeyRec != null) {
            log.debug("Cache hit for metric key {}", metricKeyRec);
            return metricKeyRec;
        }
        return saveMetricKeyInternal(key);
    }

    /**
     * Ensures this {@link MetricKey} record is present in cache or saves it to database and assigns a surrogate ID, if it's undefined.
     */
    private MetricKey saveMetricKey(MetricKey metricKeyRec) {
        ensureMetricKeyCacheLoaded();
        MetricKey cachedRec = metricKeysCache.get(generateCacheKey(metricKeyRec));
        if (cachedRec != null) {
            log.debug("Cache already has an entry for {}", metricKeyRec);
            return reconcileMetricKeyWithCachedEntry(metricKeyRec, cachedRec);
        }
        return saveMetricKeyInternal(metricKeyRec);
    }

    private MetricKey assignSurrogateKeyAndSaveMetricKeyToDatabase(String metricId, String engineName, String hostname) {
        long surrogateKey = surrogateKeys.getNextValue();
        MetricKey metricKeyRec = new MetricKey(surrogateKey, metricId, engineName, hostname);
        metricKeyRec = saveMetricKeyToDatabase(metricKeyRec);
        log.debug("Cache miss. Applied available surrogate key {} to new metric key {}", surrogateKey, metricKeyRec);
        return metricKeyRec;
    }

    private MetricKey saveMetricKeyInternal(String metricId, String engineName, String hostname) {
        MetricKey metricKeyRec = null;
        if (surrogateKeys.isAvailable() && !METRIC_SHARED_ENGINE.equals(engineName)) {
            metricKeyRec = assignSurrogateKeyAndSaveMetricKeyToDatabase(hostname, engineName, metricId);
        } else {
            metricKeyRec = generateSurrogateKeyAndSaveMetricKeyToDatabase(hostname, engineName, metricId);
            if (!METRIC_SHARED_ENGINE.equals(engineName)) {
                long nextAvailableValue = metricKeyRec.key() + 1;
                long bufferStart = SurrogateLongKeyBuffer.roundDownToBufferStart(nextAvailableValue);
                surrogateKeys.moveTo(bufferStart, nextAvailableValue);
            }
        }
        log.debug("Cache miss. Inserted new metric key record {}", metricKeyRec);
        metricKeysCache.put(generateCacheKey(metricKeyRec), metricKeyRec);
        return metricKeyRec;
    }

    private MetricKey reconcileMetricKeyWithCachedEntry(MetricKey metricKeyRec, MetricKey cachedRec) {
        if (cachedRec == null) {
            return metricKeyRec;
        }
        if (metricKeyRec == null
                || (metricKeyRec.equalsIgnoreKey(cachedRec) && metricKeyRec.isSurrogateKeyMissing())) {
            if (cachedRec.isSurrogateKeyMissing()) {
                String message = "Cached MetricKey entries must have surrogate key already assigned! " + cachedRec;
                log.warn(message);
                throw new IllegalStateException();
            }
            return cachedRec;
        }
        return metricKeyRec;
    }

    private MetricKey generateSurrogateKeyAndSaveMetricKeyToDatabase(String metricId, String engineName, String hostname) {
        String keyInfo = String.format(" metricId=%s, engine=%s, hostname=%s", metricId, engineName, hostname);
        long surrogateKeyBufferSize = SurrogateLongKeyBuffer.SURROGATE_KEY_BUFFER_SIZE;
        log.debug("Saving metric key and determining new surrogate key ... {}", keyInfo);
        ISqlTransaction transaction = null;
        String sql = "INSERT INTO " + keyTable + " (metric_key, metric_id, engine_name, hostname) "
                + " SELECT (?*(MAX(metric_key)+?))/?, ?, ?, ? FROM " + keyTable;
        Object[] statementParams = { surrogateKeyBufferSize, surrogateKeyBufferSize, surrogateKeyBufferSize, metricId, engineName, hostname };
        int[] statementTypes = { Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        try {
            transaction = engine.getSqlTemplate().startSqlTransaction();
            transaction.prepareAndExecute(sql, statementParams, statementTypes);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
                // TODO: close connection if platform cannot recover from failed transactions!
                transaction = null;
            }
            log.error("Failed to save metric key:" + keyInfo, e);
        } finally {
            if (transaction != null) {
                close(transaction);
            }
        }
        MetricKey metricKeyRec = loadMetricKeyFromDatabase(metricId, engineName, hostname);
        if (!METRIC_SHARED_ENGINE.equals(engineName)) {
            synchronized (surrogateKeys) {
                surrogateKeys.moveTo(metricKeyRec.key(), metricKeyRec.key() + 1);
            }
        }
        log.info("Saved metric key and assigned surrogate key: {}", metricKeyRec);
        return metricKeyRec;
    }

    private MetricKey saveMetricKeyToDatabase(MetricKey key) {
        String keyInfo = String.format(" metricKey=%d, metricId=%s, engine=%s, hostname=%s", key.key(), key.metricId(), key.engineName(), key.hostname());
        MetricKey metricKeyInDatabase = loadMetricKeyFromDatabase(key.metricId(), key.engineName(), key.hostname());
        if (key.equals(metricKeyInDatabase)) {
            log.debug("Skipping database update, because nothing had changed for metric key: {}", key);
            return key;
        }
        if (metricKeyInDatabase != null) {
            return updateMetricKeyInDatabase(key, metricKeyInDatabase);
        }
        log.debug("Saving metric key... {}", keyInfo);
        ISqlTransaction transaction = null;
        String sql = "INSERT INTO " + keyTable + " (metric_key, hostname, engine_name, metric_id) VALUES (?, ?, ?, ?)";
        Object[] statementParams = { key.key(), key.hostname(), key.engineName(), key.metricId() };
        int[] statementTypes = { Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        try {
            transaction = engine.getSqlTemplate().startSqlTransaction();
            transaction.prepareAndExecute(sql, statementParams, statementTypes);
            transaction.commit();
            log.debug("Saved metric key {}", key);
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
                // TODO: close connection if platform cannot recover from failed transactions!
                transaction = null;
            }
            log.error("Failed to save metric key {}", key, e);
        } finally {
            if (transaction != null) {
                close(transaction);
            }
        }
        return key;
    }

    private MetricKey updateMetricKeyInDatabase(MetricKey key, MetricKey dbRecord) {
        String keyInfo = String.format(" metricKey=%d, metricId=%s, engine=%s, hostname=%s", dbRecord.key(), dbRecord.metricId(), dbRecord.engineName(),
                dbRecord.hostname());
        if (key.isSurrogateKeyMissing() || key.equals(dbRecord)) {
            log.debug("Database record already has surrogate key assigned! DB entry for metric key:{}, Current surrogateKey={}", keyInfo, key.key());
            return dbRecord;
        }
        log.debug("Updating metric key... {}, new surrogateKey={}", keyInfo, key.key());
        ISqlTransaction transaction = null;
        String sql = "UPDATE " + keyTable + " SET metric_key=? WHERE metric_id=? AND engine_name=? AND hostname=? ";
        Object[] statementParams = { key.key(), key.metricId(), key.engineName(), key.hostname() };
        int[] statementTypes = { Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        try {
            transaction = engine.getSqlTemplate().startSqlTransaction();
            transaction.prepareAndExecute(sql, statementParams, statementTypes);
            transaction.commit();
            log.debug("Updated metric key {}", key);
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
                // TODO: close connection if platform cannot recover from failed transactions!
                transaction = null;
            }
            log.error("Failed to update metric key {}", key, e);
        } finally {
            if (transaction != null) {
                close(transaction);
            }
        }
        return key;
    }

    /**
     * Returns all {@link MetricKey} records currently stored in the database. The result is backed by the in-memory cache loaded on first use.
     */
    public List<MetricKey> loadAllMetricKeys() {
        ensureMetricKeyCacheLoaded();
        return new ArrayList<>(metricKeysCache.values());
    }

    /**
     * Persists a batch of newly completed intervals within a single transaction. Any {@link MetricKey} not yet present in {@code metric_key} is inserted first.
     *
     * @param intervals
     *            entries produced by the aggregation cycle
     */
    public void saveIntervals(List<MetricIntervalStatsRecord> intervalStats) {
        if (intervalStats == null || intervalStats.isEmpty()) {
            return;
        }
        ensureMetricKeyCacheLoaded();
        MetricKey prevKey = null; // Skips cache and database lookups for repeat references to the same metric key
        for (MetricIntervalStatsRecord record : intervalStats) {
            MetricKey key = record.key().equalsIgnoreKey(prevKey) ? prevKey : getOrRegisterMetricKey(record.key());
            saveMetricIntervalStatsInternal(key, record.stats());
        }
        log.info("Saved {} metric intervat stats records to database", intervalStats.size());
    }
    /*
     * ISqlTransaction transaction = null; try { // transaction = engine.getSqlTemplate().startSqlTransaction();
     * 
     * // Phase 1: ensure every MetricKey has a surrogate ID row in metric_key.
     * 
     * 
     * // Phase 2: batch-insert the interval rows. transaction.prepare("INSERT INTO " + intervalsTable +
     * " (metric_key, interval_start, interval_end, avg, min, max, std_dev, observation_count)" + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"); for
     * (MetricIntervalStatsRecord record : intervals) { MetricIntervalStats iv = record.stats(); long attrId = metricKeysCache.get(record.key());
     * transaction.addRow(null, new Object[] { attrId, iv.intervalStart(), iv.intervalEnd(), iv.avg(), iv.min(), iv.max(), iv.stdDev(), iv.observationCount() },
     * new int[] { Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.DOUBLE, Types.DOUBLE, Types.DOUBLE, Types.DOUBLE, Types.INTEGER }); } transaction.flush();
     * transaction.commit(); log.debug("Saved {} metric intervals to database", intervals.size()); } catch (Exception e) { if (transaction != null) {
     * transaction.rollback(); } log.error("Failed to save {} metric intervals to database", intervals.size(), e); } finally { close(transaction); } }
     */

    /**
     * Returns the most recent {@link MetricSeriesSlidingWorkset#IQR_INTERVALS_MIN} intervals for the given metric key, ordered oldest-first so they can be fed
     * sequentially into {@link MetricSeriesSlidingWorkset#add}.
     *
     * <p>
     * Uses the surrogate-key cache to avoid a JOIN: if the key is not present in the cache after loading, it has no stored intervals and an empty list is
     * returned immediately.
     */
    public List<MetricIntervalStats> loadRecentIntervalsForKeyFromDatabase(MetricKey key) {
        ensureMetricKeyCacheLoaded();
        if (key.isSurrogateKeyMissing()) {
            key = getOrRegisterMetricKey(key);
        }
        log.debug("Loading metric intervals from database for key... ", key);
        String sql = "SELECT interval_start, interval_end, avg, min, max, std_dev, observation_count"
                + " FROM " + intervalsTable
                + " WHERE metric_key = ?"
                + " ORDER BY interval_start DESC";
        List<MetricIntervalStats> rows = engine.getSqlTemplate().query(
                sql, MetricSeriesSlidingWorkset.IQR_INTERVALS_MIN, ROW_MAPPER, key.key());
        Collections.reverse(rows); // oldest-first for chronological workset population
        log.info("Loaded {} historical intervals for metric {}", rows.size(), key);
        return rows;
    }

    static class MetricKeySqlRowMapper implements ISqlRowMapper<MetricKey> {
        @Override
        public MetricKey mapRow(Row row) {
            long key = row.getLong("metric_key");
            String hostname = row.getString("hostname");
            String engineName = row.getString("engine_name");
            String metricId = row.getString("metric_id");
            return new MetricKey(key, metricId, engineName, hostname);
        }
    }

    private MetricKey loadMetricKeyFromDatabase(String metricId, String engineName, String hostname) {
        String sql = "SELECT metric_key, metric_id, hostname, engine_name FROM " + keyTable
                + " WHERE metric_id=? AND hostname=? AND engine_name IN (?, ?)";
        Object[] paramValues = { metricId, hostname, engineName, METRIC_SHARED_ENGINE };
        int[] paramTypes = { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        List<MetricKey> results = engine.getSqlTemplate().query(sql, new MetricKeySqlRowMapper(), paramValues, paramTypes);
        if (results == null || results.isEmpty())
            return null;
        return results.get(0);
    }

    /**
     * Initializes (or advances) surrogate keys buffer to be either higher than all keys or encompass the last key.
     */
    private void populateSurrogateKeyBuffer(List<MetricKey> metricKeys) {
        long nextAvailableValue = 0;
        long bufferStart = 0;
        synchronized (surrogateKeys) {
            for (MetricKey key : metricKeys) {
                if (nextAvailableValue < key.key()) {
                    if (METRIC_SHARED_ENGINE.equals(key.engineName())) {
                        nextAvailableValue = SurrogateLongKeyBuffer.roundUpToNextBufferStart(key.key());
                    } else {
                        nextAvailableValue = key.key() + 1;
                    }
                }
            }
            bufferStart = SurrogateLongKeyBuffer.roundDownToBufferStart(nextAvailableValue);
            surrogateKeys.moveTo(bufferStart, nextAvailableValue);
        }
        log.info("Processed {} keys. Computed max surrogate key span: start={}, next.value={}", metricKeys.size(), bufferStart, nextAvailableValue);
    }

    private int populateMetricKeyCache(List<MetricKey> metricKeys) {
        List<MetricKey> metricKeyRecs = loadAllMetricKeysForHostnameFromDatabase();
        if (metricKeyRecs == null || metricKeyRecs.isEmpty()) {
            log.warn("Failed to loaded any metric keys from database! hostname={}, engine_name={}", hostname, engineName);
            return 0;
        }
        for (MetricKey key : metricKeyRecs) {
            this.metricKeysCache.put(generateCacheKey(key), key);
            log.debug("Loaded metric key from database. key={}, id={}, engine={}, hostname={}",
                    key.key(), key.metricId(), key.engineName(), key.hostname());
        }
        int total = metricKeyRecs.size();
        log.info("Loaded {} metric keys from database. hostname={}, engine_name={}", total, hostname, engineName);
        return total;
    }

    private List<MetricKey> loadAllMetricKeysForHostnameFromDatabase() {
        String sql = "SELECT metric_key, metric_id, hostname, engine_name FROM " + keyTable
                + " WHERE hostname = ? AND engine_name IN (?, ?)";
        Object[] paramValues = { hostname, engineName, METRIC_SHARED_ENGINE };
        int[] paramTypes = { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        List<MetricKey> metricKeys = engine.getSqlTemplate().query(sql, new MetricKeySqlRowMapper(), paramValues, paramTypes);
        if (metricKeys == null) {
            metricKeys = new ArrayList<MetricKey>(0);
        }
        log.debug("Loaded {} metric keys from database. hostname={}, engine_name={}", metricKeys.size(), hostname, engineName);
        return metricKeys;
    }

    private void saveMetricIntervalStatsInternal(MetricKey key, MetricIntervalStats intervalStats) {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null!");
        }
        if (intervalStats == null) {
            throw new IllegalArgumentException("intervalStats cannot be null!");
        }
        log.debug("Saving metric interval stats for key... {}", key);
        ISqlTransaction transaction = null;
        String sql = "INSERT INTO " + intervalsTable
                + " (metric_key, interval_start, interval_end, avg, min, max, std_dev, observation_count, created_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)";
        Object[] statementParams = {
                key.key(),
                intervalStats.intervalStart(), intervalStats.intervalEnd(),
                intervalStats.avg(), intervalStats.min(), intervalStats.max(),
                intervalStats.stdDev(), intervalStats.observationCount() };
        int[] statementTypes = {
                Types.BIGINT,
                Types.BIGINT, Types.BIGINT,
                Types.DOUBLE, Types.DOUBLE, Types.DOUBLE, Types.DOUBLE,
                Types.INTEGER };
        try {
            transaction = engine.getSqlTemplate().startSqlTransaction();
            transaction.prepareAndExecute(sql, statementParams, statementTypes);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
                // TODO: close connection if platform cannot recover from failed transactions!
                transaction = null;
            }
            log.error("Failed to save metric key: " + key, e);
        } finally {
            if (transaction != null) {
                close(transaction);
            }
        }
        log.info("Saved metric interval stats for key: {}, Interval.start={}", key, intervalStats.getStartEpoch());
    }

    private MetricKey saveMetricKeyInternal(MetricKey metricKeyRec) {
        if (metricKeyRec == null) {
            throw new IllegalArgumentException("metricKeyRec cannot be null!");
        }
        if (metricKeyRec.isSurrogateKeyMissing()) {
            return saveMetricKeyInternal(metricKeyRec.metricId(), metricKeyRec.engineName(), metricKeyRec.hostname());
        }
        return saveMetricKeyToDatabase(metricKeyRec);
    }

    /**
     * Reads a DOUBLE column from a {@link Row}. {@code Row} has no {@code getDouble} method; JDBC drivers typically return {@link Double} objects for DOUBLE
     * columns, so we go through the {@link Number} supertype to preserve full 64-bit precision.
     */
    private static double rowDouble(Row row, String columnName) {
        Object v = row.get(columnName);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    private static final ISqlRowMapper<MetricIntervalStats> ROW_MAPPER = new ISqlRowMapper<MetricIntervalStats>() {
        @Override
        public MetricIntervalStats mapRow(Row row) {
            long intervalStart = row.getLong("interval_start");
            long intervalEnd = row.getLong("interval_end");
            double avg = rowDouble(row, "avg");
            double min = rowDouble(row, "min");
            double max = rowDouble(row, "max");
            double stdDev = rowDouble(row, "std_dev");
            int observationCount = row.getInt("observation_count");
            // mean is not persisted separately; avg is used as the best available approximation
            return new MetricIntervalStats(intervalStart, intervalEnd, avg, min, max, stdDev, observationCount, avg, false);
        }
    };

    private void close(ISqlTransaction transaction) {
        if (transaction != null) {
            try {
                transaction.close();
            } catch (Exception ignored) {
            }
        }
    }
}
