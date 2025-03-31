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
    private Map<String, Sequence> sequenceDefinitionCache = new HashMap<String, Sequence>();
    private Map<String, CachedRange> sequenceCache = new HashMap<String, CachedRange>();

    public SequenceService(IParameterService parameterService, ISymmetricDialect symmetricDialect) {
        super(parameterService, symmetricDialect);
        setSqlMap(new SequenceServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
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
        if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) && getSequenceDefinition(name).getCacheSize() > 0) {
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
        if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) && getSequenceDefinition(transaction, name).getCacheSize() > 0) {
            return nextValFromCache(transaction, name);
        }
        return nextValFromDatabase(transaction, name, 1);
    }

    protected long nextValFromCache(ISqlTransaction transaction, String name) {
        CachedRange range = sequenceCache.get(name);
        if (range != null) {
            long currentValue = range.getCurrentValue();
            if (currentValue < range.getEndValue()) {
                currentValue += range.getIncrementBy();
                range.setCurrentValue(currentValue);
                return currentValue;
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
        long sequenceTimeoutInMs = parameterService.getLong(ParameterConstants.SEQUENCE_TIMEOUT_MS, 5000);
        long ts = System.currentTimeMillis();
        int attemptNo = 0;
        do {
            attemptNo++;
            try {
                long nextVal = tryToGetNextVal(transaction, name, size);
                if (nextVal > 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Produced the next value for sequence={}, attemptNo={}, value={}", name, attemptNo, nextVal);
                    }
                    return nextVal;
                }
            } catch (Exception ex) {
                if (System.currentTimeMillis() - sequenceTimeoutInMs < ts) {
                    log.info("Delay in producing the next value for sequence={}, attemptNo={}, details={}", name, attemptNo, ex.getMessage());
                } else {
                    log.error("Failed to produce the next value for sequence={}, attemptNo={}, details={}", name, attemptNo, ex.getMessage());
                }
            }
        } while (System.currentTimeMillis() - sequenceTimeoutInMs < ts);
        throw new IllegalStateException(String.format("Timed out after %d ms trying to produce the next value for sequence=%s, attemptNo=%d",
                System.currentTimeMillis() - ts, name, attemptNo));
    }

    protected long tryToGetNextVal(ISqlTransaction transaction, String name, long size) {
        long currVal = currVal(transaction, name);
        Sequence sequence = getSequenceDefinition(transaction, name);
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
        CachedRange range = null;
        if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) && sequence.getCacheSize() > 0) {
            long endVal = nextVal + (sequence.getIncrementBy() * (sequence.getCacheSize() - 1));
            range = new CachedRange(nextVal, endVal, sequence.getIncrementBy());
            nextVal = endVal;
        }
        int updateCount = transaction.prepareAndExecute(getSql("updateCurrentValueSql"), nextVal, new Date(), name, currVal);
        if (updateCount != 1) {
            return -1;
        }
        transaction.commit();
        if (range != null) {
            sequenceCache.put(name, range);
            nextVal = range.getCurrentValue();
        }
        return nextVal;
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
        if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED) && sequence.getCacheSize() > 0) {
            CachedRange range = sequenceCache.get(name);
            if (range != null) {
                long currentValue = range.getCurrentValue();
                long endValue = range.getEndValue();
                long rangeAvailable = endValue - currentValue;
                long rangeEndValue = currentValue + rangeNeeded;
                if (currentValue < endValue && rangeEndValue <= sequence.getMaxValue()) {
                    startingValue = currentValue + sequence.getIncrementBy();
                    if (rangeNeeded <= rangeAvailable) {
                        range.setCurrentValue(currentValue + rangeNeeded);
                        rangeNeeded = 0;
                    } else {
                        rangeNeeded -= rangeAvailable;
                        size = rangeNeeded / sequence.getIncrementBy();
                        sequenceCache.remove(name);
                    }
                } else {
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
        if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            CachedRange range = sequenceCache.get(name);
            if (range != null) {
                return range.getCurrentValue();
            }
        }
        return transaction.queryForLong(getSql("getCurrentValueSql"), name);
    }

    @Override
    public synchronized long currVal(final String name) {
        if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            CachedRange range = sequenceCache.get(name);
            if (range != null) {
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

        public CachedRange(long currentValue, long endValue, int incrementBy) {
            this.currentValue = currentValue;
            this.endValue = endValue;
            this.incrementBy = incrementBy;
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