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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.integrate.TemplatedPublisherDataLoaderFilter.IFormat;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplatedPublisherDataLoaderFilterTest {
    private static final String TABLE_NAME = "ITEM";
    private TemplatedPublisherDataLoaderFilter filter;
    private DataContext context;
    private Table table;

    @BeforeEach
    void setUp() {
        filter = new TemplatedPublisherDataLoaderFilter();
        filter.setContentTableTemplate("id=%ID% name=%NAME%");
        context = new DataContext();
        table = Table.buildTable(TABLE_NAME, new String[] { "ID" }, new String[] { "ID", "NAME" });
    }

    @Test
    void testAddTextHeader() {
        filter.setHeaderTableTemplate("<items>");
        assertEquals("<items>", filter.addTextHeader(context));
    }

    @Test
    void testAddTextFooter() {
        filter.setFooterTableTemplate("</items>");
        assertEquals("</items>", filter.addTextFooter(context));
    }

    @Test
    void testAddTextElement_withInsert() {
        assertEquals("id=1 name=widget", filter.addTextElement(context, table, insert()));
    }

    @Test
    void testAddTextElement_withUpdate() {
        CsvData data = new CsvData(DataEventType.UPDATE, new String[] { "2", "gadget" });
        assertEquals("id=2 name=gadget", filter.addTextElement(context, table, data));
    }

    @Test
    void testAddTextElement_withDeleteUsesPrimaryKeyData() {
        filter.setContentTableTemplate("deleted=%ID%");
        CsvData data = new CsvData(DataEventType.DELETE, new String[] { "3" }, null);
        assertEquals("deleted=3", filter.addTextElement(context, table, data));
    }

    @Test
    void testAddTextElement_returnsNullWhenInsertsNotProcessed() {
        filter.setProcessInsert(false);
        assertNull(filter.addTextElement(context, table, insert()));
    }

    @Test
    void testAddTextElement_returnsNullWhenUpdatesNotProcessed() {
        filter.setProcessUpdate(false);
        CsvData data = new CsvData(DataEventType.UPDATE, new String[] { "2", "gadget" });
        assertNull(filter.addTextElement(context, table, data));
    }

    @Test
    void testAddTextElement_returnsNullWhenDeletesNotProcessed() {
        filter.setProcessDelete(false);
        CsvData data = new CsvData(DataEventType.DELETE, new String[] { "3" }, null);
        assertNull(filter.addTextElement(context, table, data));
    }

    @Test
    void testAddTextElement_returnsNullWithoutContentTemplate() {
        filter.setContentTableTemplate(null);
        assertNull(filter.addTextElement(context, table, insert()));
    }

    @Test
    void testAddTextElement_returnsNullWhenDataFilterRejectsRow() {
        IDatabaseWriterFilter dataFilter = mock(IDatabaseWriterFilter.class);
        when(dataFilter.beforeWrite(any(), any(), any())).thenReturn(false);
        filter.setDataFilter(dataFilter);
        assertNull(filter.addTextElement(context, table, insert()));
    }

    @Test
    void testAddTextElement_appliesTemplateWhenDataFilterAcceptsRow() {
        IDatabaseWriterFilter dataFilter = mock(IDatabaseWriterFilter.class);
        when(dataFilter.beforeWrite(any(), any(), any())).thenReturn(true);
        filter.setDataFilter(dataFilter);
        assertEquals("id=1 name=widget", filter.addTextElement(context, table, insert()));
    }

    @Test
    void testFillOutTemplate_replacesDmlTypeToken() {
        filter.setContentTableTemplate("dml=DMLTYPE");
        assertEquals("dml=INSERT", filter.addTextElement(context, table, insert()));
    }

    @Test
    void testFillOutTemplate_replacesTimestampToken() {
        filter.setContentTableTemplate("ts=TIMESTAMP");
        String result = filter.addTextElement(context, table, insert());
        assertTrue(result.startsWith("ts="));
        assertTrue(Long.parseLong(result.substring(3)) > 0);
    }

    @Test
    void testReplace_withNullValueSubstitutesEmptyString() {
        assertEquals("id=", filter.replace("id=%ID%", "ID", null));
    }

    @Test
    void testReplace_withNullTemplateReturnsNull() {
        assertNull(filter.replace(null, "ID", "1"));
    }

    @Test
    void testReplace_leavesUnmatchedToken() {
        assertEquals("id=%ID%", filter.replace("id=%ID%", "NAME", "widget"));
    }

    @Test
    void testFormat_withoutFormatters() {
        assertEquals("widget", filter.format("NAME", "widget"));
    }

    @Test
    void testFormat_appliesRegisteredFormatter() {
        Map<String, IFormat> formatters = new HashMap<>();
        formatters.put("NAME", String::toUpperCase);
        filter.setColumnNameToDataFormatter(formatters);
        assertEquals("WIDGET", filter.format("NAME", "widget"));
    }

    @Test
    void testFormat_leavesUnregisteredColumnAlone() {
        Map<String, IFormat> formatters = new HashMap<>();
        formatters.put("NAME", String::toUpperCase);
        filter.setColumnNameToDataFormatter(formatters);
        assertEquals("1", filter.format("ID", "1"));
    }

    @Test
    void testFormat_wrapsParseExceptionInRuntimeException() {
        Map<String, IFormat> formatters = new HashMap<>();
        formatters.put("NAME", data -> {
            throw new ParseException(data, 0);
        });
        filter.setColumnNameToDataFormatter(formatters);
        assertThrows(RuntimeException.class, () -> filter.format("NAME", "widget"));
    }

    private CsvData insert() {
        return new CsvData(DataEventType.INSERT, new String[] { "1", "widget" });
    }
}
