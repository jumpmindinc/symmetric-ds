package org.jumpmind.vaadin.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.NotificationVariant;

@ViewPackages(classes = { NotifyDialog.class })
class NotifyDialogTest extends BrowserlessTest {
    private NotifyDialog createAndInsert(String text, Throwable ex) {
        NotifyDialog dialog = new NotifyDialog("Error", text, ex, NotificationVariant.LUMO_ERROR);
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
        assertDoesNotThrow(() -> createAndInsert("Error message", null));
    }

    @Test
    void constructor_detailsModeIsFalse() {
        NotifyDialog dialog = createAndInsert("Error message", null);
        assertFalse(dialog.detailsMode);
    }

    @Test
    void constructor_closeButtonPresent() {
        createAndInsert("Error message", null);
        assertDoesNotThrow(() -> findButton("Close"));
    }

    @Test
    void detailsButton_visibleWhenExceptionIsPresent() {
        createAndInsert("Error message", new RuntimeException("test"));
        Button detailsButton = findButton("Details");
        assertTrue(detailsButton.isVisible());
    }

    @Test
    void detailsButton_hiddenWhenNoException() {
        createAndInsert("Error message", null);
        assertFalse($(Button.class).all().stream().anyMatch(b -> "Details".equals(b.getText())));
    }

    @Test
    void detailsButton_click_togglesToDetailsMode() {
        NotifyDialog dialog = createAndInsert("Error message", new RuntimeException("test"));
        assertFalse(dialog.detailsMode);
        findButton("Details").click();
        assertTrue(dialog.detailsMode);
    }

    @Test
    void detailsButton_clickTwice_returnsToMessageMode() {
        NotifyDialog dialog = createAndInsert("Error message", new RuntimeException("test"));
        findButton("Details").click();
        assertTrue(dialog.detailsMode);
        findButton("Message").click();
        assertFalse(dialog.detailsMode);
    }

    @Test
    void detailsButton_click_changesTextToMessage() {
        createAndInsert("Error message", new RuntimeException("test"));
        findButton("Details").click();
        assertDoesNotThrow(() -> findButton("Message"));
    }

    @Test
    void detailsButton_secondClick_changesTextBackToDetails() {
        createAndInsert("Error message", new RuntimeException("test"));
        findButton("Details").click();
        findButton("Message").click();
        assertDoesNotThrow(() -> findButton("Details"));
    }

    @Test
    void constructor_withNullTextAndException_doesNotThrow() {
        assertDoesNotThrow(() -> createAndInsert(null, new RuntimeException("fallback message")));
    }

    @Test
    void twoArgConstructor_doesNotThrow() {
        assertDoesNotThrow(() -> {
            NotifyDialog dialog = new NotifyDialog("Error message", new RuntimeException("cause"));
            UI.getCurrent().getElement().insertChild(0, dialog.getElement());
            dialog.open();
        });
    }
}
