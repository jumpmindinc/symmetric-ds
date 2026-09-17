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
package org.jumpmind.symmetric.extract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.model.TriggerHistory;
import org.junit.jupiter.api.Test;

class SelectFromTableOptionsTest {
    @Test
    void testNew_leavesDefaults() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        assertNull(options.getTriggerHistory());
        assertNull(options.getInitialLoadSql());
        assertEquals(0, options.getExpectedCommaCount());
        assertFalse(options.isSelectedAsCsv());
        assertFalse(options.isObjectValuesWillNeedEscaped());
        assertNull(options.isColumnPositionUsingTemplate());
        assertFalse(options.isCheckRowLength());
        assertEquals(0, options.getRowMaxLength());
        assertFalse(options.isReturnLobObjects());
        assertEquals(0, options.getMaxBatchSize());
    }

    @Test
    void testTriggerHistory_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableOptions result = options.triggerHistory(triggerHistory);
        assertSame(options, result);
        assertSame(triggerHistory, options.getTriggerHistory());
    }

    @Test
    void testInitialLoadSql_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        SelectFromTableOptions result = options.initialLoadSql("select * from test_table");
        assertSame(options, result);
        assertEquals("select * from test_table", options.getInitialLoadSql());
    }

    @Test
    void testExpectedCommaCount_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        SelectFromTableOptions result = options.expectedCommaCount(5);
        assertSame(options, result);
        assertEquals(5, options.getExpectedCommaCount());
    }

    @Test
    void testSelectedAsCsv_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        SelectFromTableOptions result = options.selectedAsCsv(true);
        assertSame(options, result);
        assertTrue(options.isSelectedAsCsv());
    }

    @Test
    void testObjectValuesWillNeedEscaped_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        SelectFromTableOptions result = options.objectValuesWillNeedEscaped(true);
        assertSame(options, result);
        assertTrue(options.isObjectValuesWillNeedEscaped());
    }

    @Test
    void testColumnPositionUsingTemplate_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        boolean[] template = new boolean[] { true, false, true };
        SelectFromTableOptions result = options.columnPositionUsingTemplate(template);
        assertSame(options, result);
        assertArrayEquals(template, options.isColumnPositionUsingTemplate());
    }

    @Test
    void testCheckRowLength_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        SelectFromTableOptions result = options.checkRowLength(true);
        assertSame(options, result);
        assertTrue(options.isCheckRowLength());
    }

    @Test
    void testRowMaxLength_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        SelectFromTableOptions result = options.rowMaxLength(1000000000L);
        assertSame(options, result);
        assertEquals(1000000000L, options.getRowMaxLength());
    }

    @Test
    void testReturnLobObjects_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        SelectFromTableOptions result = options.returnLobObjects(true);
        assertSame(options, result);
        assertTrue(options.isReturnLobObjects());
    }

    @Test
    void testMaxBatchSize_setsFieldAndReturnsSameInstance() {
        SelectFromTableOptions options = new SelectFromTableOptions();
        SelectFromTableOptions result = options.maxBatchSize(500);
        assertSame(options, result);
        assertEquals(500, options.getMaxBatchSize());
    }
}
