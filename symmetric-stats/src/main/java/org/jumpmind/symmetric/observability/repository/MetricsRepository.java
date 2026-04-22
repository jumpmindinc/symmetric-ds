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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricIntervalStatsRecord;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.jumpmind.symmetric.observability.stats.MetricSeriesSlidingWorkset;
import org.jumpmind.symmetric.service.impl.AbstractService;

/**
 * Repository for all database access to metrics keys and data. Manages 3 tables related to metrics:
 * <p>
 * <b>Metric_Key</b> — A header record with a surrogate int64 key used by other (fact) tables. <b>Metric_Stats_Int64</b> — A fact record with min, max and avg
 * values as int64 (long in Java). <b>Metric_Stats_Float64</b> — A fact record with min, max and avg values as float64 (double in Java).
 * <p>
 * An in-memory cache of {@link MetricKey} → surrogate {@code metric_key} is lazy-loaded and reused across all operations to avoid database JOINs.
 */
public class MetricsRepository extends AbstractService {
    public static final String METRIC_SHARED_ENGINE = "*";
    private final String engineName;
    private final String hostname;
    /** In-memory cache of MetricKey → surrogate metric_key. Loaded lazily on first use. */
    private final Map<Integer, MetricKey> metricKeysCache = new ConcurrentHashMap<>();
    /** Long surrogate key, allocated via database. */
    private final SurrogateLongKeyBuffer surrogateKeys = new SurrogateLongKeyBuffer();
    private volatile boolean cacheLoaded = false;

    public MetricsRepository(ISymmetricEngine engine, String hostname) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engineName = engine.getEngineName();
        this.hostname = hostname;
        setSqlMap(new MetricsRepositorySqlMap(platform, createSqlReplacementTokens()));
    }

    private Integer generateCacheKey(String metricId, String engineName, String hostname, MetricFactType factType) {
        return Objects.hash(metricId, engineName, hostname, factType);
    }

    private Integer generateCacheKey(MetricKey key) {
        return generateCacheKey(key.metricId(), key.engineName(), key.hostname(), key.factType());
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
            cacheLoaded = true;
        }
    }

    public MetricKey getMetricKey(String metricId, MetricFactType factType, boolean isEnabled) {
        return getOrRegisterMetricKey(metricId, this.engineName, this.hostname, factType, isEnabled);
    }

    /**
     * Fetches {@link MetricKey} record from cache or database. Ensures the surrogate ID assigned to new entries.
     */
    public MetricKey getOrRegisterMetricKey(String metricId, String engineName, String hostname, MetricFactType factType, boolean isEnabled) {
        ensureMetricKeyCacheLoaded();
        Integer cacheKey = generateCacheKey(metricId, engineName, hostname, factType);
        MetricKey metricKeyRec = metricKeysCache.get(cacheKey);
        if (metricKeyRec != null) {
            if (log.isDebugEnabled()) {
                log.debug("Cache hit for metric key {}", metricKeyRec);
            }
            return metricKeyRec;
        }
        return saveMetricKeyInternal(metricId, engineName, hostname, factType, isEnabled);
    }

    /**
     * Ensure MetricKey has surrogate ID assigned and stored in database, if it's new (not in cache already)
     */
    public MetricKey getOrRegisterMetricKey(MetricKey key) {
        ensureMetricKeyCacheLoaded();
        Integer cacheKey = generateCacheKey(key);
        MetricKey metricKeyRec = metricKeysCache.get(cacheKey);
        if (metricKeyRec != null) {
            if (log.isDebugEnabled()) {
                log.debug("Cache hit for metric key {}", metricKeyRec);
            }
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
            if (log.isDebugEnabled()) {
                log.debug("Cache already has an entry for {}", metricKeyRec);
            }
            return reconcileMetricKeyWithCachedEntry(metricKeyRec, cachedRec);
        }
        return saveMetricKeyInternal(metricKeyRec);
    }

    private MetricKey assignSurrogateKeyAndSaveMetricKeyToDatabase(String metricId, String engineName, String hostname, MetricFactType factType,
            boolean isEnabled) {
        long surrogateKey = surrogateKeys.getNextValue();
        MetricKey metricKeyRec = new MetricKey(surrogateKey, hostname, engineName, metricId, factType, isEnabled);
        metricKeyRec = saveMetricKeyToDatabase(metricKeyRec);
        if (log.isDebugEnabled()) {
            log.debug("Cache miss. Applied available surrogate key {} to new metric key {}", surrogateKey, metricKeyRec);
        }
        return metricKeyRec;
    }

    private MetricKey saveMetricKeyInternal(String metricId, String engineName, String hostname, MetricFactType factType, boolean isEnabled) {
        MetricKey metricKeyRec = null;
        if (surrogateKeys.isAvailable() && !METRIC_SHARED_ENGINE.equals(engineName)) {
            try {
                metricKeyRec = assignSurrogateKeyAndSaveMetricKeyToDatabase(metricId, engineName, hostname, factType, isEnabled);
            } catch (MetricsRepositoryException e) {
                if (!isCausedByUniqueKeyViolation(e)) {
                    throw e;
                }
                log.warn("Surrogate key collision for metricId={}, engine={}, falling back to DB-generated key", metricId, engineName);
            }
        }
        if (metricKeyRec == null) {
            metricKeyRec = generateSurrogateKeyAndSaveMetricKeyToDatabase(metricId, engineName, hostname, factType, isEnabled);
            if (!METRIC_SHARED_ENGINE.equals(engineName)) {
                long nextAvailableValue = metricKeyRec.key() + 1;
                long bufferStart = SurrogateLongKeyBuffer.roundDownToBufferStart(nextAvailableValue);
                surrogateKeys.moveTo(bufferStart, nextAvailableValue);
            }
        }
        metricKeysCache.put(generateCacheKey(metricKeyRec), metricKeyRec);
        if (log.isDebugEnabled()) {
            log.debug("Cache miss. Inserted new metric key record {}", metricKeyRec);
        }
        return metricKeyRec;
    }

    private boolean isCausedByUniqueKeyViolation(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof UniqueKeyException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private MetricKey reconcileMetricKeyWithCachedEntry(MetricKey metricKeyRec, MetricKey cachedRec) {
        if (cachedRec == null) {
            return metricKeyRec;
        }
        if (metricKeyRec == null
                || (metricKeyRec.equalsOnCompositeKey(cachedRec) && metricKeyRec.isSurrogateKeyMissing())) {
            if (cachedRec.isSurrogateKeyMissing()) {
                String message = "Cached MetricKey entries must have surrogate key already assigned! " + cachedRec;
                log.warn(message);
                throw new MetricsRepositoryException(message);
            }
            return cachedRec;
        }
        return metricKeyRec;
    }

    private static final int SURROGATE_KEY_MAX_RETRIES = 3;

    private MetricKey generateSurrogateKeyAndSaveMetricKeyToDatabase(String metricId, String engineName, String hostname, MetricFactType factType,
            boolean isEnabled) {
        String keyInfo = String.format(" metricId=%s, engine=%s, hostname=%s, factType=%s", metricId, engineName, hostname, factType);
        long surrogateKeyBufferSize = SurrogateLongKeyBuffer.SURROGATE_KEY_BUFFER_SIZE;
        log.debug("Saving metric key and determining new surrogate key ... {}", keyInfo);
        for (int attempt = 1; attempt <= SURROGATE_KEY_MAX_RETRIES; attempt++) {
            ISqlTransaction transaction = null;
            Object[] statementParams = { surrogateKeyBufferSize, surrogateKeyBufferSize, surrogateKeyBufferSize, metricId, engineName, hostname, factType
                    .name(), isEnabled ? 1 : 0 };
            int[] statementTypes = { Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT };
            try {
                transaction = sqlTemplate.startSqlTransaction();
                transaction.prepareAndExecute(getSql("generateSurrogateSql"), statementParams, statementTypes);
                transaction.commit();
                break;
            } catch (Exception e) {
                if (transaction != null) {
                    transaction.rollback();
                    transaction = null;
                }
                if (isCausedByUniqueKeyViolation(e) && attempt < SURROGATE_KEY_MAX_RETRIES) {
                    log.warn("Surrogate key collision on DB-generated path for {} (attempt {}/{}), retrying", keyInfo, attempt, SURROGATE_KEY_MAX_RETRIES);
                    continue;
                }
                String message = "Failed to save metric key: " + keyInfo;
                log.error(message, e);
                throw new MetricsRepositoryException(message, e);
            } finally {
                if (transaction != null) {
                    close(transaction);
                }
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
        String keyInfo = String.format(" metricKey=%d, metricId=%s, engine=%s, hostname=%s, factType=%s",
                key.key(), key.metricId(), key.engineName(), key.hostname(), key.factType());
        MetricKey metricKeyInDatabase = loadMetricKeyFromDatabase(key.metricId(), key.engineName(), key.hostname());
        if (key.equals(metricKeyInDatabase)) {
            log.debug("Skipping database update, because nothing had changed for metric key: {}", keyInfo);
            return key;
        }
        if (metricKeyInDatabase != null) {
            return updateMetricKeyInDatabase(key, metricKeyInDatabase);
        }
        log.debug("Saving metric key... {}", keyInfo);
        ISqlTransaction transaction = null;
        Object[] statementParams = { key.key(), key.hostname(), key.engineName(), key.metricId(), key.factType().name(), key.isEnabled() ? 1 : 0 };
        int[] statementTypes = { Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT };
        try {
            transaction = sqlTemplate.startSqlTransaction();
            transaction.prepareAndExecute(getSql("insertMetricKeySql"), statementParams, statementTypes);
            transaction.commit();
            log.debug("Saved metric key {}", key);
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
                transaction = null;
            }
            String message = "Failed to save metric key: " + key;
            log.error(message, e);
            throw new MetricsRepositoryException(message, e);
        } finally {
            if (transaction != null) {
                close(transaction);
            }
        }
        return key;
    }

    private MetricKey updateMetricKeyInDatabase(MetricKey key, MetricKey dbRecord) {
        String keyInfo = String.format(" metricKey=%d, metricId=%s, engine=%s, hostname=%s, factType=%s",
                dbRecord.key(), dbRecord.metricId(), dbRecord.engineName(), dbRecord.hostname(), dbRecord.factType());
        if (key.isSurrogateKeyMissing() || key.equals(dbRecord)) {
            log.debug("Database record already has surrogate key assigned! DB entry for metric key:{}, Current surrogateKey={}", keyInfo, key.key());
            return dbRecord;
        }
        log.debug("Updating metric key... {}, new surrogateKey={}", keyInfo, key.key());
        ISqlTransaction transaction = null;
        Object[] statementParams = { key.key(), key.factType().name(), key.isEnabled() ? 1 : 0, key.metricId(), key.engineName(), key.hostname() };
        int[] statementTypes = { Types.BIGINT, Types.VARCHAR, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        try {
            transaction = sqlTemplate.startSqlTransaction();
            transaction.prepareAndExecute(getSql("updateMetricKeySql"), statementParams, statementTypes);
            transaction.commit();
            log.debug("Updated metric key {}", key);
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
                transaction = null;
            }
            String message = "Failed to update metric key: " + key;
            log.error(message, e);
            throw new MetricsRepositoryException(message, e);
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

    private MetricKey loadMetricKeyFromDatabase(String metricId, String engineName, String hostname) {
        Object[] paramValues = { metricId, hostname, engineName, METRIC_SHARED_ENGINE };
        int[] paramTypes = { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        List<MetricKey> results = sqlTemplate.query(getSql("selectMetricKeyByIdSql"), new MetricKeySqlRowMapper(), paramValues, paramTypes);
        if (results == null || results.isEmpty()) {
            return null;
        }
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
                    } else if (nextAvailableValue <= key.key()) {
                        nextAvailableValue = key.key() + 1;
                    }
                }
            }
            bufferStart = SurrogateLongKeyBuffer.roundDownToBufferStart(nextAvailableValue);
            surrogateKeys.moveTo(bufferStart, nextAvailableValue);
        }
        log.info("Processed {} keys. Computed max surrogate key span: start={}, next.value={}", metricKeys.size(), bufferStart, nextAvailableValue);
    }

    private int populateMetricKeyCache(List<MetricKey> metricKeyRecs) {
        if (metricKeyRecs == null || metricKeyRecs.isEmpty()) {
            log.warn("Failed to loaded any metric keys from database! hostname={}, engine_name={}", hostname, engineName);
            return 0;
        }
        for (MetricKey key : metricKeyRecs) {
            this.metricKeysCache.put(generateCacheKey(key), key);
            log.debug("Loaded metric key from database. key={}, id={}, engine={}, hostname={}, factType={}",
                    key.key(), key.metricId(), key.engineName(), key.hostname(), key.factType());
        }
        int total = metricKeyRecs.size();
        log.info("Loaded {} metric keys from database. hostname={}, engine_name={}", total, hostname, engineName);
        return total;
    }

    private List<MetricKey> loadAllMetricKeysForHostnameFromDatabase() {
        Object[] paramValues = { hostname, engineName, METRIC_SHARED_ENGINE };
        int[] paramTypes = { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        List<MetricKey> metricKeys = sqlTemplate.query(getSql("selectMetricKeysByHostnameSql"), new MetricKeySqlRowMapper(), paramValues, paramTypes);
        if (metricKeys == null) {
            metricKeys = new ArrayList<MetricKey>(0);
        }
        log.debug("Loaded {} metric keys from database. Engine={}, hostname={}", metricKeys.size(), engineName, hostname);
        return metricKeys;
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
        long startTime = System.currentTimeMillis();
        ensureMetricKeyCacheLoaded();
        MetricKey prevKey = null; // Skips cache and database lookups for repeat references to the same metric key
        MetricKey skipKey = null; // Skips cache and database writes for repeat references to the same (failed to save) metric key.
        int savedCount = 0;
        for (MetricIntervalStatsRecord record : intervalStats) {
            MetricKey key = record.key();
            if (key == null || key.equalsOnCompositeKey(skipKey)) {
                continue;
            }
            if (key.equalsOnCompositeKey(prevKey)) {
                key = prevKey;
            } else {
                try {
                    key = getOrRegisterMetricKey(key);
                } catch (Exception ex) {
                    skipKey = key;
                    continue;
                }
            }
            if (!key.isEnabled()) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipped deactivated metric. Id={}, engine={}, hostname={}", key.metricId(), key.engineName(), key.hostname());
                }
                continue;
            }
            saveMetricIntervalStatsInternal(key, record.stats());
            savedCount++;
        }
        log.info("Saved {} metric interval stats records to database in {} seconds", savedCount, (System.currentTimeMillis() - startTime) / 1000.0);
    }

    /**
     * Returns recent (last 24-hours) intervals for the given metric key, ordered oldest-first so they can be fed sequentially into
     * {@link MetricSeriesSlidingWorkset#add}.
     * <p>
     * Uses the surrogate-key cache to avoid a JOIN: if the key is not present in the cache after loading, it has no stored intervals and an empty list is
     * returned immediately.
     */
    public List<ISymIntervalStats> loadRecentIntervalsForKeyFromDatabase(MetricKey key) {
        ensureMetricKeyCacheLoaded();
        if (!metricKeysCache.containsKey(generateCacheKey(key))) {
            return new ArrayList<ISymIntervalStats>(0);
        }
        if (key.isSurrogateKeyMissing()) {
            key = getOrRegisterMetricKey(key);
        }
        log.debug("Loading metric intervals from database for key... {}", key);
        long oneDayAgo = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(1);
        String sqlKey = key.factType() == MetricFactType.INT64 ? "selectRecentIntervalsInt64Sql" : "selectRecentIntervalsSql";
        List<ISymIntervalStats> rows = sqlTemplate.query(
                getSql(sqlKey), MetricSeriesSlidingWorkset.IQR_INTERVALS_MIN, new DoubleStatsSqlRowMapper(), key.key(), oneDayAgo);
        log.info("Loaded {} historical intervals for metric {}", rows.size(), key);
        return rows;
    }

    private void saveMetricIntervalStatsInternal(MetricKey key, ISymIntervalStats intervalStats) {
        if (key == null) {
            String message = "key cannot be null!";
            log.error(message);
            throw new MetricsRepositoryException(message);
        }
        if (intervalStats == null) {
            String message = "intervalStats cannot be null!";
            log.error(message);
            throw new MetricsRepositoryException(message);
        }
        log.debug("Saving metric interval stats for key... {}", key);
        ISqlTransaction transaction = null;
        String sqlKey;
        Object[] statementParams;
        int[] statementTypes;
        if (key.factType() == MetricFactType.INT64) {
            sqlKey = "insertMetricIntervalInt64Sql";
            statementParams = new Object[] {
                    key.key(),
                    intervalStats.getStartEpoch(), new java.sql.Timestamp(intervalStats.getEndEpoch()),
                    (long) intervalStats.getAvg(), (long) intervalStats.getMin(), (long) intervalStats.max(),
                    intervalStats.getStdDeviation(), intervalStats.getObservationCount(),
                    (long) intervalStats.mean(),
                    (intervalStats.getEndEpoch() - intervalStats.getStartEpoch()) / 1000 };
            statementTypes = new int[] {
                    Types.BIGINT,
                    Types.BIGINT, Types.TIMESTAMP,
                    Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.DOUBLE,
                    Types.INTEGER, Types.BIGINT, Types.INTEGER };
        } else {
            sqlKey = "insertMetricIntervalSql";
            statementParams = new Object[] {
                    key.key(),
                    intervalStats.getStartEpoch(), new java.sql.Timestamp(intervalStats.getEndEpoch()),
                    intervalStats.getAvg(), intervalStats.getMin(), intervalStats.max(),
                    intervalStats.getStdDeviation(), intervalStats.getObservationCount(),
                    intervalStats.mean(),
                    (intervalStats.getEndEpoch() - intervalStats.getStartEpoch()) / 1000 };
            statementTypes = new int[] {
                    Types.BIGINT,
                    Types.BIGINT, Types.TIMESTAMP,
                    Types.DOUBLE, Types.DOUBLE, Types.DOUBLE, Types.DOUBLE,
                    Types.INTEGER, Types.DOUBLE, Types.INTEGER };
        }
        try {
            transaction = sqlTemplate.startSqlTransaction();
            transaction.prepareAndExecute(getSql(sqlKey), statementParams, statementTypes);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
                transaction = null;
            }
            String message = "Failed to save metric interval stats for key: " + key;
            log.error(message, e);
            throw new MetricsRepositoryException(message, e);
        } finally {
            if (transaction != null) {
                close(transaction);
            }
        }
        log.info("Saved metric interval stats for key: {}, Interval.start={}", key, intervalStats.getStartEpoch());
    }

    private MetricKey saveMetricKeyInternal(MetricKey metricKeyRec) {
        if (metricKeyRec == null) {
            String message = "metricKeyRec cannot be null!";
            log.error(message);
            throw new MetricsRepositoryException(message);
        }
        if (metricKeyRec.isSurrogateKeyMissing()) {
            return saveMetricKeyInternal(metricKeyRec.metricId(), metricKeyRec.engineName(), metricKeyRec.hostname(), metricKeyRec.factType(), metricKeyRec
                    .isEnabled());
        }
        return saveMetricKeyToDatabase(metricKeyRec);
    }

    /**
     * Reads a DOUBLE column from a {@link Row}. {@code Row} has no {@code getDouble} method; JDBC drivers typically return {@link Double} objects for DOUBLE
     * columns, so we go through the {@link Number} supertype to preserve full 64-bit precision.
     */
    private static double rowDouble(Row row, String columnName) {
        Object v = row.get(columnName);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    static class MetricKeySqlRowMapper implements ISqlRowMapper<MetricKey> {
        @Override
        public MetricKey mapRow(Row row) {
            long key = row.getLong("metric_key");
            String hostname = row.getString("hostname");
            String engineName = row.getString("engine_name");
            String metricId = row.getString("metric_id");
            MetricFactType factType = MetricFactType.valueOf(row.getString("fact_type"));
            boolean isEnabled = row.getInt("enabled") != 0;
            return new MetricKey(key, hostname, engineName, metricId, factType, isEnabled);
        }
    }

    /**
     * Serializes MetricIntervalStats to/from database record.
     */
    static class DoubleStatsSqlRowMapper implements ISqlRowMapper<ISymIntervalStats> {
        @Override
        public ISymIntervalStats mapRow(Row row) {
            long intervalStart = row.getLong("interval_start");
            java.sql.Timestamp endTimestamp = (java.sql.Timestamp) row.get("end_time");
            long intervalEnd = endTimestamp != null ? endTimestamp.getTime() : 0L;
            double avg = rowDouble(row, "avg"); // The time-weighted average
            double min = rowDouble(row, "min");
            double max = rowDouble(row, "max");
            double stdDev = rowDouble(row, "std_dev");
            int observationCount = row.getInt("observation_count");
            double mean = rowDouble(row, "mean");
            return new MetricIntervalStats(intervalStart, intervalEnd, avg, min, max, stdDev, observationCount, mean, false);
        }
    };
}
