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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Sequence;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.SequenceService.CachedRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SequenceServiceTest {
    private static final String SEQUENCE_NAME = "data_id";
    private IParameterService parameterService;
    private ISqlTemplate sqlTemplate;
    private ISqlTransaction transaction;
    private SequenceService sequenceService;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(returnsFirstArg());
        transaction = mock(ISqlTransaction.class);
        sequenceService = new SequenceService(parameterService, symmetricDialect);
    }

    @Test
    void testCachedRange_tracksTheCurrentValueWithinTheRange() {
        CachedRange range = new CachedRange(10, 20, 2);
        assertEquals(10, range.getCurrentValue());
        assertEquals(20, range.getEndValue());
        assertEquals(2, range.getIncrementBy());
        range.setCurrentValue(12);
        assertEquals(12, range.getCurrentValue());
    }

    @Test
    void testSequenceRowMapper_mapsEveryColumn() {
        Row row = new Row(10);
        row.put("sequence_name", SEQUENCE_NAME);
        row.put("current_value", 100L);
        row.put("increment_by", 2);
        row.put("min_value", 1L);
        row.put("max_value", 9999999999L);
        row.put("cycle_flag", 1);
        row.put("cache_size", 5);
        row.put("create_time", Timestamp.valueOf("2023-11-14 17:13:20"));
        row.put("last_update_by", "system");
        row.put("last_update_time", Timestamp.valueOf("2024-01-02 03:04:05"));
        Sequence sequence = new SequenceService.SequenceRowMapper().mapRow(row);
        assertEquals(SEQUENCE_NAME, sequence.getSequenceName());
        assertEquals(100L, sequence.getCurrentValue());
        assertEquals(2, sequence.getIncrementBy());
        assertEquals(1L, sequence.getMinValue());
        assertEquals(9999999999L, sequence.getMaxValue());
        assertTrue(sequence.isCycle());
        assertEquals(5, sequence.getCacheSize());
        assertEquals("system", sequence.getLastUpdateBy());
    }

    @Test
    void testGetSequenceDefinition_withAnUnconfiguredSequenceThrows() {
        stubSequenceLookup(Collections.<Sequence> emptyList());
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sequenceService.getSequenceDefinition(transaction, SEQUENCE_NAME));
        assertEquals("The sequence named data_id is not configured in sym_sequence", exception.getMessage());
    }

    @Test
    void testGetSequenceDefinition_isCachedAfterTheFirstLookup() {
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 1, 9999999999L, false, 0)));
        sequenceService.getSequenceDefinition(transaction, SEQUENCE_NAME);
        sequenceService.getSequenceDefinition(transaction, SEQUENCE_NAME);
        verify(transaction, times(1)).query(anyString(), any(), any(Object[].class), any(int[].class));
    }

    @Test
    void testCurrVal_readsThroughTheTransactionWhenNothingIsCached() {
        when(transaction.queryForLong(sqlFor("getCurrentValueSql"), SEQUENCE_NAME)).thenReturn(100L);
        assertEquals(100L, sequenceService.currVal(transaction, SEQUENCE_NAME));
    }

    @Test
    void testTryToGetNextVal_handsOutTheNextValue() {
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 1, 9999999999L, false, 0)));
        stubUpdateCount(1);
        assertEquals(101L, sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1));
    }

    @Test
    void testTryToGetNextVal_appliesTheIncrementAndTheRequestedSize() {
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 5, 1, 9999999999L, false, 0)));
        stubUpdateCount(1);
        assertEquals(150L, sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 10));
    }

    @Test
    void testTryToGetNextVal_returnsMinusOneWhenAnotherNodeWonTheUpdate() {
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 1, 9999999999L, false, 0)));
        stubUpdateCount(0);
        assertEquals(-1L, sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1));
    }

    @Test
    void testTryToGetNextVal_atTheMaxValueWithoutCyclingThrows() {
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 1, 100, false, 0)));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1));
        assertTrue(exception.getMessage().contains("has reached it's max value"));
    }

    @Test
    void testTryToGetNextVal_atTheMaxValueWithCyclingWrapsToTheMinValue() {
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 7, 100, true, 0)));
        stubUpdateCount(1);
        assertEquals(7L, sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1));
    }

    @Test
    void testTryToGetNextVal_belowTheMinValueWithoutCyclingThrows() {
        stubCurrentValue(1L);
        stubSequenceLookup(Collections.singletonList(newSequence(1, -1, 1, 100, false, 0)));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1));
        assertTrue(exception.getMessage().contains("has reached it's min value"));
    }

    @Test
    void testTryToGetNextVal_belowTheMinValueWithCyclingWrapsToTheMaxValue() {
        stubCurrentValue(1L);
        stubSequenceLookup(Collections.singletonList(newSequence(1, -1, 1, 100, true, 0)));
        stubUpdateCount(1);
        assertEquals(100L, sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1));
    }

    @Test
    void testTryToGetNextVal_reservesARangeWhenTheSequenceIsCachedAndTheClusterIsNotLocking() {
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 1, 9999999999L, false, 5)));
        stubUpdateCount(1);
        assertEquals(101L, sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1));
        verify(transaction).prepareAndExecute(eq(sqlFor("updateCurrentValueSql")), eq(105L), any(), eq(SEQUENCE_NAME), eq(100L));
    }

    @Test
    void testCurrVal_readsFromTheReservedRange() {
        reserveARangeOfFive();
        assertEquals(101L, sequenceService.currVal(transaction, SEQUENCE_NAME));
    }

    @Test
    void testNextValFromCache_walksTheReservedRange() {
        reserveARangeOfFive();
        assertEquals(102L, sequenceService.nextValFromCache(transaction, SEQUENCE_NAME));
        assertEquals(103L, sequenceService.nextValFromCache(transaction, SEQUENCE_NAME));
    }

    @Test
    void testNextVal_usesTheReservedRangeWhenTheClusterIsNotLocking() {
        reserveARangeOfFive();
        assertEquals(102L, sequenceService.nextVal(transaction, SEQUENCE_NAME));
    }

    @Test
    void testNextVal_goesToTheDatabaseWhenTheClusterIsLocking() {
        when(parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)).thenReturn(true);
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 1, 9999999999L, false, 5)));
        stubUpdateCount(1);
        assertEquals(101L, sequenceService.nextVal(transaction, SEQUENCE_NAME));
    }

    @Test
    void testNextRange_withANonPositiveSizeThrows() {
        cacheSequenceDefinition(newSequence(100, 1, 1, 9999999999L, false, 0));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> sequenceService.nextRange(transaction, SEQUENCE_NAME, 0));
        assertEquals("Size of range must be a positive integer", exception.getMessage());
    }

    @Test
    void testNextRange_withANonPositiveIncrementThrows() {
        cacheSequenceDefinition(newSequence(100, 0, 1, 9999999999L, false, 0));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> sequenceService.nextRange(transaction, SEQUENCE_NAME, 10));
        assertEquals("Increment-by must be a positive integer", exception.getMessage());
    }

    @Test
    void testNextRange_servesTheWholeRangeFromTheReservedRange() {
        reserveARangeOfFive();
        assertEquals(102L, sequenceService.nextRange(transaction, SEQUENCE_NAME, 2));
        assertEquals(103L, sequenceService.currVal(transaction, SEQUENCE_NAME));
    }

    @Test
    void testCreate_writesTheSequenceRow() {
        sequenceService.create(newSequence(100, 1, 1, 9999999999L, false, 0));
        verify(sqlTemplate).update(eq(sqlFor("insertSequenceSql")), any(Object[].class));
    }

    @Test
    void testGetAll_isKeyedBySequenceName() {
        when(sqlTemplate.query(anyString(), anySequenceMapper())).thenReturn(Arrays.asList(newSequence(1, 1, 1, 10, false, 0)));
        Map<String, Sequence> sequences = sequenceService.getAll();
        assertEquals(1, sequences.size());
        assertEquals(1L, sequences.get(SEQUENCE_NAME).getCurrentValue());
    }

    @Test
    void testInit_createsTheSequencesThatAreMissing() {
        when(sqlTemplate.query(anyString(), anySequenceMapper())).thenReturn(Collections.<Sequence> emptyList());
        when(sqlTemplate.queryForLong(anyString())).thenReturn(0L);
        sequenceService.init();
        verify(sqlTemplate, atLeast(4)).update(eq(sqlFor("insertSequenceSql")), any(Object[].class));
    }

    @Test
    void testInit_leavesConfiguredSequencesAlone() {
        when(sqlTemplate.query(anyString(), anySequenceMapper())).thenReturn(Arrays.asList(
                newNamedSequence(Constants.SEQUENCE_OUTGOING_BATCH_LOAD_ID),
                newNamedSequence(Constants.SEQUENCE_OUTGOING_BATCH),
                newNamedSequence(Constants.SEQUENCE_TRIGGER_HIST),
                newNamedSequence(Constants.SEQUENCE_EXTRACT_REQ),
                newNamedSequence(Constants.SEQUENCE_COMPARE_ID)));
        sequenceService.init();
        verify(sqlTemplate, times(0)).update(eq(sqlFor("insertSequenceSql")), any(Object[].class));
    }

    @Test
    void testNextValFromCache_fallsBackToTheDatabaseOnceTheRangeIsExhausted() {
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 1, 9999999999L, false, 1)));
        stubUpdateCount(1);
        sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1);
        assertFalse(sequenceService.nextValFromCache(transaction, SEQUENCE_NAME) == 0L);
    }

    private void reserveARangeOfFive() {
        stubCurrentValue(100L);
        stubSequenceLookup(Collections.singletonList(newSequence(100, 1, 1, 9999999999L, false, 5)));
        stubUpdateCount(1);
        sequenceService.tryToGetNextVal(transaction, SEQUENCE_NAME, 1);
    }

    private void cacheSequenceDefinition(Sequence sequence) {
        stubSequenceLookup(Collections.singletonList(sequence));
        sequenceService.getSequenceDefinition(transaction, SEQUENCE_NAME);
    }

    private ISqlRowMapper<Sequence> anySequenceMapper() {
        return any();
    }

    private void stubSequenceLookup(List<Sequence> sequences) {
        when(transaction.<Sequence> query(anyString(), any(), any(Object[].class), any(int[].class))).thenReturn(sequences);
    }

    private void stubCurrentValue(long currentValue) {
        when(transaction.queryForLong(sqlFor("getCurrentValueSql"), SEQUENCE_NAME)).thenReturn(currentValue);
    }

    private void stubUpdateCount(int updateCount) {
        when(transaction.prepareAndExecute(eq(sqlFor("updateCurrentValueSql")), any(Object[].class))).thenReturn(updateCount);
    }

    private Sequence newSequence(long currentValue, int incrementBy, long minValue, long maxValue, boolean cycle, int cacheSize) {
        return new Sequence(SEQUENCE_NAME, currentValue, incrementBy, minValue, maxValue, "system", cycle, cacheSize);
    }

    private Sequence newNamedSequence(String name) {
        return new Sequence(name, 1, 1, 1, 9999999999L, "system", false, 0);
    }

    private String sqlFor(String key) {
        return sequenceService.getSql(key);
    }
}
