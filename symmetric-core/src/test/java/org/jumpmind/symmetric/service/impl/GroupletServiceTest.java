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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.util.Date;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Grouplet;
import org.jumpmind.symmetric.model.Grouplet.GroupletLinkPolicy;
import org.jumpmind.symmetric.model.GroupletLink;
import org.jumpmind.symmetric.model.TriggerRouterGrouplet;
import org.jumpmind.symmetric.model.TriggerRouterGrouplet.AppliesWhen;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GroupletServiceTest {
    private static final String TRIGGER_ID = "item_trigger";
    private static final String ROUTER_ID = "corp_2_store";
    private IParameterService parameterService;
    private ISqlTemplate sqlTemplate;
    private GroupletService groupletService;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        groupletService = new GroupletService(newMockedEngine());
    }

    @Test
    void testSaveGroupletInsertsNewGroupletAfterUpdateWithoutCreateTime() {
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B);
        stubUpdateRowCounts(0, 1);
        groupletService.saveGrouplet(grouplet);
        assertSavedAsNewRow("insertGroupletSql", "updateGroupletSql", grouplet.getCreateTime());
    }

    @Test
    void testSaveGroupletUpdatesExistingGroupletWithoutTouchingCreateTime() {
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B);
        stubUpdateRowCounts(1);
        groupletService.saveGrouplet(grouplet);
        assertSavedAsExistingRow("insertGroupletSql", "updateGroupletSql");
        assertNull(grouplet.getCreateTime());
    }

    @Test
    void testSaveGroupletLinkInsertsNewLinkAfterUpdateWithoutCreateTime() {
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B, "store-001");
        GroupletLink link = grouplet.getGroupletLinks().get(0);
        stubUpdateRowCounts(0, 1);
        groupletService.saveGroupletLink(grouplet, link);
        assertSavedAsNewRow("insertGroupletLinkSql", "updateGroupletLinkSql", link.getCreateTime());
    }

    @Test
    void testSaveGroupletLinkUpdatesExistingLinkWithoutTouchingCreateTime() {
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B, "store-001");
        GroupletLink link = grouplet.getGroupletLinks().get(0);
        stubUpdateRowCounts(1);
        groupletService.saveGroupletLink(grouplet, link);
        assertSavedAsExistingRow("insertGroupletLinkSql", "updateGroupletLinkSql");
        assertNull(link.getCreateTime());
    }

    @Test
    void testSaveTriggerRouterGroupletInsertsNewRowAfterUpdateWithoutCreateTime() {
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B);
        TriggerRouterGrouplet triggerRouterGrouplet = grouplet.getTriggerRouterGrouplets().get(0);
        stubUpdateRowCounts(0, 1);
        groupletService.saveTriggerRouterGrouplet(grouplet, triggerRouterGrouplet);
        assertSavedAsNewRow("insertTriggerRouterGroupletSql", "updateTriggerRouterGroupletSql", triggerRouterGrouplet.getCreateTime());
    }

    @Test
    void testSaveTriggerRouterGroupletUpdatesExistingRowWithoutTouchingCreateTime() {
        Grouplet grouplet = newGrouplet(GroupletLinkPolicy.I, AppliesWhen.B);
        TriggerRouterGrouplet triggerRouterGrouplet = grouplet.getTriggerRouterGrouplets().get(0);
        stubUpdateRowCounts(1);
        groupletService.saveTriggerRouterGrouplet(grouplet, triggerRouterGrouplet);
        assertSavedAsExistingRow("insertTriggerRouterGroupletSql", "updateTriggerRouterGroupletSql");
        assertNull(triggerRouterGrouplet.getCreateTime());
    }

    private void stubUpdateRowCounts(Integer firstRowCount, Integer... nextRowCounts) {
        when(sqlTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(firstRowCount, nextRowCounts);
    }

    private void assertSavedAsNewRow(String insertSqlKey, String updateSqlKey, Date createTime) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        ArgumentCaptor<int[]> types = ArgumentCaptor.forClass(int[].class);
        verify(sqlTemplate, times(2)).update(sql.capture(), values.capture(), types.capture());
        String insertSql = groupletService.getSql(insertSqlKey);
        assertEquals(groupletService.getSql(updateSqlKey), sql.getAllValues().get(0));
        UpsertSqlAssertions.assertUpdateMatchesInsertWithoutCreateTime(insertSql, sql.getAllValues().get(0),
                values.getAllValues().get(0), types.getAllValues().get(0));
        assertEquals(insertSql, sql.getAllValues().get(1));
        assertNotNull(createTime);
        assertEquals(createTime, values.getAllValues().get(1)[UpsertSqlAssertions.getCreateTimeIndex(insertSql)]);
        assertEquals(Types.TIMESTAMP, types.getAllValues().get(1)[UpsertSqlAssertions.getCreateTimeIndex(insertSql)]);
    }

    private void assertSavedAsExistingRow(String insertSqlKey, String updateSqlKey) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        ArgumentCaptor<int[]> types = ArgumentCaptor.forClass(int[].class);
        verify(sqlTemplate, times(1)).update(sql.capture(), values.capture(), types.capture());
        assertEquals(groupletService.getSql(updateSqlKey), sql.getValue());
        UpsertSqlAssertions.assertUpdateMatchesInsertWithoutCreateTime(groupletService.getSql(insertSqlKey), sql.getValue(),
                values.getValue(), types.getValue());
    }

    private ISymmetricEngine newMockedEngine() {
        ISymmetricDialect symmetricDialect = newSymmetricDialect();
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        return engine;
    }

    private ISymmetricDialect newSymmetricDialect() {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(returnsFirstArg());
        return symmetricDialect;
    }

    private Grouplet newGrouplet(GroupletLinkPolicy policy, AppliesWhen appliesWhen, String... externalIds) {
        Grouplet grouplet = new Grouplet();
        grouplet.setGroupletId("east-stores");
        grouplet.setGroupletLinkPolicy(policy);
        for (String externalId : externalIds) {
            GroupletLink link = new GroupletLink();
            link.setExternalId(externalId);
            grouplet.getGroupletLinks().add(link);
        }
        TriggerRouterGrouplet triggerRouterGrouplet = new TriggerRouterGrouplet();
        triggerRouterGrouplet.setTriggerId(TRIGGER_ID);
        triggerRouterGrouplet.setRouterId(ROUTER_ID);
        triggerRouterGrouplet.setAppliesWhen(appliesWhen);
        grouplet.getTriggerRouterGrouplets().add(triggerRouterGrouplet);
        return grouplet;
    }
}
