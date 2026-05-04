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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.metrics.ContextDefinition;
import org.jumpmind.symmetric.observability.models.MetricContext;
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
        List<MetricAttribute> attrs = List.of(new MetricAttribute("env", "prod"));
        MetricContext ctx = new MetricContext(1L, attrs);
        String cacheKey = MetricsRepository.contextCacheKey(attrs);
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
        List<MetricAttribute> attrs = List.of(new MetricAttribute("node", "001"));
        MetricContext ctx = new MetricContext(MetricContext.SEED_IDS_END + 1L, attrs);
        String cacheKey = MetricsRepository.contextCacheKey(attrs);
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
    void loadAllMetricKeysForHostnameFromDatabase_queryReturnsNull_returnsEmptyList() {
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(null);
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
        MetricKey keyMissing = key(SurrogateLongKeyBuffer.SURROGATE_KEY_UNASSIGNED, "m1", TEST_ENGINE, TEST_HOST);
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
    void updateMetricKeyInDatabase_differentSurrogateKeys_executesTransaction_returnsKey() throws Exception {
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey dbRec = key(5L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey newKey = key(99L, "m1", TEST_ENGINE, TEST_HOST);
        MetricKey result = invokePrivate(repo, "updateMetricKeyInDatabase",
                new Class<?>[] { MetricKey.class, MetricKey.class }, newKey, dbRec);
        assertSame(newKey, result);
    }

    @Test
    void updateMetricKeyInDatabase_transactionThrows_wrapsInMetricsRepositoryException() throws Exception {
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
    void generateSurrogateKeyAndSaveMetricKeyToDatabase_transactionSucceeds_returnsLoadedKey() throws Exception {
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
    void generateSurrogateKeyAndSaveMetricKeyToDatabase_firstAttemptUniqueViolationSecondSucceeds_returnsKey() throws Exception {
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
    void generateSurrogateKeyAndSaveMetricKeyToDatabase_nonUniqueException_throwsImmediately() throws Exception {
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
    void saveMetricKeyInternal_surrogateKeyMissing_sharedEngine_callsGeneratePath() throws Exception {
        // surrogateKeys is not available (default uninitialized state), so always falls through to generate path
        ISqlTransaction txn = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(txn);
        MetricKey loaded = key(60L, "m1", MetricsRepository.METRIC_SHARED_ENGINE, TEST_HOST);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), any(Object[].class), any(int[].class)))
                .thenReturn(List.of(loaded));
        MetricKey missing = key(SurrogateLongKeyBuffer.SURROGATE_KEY_UNASSIGNED,
                "m1", MetricsRepository.METRIC_SHARED_ENGINE, TEST_HOST);
        // isSurrogateKeyMissing() == true → delegates to String overload → shared engine: generate path
        MetricKey result = invokePrivate(repo, "saveMetricKeyInternal",
                new Class<?>[] { MetricKey.class }, missing);
        assertNotNull(result);
        assertFalse(result.isSurrogateKeyMissing());
    }

    @Test
    void saveMetricKeyInternal_validSurrogate_callsSaveToDatabase_returnsKey() throws Exception {
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
    void saveMetricKey_keyNotInCache_savesAndReturns() throws Exception {
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
    void getOrRegisterMetricKey_keyNotInCache_savesAndReturns() throws Exception {
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
    void getOrRegisterMetricKey_byStrings_keyNotInCache_savesAndCaches() throws Exception {
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
        List<MetricAttribute> attrs = List.of(new MetricAttribute("channel", "default"));
        MetricContext ctx = new MetricContext(1L, attrs);
        String cacheKey = MetricsRepository.contextCacheKey(attrs);
        Map<String, MetricContext> seedCache = getField(repo, "seedContextCache");
        seedCache.put(cacheKey, ctx);
        ContextDefinition def = new ContextDefinition(1L, attrs);
        MetricContext result = repo.getOrRegisterContext(def);
        assertSame(ctx, result);
    }

    @Test
    void getOrRegisterContext_def_notInCacheFoundInDb_cachesAndReturns() throws Exception {
        List<MetricAttribute> attrs = List.of(new MetricAttribute("channel", "reload"));
        MetricContext dbCtx = new MetricContext(2L, attrs);
        // loadContextByAttrsFromDatabase: sqlTemplate.query (varargs form) returns dbCtx
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of(dbCtx));
        ContextDefinition def = new ContextDefinition(2L, attrs);
        MetricContext result = repo.getOrRegisterContext(def);
        assertSame(dbCtx, result);
        // Should be in seed cache (contextId=2 <= SEED_IDS_END)
        Map<String, MetricContext> seedCache = getField(repo, "seedContextCache");
        String cacheKey = MetricsRepository.contextCacheKey(attrs);
        assertSame(dbCtx, seedCache.get(cacheKey));
    }

    @Test
    void getOrRegisterContext_def_notInCacheNotInDb_insertsAndCaches() throws Exception {
        List<MetricAttribute> attrs = List.of(new MetricAttribute("channel", "new_channel"));
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
        String cacheKey = MetricsRepository.contextCacheKey(attrs);
        assertNotNull(seedCache.get(cacheKey));
    }

    @Test
    void getOrRegisterContext_attrs_inDynamicCache_returnsCached() {
        List<MetricAttribute> attrs = List.of(new MetricAttribute("node", "001"));
        MetricContext ctx = new MetricContext(MetricContext.SEED_IDS_END + 10L, attrs);
        String cacheKey = MetricsRepository.contextCacheKey(attrs);
        Map<String, MetricContext> dynCache = getField(repo, "dynamicContextCache");
        dynCache.put(cacheKey, ctx);
        MetricContext result = repo.getOrRegisterContext(attrs);
        assertSame(ctx, result);
    }

    @Test
    void getOrRegisterContext_attrs_notInCacheFoundInDb_cachesInDynamicAndReturns() throws Exception {
        List<MetricAttribute> attrs = List.of(new MetricAttribute("node", "002"));
        MetricContext dbCtx = new MetricContext(MetricContext.SEED_IDS_END + 5L, attrs);
        when(sqlTemplate.query(anyString(), any(ISqlRowMapper.class), (Object[]) any()))
                .thenReturn(List.of(dbCtx));
        MetricContext result = repo.getOrRegisterContext(attrs);
        assertSame(dbCtx, result);
        Map<String, MetricContext> dynCache = getField(repo, "dynamicContextCache");
        String cacheKey = MetricsRepository.contextCacheKey(attrs);
        assertSame(dbCtx, dynCache.get(cacheKey));
    }

    @Test
    void getOrRegisterContext_attrs_notInCacheNotInDb_generatesAndInserts() throws Exception {
        List<MetricAttribute> attrs = List.of(new MetricAttribute("node", "003"));
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
        String cacheKey = MetricsRepository.contextCacheKey(attrs);
        assertNotNull(dynCache.get(cacheKey));
    }
}
