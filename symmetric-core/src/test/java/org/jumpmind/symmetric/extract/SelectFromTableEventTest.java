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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.junit.jupiter.api.Test;

class SelectFromTableEventTest {
    @Test
    void testNew_withNodeConstructor_setsFieldsAndLeavesDataNull() {
        Node node = new Node("target", "client");
        TriggerRouter triggerRouter = new TriggerRouter();
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        SelectFromTableEvent event = new SelectFromTableEvent(node, triggerRouter, triggerHistory, "1=1");
        assertSame(node, event.getNode());
        assertSame(triggerRouter, event.getTriggerRouter());
        assertSame(triggerHistory, event.getTriggerHistory());
        assertEquals("1=1", event.getInitialLoadSelect());
        assertNull(event.getData());
    }

    @Test
    void testNew_withDataConstructor_setsFieldsFromDataAndLeavesNodeAndSelectNull() {
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Data data = new Data("test_table", null, "", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        TriggerRouter triggerRouter = new TriggerRouter();
        SelectFromTableEvent event = new SelectFromTableEvent(data, triggerRouter);
        assertSame(data, event.getData());
        assertSame(triggerRouter, event.getTriggerRouter());
        assertSame(triggerHistory, event.getTriggerHistory());
        assertNull(event.getNode());
        assertNull(event.getInitialLoadSelect());
    }

    @Test
    void testGetTriggerHistory_withDataConstructor_returnsHistoryFromData() {
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Data data = new Data("test_table", null, "", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        SelectFromTableEvent event = new SelectFromTableEvent(data, new TriggerRouter());
        assertSame(triggerHistory, event.getTriggerHistory());
    }

    @Test
    void testGetTriggerRouter() {
        TriggerRouter triggerRouter = new TriggerRouter();
        SelectFromTableEvent event = new SelectFromTableEvent(new Node("target", "client"), triggerRouter,
                new TriggerHistory("test_table", "id", "id,name"), null);
        assertSame(triggerRouter, event.getTriggerRouter());
    }

    @Test
    void testGetData_withNodeConstructor_returnsNull() {
        SelectFromTableEvent event = new SelectFromTableEvent(new Node("target", "client"), new TriggerRouter(),
                new TriggerHistory("test_table", "id", "id,name"), null);
        assertNull(event.getData());
    }

    @Test
    void testGetNode_withDataConstructor_returnsNull() {
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Data data = new Data("test_table", null, "", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        SelectFromTableEvent event = new SelectFromTableEvent(data, new TriggerRouter());
        assertNull(event.getNode());
    }

    @Test
    void testContainsData_withNodeConstructor_returnsFalse() {
        SelectFromTableEvent event = new SelectFromTableEvent(new Node("target", "client"), new TriggerRouter(),
                new TriggerHistory("test_table", "id", "id,name"), null);
        assertFalse(event.containsData());
    }

    @Test
    void testContainsData_withDataConstructor_returnsTrue() {
        TriggerHistory triggerHistory = new TriggerHistory("test_table", "id", "id,name");
        Data data = new Data("test_table", null, "", null, triggerHistory, Constants.CHANNEL_DEFAULT, null, null);
        SelectFromTableEvent event = new SelectFromTableEvent(data, new TriggerRouter());
        assertTrue(event.containsData());
    }

    @Test
    void testGetInitialLoadSelect() {
        SelectFromTableEvent event = new SelectFromTableEvent(new Node("target", "client"), new TriggerRouter(),
                new TriggerHistory("test_table", "id", "id,name"), "where 1=1");
        assertEquals("where 1=1", event.getInitialLoadSelect());
    }
}
