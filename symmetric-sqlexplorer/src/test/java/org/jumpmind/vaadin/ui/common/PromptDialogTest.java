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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;

@ViewPackages(classes = { PromptDialog.class })
class PromptDialogTest extends BrowserlessTest {
    private PromptDialog createAndOpen(PromptDialog.IPromptListener listener) {
        PromptDialog dialog = new PromptDialog("Caption", "Prompt text", "", listener);
        UI.getCurrent().getElement().insertChild(0, dialog.getElement());
        dialog.open();
        return dialog;
    }

    private Button findButton(String text) {
        return $(Button.class).all().stream()
                .filter(b -> text.equals(b.getText()))
                .findFirst().orElseThrow();
    }

    @Test
    void constructor_doesNotThrow() {
        assertDoesNotThrow(() -> createAndOpen(content -> true));
    }

    @Test
    void constructor_cancelButtonPresent() {
        createAndOpen(content -> true);
        assertDoesNotThrow(() -> findButton("Cancel"));
    }

    @Test
    void constructor_okButtonPresent() {
        createAndOpen(content -> true);
        assertDoesNotThrow(() -> findButton("Ok"));
    }

    @Test
    void okButton_click_callsListener() {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        createAndOpen(content -> {
            listenerCalled.set(true);
            return true;
        });
        findButton("Ok").click();
        assertTrue(listenerCalled.get());
    }

    @Test
    void okButton_click_listenerReturnsTrue_dialogCloses() {
        PromptDialog dialog = createAndOpen(content -> true);
        findButton("Ok").click();
        assertFalse(dialog.isOpened());
    }

    @Test
    void okButton_click_listenerReturnsFalse_dialogRemainsOpen() {
        PromptDialog dialog = createAndOpen(content -> false);
        findButton("Ok").click();
        assertTrue(dialog.isOpened());
    }

    @Test
    void cancelButton_click_dialogCloses() {
        PromptDialog dialog = createAndOpen(content -> true);
        findButton("Cancel").click();
        assertFalse(dialog.isOpened());
    }

    @Test
    void cancelButton_click_doesNotCallListener() {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        createAndOpen(content -> {
            listenerCalled.set(true);
            return true;
        });
        findButton("Cancel").click();
        assertFalse(listenerCalled.get());
    }

    @Test
    void okButton_passesFieldValueToListener() {
        AtomicReference<String> capturedValue = new AtomicReference<>();
        PromptDialog dialog = new PromptDialog("Title", "Text", "default", content -> {
            capturedValue.set(content);
            return true;
        });
        UI.getCurrent().getElement().insertChild(0, dialog.getElement());
        dialog.open();
        findButton("Ok").click();
        assertEquals("default", capturedValue.get());
    }
}
