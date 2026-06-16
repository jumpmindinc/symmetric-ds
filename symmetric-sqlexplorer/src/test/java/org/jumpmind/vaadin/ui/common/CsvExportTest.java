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
package org.jumpmind.vaadin.ui.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvExportTest {
    @SuppressWarnings("unchecked")
    private IDataProvider<String> twoColumnProvider(List<String> rows) {
        IDataProvider<String> provider = mock(IDataProvider.class);
        doReturn(List.of("col1", "col2")).when(provider).getColumns();
        when(provider.getKeyValue("col1")).thenReturn("Name");
        when(provider.getKeyValue("col2")).thenReturn("Value");
        when(provider.getRowItems()).thenReturn(rows);
        when(provider.getCellValue(anyString(), eq("col1"))).thenAnswer(inv -> inv.getArgument(0) + "-name");
        when(provider.getCellValue(anyString(), eq("col2"))).thenAnswer(inv -> inv.getArgument(0) + "-val");
        return provider;
    }

    @Test
    void init_withNullFileName_usesDefault() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()), null);
        assertEquals("GridExport.csv", export.fileName);
    }

    @Test
    void init_withNonCsvExtension_usesDefault() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()), "output.txt");
        assertEquals("GridExport.csv", export.fileName);
    }

    @Test
    void init_withCsvExtension_usesGivenName() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()), "myfile.csv");
        assertEquals("myfile.csv", export.fileName);
    }

    @Test
    void init_withNullTitle_titleIsEmpty() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()), null, null);
        assertEquals("", export.title);
    }

    @Test
    void addTitle_withTitle_appendsTitleRow() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()), null, "My Report");
        export.cellData = new StringBuilder();
        export.addTitle();
        assertTrue(export.cellData.toString().startsWith("My Report,"));
    }

    @Test
    void addTitle_withBlankTitle_doesNotAppend() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()), null, "");
        export.cellData = new StringBuilder();
        export.addTitle();
        assertEquals("", export.cellData.toString());
    }

    @Test
    void addHeaders_writesColumnKeysAsFirstRow() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()));
        export.cellData = new StringBuilder();
        export.addHeaders();
        assertEquals("Name,Value\n", export.cellData.toString());
    }

    @Test
    void convertToCsv_emptyGrid_onlyHeaderRow() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()));
        export.convertToCsv();
        assertEquals("Name,Value\n", export.cellData.toString());
    }

    @Test
    void convertToCsv_withOneRow_includesRowData() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(List.of("row1")));
        export.convertToCsv();
        String csv = export.cellData.toString();
        assertTrue(csv.contains("\"row1-name\""));
        assertTrue(csv.contains("\"row1-val\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertToCsv_withEmbeddedQuotes_doublesThem() {
        IDataProvider<String> provider = mock(IDataProvider.class);
        doReturn(List.of("col1")).when(provider).getColumns();
        when(provider.getKeyValue("col1")).thenReturn("Text");
        when(provider.getRowItems()).thenReturn(List.of("item"));
        when(provider.getCellValue("item", "col1")).thenReturn("say \"hello\"");
        CsvExport<String> export = new CsvExport<>(provider);
        export.convertToCsv();
        assertTrue(export.cellData.toString().contains("\"say \"\"hello\"\"\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertToCsv_withNullCellValue_producesEmptyQuotedField() {
        IDataProvider<String> provider = mock(IDataProvider.class);
        doReturn(List.of("col1")).when(provider).getColumns();
        when(provider.getKeyValue("col1")).thenReturn("Text");
        when(provider.getRowItems()).thenReturn(List.of("item"));
        when(provider.getCellValue("item", "col1")).thenReturn(null);
        CsvExport<String> export = new CsvExport<>(provider);
        export.convertToCsv();
        assertEquals("Text\n\n", export.cellData.toString());
    }

    @Test
    void convertToCsv_multipleRows_eachOnSeparateLine() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(List.of("a", "b", "c")));
        export.convertToCsv();
        String csv = export.cellData.toString();
        assertEquals(4, csv.split("\n").length);
    }

    @Test
    void convertToCsv_withTitle_titleAppearsFirst() {
        CsvExport<String> export = new CsvExport<>(twoColumnProvider(Collections.emptyList()), null, "Report");
        export.convertToCsv();
        assertTrue(export.cellData.toString().startsWith("Report,"));
    }
}
