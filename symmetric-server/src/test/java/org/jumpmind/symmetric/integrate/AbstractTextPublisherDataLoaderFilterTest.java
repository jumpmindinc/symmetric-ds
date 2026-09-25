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
package org.jumpmind.symmetric.integrate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.util.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractTextPublisherDataLoaderFilterTest {
    private static final String TABLE_NAME = "ITEM";
    private RecordingPublisher publisher;
    private TextFilter filter;
    private DataContext context;
    private Table table;

    @BeforeEach
    void setUp() {
        publisher = new RecordingPublisher();
        filter = new TextFilter();
        filter.setPublisher(publisher);
        filter.setTableName(TABLE_NAME);
        context = new DataContext();
        table = Table.buildTable(TABLE_NAME, new String[] { "ID" }, new String[] { "ID", "NAME" });
    }

    @Test
    void testBeforeWrite_returnsTrueByDefault() {
        assertTrue(filter.beforeWrite(context, table, insert()));
    }

    @Test
    void testBeforeWrite_returnsFalseWhenTargetLoadDisabled() {
        filter.setLoadDataInTargetDatabase(false);
        assertFalse(filter.beforeWrite(context, table, insert()));
    }

    @Test
    void testBeforeWrite_cachesElementForMatchingTable() {
        filter.beforeWrite(context, table, insert());
        assertTrue(filter.doesTextExistToPublish(context));
    }

    @Test
    void testBeforeWrite_ignoresOtherTables() {
        Table otherTable = Table.buildTable("OTHER", new String[] { "ID" }, new String[] { "ID" });
        filter.beforeWrite(context, otherTable, insert());
        assertFalse(filter.doesTextExistToPublish(context));
    }

    @Test
    void testBeforeWrite_ignoresNonDmlEvents() {
        filter.beforeWrite(context, table, new CsvData(DataEventType.SQL, new String[] { "delete from item" }));
        assertFalse(filter.doesTextExistToPublish(context));
    }

    @Test
    void testBeforeWrite_ignoresNullElement() {
        filter.suppressElement = true;
        filter.beforeWrite(context, table, insert());
        assertFalse(filter.doesTextExistToPublish(context));
    }

    @Test
    void testDoesTextExistToPublish_isFalseBeforeAnyWrite() {
        assertFalse(filter.doesTextExistToPublish(context));
    }

    @Test
    void testBatchComplete_publishesHeaderElementAndFooter() {
        filter.beforeWrite(context, table, insert());
        filter.batchComplete(context);
        assertEquals("[header][I:1,widget][footer]", publisher.lastText);
    }

    @Test
    void testBatchComplete_publishesEachCachedElement() {
        filter.beforeWrite(context, table, insert());
        filter.beforeWrite(context, table, new CsvData(DataEventType.UPDATE, new String[] { "2", "gadget" }));
        filter.batchComplete(context);
        assertEquals("[header][I:1,widget][U:2,gadget][footer]", publisher.lastText);
    }

    @Test
    void testBatchComplete_publishesNothingWhenNoDataWasCached() {
        filter.batchComplete(context);
        assertEquals(0, publisher.publishCount);
    }

    @Test
    void testBatchComplete_clearsCacheAfterPublishing() {
        filter.beforeWrite(context, table, insert());
        filter.batchComplete(context);
        filter.batchComplete(context);
        assertEquals(1, publisher.publishCount);
    }

    @Test
    void testSetNodeGroupIdToApplyTo() {
        filter.setNodeGroupIdToApplyTo("store");
        assertArrayEquals(new String[] { "store" }, filter.getNodeGroupIdsToApplyTo());
    }

    @Test
    void testGetNodeGroupIdsToApplyTo_isNullByDefault() {
        assertNull(filter.getNodeGroupIdsToApplyTo());
    }

    private CsvData insert() {
        return new CsvData(DataEventType.INSERT, new String[] { "1", "widget" });
    }

    private static class TextFilter extends AbstractTextPublisherDataLoaderFilter {
        private boolean suppressElement;

        @Override
        protected String addTextHeader(DataContext context) {
            return "[header]";
        }

        @Override
        protected String addTextElement(DataContext context, Table table, CsvData data) {
            if (suppressElement) {
                return null;
            }
            return "[" + data.getDataEventType().getCode() + ":" + String.join(",", data.getParsedData(CsvData.ROW_DATA)) + "]";
        }

        @Override
        protected String addTextFooter(DataContext context) {
            return "[footer]";
        }
    }

    private static class RecordingPublisher implements IPublisher {
        private String lastText;
        private int publishCount;

        @Override
        public void publish(Context context, String text) {
            lastText = text;
            publishCount++;
        }
    }
}
