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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.jumpmind.symmetric.model.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TriggerRouterContextTest {
    private static final String TRIGGER_ID = "item_trigger";
    private TriggerRouterContext context;

    @BeforeEach
    void setUp() {
        context = new TriggerRouterContext();
    }

    @Test
    void testIncrementReadTableCount() {
        context.incrementReadTableCount(newTrigger());
        context.incrementReadTableCount(newTrigger());
        assertEquals(2, context.getTriggerReadTableFromDatabaseCount(TRIGGER_ID));
        assertEquals(Collections.singleton(TRIGGER_ID), context.getTriggerReadTableFromDatabaseKeyset());
    }

    @Test
    void testIncrementReadTableCount_withNullTriggerIsIgnored() {
        context.incrementReadTableCount(null);
        assertTrue(context.getTriggerReadTableFromDatabaseKeyset().isEmpty());
    }

    @Test
    void testGetTriggerReadTableFromDatabaseCount_withUnknownTrigger() {
        assertEquals(0, context.getTriggerReadTableFromDatabaseCount("nope"));
    }

    @Test
    void testIncrementCopyTableCount() {
        context.incrementCopyTableCount(newTrigger());
        assertEquals(1, context.getTriggerCopyTableCount(TRIGGER_ID));
        assertEquals(Collections.singleton(TRIGGER_ID), context.getTriggerCopyTableKeyset());
    }

    @Test
    void testIncrementCopyTableCount_withNullTriggerIsIgnored() {
        context.incrementCopyTableCount(null);
        assertTrue(context.getTriggerCopyTableKeyset().isEmpty());
    }

    @Test
    void testGetTriggerCopyTableCount_withUnknownTrigger() {
        assertEquals(0, context.getTriggerCopyTableCount("nope"));
    }

    @Test
    void testIncrementMultipleActiveTriggerRouterCount() {
        context.incrementMultipleActiveTriggerRouterCount(11);
        context.incrementMultipleActiveTriggerRouterCount(11);
        context.incrementMultipleActiveTriggerRouterCount(12);
        assertEquals(2, context.getMultipleActiveTriggerRouterCount(11));
        assertEquals(1, context.getMultipleActiveTriggerRouterCount(12));
        assertEquals(2, context.getMultipleActiveTriggerRouterKeyset().size());
    }

    @Test
    void testIncrementMultipleActiveTriggerRouterCount_withNullIdIsIgnored() {
        context.incrementMultipleActiveTriggerRouterCount(null);
        assertTrue(context.getMultipleActiveTriggerRouterKeyset().isEmpty());
    }

    @Test
    void testGetMultipleActiveTriggerRouterCount_withUnknownId() {
        assertEquals(0, context.getMultipleActiveTriggerRouterCount(99));
    }

    @Test
    void testIncrementTimers_accumulate() {
        context.incrementFixMultipleActiveTriggerHistoriesTime(5);
        context.incrementFixMultipleActiveTriggerHistoriesTime(6);
        context.incrementTriggersForCurrentNodeTime(7);
        context.incrementSyncTriggersStartedTime(8);
        context.incrementActiveTriggerHistoriesTime(9);
        context.incrementUpdateOrCreateDdlTriggersTime(10);
        context.incrementSyncTriggersEndedTime(11);
        assertEquals(11, context.getFixMultipleActiveTriggerHistoriesTime());
        assertEquals(7, context.getTriggersForCurrentNodeTime());
        assertEquals(8, context.getSyncTriggersStartedTime());
        assertEquals(9, context.getActiveTriggerHistoriesTime());
        assertEquals(10, context.getUpdateOrCreateDdlTriggersTime());
        assertEquals(11, context.getSyncTriggersEndedTime());
    }

    @Test
    void testIncrementRemainingTimers_accumulate() {
        context.incrementTablesForTriggerTime(1);
        context.incrementDropTriggerTime(2);
        context.incrementDoesTriggerExistTime(3);
        context.incrementInactivateTriggerHistTime(4);
        context.incrementTriggerToRelationSupportingInfoTime(5);
        context.incrementTableDoesNotExistTime(6);
        context.incrementUpdateOrCreateDatabaseTriggersTime(7);
        context.incrementTriggerInactivatedTime(8);
        assertEquals(1, context.getTablesForTriggerTime());
        assertEquals(2, context.getDropTriggerTime());
        assertEquals(3, context.getDoesTriggerExistTime());
        assertEquals(4, context.getInactivateTriggerHistTime());
        assertEquals(5, context.getTriggerToRelationSupportingInfoTime());
        assertEquals(6, context.getTableDoesNotExistTime());
        assertEquals(7, context.getUpdateOrCreateDatabaseTriggersTime());
        assertEquals(8, context.getTriggerInactivatedTime());
    }

    @Test
    void testIncrementTriggerCounts() {
        context.incrementTriggersToSyncCount(4);
        context.incrementTriggersToSyncCount(1);
        context.incrementTriggersSyncedCount(3);
        assertEquals(5, context.getTriggersToSyncCount());
        assertEquals(3, context.getTriggersSyncedCount());
        assertEquals(0, context.getTriggersFailedCount());
        assertEquals(0, context.getTablesNotFoundCount());
    }

    @Test
    void testAddTablesNotFound() {
        context.addTablesNotFound("ITEM");
        context.addTablesNotFound("ITEM_SELLING_PRICE");
        assertEquals(2, context.getTablesNotFoundCount());
        assertEquals("2 tables were not found: ITEM, ITEM_SELLING_PRICE", context.getErrorMessage());
    }

    @Test
    void testAddTriggersFailed_keepsTheFirstException() {
        Exception first = new IllegalStateException("first");
        context.addTriggersFailed("ITEM", first);
        context.addTriggersFailed("SALE", new IllegalStateException("second"));
        assertEquals(2, context.getTriggersFailedCount());
        assertEquals("2 table triggers failed: ITEM, SALE\nIllegalStateException: first", context.getErrorMessage());
    }

    @Test
    void testAddTriggersFailed_withExceptionWithoutAMessage() {
        context.addTriggersFailed("ITEM", new IllegalStateException());
        assertEquals("1 table triggers failed: ITEM\nIllegalStateException", context.getErrorMessage());
    }

    @Test
    void testAddTriggersFailed_withNullException() {
        context.addTriggersFailed("ITEM", null);
        assertEquals("1 table triggers failed: ITEM", context.getErrorMessage());
    }

    @Test
    void testGetErrorMessage_withNoFailures() {
        assertEquals("", context.getErrorMessage());
    }

    @Test
    void testGetErrorMessage_withBothKindsOfFailure() {
        context.addTablesNotFound("ITEM");
        context.addTriggersFailed("SALE", null);
        assertEquals("1 tables were not found: ITEM\n1 table triggers failed: SALE", context.getErrorMessage());
    }

    @Test
    void testAbbreviateMessage_withNoExistingMessage() {
        assertEquals("ITEM", context.abbreviateMessage(null, "ITEM"));
    }

    @Test
    void testAbbreviateMessage_withShortExistingMessage() {
        assertEquals("ITEM, SALE", context.abbreviateMessage("ITEM", "SALE"));
    }

    @Test
    void testAbbreviateMessage_withMessageAtTheLengthLimit() {
        String existing = "x".repeat(500);
        assertEquals(existing + "...", context.abbreviateMessage(existing, "SALE"));
    }

    @Test
    void testAbbreviateMessage_withAlreadyAbbreviatedMessage() {
        String existing = "x".repeat(500) + "...";
        assertEquals(existing, context.abbreviateMessage(existing, "SALE"));
    }

    private Trigger newTrigger() {
        Trigger trigger = new Trigger();
        trigger.setTriggerId(TRIGGER_ID);
        return trigger;
    }
}
