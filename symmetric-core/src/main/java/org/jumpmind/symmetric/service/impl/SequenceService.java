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
package org.jumpmind.symmetric.service.impl;

import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTransactionListenerAdapter;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Sequence;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ISequenceService;

public class SequenceService extends AbstractService implements ISequenceService {
    private final int SEQUENCE_TIMEOUT_MS_DEFAULT = 5000;
    private final int SEQUENCE_EXPIRATION_MS_DEFAULT = 30000;
    private final int SEQUENCE_EXPIRATION_MS_MAX = 60000;
    private int sequenceCacheItemExpirationMs;
    private Map<String, Sequence> sequenceDefinitionCache = new HashMap<String, Sequence>();
    private Map<String, CachedRange> sequenceCache = new HashMap<String, CachedRange>();

    public SequenceService(IParameterService parameterService, ISymmetricDialect symmetricDialect) {
        super(parameterService, symmetricDialect);
        setSqlMap(new SequenceServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
        sequenceCacheItemExpirationMs = 0;
    }

    @Override
    public void init() {
        Map<String, Sequence> sequences = getAll();
        if (sequences.get(Constants.SEQUENCE_OUTGOING_BATCH_LOAD_ID) == null) {
            initSequence(Constants.SEQUENCE_OUTGOING_BATCH_LOAD_ID, 1, 0);
        }
        if (sequences.get(Constants.SEQUENCE_OUTGOING_BATCH) == null) {
            long maxBatchId = sqlTemplate.queryForLong(getSql("maxOutgoingBatchSql"));
            initSequence(Constants.SEQUENCE_OUTGOING_BATCH, maxBatchId, 10);
        }
        if (sequences.get(Constants.SEQUENCE_TRIGGER_HIST) == null) {
            long maxTriggerHistId = sqlTemplate.queryForLong(getSql("maxTriggerHistSql"));
            initSequence(Constants.SEQUENCE_TRIGGER_HIST, maxTriggerHistId, 0);
        }
        if (sequences.get(Constants.SEQUENCE_EXTRACT_REQ) == null) {
            long maxRequestId = sqlTemplate.queryForLong(getSql("maxExtractRequestSql"));
            initSequence(Constants.SEQUENCE_EXTRACT_REQ, maxRequestId, 0);
        }
        if (TableConstants.hasConsoleSchema() && sequences.get(Constants.SEQUENCE_COMPARE_ID) == null) {
            long maxRequestId = sqlTemplate.queryForLong(getSql("maxCompareRequestSql"));
            initSequence(Constants.SEQUENCE_COMPARE_ID, maxRequestId, 0);
        }
        if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            sequenceCacheItemExpirationMs = parameterService.getInt(ParameterConstants.SEQUENCE_CACHE_EXPIRES_MS, SEQUENCE_EXPIRATION_MS_DEFAULT);
            if (sequenceCacheItemExpirationMs > SEQUENCE_EXPIRATION_MS_MAX) {
                log.warn("The {} parameter value {} exceeds maximum of {}. Ignored.", ParameterConstants.SEQUENCE_CACHE_EXPIRES_MS,
                        sequenceCacheItemExpirationMs,
                        SEQUENCE_EXPIRATION_MS_MAX);
                sequenceCacheItemExpirationMs = SEQUENCE_EXPIRATION_MS_MAX;
            }
        }
    }

    private void initSequence(String name, long initialValue, int cacheSize) {
        try {
            if (initialValue < 1) {
                initialValue = 1;
            }
            create(new Sequence(name, initialValue, 1, 1, 9999999999l,
                    "system", false, cacheSize));
        } catch (UniqueKeyException ex) {
            log.debug("Failed to create sequence {}.  Must be initialized already.",
                    name);
        }
    }

    @Override
    public synchronized long nextVal(String name) {
        if (isSequenceCached(name)) {
            return nextValFromCache(null, name);
        }
        return nextValFromDatabase(name, 1);
    }

    @Override
    public synchronized long nextVal(ISqlTransaction transaction, final String name) {
        if (transaction != null) {
            transaction.addSqlTransactionListener(new SqlTransactionListenerAdapter() {
                @Override
                public void transactionRolledBack() {
                    sequenceCache.remove(name);
                }
            });
        }
        if (isSequenceCached(name)) {
            return nextValFromCache(transaction, name);
        }
        return nextValFromDatabase(transaction, name, 1);
    }

    protected boolean isSequenceCached(String name) {
        Sequence sequence = sequenceDefinitionCache.get(name);
        if (sequence == null) {
            return false;
        }
        if (sequence.getCacheSize() < 1) {
            return false;
        }
        CachedRange range = sequenceCache.get(name);
        if (range == null) {
            return false;
        }
        if (sequenceCacheItemExpirationMs > 0 && range.isExpired()) {
            if (log.isDebugEnabled()) {
                log.debug("Cached range [{} - {}] for sequence {} has expired, losing {} values.", range.getCurrentValue(), range.getEndValue(), name, range
                        .getRemainingCount());
            }
            sequenceCache.remove(name);
            return false;
        }
        return true;
    }

    protected long nextValFromCache(ISqlTransaction transaction, String name) {
        CachedRange range = sequenceCache.get(name);
        if (range != null && (sequenceCacheItemExpirationMs == 0 || !range.isExpired())) {
            if (log.isDebugEnabled()) {
                log.debug("Cache hit. Using one value from range [{} - {}] for sequence={}, remainingCount={}", range.getCurrentValue(), range.getEndValue(),
                        name, range.getRemainingCount());
            }
            if (range.getRemainingCount() > 0) {
                return range.claim(1);
            } else {
                sequenceCache.remove(name);
            }
        }
        return nextValFromDatabase(transaction, name, 1);
    }

    protected long nextValFromDatabase(final String name, long size) {
        return new DoTransaction<Long>() {
            @Override
            public Long execute(ISqlTransaction transaction) {
                return nextValFromDatabase(transaction, name, size);
            }
        }.execute();
    }

    protected long nextValFromDatabase(ISqlTransaction transaction, String name, long size) {
        if (transaction == null) {
            return nextValFromDatabase(name, size);
        }
        long sequenceDbTimeoutInMs = parameterService.getLong(ParameterConstants.SEQUENCE_TIMEOUT_MS, SEQUENCE_TIMEOUT_MS_DEFAULT);
        long ts = System.currentTimeMillis();
        int attemptNo = 0;
        do {
            attemptNo++;
            try {
                CachedRange range = tryToGetNextVal(transaction, name, size);
                if (range != null) {
                    sequenceCache.put(name, range);
                    long nextVal = range.claim(size);
                    if (log.isDebugEnabled()) {
                        log.debug("Produced the next value for sequence={}, size={}, attemptNo={}, value={}", name, size, attemptNo, nextVal);
                    }
                    return nextVal;
                }
            } catch (SqlException ex) {
                String errorMessage = ex.getMessage();
                if (System.currentTimeMillis() - sequenceDbTimeoutInMs < ts &&
                        !(errorMessage.toUpperCase().contains("DEADLOCK"))) {
                    log.info("Delay in producing the next value for sequence={}, size={}, attemptNo={}, details={}", name, size, attemptNo, errorMessage);
                } else {
                    String finalMessage = String.format("Failed to produce the next value for sequence=%s, size=%d", name, size);
                    log.error("{}, attemptNo={}, details={}", finalMessage, attemptNo, errorMessage);
                    throw new IllegalStateException(finalMessage, ex);
                }
            }
        } while (System.currentTimeMillis() - sequenceDbTimeoutInMs < ts);
        throw new IllegalStateException(String.format("Timed out after %d ms trying to produce the next value for sequence=%s, size=%d, attemptNo=%d",
                System.currentTimeMillis() - ts, name, size, attemptNo));
    }

    /**
     * Updates internal table in the runtime database to claim a number of sequential values (specified by demandedSize). Sets the current_value column to the
     * end of the cached range (these values had not been used yet, but are reserved for this server). Warning: In a clustered environment a large
     * sequence.getCacheSize() together with a very small sequenceCacheItemExpirationMs will lead to excessive churn, increasing risk of database deadlocks!
     * 
     * @return Range of reserved values as CachedRange
     */
    protected CachedRange tryToGetNextVal(ISqlTransaction transaction, String name, long demandedSize) {
        transaction.commit();
        long currVal = currVal(transaction, name);
        Sequence sequence = getSequenceDefinition(transaction, name);
        long size = (demandedSize > sequence.getCacheSize() + 1) ? demandedSize : sequence.getCacheSize();
        long nextVal = currVal + (sequence.getIncrementBy() * size);
        if (nextVal > sequence.getMaxValue()) {
            if (sequence.isCycle()) {
                nextVal = sequence.getMinValue() + ((sequence.getIncrementBy() * size) - sequence.getIncrementBy());
            } else {
                throw new IllegalStateException(String.format(
                        "The sequence named %s has reached it's max value.  "
                                + "No more numbers can be handed out.", name));
            }
        } else if (nextVal < sequence.getMinValue()) {
            if (sequence.isCycle()) {
                nextVal = sequence.getMaxValue() + ((sequence.getIncrementBy() * size) - sequence.getIncrementBy());
            } else {
                throw new IllegalStateException(String.format(
                        "The sequence named %s has reached it's min value.  "
                                + "No more numbers can be handed out.", name));
            }
        }
        int updateCount = transaction.prepareAndExecute(getSql("updateCurrentValueSql"), nextVal, new Date(), name, currVal);
        if (updateCount != 1) {
            if (log.isDebugEnabled()) {
                log.debug("Contention detected, should re-try. Unable to reserve range of values for sequence={}, start={}, size={}", name, currVal, size);
            }
            return null;
        }
        transaction.commit();
        long endVal = currVal + (sequence.getIncrementBy() * (size - 1));
        if (log.isDebugEnabled()) {
            log.debug("Reserved range of values [{} - {}] for sequence={}, size={}", currVal, endVal, name, currVal, size);
        }
        return new CachedRange(currVal, endVal, sequence.getIncrementBy(), System.currentTimeMillis() + sequenceCacheItemExpirationMs);
    }

    /**
     * Obtain a contiguous range of sequence numbers. The initial load extract in background needs an uninterrupted range of batch numbers. As a bonus, it's
     * more efficient to request the entire range in one call.
     * 
     * @param name
     *            Sequence name to use
     * @param size
     *            Number of sequence numbers to obtain
     * @return Starting sequence number for the entire range that was obtained
     */
    @Override
    public synchronized long nextRange(ISqlTransaction transaction, String name, long size) {
        Sequence sequence = getSequenceDefinition(name);
        if (size <= 0) {
            throw new IllegalStateException("Size of range must be a positive integer");
        }
        if (sequence.getIncrementBy() <= 0) {
            throw new IllegalStateException("Increment-by must be a positive integer");
        }
        long startingValue = 0;
        long rangeNeeded = size * sequence.getIncrementBy();
        if (sequence.getCacheSize() > 0) {
            CachedRange range = sequenceCache.get(name);
            if (range != null && (sequenceCacheItemExpirationMs == 0 || !range.isExpired())) {
                long currentValue = range.getCurrentValue();
                long endValue = range.getEndValue();
                long rangeAvailable = endValue - currentValue;
                long remainingCount = range.getRemainingCount();
                if (remainingCount >= size) {
                    if (log.isDebugEnabled()) {
                        log.debug("Cache hit for sequence={}, currentValue={}, size={}", name, currentValue, size);
                    }
                    if (remainingCount == size) {
                        sequenceCache.remove(name);
                    }
                    return range.claim(size);
                }
                if (parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Discarding unexpired but too small range of values for sequence={}, currentValue={}, remainingCount={}", name, currentValue,
                                remainingCount);
                    }
                    sequenceCache.remove(name);
                } else {
                    rangeNeeded -= rangeAvailable;
                    size = rangeNeeded / sequence.getIncrementBy();
                    sequenceCache.remove(name);
                }
            }
        }
        if (rangeNeeded > 0) {
            long databaseStartingValue = 0;
            if (transaction == null) {
                databaseStartingValue = nextValFromDatabase(name, size) - (rangeNeeded - sequence.getIncrementBy());
            } else {
                databaseStartingValue = nextValFromDatabase(transaction, name, size) - (rangeNeeded - sequence.getIncrementBy());
            }
            if (startingValue == 0) {
                startingValue = databaseStartingValue;
            }
        }
        return startingValue;
    }

    @Override
    public synchronized long nextRange(String name, long size) {
        return nextRange(null, name, size);
    }

    protected Sequence getSequenceDefinition(final String name) {
        Sequence sequence = sequenceDefinitionCache.get(name);
        if (sequence != null) {
            return sequence;
        }
        return new DoTransaction<Sequence>() {
            @Override
            public Sequence execute(ISqlTransaction transaction) {
                return getSequenceDefinition(transaction, name);
            }
        }.execute();
    }

    protected Sequence getSequenceDefinition(ISqlTransaction transaction, String name) {
        Sequence sequence = sequenceDefinitionCache.get(name);
        if (sequence == null) {
            sequence = get(transaction, name);
            if (sequence != null) {
                sequenceDefinitionCache.put(name, sequence);
            } else {
                throw new IllegalStateException(String.format(
                        "The sequence named %s is not configured in %s", name,
                        TableConstants.getTableName(getTablePrefix(), TableConstants.SYM_SEQUENCE)));
            }
        }
        return sequence;
    }

    @Override
    public synchronized long currVal(ISqlTransaction transaction, String name) {
        if (isSequenceCached(name)) {
            CachedRange range = sequenceCache.get(name);
            if (range != null) {
                return range.getCurrentValue();
            }
        }
        return transaction.queryForLong(getSql("getCurrentValueSql"), name);
    }

    @Override
    public synchronized long currVal(final String name) {
        if (isSequenceCached(name)) {
            CachedRange range = sequenceCache.get(name);
            if (range != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Lookup-Cache hit for sequence={}, currentValue={}, size={}", name, range.getCurrentValue(), range.getRemainingCount());
                }
                return range.getCurrentValue();
            }
        }
        return new DoTransaction<Long>() {
            @Override
            public Long execute(ISqlTransaction transaction) {
                return currVal(transaction, name);
            }
        }.execute();
    }

    @Override
    public void create(Sequence sequence) {
        sqlTemplate.update(getSql("insertSequenceSql"), sequence.getSequenceName(), sequence.getCurrentValue(),
                sequence.getIncrementBy(), sequence.getMinValue(), sequence.getMaxValue(), sequence.isCycle() ? 1 : 0,
                sequence.getCacheSize(), new Date(), sequence.getLastUpdateBy(), new Date());
    }

    protected Sequence get(ISqlTransaction transaction, String name) {
        List<Sequence> values = transaction.query(getSql("getSequenceSql"), new SequenceRowMapper(), new Object[] { name }, new int[] { Types.VARCHAR });
        if (values.size() > 0) {
            return values.get(0);
        } else {
            return null;
        }
    }

    protected Map<String, Sequence> getAll() {
        Map<String, Sequence> map = new HashMap<String, Sequence>();
        List<Sequence> sequences = sqlTemplate.query(getSql("getAllSequenceSql"), new SequenceRowMapper());
        for (Sequence sequence : sequences) {
            map.put(sequence.getSequenceName(), sequence);
        }
        return map;
    }

    static class CachedRange {
        long currentValue;
        long endValue;
        int incrementBy;
        long expires;

        public CachedRange(long currentValue, long endValue, int incrementBy, long expires) {
            this.currentValue = currentValue;
            this.endValue = endValue;
            this.incrementBy = incrementBy;
            this.expires = expires;
        }

        public long getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(long currentValue) {
            this.currentValue = currentValue;
        }

        public long getEndValue() {
            return endValue;
        }

        public int getIncrementBy() {
            return incrementBy;
        }

        public long getExpires() {
            return expires;
        }

        public boolean isExpired() {
            return (this.expires < System.currentTimeMillis());
        }

        public long claim(long size) {
            long currVal = this.currentValue;
            this.currentValue = this.currentValue + size * this.incrementBy;
            return currVal;
        }

        public long getRemainingCount() {
            return (endValue - this.currentValue) / this.incrementBy;
        }
    }

    abstract class DoTransaction<T> {
        public T execute() {
            ISqlTransaction transaction = null;
            try {
                transaction = sqlTemplate.startSqlTransaction();
                T result = execute(transaction);
                transaction.commit();
                return result;
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                close(transaction);
            }
        }

        abstract public T execute(ISqlTransaction transaction);
    }

    static class SequenceRowMapper implements ISqlRowMapper<Sequence> {
        @Override
        public Sequence mapRow(Row rs) {
            Sequence sequence = new Sequence();
            sequence.setCreateTime(rs.getDateTime("create_time"));
            sequence.setCurrentValue(rs.getLong("current_value"));
            sequence.setIncrementBy(rs.getInt("increment_by"));
            sequence.setLastUpdateBy(rs.getString("last_update_by"));
            sequence.setLastUpdateTime(rs.getDateTime("last_update_time"));
            sequence.setMaxValue(rs.getLong("max_value"));
            sequence.setMinValue(rs.getLong("min_value"));
            sequence.setSequenceName(rs.getString("sequence_name"));
            sequence.setCycle(rs.getBoolean("cycle_flag"));
            sequence.setCacheSize(rs.getInt("cache_size"));
            return sequence;
        }
    }
}