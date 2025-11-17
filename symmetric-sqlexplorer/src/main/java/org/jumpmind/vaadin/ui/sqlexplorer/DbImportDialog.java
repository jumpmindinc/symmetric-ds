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
package org.jumpmind.vaadin.ui.sqlexplorer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DbImport;
import org.jumpmind.symmetric.io.data.DbImport.Format;
import org.jumpmind.util.AppUtils;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.ResizableDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.Scroller.ScrollDirection;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.server.streams.UploadHandler;

public class DbImportDialog extends ResizableDialog {
    private static final long serialVersionUID = 1L;
    final Logger log = LoggerFactory.getLogger(getClass());

    enum DbImportFormat {
        SQL, XML, CSV, SYM_XML, CSV_DQUOTE;
    }

    private Set<Table> selectedTablesSet;
    private Table selectedTable;
    private Scroller importLayout;
    private ComboBox<DbImportFormat> formatSelect;
    private Checkbox force;
    private Checkbox ignoreConflicts;
    private Checkbox ignoreMissingTables;
    private Checkbox replace;
    private Checkbox convertUniIndexes;
    private ComboBox<String> schemaSelect;
    private ComboBox<String> catalogSelect;
    private ComboBox<String> listOfTablesSelect;
    private TextField commitField;
    private Checkbox alter;
    private Checkbox alterCase;
    private Button cancelButton;
    private DbImport dbImport;
    private Upload upload;
    private Format format;
    private IDatabasePlatform databasePlatform;

    public DbImportDialog(IDatabasePlatform databasePlatform) {
        this(databasePlatform, new HashSet<Table>(0));
    }

    public DbImportDialog(IDatabasePlatform databasePlatform, Set<Table> selectedTableSet) {
        super("Database Import");
        this.selectedTablesSet = selectedTableSet;
        this.databasePlatform = databasePlatform;
        setCloseOnOutsideClick(false);
        createImportLayout();
    }

    protected void createImportLayout() {
        VerticalLayout importContent = new VerticalLayout();
        importContent.setSizeFull();
        importContent.setMargin(false);
        importContent.setSpacing(false);
        importContent.add(new NativeLabel("Please select from the following options"));
        FormLayout formLayout = new FormLayout();
        formLayout.setSizeFull();
        importContent.addAndExpand(formLayout);
        formatSelect = new ComboBox<>("Format");
        formatSelect.setItems((format, filter) -> !CommonUiUtils.isFilteredOut(Objects.toString(format, null), filter),
                DbImportFormat.values());
        formatSelect.setValue(DbImportFormat.SQL);
        formatSelect.addValueChangeListener((e) -> {
            DbImportFormat format = (DbImportFormat) formatSelect.getValue();
            switch (format) {
                case XML:
                    listOfTablesSelect.setEnabled(false);
                    alter.setEnabled(true);
                    alterCase.setEnabled(true);
                    ignoreConflicts.setEnabled(false);
                    ignoreMissingTables.setEnabled(false);
                    replace.setEnabled(false);
                    break;
                case SQL:
                case SYM_XML:
                case CSV:
                case CSV_DQUOTE:
                    listOfTablesSelect.setEnabled(true);
                    alter.setEnabled(false);
                    alterCase.setEnabled(false);
                    ignoreConflicts.setEnabled(true);
                    ignoreMissingTables.setEnabled(true);
                    replace.setEnabled(true);
                    break;
            }
        });
        formLayout.add(formatSelect);
        catalogSelect = new ComboBox<>("Catalog");
        catalogSelect.setItems((catalog, filter) -> !CommonUiUtils.isFilteredOut(catalog, filter), getCatalogs());
        catalogSelect.setValue(databasePlatform.getDefaultCatalog());
        formLayout.add(catalogSelect);
        schemaSelect = new ComboBox<>("Schema");
        schemaSelect.setItems((schema, filter) -> !CommonUiUtils.isFilteredOut(schema, filter), getSchemas());
        if (selectedTablesSet.size() > 0) {
            schemaSelect.setValue(selectedTablesSet.iterator().next().getSchema());
        } else {
            schemaSelect.setValue(databasePlatform.getDefaultSchema());
        }
        schemaSelect.addValueChangeListener((e) -> {
            populateListOfTablesSelect();
        });
        formLayout.add(schemaSelect);
        listOfTablesSelect = new ComboBox<>("Tables");
        populateListOfTablesSelect();
        listOfTablesSelect.setEnabled(false);
        if (!this.selectedTablesSet.isEmpty()) {
            if (this.selectedTablesSet.size() == 1) {
                this.selectedTable = this.selectedTablesSet.iterator().next();
                listOfTablesSelect.setValue(this.selectedTable.getName());
                this.selectedTablesSet.clear();
            } else {
                List<Table> list = new ArrayList<Table>(this.selectedTablesSet);
                listOfTablesSelect.setValue(list.get(0).getName());
                this.selectedTable = list.get(0);
                this.selectedTablesSet.clear();
            }
        }
        formLayout.add(listOfTablesSelect);
        commitField = new TextField("Rows to Commit");
        commitField.addValueChangeListener((event) -> {
            commitField.setValue(event.getValue());
        });
        commitField.setValueChangeMode(ValueChangeMode.LAZY);
        commitField.setValueChangeTimeout(200);
        commitField.setValue("10000");
        formLayout.add(commitField);
        force = new Checkbox("Ignore any errors and continue");
        formLayout.add(force);
        ignoreConflicts = new Checkbox("Skip rows with conflicts");
        formLayout.add(ignoreConflicts);
        ignoreMissingTables = new Checkbox("Skip rows with missing tables");
        formLayout.add(ignoreMissingTables);
        replace = new Checkbox("Replace rows with conflicts");
        formLayout.add(replace);
        convertUniIndexes = new Checkbox("Convert unique indexes to non-unique when one of the columns is defined as not required");
        convertUniIndexes.setValue(true);
        formLayout.add(convertUniIndexes);
        alter = new Checkbox("Alter existing tables, if needed");
        alter.setEnabled(false);
        alter.setValue(true);
        formLayout.add(alter);
        alterCase = new Checkbox("Match default case of database");
        alterCase.setEnabled(false);
        alterCase.setValue(true);
        formLayout.add(alterCase);
        upload = new Upload(UploadHandler.toFile((metadata, file) -> {
            createDbImport();
            try {
                doDbImport(file);
                close();
                Notification successNotification = new Notification("Successful Import", 10000);
                successNotification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                successNotification.open();
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                Notification errorNotification = new Notification(e.getMessage(), 10000);
                errorNotification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                errorNotification.open();
            } finally {
                file.delete();
            }
        }, metadata -> AppUtils.createTempFile("dbimport", formatSelect.getValue().toString())));
        upload.setMaxFiles(100);
        upload.setDropAllowed(false);
        Button uploadButton = new Button("Import");
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        upload.setUploadButton(uploadButton);
        formLayout.add(upload);
        importLayout = new Scroller(importContent);
        importLayout.setScrollDirection(ScrollDirection.VERTICAL);
        importLayout.setSizeFull();
        cancelButton = new Button("Cancel", event -> close());
        add(importLayout, 1);
        buildButtonFooter(cancelButton, upload);
    }

    protected void doDbImport(File file) throws FileNotFoundException {
        if (format.toString().equals("CSV") || format.toString().equals("CSV_DQUOTE")) {
            dbImport.importTables(new BufferedInputStream(new FileInputStream(file)),
                    listOfTablesSelect.getValue().toString());
        } else {
            dbImport.importTables(new BufferedInputStream(new FileInputStream(file)));
        }
    }

    protected void createDbImport() {
        dbImport = new DbImport(databasePlatform);
        format = DbImport.Format.valueOf(formatSelect.getValue().toString());
        dbImport.setFormat(format);
        dbImport.setCatalog((String) catalogSelect.getValue());
        dbImport.setSchema((String) schemaSelect.getValue());
        dbImport.setCommitRate(Long.parseLong(commitField.getValue()));
        dbImport.setForceImport(force.getValue());
        dbImport.setIgnoreCollisions(ignoreConflicts.getValue());
        dbImport.setIgnoreMissingTables(ignoreMissingTables.getValue());
        dbImport.setAlterTables(alter.getValue());
        dbImport.setAlterCaseToMatchDatabaseDefaultCase(alterCase.getValue());
        dbImport.setCreateIndexConvertUniqueToNonuniqueWhenColumnsNotRequired(convertUniIndexes.getValue());
    }

    protected boolean importButtonEnable() {
        if (formatSelect.getValue() != null) {
            if (!commitField.getValue().equals("")) {
                if (formatSelect.getValue().toString().equals("CSV") || formatSelect.getValue().toString().equals("CSV_DQUOTE")) {
                    if (listOfTablesSelect.getValue() != null) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    protected void populateListOfTablesSelect() {
        listOfTablesSelect.clear();
        listOfTablesSelect.setItems((table, filter) -> !CommonUiUtils.isFilteredOut(table, filter), getTables());
    }

    public String getSelectedSchema() {
        String schemaName = (String) schemaSelect.getValue();
        if (schemaName != null && schemaName.equals(databasePlatform.getDefaultSchema())) {
            schemaName = null;
        }
        return StringUtils.isBlank(schemaName) ? null : schemaName;
    }

    public String getSelectedCatalog() {
        String catalogName = (String) catalogSelect.getValue();
        if (catalogName != null && catalogName.equals(databasePlatform.getDefaultCatalog())) {
            catalogName = null;
        }
        return StringUtils.isBlank(catalogName) ? null : catalogName;
    }

    public List<String> getSchemas() {
        return databasePlatform.getDdlReader().getSchemaNames(null);
    }

    public List<String> getCatalogs() {
        return databasePlatform.getDdlReader().getCatalogNames();
    }

    public List<String> getTables() {
        return databasePlatform.getDdlReader().getTableNames((String) catalogSelect.getValue(),
                (String) schemaSelect.getValue(), new String[] { "TABLE" });
    }
}
