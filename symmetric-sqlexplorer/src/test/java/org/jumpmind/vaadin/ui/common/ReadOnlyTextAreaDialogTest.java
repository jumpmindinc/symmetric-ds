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

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;

@ViewPackages(classes = { ReadOnlyTextAreaDialog.class })
class ReadOnlyTextAreaDialogTest extends BrowserlessTest {
    private ReadOnlyTextAreaDialog createSimple(String value, boolean isEncodedInHex) {
        ReadOnlyTextAreaDialog dialog = new ReadOnlyTextAreaDialog("title", value, isEncodedInHex);
        UI.getCurrent().getElement().insertChild(0, dialog.getElement());
        dialog.open();
        return dialog;
    }

    private IDatabasePlatform buildPlatform() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class, RETURNS_DEEP_STUBS);
        when(platform.getDdlBuilder().isDelimitedIdentifierModeOn()).thenReturn(false);
        when(platform.getDatabaseInfo().getCatalogSeparator()).thenReturn(".");
        when(platform.getDatabaseInfo().getSchemaSeparator()).thenReturn(".");
        return platform;
    }

    @Test
    void constructor_simple_doesNotThrow() {
        assertDoesNotThrow(() -> createSimple("hello", false));
    }

    @Test
    void constructor_simple_textFieldContainsValue() {
        ReadOnlyTextAreaDialog dialog = createSimple("hello", false);
        assertEquals("hello", dialog.textField.getValue());
    }

    @Test
    void constructor_notHexEncoded_displayBoxIsNull() {
        ReadOnlyTextAreaDialog dialog = createSimple("hello", false);
        assertNull(dialog.displayBox);
    }

    @Test
    void constructor_hexEncoded_displayBoxIsPresent() {
        ReadOnlyTextAreaDialog dialog = createSimple("deadbeef", true);
        assertNotNull(dialog.displayBox);
    }

    @Test
    void constructor_hexEncoded_displayBoxDefaultsToHex() {
        ReadOnlyTextAreaDialog dialog = createSimple("deadbeef", true);
        assertEquals("Hex", dialog.displayBox.getValue());
    }

    @Test
    void constructor_hexEncoded_displayBoxHasThreeOptions() {
        ReadOnlyTextAreaDialog dialog = createSimple("deadbeef", true);
        assertEquals(3, dialog.displayBox.getListDataView().getItemCount());
    }

    @Test
    void displayBox_switchToHex_showsRawValue() {
        ReadOnlyTextAreaDialog dialog = createSimple("deadbeef", true);
        dialog.displayBox.setValue("Text");
        dialog.displayBox.setValue("Hex");
        assertEquals("deadbeef", dialog.textField.getValue());
    }

    @Test
    void displayBox_switchToText_decodesHexToString() {
        // 68656c6c6f = "hello" in ASCII hex
        ReadOnlyTextAreaDialog dialog = createSimple("68656c6c6f", true);
        dialog.displayBox.setValue("Text");
        assertEquals("hello", dialog.textField.getValue());
    }

    @Test
    void displayBox_switchToDecimal_producesSpaceSeparatedDecimals() {
        // deadbeef = 222 173 190 239
        ReadOnlyTextAreaDialog dialog = createSimple("deadbeef", true);
        dialog.displayBox.setValue("Decimal");
        assertEquals("222 173 190 239", dialog.textField.getValue());
    }

    @Test
    void displayBox_switchToText_invalidHex_doesNotThrow() {
        ReadOnlyTextAreaDialog dialog = createSimple("not-valid-hex", true);
        assertDoesNotThrow(() -> dialog.displayBox.setValue("Text"));
    }

    @Test
    void constructor_closeButtonPresentInFooter() {
        createSimple("hello", false);
        assertDoesNotThrow(() -> find(Button.class).all().stream()
                .filter(b -> "Close".equals(b.getText()))
                .findFirst().orElseThrow());
    }

    @Test
    void buildLobSelect_withSinglePkColumn_producesCorrectSql() {
        IDatabasePlatform platform = buildPlatform();
        Table table = mock(Table.class);
        Column column = mock(Column.class);
        Column pkColumn = mock(Column.class);
        when(column.getName()).thenReturn("image");
        when(table.getColumnWithName("image")).thenReturn(column);
        when(table.getQualifiedName(anyString(), eq("."), eq("."))).thenReturn("products");
        when(pkColumn.getName()).thenReturn("id");
        ReadOnlyTextAreaDialog dialog = new ReadOnlyTextAreaDialog("image", "value", table,
                new Object[] { "1" }, platform, false, false, null);
        String sql = dialog.buildLobSelect(new Column[] { pkColumn });
        assertTrue(sql.startsWith("select"), "Expected SELECT statement: " + sql);
        assertTrue(sql.contains("image"), "Expected column name: " + sql);
        assertTrue(sql.contains("products"), "Expected table name: " + sql);
        assertTrue(sql.contains("id=?"), "Expected PK condition: " + sql);
        assertFalse(sql.contains("and "), "Trailing AND should be removed: " + sql);
    }

    @Test
    void buildLobUpdate_withSinglePkColumn_producesCorrectSql() {
        IDatabasePlatform platform = buildPlatform();
        Table table = mock(Table.class);
        Column column = mock(Column.class);
        Column pkColumn = mock(Column.class);
        when(column.getName()).thenReturn("image");
        when(table.getColumnWithName("image")).thenReturn(column);
        when(table.getQualifiedName(anyString(), eq("."), eq("."))).thenReturn("products");
        when(pkColumn.getName()).thenReturn("id");
        ReadOnlyTextAreaDialog dialog = new ReadOnlyTextAreaDialog("image", "value", table,
                new Object[] { "1" }, platform, false, false, null);
        String sql = dialog.buildLobUpdate(new Column[] { pkColumn });
        assertTrue(sql.startsWith("update"), "Expected UPDATE statement: " + sql);
        assertTrue(sql.contains("products"), "Expected table name: " + sql);
        assertTrue(sql.contains("image=?"), "Expected column assignment: " + sql);
        assertTrue(sql.contains("id=?"), "Expected PK condition: " + sql);
        assertFalse(sql.contains("and "), "Trailing AND should be removed: " + sql);
    }

    @Test
    void buildLobUpdate_withTwoPkColumns_includesBothInWhereClause() {
        IDatabasePlatform platform = buildPlatform();
        Table table = mock(Table.class);
        Column column = mock(Column.class);
        Column pk1 = mock(Column.class);
        Column pk2 = mock(Column.class);
        when(column.getName()).thenReturn("data");
        when(table.getColumnWithName("data")).thenReturn(column);
        when(table.getQualifiedName(anyString(), eq("."), eq("."))).thenReturn("mytable");
        when(pk1.getName()).thenReturn("id1");
        when(pk2.getName()).thenReturn("id2");
        ReadOnlyTextAreaDialog dialog = new ReadOnlyTextAreaDialog("data", "val", table,
                new Object[] { "1", "2" }, platform, false, false, null);
        String sql = dialog.buildLobUpdate(new Column[] { pk1, pk2 });
        assertTrue(sql.contains("id1=?"), "Expected first PK: " + sql);
        assertTrue(sql.contains("id2=?"), "Expected second PK: " + sql);
        assertFalse(sql.endsWith(" and "), "Trailing AND should be removed: " + sql);
    }
}
