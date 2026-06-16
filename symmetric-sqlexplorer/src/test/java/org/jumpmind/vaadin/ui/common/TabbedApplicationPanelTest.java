package org.jumpmind.vaadin.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

@ViewPackages(classes = { TabbedApplicationPanel.class })
class TabbedApplicationPanelTest extends BrowserlessTest {
    static class ClosingPanel extends Span implements IUiPanel {
        private static final long serialVersionUID = 1L;
        boolean selectedCalled = false;
        final boolean allowClose;

        ClosingPanel(boolean allowClose) {
            this.allowClose = allowClose;
        }

        @Override
        public void selected() {
            selectedCalled = true;
        }

        @Override
        public void deselected() {
        }

        @Override
        public boolean closing() {
            return allowClose;
        }
    }

    private TabbedApplicationPanel createAndAttach() {
        TabbedApplicationPanel panel = new TabbedApplicationPanel();
        UI.getCurrent().getElement().insertChild(0, panel.getElement());
        return panel;
    }

    @Test
    void constructor_doesNotThrow() {
        assertDoesNotThrow(this::createAndAttach);
    }

    @Test
    void constructor_initiallyEmpty() {
        TabbedApplicationPanel panel = createAndAttach();
        assertTrue(panel.tabList.isEmpty());
    }

    @Test
    void setMainTab_addsOneTab() {
        TabbedApplicationPanel panel = createAndAttach();
        panel.setMainTab("Main", new Icon(VaadinIcon.HOME), new Span("main content"));
        assertEquals(1, panel.tabList.size());
    }

    @Test
    void addCloseableTab_addsTab() {
        TabbedApplicationPanel panel = createAndAttach();
        panel.setMainTab("Main", new Icon(VaadinIcon.HOME), new Span("main content"));
        panel.addCloseableTab("Tab1", new Icon(VaadinIcon.STAR), new Span("content1"));
        assertEquals(2, panel.tabList.size());
    }

    @Test
    void addCloseableTab_sameCaptionTwice_doesNotDuplicate() {
        TabbedApplicationPanel panel = createAndAttach();
        panel.setMainTab("Main", new Icon(VaadinIcon.HOME), new Span("main"));
        panel.addCloseableTab("Feature", new Icon(VaadinIcon.STAR), new Span("content"));
        panel.addCloseableTab("Feature", new Icon(VaadinIcon.STAR), new Span("content2"));
        assertEquals(2, panel.tabList.size());
    }

    @Test
    void remove_withNonIUiPanel_removesTab() {
        TabbedApplicationPanel panel = createAndAttach();
        panel.setMainTab("Main", new Icon(VaadinIcon.HOME), new Span("main"));
        Span tabContent = new Span("closeable");
        panel.addCloseableTab("Close Me", new Icon(VaadinIcon.STAR), tabContent);
        assertEquals(2, panel.tabList.size());
        TabSheet.EnhancedTab tab = panel.getTab("Close Me");
        panel.remove(tab);
        assertEquals(1, panel.tabList.size());
    }

    @Test
    void remove_withIUiPanelReturningFalse_doesNotRemoveTab() {
        TabbedApplicationPanel panel = createAndAttach();
        panel.setMainTab("Main", new Icon(VaadinIcon.HOME), new Span("main"));
        ClosingPanel noClose = new ClosingPanel(false);
        panel.addCloseableTab("Locked", new Icon(VaadinIcon.STAR), noClose);
        assertEquals(2, panel.tabList.size());
        TabSheet.EnhancedTab tab = panel.getTab("Locked");
        panel.remove(tab);
        assertEquals(2, panel.tabList.size());
    }

    @Test
    void remove_withIUiPanelReturningTrue_removesTab() {
        TabbedApplicationPanel panel = createAndAttach();
        panel.setMainTab("Main", new Icon(VaadinIcon.HOME), new Span("main"));
        ClosingPanel canClose = new ClosingPanel(true);
        panel.addCloseableTab("Closeable", new Icon(VaadinIcon.STAR), canClose);
        TabSheet.EnhancedTab tab = panel.getTab("Closeable");
        panel.remove(tab);
        assertEquals(1, panel.tabList.size());
    }

    @Test
    void tabSelection_withIUiPanel_callsSelectedMethod() {
        TabbedApplicationPanel panel = createAndAttach();
        Span mainContent = new Span("main");
        panel.setMainTab("Main", new Icon(VaadinIcon.HOME), mainContent);
        ClosingPanel panelComponent = new ClosingPanel(true);
        panel.addCloseableTab("Feature", new Icon(VaadinIcon.STAR), panelComponent);
        panel.setSelectedTab("Main");
        panelComponent.selectedCalled = false;
        panel.setSelectedTab("Feature");
        assertTrue(panelComponent.selectedCalled);
    }
}
