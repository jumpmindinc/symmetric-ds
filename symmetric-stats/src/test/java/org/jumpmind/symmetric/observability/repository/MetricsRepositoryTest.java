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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.ISymIntervalStats;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.metrics.ContextDefinition;
import org.jumpmind.symmetric.observability.models.MetricContext;
import org.jumpmind.symmetric.observability.models.MetricIntervalStats;
import org.jumpmind.symmetric.observability.models.MetricIntervalStatsRecord;
import org.jumpmind.symmetric.observability.models.MetricKey;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class MetricsRepositoryTest {
    private static final String TEST_ENGINE = "test-engine";
    private static final String TEST_HOST = "test-host";
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ISymmetricDialect dialect;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;
    private MetricsRepository repo;

    private static <T> T invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = MetricsRepository.class.getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
            return (T) m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException r) {
                throw r;
            }
            throw new RuntimeException(c);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T getField(Object target, String fieldName) {
        try {
            Field f = MetricsRepository.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = MetricsRepository.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        dialect = mock(ISymmetricDialect.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        when(engine.getEngineName()).thenReturn(TEST_ENGINE);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(dialect.getPlatform()).thenReturn(platform);
        when(dialect.getSqlReplacementTokens()).thenReturn(Map.of());
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(inv -> inv.getArgument(0));
        repo = new MetricsRepository(engine, TEST_HOST);
        // Bypass cache loading so most tests don't need to stub
        // loadAllMetricKeysForHostnameFromDatabase
        setField(repo, "cacheLoaded", true);
    }

    private MetricKey key(long surrogateKey, String metricId, String engineName, String hostname) {
        return new MetricKey(surrogateKey, hostname, engineName, metricId, MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
    }

    @Test
    void isCausedByUniqueKeyViolation_directCause_returnsTrue() {
        UniqueKeyException ex = new UniqueKeyException("dup");
        boolean result = invokePrivate(repo, "isCausedByUniqueKeyViolation",
                new Class<?>[] { Exception.class }, ex);
        assertTrue(result);
    }

    @Test
    void isCausedByUniqueKeyViolation_wrappedOnce_returnsTrue() {
        UniqueKeyException inner = new UniqueKeyException("dup");
        RuntimeException wrapper = new RuntimeException("wrapper", inner);
        boolean result = invokePrivate(repo, "isCausedByUniqueKeyViolation",
                new Class<?>[] { Exception.class }, wrapper);
        assertTrue(result);
    }

    @Test
    void isCausedByUniqueKeyViolation_wrappedTwoLevels_returnsTrue() {
        UniqueKeyException inner = new UniqueKeyException("dup");
        RuntimeException mid = new RuntimeException("mid", inner);
        RuntimeException outer = new RuntimeException("outer", mid);
        boolean result = invokePrivate(repo, "isCausedByUniqueKeyViolation",
                new Class<?>[] { Exception.class }, outer);
        assertTrue(result);
    }

    @Test
    void isCausedByUniqueKeyViolation_unrelated_returnsFalse() {
        RuntimeException ex = new RuntimeException("unrelated");
        boolean result = invokePrivate(repo, "isCausedByUniqueKeyViolation",
                new Class<?>[] { Exception.class }, ex);
        assertFalse(result);
    }

    @Test
    void populateSurrogateKeyBuffer_singleRegularKey_value5_peekIs6() {
        MetricKey k = key(5L, "m1", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateSurrogateKeyBuffer",
                new Class<?>[] { List.class }, List.of(k));
        SurrogateLongKeyBuffer buf = getField(repo, "surrogateKeys");
        assertEquals(6L, buf.peekNextValue());
    }

    @Test
    void populateSurrogateKeyBuffer_singleSharedKey_value5_peekIsRoundUp() {
        // roundUp(5) = roundDown(5) + 10 = 0 + 10 = 10
        MetricKey k = key(5L, "m1", MetricsRepository.METRIC_SHARED_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateSurrogateKeyBuffer",
                new Class<?>[] { List.class }, List.of(k));
        SurrogateLongKeyBuffer buf = getField(repo, "surrogateKeys");
        long expected = SurrogateLongKeyBuffer.roundUpToNextBufferStart(5L);
        assertEquals(expected, buf.peekNextValue());
    }

    @Test
    void populateSurrogateKeyBuffer_twoRegularKeys_3and8_peekIs9() {
        MetricKey k3 = key(3L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey k8 = key(8L, "m2", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateSurrogateKeyBuffer",
                new Class<?>[] { List.class }, List.of(k3, k8));
        SurrogateLongKeyBuffer buf = getField(repo, "surrogateKeys");
        assertEquals(9L, buf.peekNextValue());
    }

    @Test
    void populateSurrogateKeyBuffer_regularKey7ThenSharedKey5_regularWins_peekIs8() {
        // Regular key=7 processed first → nextAvailable=8.
        // Shared key=5: nextAvailable (8) > 5, so condition nextAvailable < key.key() is false → no update.
        MetricKey k7 = key(7L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey k5 = key(5L, "m2", MetricsRepository.METRIC_SHARED_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateSurrogateKeyBuffer",
                new Class<?>[] { List.class }, List.of(k7, k5));
        SurrogateLongKeyBuffer buf = getField(repo, "surrogateKeys");
        assertEquals(8L, buf.peekNextValue());
    }

    @Test
    void populateSurrogateKeyBuffer_sharedKey5ThenRegularKey3_sharedWins_peekIs10() {
        // Shared key=5 first → nextAvailable = roundUp(5)=10.
        // Regular key=3: nextAvailable (10) > 3 → condition false → no update.
        MetricKey k5 = key(5L, "m1", MetricsRepository.METRIC_SHARED_ENGINE, TEST_HOST);
        MetricKey k3 = key(3L, "m2", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateSurrogateKeyBuffer",
                new Class<?>[] { List.class }, List.of(k5, k3));
        SurrogateLongKeyBuffer buf = getField(repo, "surrogateKeys");
        long expected = SurrogateLongKeyBuffer.roundUpToNextBufferStart(5L);
        assertEquals(expected, buf.peekNextValue());
    }

    @Test
    void populateMetricKeyCache_nullList_returns0() {
        int count = invokePrivate(repo, "populateMetricKeyCache",
                new Class<?>[] { List.class }, (Object) null);
        assertEquals(0, count);
    }

    @Test
    void populateMetricKeyCache_emptyList_returns0() {
        int count = invokePrivate(repo, "populateMetricKeyCache",
                new Class<?>[] { List.class }, List.of());
        assertEquals(0, count);
    }

    @Test
    void populateMetricKeyCache_singleKey_returns1AndCached() {
        MetricKey k = key(42L, "m1", TEST_ENGINE, TEST_HOST);
        int count = invokePrivate(repo, "populateMetricKeyCache",
                new Class<?>[] { List.class }, List.of(k));
        assertEquals(1, count);
        Map<Integer, MetricKey> cache = getField(repo, "metricKeysCache");
        assertTrue(cache.containsValue(k));
    }

    @Test
    void populateMetricKeyCache_multipleKeys_allCached() {
        MetricKey k1 = key(1L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey k2 = key(2L, "m2", TEST_ENGINE, TEST_HOST);
        MetricKey k3 = key(3L, "m3", TEST_ENGINE, TEST_HOST);
        int count = invokePrivate(repo, "populateMetricKeyCache",
                new Class<?>[] { List.class }, List.of(k1, k2, k3));
        assertEquals(3, count);
        Map<Integer, MetricKey> cache = getField(repo, "metricKeysCache");
        assertTrue(cache.containsValue(k1));
        assertTrue(cache.containsValue(k2));
        assertTrue(cache.containsValue(k3));
    }

    @Test
    void putToContextCache_seedContextId_storesInSeedCache() {
        // contextId=1 <= SEED_IDS_END → seed cache
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("env", "prod"));
        MetricContext ctx = new MetricContext(1L, attrs);
        String cacheKey = MetricsRepository.generateContextCacheKey(attrs);
        invokePrivate(repo, "putToContextCache",
                new Class<?>[] { String.class, MetricContext.class }, cacheKey, ctx);
        Map<String, MetricContext> seedCache = getField(repo, "seedContextCache");
        Map<String, MetricContext> dynCache = getField(repo, "dynamicContextCache");
        assertSame(ctx, seedCache.get(cacheKey));
        assertNull(dynCache.get(cacheKey));
    }

    @Test
    void putToContextCache_dynamicContextId_storesInDynamicCache() {
        // contextId = SEED_IDS_END + 1 → dynamic cache
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("node", "001"));
        MetricContext ctx = new MetricContext(MetricContext.SEED_IDS_END + 1L, attrs);
        String cacheKey = MetricsRepository.generateContextCacheKey(attrs);
        invokePrivate(repo, "putToContextCache",
                new Class<?>[] { String.class, MetricContext.class }, cacheKey, ctx);
        Map<String, MetricContext> seedCache = getField(repo, "seedContextCache");
        Map<String, MetricContext> dynCache = getField(repo, "dynamicContextCache");
        assertNull(seedCache.get(cacheKey));
        assertSame(ctx, dynCache.get(cacheKey));
    }

    @Test
    void loadAllMetricKeysForHostnameFromDatabase_returnsNonNullList() {
        MetricKey k = key(7L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(k));
        List<MetricKey> result = invokePrivate(repo, "loadAllMetricKeysForHostnameFromDatabase",
                new Class<?>[] {});
        assertNotNull(result);
        assertEquals(1, result.size());
        assertSame(k, result.get(0));
    }

    @Test
    void loadAllMetricKeysForHostnameFromDatabase_queryReturnsEmptyList_returnsEmptyList() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of());
        List<MetricKey> result = invokePrivate(repo, "loadAllMetricKeysForHostnameFromDatabase",
                new Class<?>[] {});
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void loadMetricKeyFromDatabase_queryReturnsOne_returnsFirst() {
        MetricKey k = key(99L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(k));
        MetricKey result = invokePrivate(repo, "loadMetricKeyFromDatabase",
                new Class<?>[] { String.class, String.class, String.class },
                "m1", TEST_ENGINE, TEST_HOST);
        assertSame(k, result);
    }

    @Test
    void loadMetricKeyFromDatabase_queryReturnsEmpty_returnsNull() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of());
        MetricKey result = invokePrivate(repo, "loadMetricKeyFromDatabase",
                new Class<?>[] { String.class, String.class, String.class },
                "m1", TEST_ENGINE, TEST_HOST);
        assertNull(result);
    }

    @Test
    void updateMetricKeyInDatabase_surrogateKeyMissing_returnsDbRecord() {
        MetricKey dbRec = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        // isSurrogateKeyMissing() → key == SURROGATE_KEY_UNASSIGNED (-1)
        MetricKey keyMissing = key(SurrogateKeyConstants.SURROGATE_KEY_UNASSIGNED, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey result = invokePrivate(repo, "updateMetricKeyInDatabase",
                new Class<?>[] { MetricKey.class, MetricKey.class }, keyMissing, dbRec);
        assertSame(dbRec, result);
    }

    @Test
    void updateMetricKeyInDatabase_keyEqualsDbRecord_returnsDbRecord() {
        MetricKey dbRec = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey same = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey result = invokePrivate(repo, "updateMetricKeyInDatabase",
                new Class<?>[] { MetricKey.class, MetricKey.class }, same, dbRec);
        assertSame(dbRec, result);
    }

    @Test
    void updateMetricKeyInDatabase_differentSurrogateKeys_executesTransaction_returnsKey() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey dbRec = key(5L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey newKey = key(99L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey result = invokePrivate(repo, "updateMetricKeyInDatabase",
                new Class<?>[] { MetricKey.class, MetricKey.class }, newKey, dbRec);
        assertSame(newKey, result);
    }

    @Test
    void updateMetricKeyInDatabase_transactionThrows_wrapsInMetricsRepositoryException() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        when(txn.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new RuntimeException("db error"));
        MetricKey dbRec = key(5L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey newKey = key(99L, "m1", TEST_ENGINE, TEST_HOST);
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "updateMetricKeyInDatabase",
                new Class<?>[] { MetricKey.class, MetricKey.class }, newKey, dbRec));
    }

    @Test
    void generateSurrogateKeyAndSaveMetricKeyToDatabase_transactionSucceeds_returnsLoadedKey() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey loaded = key(50L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(loaded));
        MetricKey result = invokePrivate(repo,
                "generateSurrogateKeyAndSaveMetricKeyToDatabase",
                new Class<?>[] { String.class, String.class, String.class,
                        MetricFactType.class, InstrumentType.class, boolean.class },
                "m1", TEST_ENGINE, TEST_HOST, MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        assertSame(loaded, result);
    }

    @Test
    void generateSurrogateKeyAndSaveMetricKeyToDatabase_firstAttemptUniqueViolationSecondSucceeds_returnsKey() {
        ISqlTransaction txn1 = mock(ISqlTransaction.class);
        ISqlTransaction txn2 = mock(ISqlTransaction.class);
        // First call throws UniqueKeyException; second succeeds
        when(sqlTemplate.startSqlTransaction())
                .thenReturn(txn1)
                .thenReturn(txn2);
        when(txn1.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new UniqueKeyException("collision"));
        MetricKey loaded = key(55L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(loaded));
        MetricKey result = invokePrivate(repo,
                "generateSurrogateKeyAndSaveMetricKeyToDatabase",
                new Class<?>[] { String.class, String.class, String.class,
                        MetricFactType.class, InstrumentType.class, boolean.class },
                "m1", TEST_ENGINE, TEST_HOST, MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        assertSame(loaded, result);
    }

    @Test
    void generateSurrogateKeyAndSaveMetricKeyToDatabase_nonUniqueException_throwsImmediately() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        when(txn.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new RuntimeException("non-unique error"));
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo,
                "generateSurrogateKeyAndSaveMetricKeyToDatabase",
                new Class<?>[] { String.class, String.class, String.class,
                        MetricFactType.class, InstrumentType.class, boolean.class },
                "m1", TEST_ENGINE, TEST_HOST, MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true));
    }

    @Test
    void saveMetricKeyInternal_nullKey_throwsMetricsRepositoryException() {
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "saveMetricKeyInternal",
                new Class<?>[] { MetricKey.class }, (Object) null));
    }

    @Test
    void saveMetricKeyInternal_surrogateKeyMissing_sharedEngine_callsGeneratePath() {
        // surrogateKeys is not available (default uninitialized state), so always falls through to generate path
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey loaded = key(60L, "m1", MetricsRepository.METRIC_SHARED_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(loaded));
        MetricKey missing = key(SurrogateKeyConstants.SURROGATE_KEY_UNASSIGNED,
                "m1", MetricsRepository.METRIC_SHARED_ENGINE, TEST_HOST);
        // isSurrogateKeyMissing() == true → delegates to String overload → shared engine: generate path
        MetricKey result = invokePrivate(repo, "saveMetricKeyInternal",
                new Class<?>[] { MetricKey.class }, missing);
        assertNotNull(result);
        assertFalse(result.isSurrogateKeyMissing());
    }

    @Test
    void saveMetricKeyInternal_validSurrogate_callsSaveToDatabase_returnsKey() {
        MetricKey k = key(77L, "m1", TEST_ENGINE, TEST_HOST);
        // saveMetricKeyToDatabase → loadMetricKeyFromDatabase returns equal key → no write needed
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(k));
        MetricKey result = invokePrivate(repo, "saveMetricKeyInternal",
                new Class<?>[] { MetricKey.class }, k);
        assertSame(k, result);
    }

    @Test
    void saveMetricKey_keyInCache_returnsCachedEntry() {
        MetricKey k = key(20L, "m1", TEST_ENGINE, TEST_HOST);
        // Manually insert into cache
        invokePrivate(repo, "populateMetricKeyCache",
                new Class<?>[] { List.class }, List.of(k));
        MetricKey result = repo.saveMetricKey(k);
        assertSame(k, result);
    }

    @Test
    void saveMetricKey_keyNotInCache_savesAndReturns() {
        MetricKey k = key(33L, "m2", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(k));
        MetricKey result = repo.saveMetricKey(k);
        assertNotNull(result);
        assertEquals(33L, result.key());
    }

    @Test
    void getOrRegisterMetricKey_keyInCache_returnsCached() {
        MetricKey k = key(11L, "m1", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateMetricKeyCache",
                new Class<?>[] { List.class }, List.of(k));
        MetricKey result = repo.getOrRegisterMetricKey(k);
        assertSame(k, result);
    }

    @Test
    void getOrRegisterMetricKey_keyNotInCache_savesAndReturns() {
        MetricKey k = key(22L, "m3", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(k));
        MetricKey result = repo.getOrRegisterMetricKey(k);
        assertNotNull(result);
        assertEquals(22L, result.key());
    }

    @Test
    void getOrRegisterMetricKey_byStrings_keyInCache_returnsCached() {
        MetricKey k = key(44L, "m4", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateMetricKeyCache",
                new Class<?>[] { List.class }, List.of(k));
        MetricKey result = repo.getOrRegisterMetricKey("m4", TEST_ENGINE, TEST_HOST,
                MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        assertSame(k, result);
    }

    @Test
    void getOrRegisterMetricKey_byStrings_keyNotInCache_savesAndCaches() {
        // surrogateKeys not initialized → generate path; loadMetricKeyFromDatabase returns key
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey loaded = key(88L, "m5", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(loaded));
        MetricKey result = repo.getOrRegisterMetricKey("m5", TEST_ENGINE, TEST_HOST,
                MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        assertNotNull(result);
        assertEquals(88L, result.key());
        // Verify it was cached
        Map<Integer, MetricKey> cache = getField(repo, "metricKeysCache");
        assertTrue(cache.containsValue(result));
    }

    @Test
    void getOrRegisterContext_def_inSeedCache_returnsCached() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        MetricContext ctx = new MetricContext(1L, attrs);
        String cacheKey = MetricsRepository.generateContextCacheKey(attrs);
        Map<String, MetricContext> seedCache = getField(repo, "seedContextCache");
        seedCache.put(cacheKey, ctx);
        ContextDefinition def = new ContextDefinition(1L, attrs);
        MetricContext result = repo.getOrRegisterContext(def);
        assertSame(ctx, result);
    }

    @Test
    void getOrRegisterContext_def_notInCacheFoundInDb_cachesAndReturns() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_RELOAD));
        MetricContext dbCtx = new MetricContext(2L, attrs);
        // loadContextByAttrsFromDatabase: sqlTemplate.query (varargs form) returns dbCtx
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of(dbCtx));
        ContextDefinition def = new ContextDefinition(2L, attrs);
        MetricContext result = repo.getOrRegisterContext(def);
        assertSame(dbCtx, result);
        // Should be in seed cache (contextId=2 <= SEED_IDS_END)
        Map<String, MetricContext> seedCache = getField(repo, "seedContextCache");
        String cacheKey = MetricsRepository.generateContextCacheKey(attrs);
        assertSame(dbCtx, seedCache.get(cacheKey));
    }

    @Test
    void getOrRegisterContext_def_notInCacheNotInDb_insertsAndCaches() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", "new_channel"));
        // loadContextByAttrsFromDatabase returns empty → insertContextToDatabase called
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of());
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        long ctxId = 100L;
        ContextDefinition def = new ContextDefinition(ctxId, attrs);
        MetricContext result = repo.getOrRegisterContext(def);
        assertNotNull(result);
        assertEquals(ctxId, result.contextId());
        // Should be cached in seed cache (100 <= SEED_IDS_END)
        Map<String, MetricContext> seedCache = getField(repo, "seedContextCache");
        String cacheKey = MetricsRepository.generateContextCacheKey(attrs);
        assertNotNull(seedCache.get(cacheKey));
    }

    @Test
    void getOrRegisterContext_attrs_inDynamicCache_returnsCached() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("node", "001"));
        MetricContext ctx = new MetricContext(MetricContext.SEED_IDS_END + 10L, attrs);
        String cacheKey = MetricsRepository.generateContextCacheKey(attrs);
        Map<String, MetricContext> dynCache = getField(repo, "dynamicContextCache");
        dynCache.put(cacheKey, ctx);
        MetricContext result = repo.getOrRegisterContext(attrs);
        assertSame(ctx, result);
    }

    @Test
    void getOrRegisterContext_attrs_notInCacheFoundInDb_cachesInDynamicAndReturns() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("node", "002"));
        MetricContext dbCtx = new MetricContext(MetricContext.SEED_IDS_END + 5L, attrs);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of(dbCtx));
        MetricContext result = repo.getOrRegisterContext(attrs);
        assertSame(dbCtx, result);
        Map<String, MetricContext> dynCache = getField(repo, "dynamicContextCache");
        String cacheKey = MetricsRepository.generateContextCacheKey(attrs);
        assertSame(dbCtx, dynCache.get(cacheKey));
    }

    @Test
    void getOrRegisterContext_attrs_notInCacheNotInDb_generatesAndInserts() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("node", "003"));
        MetricContext insertedCtx = new MetricContext(MetricContext.SEED_IDS_END + 20L, attrs);
        // First call: loadContextByAttrsFromDatabase returns empty (before insert)
        // Second call after generateContextSurrogateAndInsert: returns inserted context
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of()) // first probe: not found
                .thenReturn(List.of(insertedCtx)); // second probe: after insert
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricContext result = repo.getOrRegisterContext(attrs);
        assertNotNull(result);
        assertEquals(insertedCtx.contextId(), result.contextId());
        Map<String, MetricContext> dynCache = getField(repo, "dynamicContextCache");
        String cacheKey = MetricsRepository.generateContextCacheKey(attrs);
        assertNotNull(dynCache.get(cacheKey));
    }

    @Test
    void validateAttributes_emptyList_throwsWithMinMessage() {
        var emptyAttrs = new MetricAttributeList(MetricsRepository.ATTR_MAX_VALUES);
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, emptyAttrs));
        assertTrue(ex.getMessage().contains(String.valueOf(MetricsRepository.ATTR_MIN_VALUES)));
    }

    @Test
    void validateAttributes_singleValidAttr_doesNotThrow() {
        var attrs = MetricAttributeList.of(new MetricAttribute("channel", Constants.CHANNEL_DEFAULT));
        assertDoesNotThrow(() -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
    }

    @Test
    void validateAttributes_maxValidAttrs_doesNotThrow() {
        var attrs = MetricAttributeList.of(
                new MetricAttribute("a", "1"),
                new MetricAttribute("b", "2"),
                new MetricAttribute("c", "3"));
        assertDoesNotThrow(() -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
    }

    @Test
    void validateAttributes_fourthAttrInvalid_doesNotThrow() {
        var attrs = MetricAttributeList.of(
                new MetricAttribute("a", "1"),
                new MetricAttribute("b", "2"),
                new MetricAttribute("c", "3"),
                new MetricAttribute("", "bad"));
        assertDoesNotThrow(() -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
    }

    @Test
    void validateAttributes_nullName_throwsWithIndexAndNameMessage() {
        var attrs = MetricAttributeList.of(new MetricAttribute(null, "value"));
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
        assertEquals("MetricAttribute at index 0 has null or empty name", ex.getMessage());
    }

    @Test
    void validateAttributes_emptyName_throwsWithIndexAndNameMessage() {
        var attrs = MetricAttributeList.of(new MetricAttribute("", "value"));
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
        assertEquals("MetricAttribute at index 0 has null or empty name", ex.getMessage());
    }

    @Test
    void validateAttributes_nameTooLong_throwsWithIndexAndLengthMessage() {
        String longName = "x".repeat(MetricsRepository.ATTR_MAX_LENGTH + 1);
        var attrs = MetricAttributeList.of(new MetricAttribute(longName, "value"));
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
        assertEquals("MetricAttribute at index 0 name exceeds " + MetricsRepository.ATTR_MAX_LENGTH + " characters", ex.getMessage());
    }

    @Test
    void validateAttributes_nameAtMaxLength_doesNotThrow() {
        String maxName = "x".repeat(MetricsRepository.ATTR_MAX_LENGTH);
        var attrs = MetricAttributeList.of(new MetricAttribute(maxName, "value"));
        assertDoesNotThrow(() -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
    }

    @Test
    void validateAttributes_nullValue_throwsWithIndexAndValueMessage() {
        var attrs = MetricAttributeList.of(new MetricAttribute("name", null));
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
        assertEquals("MetricAttribute at index 0 has null or empty value", ex.getMessage());
    }

    @Test
    void validateAttributes_emptyValue_throwsWithIndexAndValueMessage() {
        var attrs = MetricAttributeList.of(new MetricAttribute("name", ""));
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
        assertEquals("MetricAttribute at index 0 has null or empty value", ex.getMessage());
    }

    @Test
    void validateAttributes_valueTooLong_throwsWithIndexAndLengthMessage() {
        String longValue = "x".repeat(MetricsRepository.ATTR_MAX_LENGTH + 1);
        var attrs = MetricAttributeList.of(new MetricAttribute("name", longValue));
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
        assertEquals("MetricAttribute at index 0 value exceeds " + MetricsRepository.ATTR_MAX_LENGTH + " characters", ex.getMessage());
    }

    @Test
    void validateAttributes_valueAtMaxLength_doesNotThrow() {
        String maxValue = "x".repeat(MetricsRepository.ATTR_MAX_LENGTH);
        var attrs = MetricAttributeList.of(new MetricAttribute("name", maxValue));
        assertDoesNotThrow(() -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
    }

    @Test
    void validateAttributes_invalidAttrAtIndex1_throwsWithCorrectIndex() {
        var attrs = MetricAttributeList.of(new MetricAttribute("a", "1"), new MetricAttribute("", "v"));
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
        assertTrue(ex.getMessage().contains("index 1"));
    }

    @Test
    void validateAttributes_invalidAttrAtIndex2_throwsWithCorrectIndex() {
        var attrs = MetricAttributeList.of(
                new MetricAttribute("a", "1"),
                new MetricAttribute("b", "2"),
                new MetricAttribute("", "v"));
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invokePrivate(null, "validateAttributes", new Class<?>[] { MetricAttributeList.class }, attrs));
        assertTrue(ex.getMessage().contains("index 2"));
    }

    @Test
    void ensureMetricKeyCacheLoaded_notLoaded_withKeys_populatesCacheAndSetsFlag() {
        setField(repo, "cacheLoaded", false);
        MetricKey k = key(5L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(k));
        repo.initializeCache();
        Map<Integer, MetricKey> cache = getField(repo, "metricKeysCache");
        assertTrue(cache.containsValue(k));
        boolean loaded = getField(repo, "cacheLoaded");
        assertTrue(loaded);
    }

    @Test
    void ensureMetricKeyCacheLoaded_notLoaded_emptyList_setsLoadedFlagWithWarning() {
        setField(repo, "cacheLoaded", false);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of());
        repo.initializeCache();
        boolean loaded = getField(repo, "cacheLoaded");
        assertTrue(loaded);
    }

    @Test
    void getMetricKey_keyInCache_returnsCachedKey() {
        MetricKey k = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateMetricKeyCache", new Class<?>[] { List.class }, List.of(k));
        MetricKey result = repo.getMetricKey("m1", MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        assertSame(k, result);
    }

    @Test
    void saveMetricKeyInternal_string_surrogateAvailable_usesAssignPath() {
        SurrogateLongKeyBuffer buf = new SurrogateLongKeyBuffer(100L, 100L);
        setField(repo, "surrogateKeys", buf);
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of());
        MetricKey result = invokePrivate(repo, "saveMetricKeyInternal",
                new Class<?>[] { String.class, String.class, String.class, MetricFactType.class, InstrumentType.class, boolean.class },
                "m1", TEST_ENGINE, TEST_HOST, MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        assertNotNull(result);
        assertEquals(100L, result.key());
    }

    @Test
    void saveMetricKeyInternal_string_surrogateAvailable_uniqueKeyCollision_fallsBackToGenerate() {
        SurrogateLongKeyBuffer buf = new SurrogateLongKeyBuffer(100L, 100L);
        setField(repo, "surrogateKeys", buf);
        ISqlTransaction txn1 = mock(ISqlTransaction.class);
        ISqlTransaction txn2 = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn1).thenReturn(txn2);
        when(txn1.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new UniqueKeyException("collision"));
        MetricKey loaded = key(200L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of())
                .thenReturn(List.of(loaded));
        MetricKey result = invokePrivate(repo, "saveMetricKeyInternal",
                new Class<?>[] { String.class, String.class, String.class, MetricFactType.class, InstrumentType.class, boolean.class },
                "m1", TEST_ENGINE, TEST_HOST, MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        assertNotNull(result);
        assertEquals(200L, result.key());
    }

    @Test
    void saveMetricKeyInternal_string_surrogateAvailable_nonUniqueException_throws() {
        SurrogateLongKeyBuffer buf = new SurrogateLongKeyBuffer(100L, 100L);
        setField(repo, "surrogateKeys", buf);
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of());
        when(txn.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new RuntimeException("non-unique error"));
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "saveMetricKeyInternal",
                new Class<?>[] { String.class, String.class, String.class, MetricFactType.class, InstrumentType.class, boolean.class },
                "m1", TEST_ENGINE, TEST_HOST, MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true));
    }

    @Test
    void reconcileMetricKeyWithCachedEntry_cachedRecMissingSurrogate_throwsMetricsRepositoryException() {
        MetricKey cachedMissing = key(SurrogateKeyConstants.SURROGATE_KEY_UNASSIGNED, "m1", TEST_ENGINE, TEST_HOST);
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "reconcileMetricKeyWithCachedEntry",
                new Class<?>[] { MetricKey.class, MetricKey.class }, null, cachedMissing));
    }

    @Test
    void saveMetricKeyToDatabase_dbRecordExistsDifferent_callsUpdateAndReturnsNewKey() {
        MetricKey k = key(99L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey dbRecord = key(5L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(dbRecord));
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey result = invokePrivate(repo, "saveMetricKeyToDatabase",
                new Class<?>[] { MetricKey.class }, k);
        assertSame(k, result);
    }

    @Test
    void saveMetricKeyToDatabase_dbRecordNull_insertsAndReturnsKey() {
        MetricKey k = key(99L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of());
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey result = invokePrivate(repo, "saveMetricKeyToDatabase",
                new Class<?>[] { MetricKey.class }, k);
        assertSame(k, result);
    }

    @Test
    void saveMetricKeyToDatabase_insertThrows_rollsBackAndThrowsMetricsRepositoryException() {
        MetricKey k = key(99L, "m1", TEST_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of());
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        when(txn.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new RuntimeException("db error"));
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "saveMetricKeyToDatabase",
                new Class<?>[] { MetricKey.class }, k));
        verify(txn).rollback();
    }

    @Test
    void loadAllMetricKeys_returnsAllCachedKeys() {
        MetricKey k1 = key(1L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey k2 = key(2L, "m2", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateMetricKeyCache", new Class<?>[] { List.class }, List.of(k1, k2));
        List<MetricKey> result = repo.loadAllMetricKeys();
        assertEquals(2, result.size());
        assertTrue(result.contains(k1));
        assertTrue(result.contains(k2));
    }

    @Test
    void getOrRegisterContext_def_insertTransactionThrows_rethrowsOriginalException() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("channel", "test"));
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of());
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        RuntimeException dbError = new RuntimeException("insert failed");
        when(txn.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(dbError);
        ContextDefinition def = new ContextDefinition(50L, attrs);
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> repo.getOrRegisterContext(def));
        assertSame(dbError, thrown);
    }

    @Test
    void getOrRegisterContext_attrs_firstAttemptCollision_retriesAndSucceeds() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("node", "004"));
        MetricContext insertedCtx = new MetricContext(MetricContext.SEED_IDS_END + 30L, attrs);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of())
                .thenReturn(List.of(insertedCtx));
        ISqlTransaction txn1 = mock(ISqlTransaction.class);
        ISqlTransaction txn2 = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn1).thenReturn(txn2);
        when(txn1.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new UniqueKeyException("collision"));
        MetricContext result = repo.getOrRegisterContext(attrs);
        assertSame(insertedCtx, result);
    }

    @Test
    void generateContextSurrogateAndInsert_contextNotFoundAfterInsert_throwsMetricsRepositoryException() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("node", "005"));
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of());
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        assertThrows(MetricsRepositoryException.class, () -> repo.getOrRegisterContext(attrs));
    }

    @Test
    void saveIntervals_nullList_doesNothing() {
        assertDoesNotThrow(() -> repo.saveIntervals(null));
    }

    @Test
    void saveIntervals_emptyList_doesNothing() {
        assertDoesNotThrow(() -> repo.saveIntervals(List.of()));
    }

    @Test
    void prepareStatsForDatabase_nullKey_skipsRecord() {
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        MetricIntervalStatsRecord statsRecord = new MetricIntervalStatsRecord(null, 1L, stats);
        List<MetricIntervalStatsRecord> result = repo.prepareStatsForDatabase(List.of(statsRecord));
        assertTrue(result.isEmpty());
    }

    @Test
    void prepareStatsForDatabase_disabledKey_skipsRecord() {
        MetricKey disabled = new MetricKey(10L, TEST_HOST, TEST_ENGINE, "m1", MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, false);
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        MetricIntervalStatsRecord statsRecord = new MetricIntervalStatsRecord(disabled, 1L, stats);
        List<MetricIntervalStatsRecord> result = repo.prepareStatsForDatabase(List.of(statsRecord));
        assertTrue(result.isEmpty());
    }

    @Test
    void prepareStatsForDatabase_exceptionDuringLookup_setsSkipKeyAndSkipsSubsequent() {
        MetricKey k = key(SurrogateKeyConstants.SURROGATE_KEY_UNASSIGNED, "m1", TEST_ENGINE, TEST_HOST);
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        MetricIntervalStatsRecord r1 = new MetricIntervalStatsRecord(k, 1L, stats);
        MetricIntervalStatsRecord r2 = new MetricIntervalStatsRecord(k, 2L, stats);
        when(sqlTemplate.startSqlTransaction()).thenThrow(new RuntimeException("db failure"));
        List<MetricIntervalStatsRecord> result = repo.prepareStatsForDatabase(List.of(r1, r2));
        assertTrue(result.isEmpty());
    }

    @Test
    void prepareStatsForDatabase_enabledKeyInCache_addsToReadyStats() {
        MetricKey k = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateMetricKeyCache", new Class<?>[] { List.class }, List.of(k));
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        MetricIntervalStatsRecord statsRecord = new MetricIntervalStatsRecord(k, 1L, stats);
        List<MetricIntervalStatsRecord> result = repo.prepareStatsForDatabase(List.of(statsRecord));
        assertEquals(1, result.size());
    }

    @Test
    void prepareStatsForDatabase_resolvedKeyDisabled_doesNotAddToReadyStats() {
        MetricKey disabledCached = new MetricKey(10L, TEST_HOST, TEST_ENGINE, "m1", MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, false);
        invokePrivate(repo, "populateMetricKeyCache", new Class<?>[] { List.class }, List.of(disabledCached));
        MetricKey enabledRecord = key(SurrogateKeyConstants.SURROGATE_KEY_UNASSIGNED, "m1", TEST_ENGINE, TEST_HOST);
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        MetricIntervalStatsRecord statsRecord = new MetricIntervalStatsRecord(enabledRecord, 1L, stats);
        List<MetricIntervalStatsRecord> result = repo.prepareStatsForDatabase(List.of(statsRecord));
        assertTrue(result.isEmpty());
    }

    @Test
    void saveMetricIntervalStatsAll_emptyList_doesNotStartTransaction() {
        assertDoesNotThrow(() -> invokePrivate(repo, "saveMetricIntervalStatsAll",
                new Class<?>[] { List.class }, List.of()));
    }

    @Test
    void saveMetricIntervalStatsAll_withRecords_commitsTransaction() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey k = new MetricKey(10L, TEST_HOST, TEST_ENGINE, "m1", MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        MetricIntervalStatsRecord statsRecord = new MetricIntervalStatsRecord(k, 1L, stats);
        invokePrivate(repo, "saveMetricIntervalStatsAll",
                new Class<?>[] { List.class }, List.of(statsRecord));
        verify(txn).commit();
    }

    @Test
    void saveMetricIntervalStatsAll_transactionThrows_rollsBackAndThrowsMetricsRepositoryException() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        when(txn.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new RuntimeException("db error"));
        MetricKey k = new MetricKey(10L, TEST_HOST, TEST_ENGINE, "m1", MetricFactType.FLOAT64, InstrumentType.DOUBLE_GAUGE, true);
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        MetricIntervalStatsRecord statsRecord = new MetricIntervalStatsRecord(k, 1L, stats);
        var records = List.of(statsRecord);
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "saveMetricIntervalStatsAll",
                new Class<?>[] { List.class }, records));
        verify(txn).rollback();
    }

    @Test
    void loadRecentIntervalsForKeyFromDatabase_keyNotInCache_returnsEmptyList() {
        MetricKey k = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        List<ISymIntervalStats> result = repo.loadRecentIntervalsForKeyFromDatabase(k);
        assertTrue(result.isEmpty());
    }

    @Test
    void loadRecentIntervalsForKeyFromDatabase_keyInCache_queriesAndReturnsStats() {
        MetricKey k = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateMetricKeyCache", new Class<?>[] { List.class }, List.of(k));
        MetricIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        when(sqlTemplate.query(anyString(), anyInt(), any(ISqlRowMapper.class), any(Object[].class)))
                .thenReturn(List.of(stats));
        List<ISymIntervalStats> result = repo.loadRecentIntervalsForKeyFromDatabase(k);
        assertEquals(1, result.size());
        assertSame(stats, result.get(0));
    }

    @Test
    void loadRecentIntervalsPerKey_emptyCollection_returnsEmptyMap() {
        Map<MetricKey, List<ISymIntervalStats>> result = repo.loadRecentIntervalsPerKey(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void loadRecentIntervalsPerKey_uniqueKeys_queriesEachOnce() {
        MetricKey k1 = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey k2 = key(11L, "m2", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateMetricKeyCache", new Class<?>[] { List.class }, List.of(k1, k2));
        MetricIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        when(sqlTemplate.query(anyString(), anyInt(), any(ISqlRowMapper.class), any(Object[].class)))
                .thenReturn(List.of(stats));
        Map<MetricKey, List<ISymIntervalStats>> result = repo.loadRecentIntervalsPerKey(List.of(k1, k2));
        assertEquals(2, result.size());
        verify(sqlTemplate, times(2)).query(anyString(), anyInt(), any(ISqlRowMapper.class), any(Object[].class));
    }

    @Test
    void loadRecentIntervalsPerKey_duplicateKeys_queriesOnce() {
        MetricKey k = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        invokePrivate(repo, "populateMetricKeyCache", new Class<?>[] { List.class }, List.of(k));
        when(sqlTemplate.query(anyString(), anyInt(), any(ISqlRowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        Map<MetricKey, List<ISymIntervalStats>> result = repo.loadRecentIntervalsPerKey(List.of(k, k));
        assertEquals(1, result.size());
        verify(sqlTemplate, times(1)).query(anyString(), anyInt(), any(ISqlRowMapper.class), any(Object[].class));
    }

    @Test
    void saveMetricIntervalStatsInternal_nullKey_throwsMetricsRepositoryException() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "saveMetricIntervalStatsInternal",
                new Class<?>[] { ISqlTransaction.class, MetricKey.class, long.class, ISymIntervalStats.class },
                txn, null, 1L, stats));
    }

    @Test
    void saveMetricIntervalStatsInternal_nullStats_throwsMetricsRepositoryException() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        MetricKey k = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "saveMetricIntervalStatsInternal",
                new Class<?>[] { ISqlTransaction.class, MetricKey.class, long.class, ISymIntervalStats.class },
                txn, k, 1L, null));
    }

    @Test
    void saveMetricIntervalStatsInternal_int64FactType_executesInt64Insert() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        MetricKey k = new MetricKey(10L, TEST_HOST, TEST_ENGINE, "m1", MetricFactType.INT64, InstrumentType.LONG_GAUGE, true);
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        invokePrivate(repo, "saveMetricIntervalStatsInternal",
                new Class<?>[] { ISqlTransaction.class, MetricKey.class, long.class, ISymIntervalStats.class },
                txn, k, 1L, stats);
        verify(txn).prepareAndExecute(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void saveMetricIntervalStatsInternal_float64FactType_executesFloat64Insert() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        MetricKey k = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        invokePrivate(repo, "saveMetricIntervalStatsInternal",
                new Class<?>[] { ISqlTransaction.class, MetricKey.class, long.class, ISymIntervalStats.class },
                txn, k, 1L, stats);
        verify(txn).prepareAndExecute(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void saveMetricIntervalStatsInternal_transactionThrows_wrapsInMetricsRepositoryException() {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        MetricKey k = key(10L, "m1", TEST_ENGINE, TEST_HOST);
        when(txn.prepareAndExecute(anyString(), any(Object[].class), any(int[].class)))
                .thenThrow(new RuntimeException("insert error"));
        ISymIntervalStats stats = new MetricIntervalStats(1000L, 2000L, 1.0, 0.5, 2.0, 0.1, 5, 1.0, false);
        assertThrows(MetricsRepositoryException.class, () -> invokePrivate(repo, "saveMetricIntervalStatsInternal",
                new Class<?>[] { ISqlTransaction.class, MetricKey.class, long.class, ISymIntervalStats.class },
                txn, k, 1L, stats));
    }

    @Test
    void purgeIntervalStats_allSucceed_returnsTotal() {
        when(sqlTemplate.update(anyString(), (Object) any())).thenReturn(0);
        when(sqlTemplate.update(anyString())).thenReturn(0);
        java.util.Date cutoff = new java.util.Date();
        int result = repo.purgeIntervalStats(cutoff);
        assertEquals(0, result);
        verify(sqlTemplate, times(2)).update(anyString(), (Object) any());
        verify(sqlTemplate, times(1)).update(anyString());
    }

    @Test
    void purgeIntervalStats_float64Throws_continuesPurgingOthers() {
        when(sqlTemplate.update(anyString(), (Object) any())).thenThrow(new RuntimeException("float64 error"));
        when(sqlTemplate.update(anyString())).thenReturn(0);
        java.util.Date cutoff = new java.util.Date();
        assertDoesNotThrow(() -> repo.purgeIntervalStats(cutoff));
    }

    @Test
    void purgeIntervalStats_int64Throws_continuesPurgingOthers() {
        when(sqlTemplate.update(anyString(), (Object) any()))
                .thenReturn(0)
                .thenThrow(new RuntimeException("int64 error"));
        when(sqlTemplate.update(anyString())).thenReturn(0);
        java.util.Date cutoff = new java.util.Date();
        assertDoesNotThrow(() -> repo.purgeIntervalStats(cutoff));
    }

    @Test
    void purgeIntervalStats_orphanedContextsThrows_continuesPurgingOthers() {
        when(sqlTemplate.update(anyString(), (Object) any())).thenReturn(0);
        when(sqlTemplate.update(anyString())).thenThrow(new RuntimeException("orphaned error"));
        java.util.Date cutoff = new java.util.Date();
        assertDoesNotThrow(() -> repo.purgeIntervalStats(cutoff));
    }

    @Test
    void metricKeySqlRowMapper_mapRow_allFields() {
        Row row = new Row(7);
        row.put("metric_key", 42L);
        row.put("hostname", TEST_HOST);
        row.put("engine_name", TEST_ENGINE);
        row.put("metric_id", "m1");
        row.put("fact_type", "FLOAT64");
        row.put("metric_type", "DOUBLE_GAUGE");
        row.put("enabled", 1);
        MetricKey result = new MetricsRepository.MetricKeySqlRowMapper().mapRow(row);
        assertEquals(42L, result.key());
        assertEquals(TEST_HOST, result.hostname());
        assertEquals(TEST_ENGINE, result.engineName());
        assertEquals("m1", result.metricId());
        assertEquals(MetricFactType.FLOAT64, result.factType());
        assertEquals(InstrumentType.DOUBLE_GAUGE, result.metricType());
        assertTrue(result.isEnabled());
    }

    @Test
    void metricKeySqlRowMapper_mapRow_nullMetricType_returnsNullInstrumentType() {
        Row row = new Row(7);
        row.put("metric_key", 5L);
        row.put("hostname", TEST_HOST);
        row.put("engine_name", TEST_ENGINE);
        row.put("metric_id", "m2");
        row.put("fact_type", "INT64");
        row.put("metric_type", null);
        row.put("enabled", 0);
        MetricKey result = new MetricsRepository.MetricKeySqlRowMapper().mapRow(row);
        assertNull(result.metricType());
        assertFalse(result.isEnabled());
    }

    @Test
    void metricContextSqlRowMapper_mapRow_allThreeAttrs() {
        Row row = new Row(7);
        row.put("context_id", 100L);
        row.put("attr1_name", "channel");
        row.put("attr1_value", "default");
        row.put("attr2_name", "node_group");
        row.put("attr2_value", "store");
        row.put("attr3_name", "type");
        row.put("attr3_value", "inbound");
        MetricContext result = new MetricsRepository.MetricContextSqlRowMapper().mapRow(row);
        assertEquals(100L, result.contextId());
        assertEquals(3, result.getAttributes().size());
        assertEquals("channel", result.getAttributes().get(0).name());
        assertEquals("node_group", result.getAttributes().get(1).name());
        assertEquals("type", result.getAttributes().get(2).name());
    }

    @Test
    void metricContextSqlRowMapper_mapRow_onlyFirstAttrPopulated() {
        Row row = new Row(7);
        row.put("context_id", 200L);
        row.put("attr1_name", "channel");
        row.put("attr1_value", Constants.CHANNEL_RELOAD);
        row.put("attr2_name", null);
        row.put("attr2_value", null);
        row.put("attr3_name", "");
        row.put("attr3_value", null);
        MetricContext result = new MetricsRepository.MetricContextSqlRowMapper().mapRow(row);
        assertEquals(200L, result.contextId());
        assertEquals(1, result.getAttributes().size());
        assertEquals("channel", result.getAttributes().get(0).name());
    }

    @Test
    void doubleStatsSqlRowMapper_mapRow_allFields() {
        java.sql.Timestamp startTs = new java.sql.Timestamp(1000L);
        Row row = new Row(9);
        row.put("interval_start_time", startTs);
        row.put("interval_end_millis", 2000L);
        row.put("avg_value", 1.5);
        row.put("min_value", 0.5);
        row.put("max_value", 2.5);
        row.put("std_dev", 0.3);
        row.put("observation_count", 10);
        row.put("mean", 1.2);
        row.put("outlier", 1);
        ISymIntervalStats result = new MetricsRepository.DoubleStatsSqlRowMapper().mapRow(row);
        assertEquals(1000L, result.getStartEpoch());
        assertEquals(2000L, result.getEndEpoch());
        assertEquals(1.5, result.getAvg(), 0.001);
        assertEquals(0.5, result.getMin(), 0.001);
        assertEquals(2.5, result.max(), 0.001);
        assertEquals(0.3, result.getStdDeviation(), 0.001);
        assertEquals(10, result.getObservationCount());
        assertTrue(result.isOutlier());
    }

    @Test
    void doubleStatsSqlRowMapper_mapRow_localDateTimeStartTime_convertsCorrectly() {
        LocalDateTime startLocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0, 1);
        Row row = new Row(9);
        row.put("interval_start_time", startLocalDateTime);
        row.put("interval_end_millis", 2000L);
        row.put("avg_value", 1.5);
        row.put("min_value", 0.5);
        row.put("max_value", 2.5);
        row.put("std_dev", 0.3);
        row.put("observation_count", 10);
        row.put("mean", 1.2);
        row.put("outlier", 1);
        ISymIntervalStats result = new MetricsRepository.DoubleStatsSqlRowMapper().mapRow(row);
        assertEquals(java.sql.Timestamp.valueOf(startLocalDateTime).getTime(), result.getStartEpoch());
    }

    @Test
    void doubleStatsSqlRowMapper_mapRow_nullStartTime_setsStartEpochToZero() {
        Row row = new Row(9);
        row.put("interval_start_time", null);
        row.put("interval_end_millis", 2000L);
        row.put("avg_value", 1.0);
        row.put("min_value", 0.0);
        row.put("max_value", 2.0);
        row.put("std_dev", 0.0);
        row.put("observation_count", 3);
        row.put("mean", 1.0);
        row.put("outlier", 0);
        ISymIntervalStats result = new MetricsRepository.DoubleStatsSqlRowMapper().mapRow(row);
        assertEquals(0L, result.getStartEpoch());
        assertFalse(result.isOutlier());
    }

    @Test
    void doubleStatsSqlRowMapper_rowDouble_nullValue_returnsZero() {
        Row row = new Row(9);
        row.put("interval_start_time", new java.sql.Timestamp(0L));
        row.put("interval_end_millis", 0L);
        row.put("avg_value", null);
        row.put("min_value", null);
        row.put("max_value", 0.0);
        row.put("std_dev", 0.0);
        row.put("observation_count", 0);
        row.put("mean", 0.0);
        row.put("outlier", 0);
        ISymIntervalStats result = new MetricsRepository.DoubleStatsSqlRowMapper().mapRow(row);
        assertEquals(0.0, result.getAvg(), 0.0);
        assertEquals(0.0, result.getMin(), 0.0);
    }
}
