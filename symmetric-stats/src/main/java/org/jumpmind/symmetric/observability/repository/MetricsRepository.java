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
import java.util.LinkedHashMap;
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
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.metrics.ContextDefinition;
import org.jumpmind.symmetric.observability.models.MetricContext;
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
    public static final int ATTR_MAX_VALUES = 3;
    public static final int ATTR_MIN_VALUES = 1;
    private final String engineName;
    private final String hostname;
    /** In-memory cache of MetricKey → surrogate metric_key. Loaded lazily on first use. */
    private final Map<Integer, MetricKey> metricKeysCache = new ConcurrentHashMap<>();
    /** Long surrogate key, allocated via database. */
    private final SurrogateLongKeyBuffer surrogateKeys = new SurrogateLongKeyBuffer();
    private volatile boolean cacheLoaded = false;
    /** Seed contexts (contextId <= MetricContext.SEED_IDS_END) — never evicted. */
    private final Map<String, MetricContext> seedContextCache = new ConcurrentHashMap<>();
    /** Dynamic contexts — LRU, max 2000 entries. */
    private final Map<String, MetricContext> dynamicContextCache = Collections.synchronizedMap(
            new LinkedHashMap<String, MetricContext>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, MetricContext> e) {
                    return size() > 2000;
                }
            });

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
            synchronized (this) {
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
            return metricKeyRec;
        }
        return saveMetricKeyInternal(key);
    }

    /**
     * Ensures this {@link MetricKey} record is present in cache or saves it to database and assigns a surrogate ID, if it's undefined.
     */
    public MetricKey saveMetricKey(MetricKey metricKeyRec) {
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
                }
                if (!isCausedByUniqueKeyViolation(e) || attempt >= SURROGATE_KEY_MAX_RETRIES) {
                    String message = "Failed to save metric key: " + keyInfo;
                    log.error(message, e);
                    throw new MetricsRepositoryException(message, e);
                }
                log.warn("Surrogate key collision on DB-generated path for {} (attempt {}/{}), retrying", keyInfo, attempt, SURROGATE_KEY_MAX_RETRIES);
            } finally {
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
            }
            String message = "Failed to save metric key: " + key;
            log.error(message, e);
            throw new MetricsRepositoryException(message, e);
        } finally {
            close(transaction);
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
            }
            String message = "Failed to update metric key: " + key;
            log.error(message, e);
            throw new MetricsRepositoryException(message, e);
        } finally {
            close(transaction);
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
            metricKeys = new ArrayList<>(0);
        }
        log.debug("Loaded {} metric keys from database. Engine={}, hostname={}", metricKeys.size(), engineName, hostname);
        return metricKeys;
    }
    // -----------------------------------------------------------------------
    // MetricContext — cache, surrogate ID, DB read/write
    // -----------------------------------------------------------------------

    static String contextCacheKey(List<MetricAttribute> attrs) {
        String v1 = MetricContext.NA;
        String v2 = MetricContext.NA;
        String v3 = MetricContext.NA;
        StringBuilder names = new StringBuilder();
        int size = attrs != null ? Math.min(attrs.size(), ATTR_MAX_VALUES) : 0;
        for (int i = 0; i < size; i++) {
            MetricAttribute a = attrs.get(i);
            if (i > 0) {
                names.append('+');
            }
            names.append(a.name() != null ? a.name() : "");
            String v = a.value() != null ? a.value() : MetricContext.NA;
            if (i == 0) {
                v1 = v;
            } else if (i == 1) {
                v2 = v;
            } else {
                v3 = v;
            }
        }
        return names.append('=').append(Objects.hash(v1, v2, v3)).toString();
    }

    private MetricContext getFromContextCache(String cacheKey) {
        MetricContext ctx = seedContextCache.get(cacheKey);
        return ctx != null ? ctx : dynamicContextCache.get(cacheKey);
    }

    private void putToContextCache(String cacheKey, MetricContext ctx) {
        if (ctx.contextId() <= MetricContext.SEED_IDS_END) {
            seedContextCache.put(cacheKey, ctx);
        } else {
            dynamicContextCache.put(cacheKey, ctx);
        }
    }

    static boolean attributesMatch(MetricContext ctx, List<MetricAttribute> attrs) {
        return contextCacheKey(ctx.getAttributes()).equals(contextCacheKey(attrs));
    }

    /**
     * Looks up or inserts a {@link MetricContext} for the given pre-assigned {@link ContextDefinition}. Used at startup to seed well-known contexts.
     */
    public MetricContext getOrRegisterContext(ContextDefinition def) {
        List<MetricAttribute> attrs = def.attributes();
        String cacheKey = contextCacheKey(attrs);
        MetricContext cached = getFromContextCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        int hash = MetricContext.computeHash(attrs);
        MetricContext ctx = loadContextByAttrsFromDatabase(hash, attrs);
        if (ctx == null) {
            ctx = insertContextToDatabase(def.contextId(), attrs);
        }
        putToContextCache(cacheKey, ctx);
        return ctx;
    }

    /**
     * Looks up or inserts a {@link MetricContext} for the given attributes. Surrogate ID is always computed by the database to avoid ownership conflicts across
     * engines and hosts.
     */
    public MetricContext getOrRegisterContext(List<MetricAttribute> attrs) {
        String cacheKey = contextCacheKey(attrs);
        MetricContext cached = getFromContextCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        int hash = MetricContext.computeHash(attrs);
        MetricContext found = loadContextByAttrsFromDatabase(hash, attrs);
        if (found != null) {
            putToContextCache(cacheKey, found);
            return found;
        }
        MetricContext ctx = generateContextSurrogateAndInsert(attrs);
        putToContextCache(cacheKey, ctx);
        return ctx;
    }

    public static final int ATTR_MAX_LENGTH = 255;

    private static void validateAttributes(List<MetricAttribute> attrs) {
        if (attrs == null || attrs.size() < ATTR_MIN_VALUES) {
            throw new MetricsRepositoryException("MetricContext must have at least " + ATTR_MIN_VALUES + " attribute(s)");
        }
        int size = Math.min(attrs.size(), ATTR_MAX_VALUES);
        for (int i = 0; i < size; i++) {
            MetricAttribute a = attrs.get(i);
            if (a.name() == null || a.name().isEmpty()) {
                throw new MetricsRepositoryException("MetricAttribute at index " + i + " has null or empty name");
            }
            if (a.name().length() > ATTR_MAX_LENGTH) {
                throw new MetricsRepositoryException("MetricAttribute at index " + i + " name exceeds " + ATTR_MAX_LENGTH + " characters");
            }
            if (a.value() == null || a.value().isEmpty()) {
                throw new MetricsRepositoryException("MetricAttribute at index " + i + " has null or empty value");
            }
            if (a.value().length() > ATTR_MAX_LENGTH) {
                throw new MetricsRepositoryException("MetricAttribute at index " + i + " value exceeds " + ATTR_MAX_LENGTH + " characters");
            }
        }
    }

    /** Returns [n1, v1, n2, v2, n3, v3] with null for unused attribute slots. */
    private static String[] attrValues(List<MetricAttribute> attrs) {
        String[] av = new String[6];
        int size = attrs != null ? Math.min(attrs.size(), ATTR_MAX_VALUES) : 0;
        for (int i = 0; i < size; i++) {
            av[i * 2] = attrs.get(i).name();
            av[i * 2 + 1] = attrs.get(i).value();
        }
        return av;
    }

    private Object[] packageSqlParamForContextToDatabase(long contextId, List<MetricAttribute> attrs) {
        validateAttributes(attrs);
        int hash = MetricContext.computeHash(attrs);
        String[] av = attrValues(attrs);
        return new Object[] { contextId, hash, av[0], av[1], av[2], av[3], av[4], av[5] };
    }

    private MetricContext insertContextToDatabase(long contextId, List<MetricAttribute> attrs) {
        Object[] params = packageSqlParamForContextToDatabase(contextId, attrs);
        int[] types = { Types.BIGINT, Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
        ISqlTransaction tx = null;
        try {
            tx = sqlTemplate.startSqlTransaction();
            tx.prepareAndExecute(getSql("insertMetricContextSql"), params, types);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            throw e;
        } finally {
            close(tx);
        }
        log.debug("Inserted metric context id={}, hash={}, attr1={}, value1={}", contextId, params[1], params[2], params[3]);
        return new MetricContext(contextId, List.copyOf(attrs));
    }

    private MetricContext loadContextByAttrsFromDatabase(int hash, List<MetricAttribute> attrs) {
        List<MetricContext> candidates = sqlTemplate.query(
                getSql("selectMetricContextByHashSql"), new MetricContextSqlRowMapper(), hash);
        for (MetricContext context : candidates) {
            if (attributesMatch(context, attrs)) {
                return context;
            }
        }
        return null;
    }

    private static final int CONTEXT_SURROGATE_MAX_RETRIES = 3;

    private MetricContext generateContextSurrogateAndInsert(List<MetricAttribute> attrs) {
        validateAttributes(attrs);
        int hash = MetricContext.computeHash(attrs);
        String[] av = attrValues(attrs);
        long bufferSize = SurrogateLongKeyBuffer.SURROGATE_KEY_BUFFER_SIZE;
        Object[] params = { bufferSize, bufferSize, bufferSize, hash, av[0], av[1], av[2], av[3], av[4], av[5] };
        int[] types = { Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR };
        for (int attempt = 1; attempt <= CONTEXT_SURROGATE_MAX_RETRIES; attempt++) {
            ISqlTransaction tx = null;
            try {
                tx = sqlTemplate.startSqlTransaction();
                tx.prepareAndExecute(getSql("generateContextSurrogateSql"), params, types);
                tx.commit();
                break;
            } catch (Exception e) {
                if (tx != null) {
                    tx.rollback();
                }
                if (!isCausedByUniqueKeyViolation(e) || attempt >= CONTEXT_SURROGATE_MAX_RETRIES) {
                    throw new MetricsRepositoryException("Failed to generate context surrogate after " + attempt + " attempts", e);
                }
                log.warn("Context surrogate collision (attempt {}/{}), retrying", attempt, CONTEXT_SURROGATE_MAX_RETRIES);
            } finally {
                close(tx);
            }
        }
        MetricContext ctx = loadContextByAttrsFromDatabase(hash, attrs);
        if (ctx == null) {
            throw new MetricsRepositoryException("Context not found after generateContextSurrogateSql insert");
        }
        return ctx;
    }

    /**
     * Persists a batch of newly completed intervals within a single transaction. Any {@link MetricKey} not yet present in {@code metric_key} is inserted first.
     */
    public void saveIntervals(List<MetricIntervalStatsRecord> intervalStats) {
        if (intervalStats == null || intervalStats.isEmpty()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        ensureMetricKeyCacheLoaded();
        List<MetricIntervalStatsRecord> readyStats = prepareStatsForDatabase(intervalStats);
        saveMetricIntervalStatsAll(readyStats);
        log.info("Saved {} metric interval stats records to database in {} seconds", readyStats.size(), (System.currentTimeMillis() - startTime) / 1000.0);
    }

    public List<MetricIntervalStatsRecord> prepareStatsForDatabase(List<MetricIntervalStatsRecord> intervalStats) {
        List<MetricIntervalStatsRecord> readyStats = new ArrayList<>(intervalStats.size());
        MetricKey prevKey = null;
        MetricKey skipKey = null; // Optimization: skip cache/DB lookups for repeat references to a failed key
        for (MetricIntervalStatsRecord statsRecord : intervalStats) {
            MetricKey key = statsRecord.key();
            if (key == null || !key.isEnabled() || key.equalsOnCompositeKey(skipKey)) {
                continue;
            }
            try {
                prevKey = ensureMetricKeyIdForStatsRecord(key, prevKey, statsRecord, readyStats);
            } catch (Exception ex) {
                skipKey = key;
                if (log.isDebugEnabled()) {
                    log.debug("Trouble with metric " + key, ex);
                }
            }
        }
        return readyStats;
    }

    private MetricKey ensureMetricKeyIdForStatsRecord(MetricKey key, MetricKey prevKey, MetricIntervalStatsRecord statsRecord,
            List<MetricIntervalStatsRecord> readyStats) {
        MetricKey resolved = (key == prevKey || key.equalsOnCompositeKey(prevKey)) ? prevKey : getOrRegisterMetricKey(key);
        if (resolved.isEnabled()) {
            readyStats.add(new MetricIntervalStatsRecord(resolved, statsRecord.contextId(), statsRecord.stats()));
            return resolved;
        }
        if (log.isDebugEnabled()) {
            log.debug("Skipped deactivated metric. Id={}, engine={}, hostname={}", resolved.metricId(), resolved.engineName(), resolved.hostname());
        }
        return prevKey;
    }

    private void saveMetricIntervalStatsAll(List<MetricIntervalStatsRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            for (MetricIntervalStatsRecord statsRecord : records) {
                saveMetricIntervalStatsInternal(transaction, statsRecord.key(), statsRecord.contextId(), statsRecord.stats());
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            log.error("Failed to save {} metric interval stats records", records.size(), e);
            throw new MetricsRepositoryException("Failed to save metric interval stats batch of " + records.size(), e);
        } finally {
            close(transaction);
        }
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
            return new ArrayList<>(0);
        }
        if (key.isSurrogateKeyMissing()) {
            key = getOrRegisterMetricKey(key);
        }
        log.debug("Loading metric intervals from database for key... {}", key);
        java.sql.Timestamp oneDayAgo = new java.sql.Timestamp(System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(1));
        String sqlKey = key.factType() == MetricFactType.INT64 ? "selectRecentIntervalsInt64Sql" : "selectRecentIntervalsSql";
        List<ISymIntervalStats> rows = sqlTemplate.query(
                getSql(sqlKey), MetricSeriesSlidingWorkset.IQR_INTERVALS_MIN, new DoubleStatsSqlRowMapper(), key.key(), oneDayAgo);
        log.info("Loaded {} historical intervals for metric {}", rows.size(), key);
        return rows;
    }

    private void saveMetricIntervalStatsInternal(ISqlTransaction transaction, MetricKey key, long contextId, ISymIntervalStats intervalStats) {
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
        if (key.factType() == MetricFactType.INT64) {
            saveMetricIntervalInt64(transaction, key, contextId, intervalStats);
        } else {
            saveMetricIntervalFloat64(transaction, key, contextId, intervalStats);
        }
        log.debug("Queued metric interval stats for {}, Interval.start={}", key, intervalStats.getStartEpoch());
    }

    private void saveMetricIntervalInt64(ISqlTransaction transaction, MetricKey key, long contextId, ISymIntervalStats intervalStats) {
        long durationSeconds = (intervalStats.getEndEpoch() - intervalStats.getStartEpoch()) / 1000;
        java.sql.Timestamp intervalStartTime = new java.sql.Timestamp(intervalStats.getStartEpoch());
        Object[] params = new Object[] {
                key.key(), contextId,
                intervalStartTime, durationSeconds, intervalStats.getEndEpoch(),
                intervalStats.getObservationCount(),
                (long) intervalStats.getMin(), (long) intervalStats.max(),
                (long) intervalStats.getAvg(), (long) intervalStats.mean(),
                intervalStats.getStdDeviation(),
                intervalStats.isOutlier() ? 1 : 0 };
        int[] types = new int[] {
                Types.BIGINT, Types.BIGINT,
                Types.TIMESTAMP, Types.INTEGER, Types.BIGINT,
                Types.INTEGER,
                Types.BIGINT, Types.BIGINT,
                Types.BIGINT, Types.BIGINT,
                Types.DOUBLE,
                Types.SMALLINT };
        executeIntervalInsert(transaction, key, "insertMetricIntervalInt64Sql", params, types);
    }

    private void saveMetricIntervalFloat64(ISqlTransaction transaction, MetricKey key, long contextId, ISymIntervalStats intervalStats) {
        long durationSeconds = (intervalStats.getEndEpoch() - intervalStats.getStartEpoch()) / 1000;
        java.sql.Timestamp intervalStartTime = new java.sql.Timestamp(intervalStats.getStartEpoch());
        Object[] params = new Object[] {
                key.key(), contextId,
                intervalStartTime, durationSeconds, intervalStats.getEndEpoch(),
                intervalStats.getObservationCount(),
                intervalStats.getMin(), intervalStats.max(),
                intervalStats.getAvg(), intervalStats.mean(),
                intervalStats.getStdDeviation(),
                intervalStats.isOutlier() ? 1 : 0 };
        int[] types = new int[] {
                Types.BIGINT, Types.BIGINT,
                Types.TIMESTAMP, Types.INTEGER, Types.BIGINT,
                Types.INTEGER,
                Types.DOUBLE, Types.DOUBLE,
                Types.DOUBLE, Types.DOUBLE,
                Types.DOUBLE,
                Types.SMALLINT };
        executeIntervalInsert(transaction, key, "insertMetricIntervalFloat64Sql", params, types);
    }

    private void executeIntervalInsert(ISqlTransaction transaction, MetricKey key, String sqlKey, Object[] params, int[] types) {
        try {
            transaction.prepareAndExecute(getSql(sqlKey), params, types);
        } catch (Exception e) {
            String message = "Failed to save metric interval stats for " + key;
            log.error(message, e);
            throw new MetricsRepositoryException(message, e);
        }
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

    public int purgeIntervalStats(java.util.Date cutoff) {
        int count = 0;
        try {
            count += sqlTemplate.update(getSql("purgeMetricStatsFloat64Sql"), cutoff);
        } catch (Exception e) {
            log.warn("Failed to purge metric_stats_float64", e);
        }
        try {
            count += sqlTemplate.update(getSql("purgeMetricStatsInt64Sql"), cutoff);
        } catch (Exception e) {
            log.warn("Failed to purge metric_stats_int64", e);
        }
        try {
            count += sqlTemplate.update(getSql("purgeOrphanedMetricContextsSql"));
        } catch (Exception e) {
            log.warn("Failed to purge orphaned metric_context rows", e);
        }
        return count;
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

    static class MetricContextSqlRowMapper implements ISqlRowMapper<MetricContext> {
        @Override
        public MetricContext mapRow(Row row) {
            long contextId = row.getLong("context_id");
            List<MetricAttribute> attrs = new ArrayList<>();
            String n1 = row.getString("attr1_name");
            String v1 = row.getString("attr1_value");
            if (n1 != null && !n1.isEmpty()) {
                attrs.add(new MetricAttribute(n1, v1 != null ? v1 : ""));
            }
            String n2 = row.getString("attr2_name");
            String v2 = row.getString("attr2_value");
            if (n2 != null && !n2.isEmpty()) {
                attrs.add(new MetricAttribute(n2, v2 != null ? v2 : ""));
            }
            String n3 = row.getString("attr3_name");
            String v3 = row.getString("attr3_value");
            if (n3 != null && !n3.isEmpty()) {
                attrs.add(new MetricAttribute(n3, v3 != null ? v3 : ""));
            }
            return new MetricContext(contextId, attrs);
        }
    }

    /**
     * Serializes MetricIntervalStats to/from database record.
     */
    static class DoubleStatsSqlRowMapper implements ISqlRowMapper<ISymIntervalStats> {
        @Override
        public ISymIntervalStats mapRow(Row row) {
            java.sql.Timestamp startTimestamp = (java.sql.Timestamp) row.get("interval_start_time");
            long intervalStart = startTimestamp != null ? startTimestamp.getTime() : 0L;
            long intervalEnd = row.getLong("interval_end_millis");
            double avg = rowDouble(row, "avg"); // The time-weighted average
            double min = rowDouble(row, "min");
            double max = rowDouble(row, "max");
            double stdDev = rowDouble(row, "std_dev");
            int observationCount = row.getInt("observation_count");
            double mean = rowDouble(row, "mean");
            boolean isOutlier = row.getInt("outlier") != 0;
            return new MetricIntervalStats(intervalStart, intervalEnd, avg, min, max, stdDev, observationCount, mean, isOutlier);
        }

        /**
         * Reads a DOUBLE column from a {@link Row}. {@code Row} has no {@code getDouble} method; JDBC drivers typically return {@link Double} objects for
         * DOUBLE columns, so we go through the {@link Number} supertype to preserve full 64-bit precision.
         */
        private static double rowDouble(Row row, String columnName) {
            Object v = row.get(columnName);
            return v instanceof Number n ? n.doubleValue() : 0.0;
        }
    }
}
