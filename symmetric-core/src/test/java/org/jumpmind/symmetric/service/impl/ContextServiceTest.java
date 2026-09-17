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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextServiceTest {
    private ISqlTemplate sqlTemplate;
    private ISqlTransaction transaction;
    private ContextService contextService;

    @BeforeEach
    void setUp() {
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        ISqlTemplate sqlTemplateDirty = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(platform.scrubSql(anyString())).thenAnswer(returnsFirstArg());
        transaction = mock(ISqlTransaction.class);
        contextService = new ContextService(parameterService, symmetricDialect);
    }

    @Test
    void testIs_withTrueValue() {
        stubStoredValue("true");
        assertTrue(contextService.is("name"));
    }

    @Test
    void testIs_withNonBooleanValue() {
        stubStoredValue("yes");
        assertFalse(contextService.is("name"));
    }

    @Test
    void testIs_withMissingValue() {
        stubStoredValue(null);
        assertFalse(contextService.is("name"));
    }

    @Test
    void testGetInt() {
        stubStoredValue("42");
        assertEquals(42, contextService.getInt("name"));
    }

    @Test
    void testGetInt_withMissingValueThrows() {
        stubStoredValue(null);
        assertThrows(NumberFormatException.class, () -> contextService.getInt("name"));
    }

    @Test
    void testGetInt_withMissingValueReturnsDefault() {
        stubStoredValue(null);
        assertEquals(7, contextService.getInt("name", 7));
    }

    @Test
    void testGetInt_withValuePreferredOverDefault() {
        stubStoredValue("3");
        assertEquals(3, contextService.getInt("name", 7));
    }

    @Test
    void testGetLong_withMissingValueReturnsZero() {
        stubStoredValue(null);
        assertEquals(0L, contextService.getLong("name"));
    }

    @Test
    void testGetLong() {
        stubStoredValue("9000000000");
        assertEquals(9000000000L, contextService.getLong("name"));
    }

    @Test
    void testGetLong_withMissingValueReturnsDefault() {
        stubStoredValue(null);
        assertEquals(5L, contextService.getLong("name", 5L));
    }

    @Test
    void testGetString() {
        stubStoredValue("value");
        assertEquals("value", contextService.getString("name"));
        verify(sqlTemplate).queryForString(contextService.getSql("selectSql"), "name");
    }

    @Test
    void testInsert_writesThroughTheTransaction() {
        when(transaction.prepareAndExecute(eq(contextService.getSql("insertSql")), eq("name"), eq("value"), any(Date.class))).thenReturn(1);
        assertEquals(1, contextService.insert(transaction, "name", "value"));
    }

    @Test
    void testUpdate_writesThroughTheTransaction() {
        when(transaction.prepareAndExecute(eq(contextService.getSql("updateSql")), eq("value"), any(Date.class), eq("name"))).thenReturn(1);
        assertEquals(1, contextService.update(transaction, "name", "value"));
    }

    @Test
    void testDelete_writesThroughTheTransaction() {
        when(transaction.prepareAndExecute(contextService.getSql("deleteSql"), "name")).thenReturn(1);
        assertEquals(1, contextService.delete(transaction, "name"));
    }

    @Test
    void testDelete_withoutTransactionUsesTheTemplate() {
        when(sqlTemplate.update(contextService.getSql("deleteSql"), "name")).thenReturn(1);
        assertEquals(1, contextService.delete("name"));
    }

    @Test
    void testSave_withTransactionInsertsWhenNothingWasUpdated() {
        when(transaction.prepareAndExecute(eq(contextService.getSql("updateSql")), eq("value"), any(Date.class), eq("name"))).thenReturn(0);
        contextService.save(transaction, "name", "value");
        verify(transaction).prepareAndExecute(eq(contextService.getSql("insertSql")), eq("name"), eq("value"), any(Date.class));
    }

    @Test
    void testSave_withTransactionSkipsTheInsertWhenTheUpdateSucceeded() {
        when(transaction.prepareAndExecute(eq(contextService.getSql("updateSql")), eq("value"), any(Date.class), eq("name"))).thenReturn(1);
        contextService.save(transaction, "name", "value");
        verify(transaction, never()).prepareAndExecute(eq(contextService.getSql("insertSql")), eq("name"), eq("value"), any(Date.class));
    }

    @Test
    void testSave_withoutTransactionInsertsWhenNothingWasUpdated() {
        when(sqlTemplate.update(eq(contextService.getSql("updateSql")), eq("value"), any(Date.class), eq("name"))).thenReturn(0);
        contextService.save("name", "value");
        verify(sqlTemplate).update(eq(contextService.getSql("insertSql")), eq("name"), eq("value"), any(Date.class));
    }

    @Test
    void testGetSql_resolvesTheTablePrefix() {
        assertEquals("select context_value from sym_context where name = ?", contextService.getSql("selectSql"));
    }

    private void stubStoredValue(String value) {
        when(sqlTemplate.queryForString(contextService.getSql("selectSql"), "name")).thenReturn(value);
    }
}
