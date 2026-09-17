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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.EOFException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.FilterCriterion;
import org.jumpmind.symmetric.service.FilterCriterion.FilterOption;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractServiceTest {
    private static final Date EARLIER = Timestamp.valueOf("2023-11-14 17:13:20");
    private static final Date LATER = Timestamp.valueOf("2024-01-02 03:04:05");
    private ISymmetricDialect symmetricDialect;
    private ISymmetricDialect targetDialect;
    private IDatabasePlatform platform;
    private IDatabasePlatform targetPlatform;
    private TestService service;

    @BeforeEach
    void setUp() {
        platform = mock(IDatabasePlatform.class);
        targetPlatform = mock(IDatabasePlatform.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        service = new TestService(parameterService, newSymmetricDialect());
    }

    @Test
    void testMaxDate_picksTheLatest() {
        assertEquals(LATER, service.maxDate(EARLIER, LATER, null));
    }

    @Test
    void testMaxDate_withOnlyNulls() {
        assertNull(service.maxDate(null, null));
    }

    @Test
    void testMaxDate_withNullArray() {
        assertNull(service.maxDate((Date[]) null));
    }

    @Test
    void testIsSet_withOne() {
        assertTrue(service.isSet("1"));
        assertTrue(service.isSet(Integer.valueOf(1)));
    }

    @Test
    void testIsSet_withOtherValues() {
        assertFalse(service.isSet("0"));
        assertFalse(service.isSet(null));
        assertFalse(service.isSet("true"));
    }

    @Test
    void testToNodeIds() {
        Set<Node> nodes = new HashSet<Node>(Arrays.asList(new Node("store-001", "store"), new Node("store-002", "store")));
        assertEquals(new HashSet<String>(Arrays.asList("store-001", "store-002")), service.toNodeIds(nodes));
    }

    @Test
    void testToNodeIds_addsToAnExistingSet() {
        Set<String> nodeIds = new HashSet<String>(Collections.singletonList("corp-000"));
        service.toNodeIds(Collections.singleton(new Node("store-001", "store")), nodeIds);
        assertEquals(new HashSet<String>(Arrays.asList("corp-000", "store-001")), nodeIds);
    }

    @Test
    void testGetTablePrefix() {
        assertEquals("sym", service.getTablePrefix());
    }

    @Test
    void testGetSql_withoutASqlMap() {
        assertNull(service.getSql("selectSql"));
    }

    @Test
    void testGetParameterServiceAndDialect() {
        assertSame(symmetricDialect, service.getSymmetricDialect());
        assertSame(targetDialect, service.getTargetDialect());
    }

    @Test
    void testGetJdbcTemplate() {
        assertSame(platform.getSqlTemplate(), service.getJdbcTemplate());
    }

    @Test
    void testIsSymmetricTable() {
        assertTrue(service.isSymmetricTable("SYM_NODE"));
        assertTrue(service.isSymmetricTable("sym_node"));
        assertFalse(service.isSymmetricTable("ITEM"));
    }

    @Test
    void testGetTargetPlatform_forASymmetricTable() {
        assertSame(platform, service.getTargetPlatform("sym_node"));
    }

    @Test
    void testGetTargetPlatform_forAnApplicationTable() {
        assertSame(targetPlatform, service.getTargetPlatform("ITEM"));
        assertSame(targetPlatform, service.getTargetPlatform());
    }

    @Test
    void testSynchronize_runsTheTask() {
        StringBuilder ran = new StringBuilder();
        service.synchronize(() -> ran.append("ran"));
        assertEquals("ran", ran.toString());
    }

    @Test
    void testClose_withNullTransactionIsHarmless() {
        assertDoesNotThrow(() -> service.close(null));
    }

    @Test
    void testIsStreamClosedByClient_withAWrappedEofException() {
        assertTrue(service.isStreamClosedByClient(new IOException(new EOFException())));
    }

    @Test
    void testIsStreamClosedByClient_withAnyOtherException() {
        assertFalse(service.isStreamClosedByClient(new IOException("broken pipe")));
    }

    @Test
    void testAssertNotNull_withAValue() {
        assertDoesNotThrow(() -> service.assertNotNull("value", "should not be reported"));
    }

    @Test
    void testAssertNotNull_withNullThrows() {
        SymmetricException exception = assertThrows(SymmetricException.class, () -> service.assertNotNull(null, "missing sym_node row"));
        assertEquals("missing sym_node row", exception.getMessage());
    }

    @Test
    void testBuildBatchWhere_withNothingToFilterOn() {
        assertEquals("", service.buildBatchWhere(null, null, Collections.emptyList(), null, null));
    }

    @Test
    void testBuildBatchWhere_withNodeIds() {
        assertEquals(" where node_id in (:NODES)",
                service.buildBatchWhere(Collections.singletonList("store-001"), null, Collections.emptyList(), null, null));
    }

    @Test
    void testBuildBatchWhere_withChannels() {
        assertEquals(" where channel_id in (:CHANNELS)",
                service.buildBatchWhere(null, Collections.singletonList("default"), Collections.emptyList(), null, null));
    }

    @Test
    void testBuildBatchWhere_withLoads() {
        assertEquals(" where load_id in (:LOADS)",
                service.buildBatchWhere(null, null, Collections.emptyList(), Collections.singletonList(1L), null));
    }

    @Test
    void testBuildBatchWhere_withErrorStatusAlsoMatchesTheErrorFlag() {
        assertEquals(" where (status in (:STATUSES) or error_flag = 1 )",
                service.buildBatchWhere(null, null, Collections.singletonList(Status.ER), null, null));
    }

    @Test
    void testBuildBatchWhere_withIgnoredStatusAlsoMatchesTheIgnoreCount() {
        assertEquals(" where (status in (:STATUSES) or ignore_count > 0 )",
                service.buildBatchWhere(null, null, Collections.singletonList(Status.IG), null, null));
    }

    @Test
    void testBuildBatchWhere_withLastUpdatedTime() {
        assertEquals(" where last_update_time >= :LAST_UPDATE_TIME",
                service.buildBatchWhere(null, null, Collections.emptyList(), null, LATER));
    }

    @Test
    void testBuildBatchWhere_withEveryCriterionJoinedByAnd() {
        assertEquals(" where node_id in (:NODES) and channel_id in (:CHANNELS) and load_id in (:LOADS) "
                + "and (status in (:STATUSES)) and last_update_time >= :LAST_UPDATE_TIME",
                service.buildBatchWhere(Collections.singletonList("store-001"), Collections.singletonList("default"),
                        Collections.singletonList(Status.OK), Collections.singletonList(1L), LATER));
    }

    @Test
    void testBuildBatchWhereFromFilter_withNoCriteria() {
        assertEquals("", service.buildBatchWhereFromFilter(Collections.emptyList()));
    }

    @Test
    void testBuildBatchWhereFromFilter_withEachSupportedProperty() {
        assertEquals(" where node_id = :0", whereFor(new FilterCriterion("nodeId", "store-001", FilterOption.EQUALS)));
        assertEquals(" where batch_id = :0", whereFor(new FilterCriterion("batchId", 1L, FilterOption.EQUALS)));
        assertEquals(" where channel_id = :0", whereFor(new FilterCriterion("channelId", "default", FilterOption.EQUALS)));
        assertEquals(" where create_time = :0", whereFor(new FilterCriterion("createTime", LATER, FilterOption.EQUALS)));
        assertEquals(" where load_id = :0", whereFor(new FilterCriterion("loadId", 1L, FilterOption.EQUALS)));
        assertEquals(" where last_update_time = :0", whereFor(new FilterCriterion("lastUpdatedTime", LATER, FilterOption.EQUALS)));
    }

    @Test
    void testBuildBatchWhereFromFilter_withAnUnknownPropertyIsSkipped() {
        assertEquals("", whereFor(new FilterCriterion("somethingElse", "value", FilterOption.EQUALS)));
    }

    @Test
    void testBuildBatchWhereFromFilter_withAnErrorStatusAlsoMatchesTheErrorFlag() {
        assertEquals(" where (error_flag = 1 or status = :0)", whereFor(new FilterCriterion("status", Status.ER.toString(), FilterOption.EQUALS)));
    }

    @Test
    void testBuildBatchWhereFromFilter_withANonErrorStatus() {
        assertEquals(" where status = :0", whereFor(new FilterCriterion("status", Status.OK.toString(), FilterOption.EQUALS)));
    }

    @Test
    void testBuildBatchWhereFromFilter_withAListOption() {
        assertEquals(" where node_id in (:0)",
                whereFor(new FilterCriterion("nodeId", Arrays.<Object> asList("store-001", "store-002"), FilterOption.IN_LIST)));
    }

    @Test
    void testBuildBatchWhereFromFilter_withABetweenOption() {
        assertEquals(" where create_time between :0 and :1",
                whereFor(new FilterCriterion("createTime", Arrays.<Object> asList(EARLIER, LATER), FilterOption.BETWEEN)));
    }

    @Test
    void testBuildBatchWhereFromFilter_withTheIgnoredPseudoProperty() {
        assertEquals(" where (status = 'IG' or (status = 'OK' and ignore_count > 0))",
                whereFor(new FilterCriterion("ignored", Boolean.TRUE, FilterOption.EQUALS)));
    }

    @Test
    void testBuildBatchWhereFromFilter_withTwoCriteriaJoinedByAnd() {
        List<FilterCriterion> filter = Arrays.asList(new FilterCriterion("nodeId", "store-001", FilterOption.EQUALS),
                new FilterCriterion("channelId", "default", FilterOption.NOT_EQUALS));
        assertEquals(" where node_id = :0 and channel_id <> :1", service.buildBatchWhereFromFilter(filter));
    }

    @Test
    void testBuildBatchParams_withNoCriteria() {
        assertTrue(service.buildBatchParams(Collections.emptyList()).isEmpty());
    }

    @Test
    void testBuildBatchParams_translatesStatusDescriptionsToNames() {
        Map<String, Object> params = paramsFor(new FilterCriterion("status", Status.ER.toString(), FilterOption.EQUALS));
        assertEquals(Collections.singletonList("ER"), params.get("0"));
    }

    @Test
    void testBuildBatchParams_convertsDatesToTimestamps() {
        Map<String, Object> params = paramsFor(new FilterCriterion("createTime", LATER, FilterOption.EQUALS));
        assertEquals(new Timestamp(LATER.getTime()), params.get("0"));
    }

    @Test
    void testBuildBatchParams_withABetweenOptionOnDates() {
        Map<String, Object> params = paramsFor(new FilterCriterion("lastUpdatedTime", Arrays.<Object> asList(EARLIER, LATER), FilterOption.BETWEEN));
        assertEquals(new Timestamp(EARLIER.getTime()), params.get("0"));
        assertEquals(new Timestamp(LATER.getTime()), params.get("1"));
    }

    @Test
    void testBuildBatchParams_withAListOptionOnDatesKeepsTheRawValues() {
        List<Object> values = Arrays.<Object> asList(EARLIER, LATER);
        assertEquals(values, paramsFor(new FilterCriterion("createTime", values, FilterOption.IN_LIST)).get("0"));
    }

    @Test
    void testBuildBatchParams_wrapsContainsValuesInWildcards() {
        assertEquals("%store%", paramsFor(new FilterCriterion("nodeId", "store", FilterOption.CONTAINS)).get("0"));
    }

    @Test
    void testBuildBatchParams_withASimpleValue() {
        assertEquals("store-001", paramsFor(new FilterCriterion("nodeId", "store-001", FilterOption.EQUALS)).get("0"));
    }

    @Test
    void testBuildBatchParams_withABetweenOptionOnBatchIds() {
        Map<String, Object> params = paramsFor(new FilterCriterion("batchId", Arrays.<Object> asList(1L, 9L), FilterOption.BETWEEN));
        assertEquals(1L, params.get("0"));
        assertEquals(9L, params.get("1"));
    }

    @Test
    void testBuildBatchParams_withTheIgnoredPseudoPropertyContributesNothing() {
        assertTrue(paramsFor(new FilterCriterion("ignored", Boolean.TRUE, FilterOption.EQUALS)).isEmpty());
    }

    @Test
    void testBuildBatchOrderBy_withoutAColumn() {
        assertEquals(" order by create_time desc", service.buildBatchOrderBy(null, "ASCENDING"));
    }

    @Test
    void testBuildBatchOrderBy_convertsCamelCaseToUnderscores() {
        assertEquals(" order by channel_id", service.buildBatchOrderBy("channelId", "ASCENDING"));
    }

    @Test
    void testBuildBatchOrderBy_withTheSpeciallyMappedColumns() {
        assertEquals(" order by last_update_time", service.buildBatchOrderBy("lastUpdatedTime", "ASCENDING"));
        assertEquals(" order by last_update_hostname", service.buildBatchOrderBy("lastUpdatedHostName", "ASCENDING"));
    }

    @Test
    void testBuildBatchOrderBy_withADescendingDirection() {
        assertEquals(" order by batch_id desc", service.buildBatchOrderBy("batchId", "DESCENDING"));
    }

    private ISymmetricDialect newSymmetricDialect() {
        symmetricDialect = mock(ISymmetricDialect.class);
        targetDialect = mock(ISymmetricDialect.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getTargetDialect()).thenReturn(targetDialect);
        when(targetDialect.getPlatform()).thenReturn(targetPlatform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        return symmetricDialect;
    }

    private String whereFor(FilterCriterion criterion) {
        return service.buildBatchWhereFromFilter(Collections.singletonList(criterion));
    }

    private Map<String, Object> paramsFor(FilterCriterion criterion) {
        return service.buildBatchParams(Collections.singletonList(criterion));
    }

    private static class TestService extends AbstractService {
        TestService(IParameterService parameterService, ISymmetricDialect symmetricDialect) {
            super(parameterService, symmetricDialect);
        }
    }
}
