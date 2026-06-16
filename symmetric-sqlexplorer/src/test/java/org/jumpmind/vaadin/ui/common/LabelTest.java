package org.jumpmind.vaadin.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.icon.VaadinIcon;

@ViewPackages(classes = { Label.class })
class LabelTest extends BrowserlessTest {
    private Label createAndAttach(Label label) {
        UI.getCurrent().getElement().insertChild(0, label.getElement());
        return label;
    }

    @Test
    void defaultConstructor_hasNoChildren() {
        Label label = createAndAttach(new Label());
        assertEquals(0, label.getChildren().count());
    }

    @Test
    void textConstructor_hasOneHtmlChild() {
        Label label = createAndAttach(new Label("hello"));
        assertEquals(1, label.getChildren().count());
    }

    @Test
    void leftIconConstructor_hasTwoChildren() {
        Label label = createAndAttach(new Label(VaadinIcon.STAR, "hello"));
        assertEquals(2, label.getChildren().count());
    }

    @Test
    void rightIconConstructor_hasTwoChildren() {
        Label label = createAndAttach(new Label("hello", VaadinIcon.STAR));
        assertEquals(2, label.getChildren().count());
    }

    @Test
    void leftIconConstructor_iconIsFirstChild() {
        Label label = createAndAttach(new Label(VaadinIcon.STAR, "hello"));
        assertTrue(label.getChildren().findFirst().isPresent());
        assertFalse(label.getChildren().findFirst().get() instanceof Html);
    }

    @Test
    void setText_onEmptyLabel_addsHtmlChild() {
        Label label = createAndAttach(new Label());
        assertEquals(0, label.getChildren().count());
        label.setText("updated");
        assertEquals(1, label.getChildren().count());
    }

    @Test
    void setText_replacesExistingText() {
        Label label = createAndAttach(new Label("original"));
        assertEquals(1, label.getChildren().count());
        label.setText("updated");
        assertEquals(1, label.getChildren().count());
    }

    @Test
    void setLeftIcon_onTextLabel_addsTwoChildren() {
        Label label = createAndAttach(new Label("hello"));
        assertEquals(1, label.getChildren().count());
        label.setLeftIcon(VaadinIcon.STAR);
        assertEquals(2, label.getChildren().count());
    }

    @Test
    void setRightIcon_onTextLabel_addsTwoChildren() {
        Label label = createAndAttach(new Label("hello"));
        label.setRightIcon(VaadinIcon.STAR);
        assertEquals(2, label.getChildren().count());
    }

    @Test
    void setLeftIcon_twice_replacesOldIcon() {
        Label label = createAndAttach(new Label("hello"));
        label.setLeftIcon(VaadinIcon.STAR);
        assertEquals(2, label.getChildren().count());
        label.setLeftIcon(VaadinIcon.HEART);
        assertEquals(2, label.getChildren().count());
    }

    @Test
    void setRightIcon_twice_replacesOldIcon() {
        Label label = createAndAttach(new Label("hello"));
        label.setRightIcon(VaadinIcon.STAR);
        label.setRightIcon(VaadinIcon.HEART);
        assertEquals(2, label.getChildren().count());
    }

    @Test
    void leftIcon_setsMarginRightStyle() {
        Label label = createAndAttach(new Label(VaadinIcon.STAR, "hello"));
        String iconStyle = label.getChildren().findFirst().get().getElement().getStyle().get("margin-right");
        assertEquals("5px", iconStyle);
    }

    @Test
    void rightIcon_setsMarginLeftStyle() {
        Label label = createAndAttach(new Label("hello", VaadinIcon.STAR));
        String iconStyle = label.getChildren().reduce((a, b) -> b).get().getElement().getStyle().get("margin-left");
        assertEquals("5px", iconStyle);
    }
}
