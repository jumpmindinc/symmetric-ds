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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;

public class ReadOnlyTextAreaDialog extends ResizableDialog {
    private static final long serialVersionUID = 1L;
    final Logger log = LoggerFactory.getLogger(getClass());
    VerticalLayout wrapper;
    protected List<Component> leftComponentList;
    protected TextArea textField;
    protected Select<String> displayBox;
    protected Button downloadButton;
    protected Table table;
    protected Column column;
    protected Object[] primaryKeys;
    protected IDatabasePlatform platform;

    public ReadOnlyTextAreaDialog(final String title, final String value, boolean isEncodedInHex) {
        this(title, value, isEncodedInHex, null);
    }

    public ReadOnlyTextAreaDialog(final String title, final String value, boolean isEncodedInHex, Consumer<Boolean> shortcutToggler) {
        this(title, value, null, null, null, isEncodedInHex, false, shortcutToggler);
    }

    public ReadOnlyTextAreaDialog(final String title, final String value, Table table, Object[] primaryKeys,
            IDatabasePlatform platform, boolean isEncodedInHex, boolean isLob, Consumer<Boolean> shortcutToggler) {
        super(title);
        this.table = table;
        this.primaryKeys = primaryKeys;
        this.platform = platform;
        this.column = table == null ? null : table.getColumnWithName(title);
        if (shortcutToggler != null) {
            addOpenedChangeListener(event -> shortcutToggler.accept(event.isOpened()));
        }
        wrapper = new VerticalLayout();
        wrapper.setMargin(false);
        wrapper.setHeight("100px");
        wrapper.setWidthFull();
        textField = new TextArea();
        textField.setSizeFull();
        wrapper.add(textField);
        add(wrapper, 1);
        leftComponentList = new ArrayList<Component>();
        if (value != null && isEncodedInHex) {
            displayBox = new Select<String>();
            displayBox.setItems("Hex", "Text", "Decimal");
            displayBox.setLabel("Display As");
            displayBox.setEmptySelectionAllowed(false);
            displayBox.setValue("Hex");
            displayBox.addValueChangeListener(event -> updateTextField(displayBox.getValue(), value));
            leftComponentList.add(displayBox);
        }
        if (table != null && isLob) {
            buildUploadButton(title, value);
            buildDownloadButton(title);
        }
        Button closeButton = buildCloseButton();
        if (!leftComponentList.isEmpty()) {
            buildButtonFooter(leftComponentList, closeButton);
        } else {
            buildButtonFooter(closeButton);
        }
        textField.setValue(value);
        textField.addValueChangeListener(event -> {
            if (displayBox != null) {
                updateTextField(displayBox.getValue(), value);
            } else {
                textField.setValue(value);
            }
        });
    }

    @Override
    public void show() {
        super.show();
        selectAll();
    }

    private void buildUploadButton(String title, final String value) {
        final Button uploadButton = new Button("Upload");
        final Button viewTextButton = new Button("View Text");
        final Upload upload = new Upload(UploadHandler.toFile((metadata, file) -> {
            log.info("File received successfully. Updating database");
            String sql = buildLobUpdate(table.getPrimaryKeyColumns());
            Connection con = null;
            PreparedStatement ps = null;
            try {
                long startTime = System.nanoTime();
                con = ((DataSource) platform.getDataSource()).getConnection();
                con.setAutoCommit(false);
                ps = con.prepareStatement(sql);
                InputStream stream = new FileInputStream(file);
                ps.setBinaryStream(1, stream, (int) file.length());
                for (int i = 0; i < primaryKeys.length; i++) {
                    ps.setObject(i + 2, primaryKeys[i], table.getPrimaryKeyColumns()[i].getMappedTypeCode());
                }
                ps.executeUpdate();
                con.commit();
                long executionTime = System.nanoTime() - startTime;
                log.info("Upload succeeded in " + executionTime + " ms");
                ReadOnlyTextAreaDialog.this.close();
            } catch (SQLException e1) {
                NotifyDialog.show("Upload Error", "<b>The file could not be uploaded.</b><br>" +
                        "Cause: the sql update statement failed.<br><br>" +
                        "To view the <b>Stack Trace</b>, click <b>\"Details\"</b>.", e1, NotificationVariant.LUMO_ERROR);
            } catch (FileNotFoundException e2) {
                // do nothing -- already notified
            } finally {
                if (ps != null) {
                    try {
                        ps.close();
                    } catch (SQLException e) {
                    }
                }
                if (con != null) {
                    try {
                        con.close();
                    } catch (SQLException e) {
                    }
                }
                FileUtils.deleteQuietly(file);
                log.info("Deleted uploaded file");
            }
        }, metadata -> new File(System.getProperty("java.io.tmpdir"), metadata.fileName())));
        upload.setMaxFiles(100);
        upload.setDropLabel(new Span("Upload new " + table.getColumnWithName(title).getMappedType()));
        uploadButton.addClickListener(event -> {
            wrapper.replace(textField, upload);
            wrapper.setHorizontalComponentAlignment(Alignment.CENTER, upload);
            getFooter().removeAll();
            if (!leftComponentList.isEmpty()) {
                buildButtonFooter(leftComponentList, viewTextButton, buildCloseButton());
            } else {
                buildButtonFooter(viewTextButton, buildCloseButton());
            }
        });
        viewTextButton.addClickListener(event -> {
            wrapper.replace(upload, textField);
            wrapper.setHorizontalComponentAlignment(Alignment.END, textField);
            getFooter().removeAll();
            if (!leftComponentList.isEmpty()) {
                buildButtonFooter(leftComponentList, uploadButton, buildCloseButton());
            } else {
                buildButtonFooter(uploadButton, buildCloseButton());
            }
        });
        if (value != null) {
            getFooter().removeAll();
            if (!leftComponentList.isEmpty()) {
                buildButtonFooter(leftComponentList, uploadButton, buildCloseButton());
            } else {
                buildButtonFooter(uploadButton, buildCloseButton());
            }
        } else {
            wrapper.replace(textField, upload);
            wrapper.setHorizontalComponentAlignment(Alignment.CENTER, upload);
        }
    }

    private void buildDownloadButton(String title) {
        downloadButton = new Button("Download");
        final byte[] lobData = getLobData(title);
        if (lobData != null) {
            DownloadHandler downloadHandler = DownloadHandler.fromInputStream(
                    event -> new DownloadResponse(new ByteArrayInputStream(lobData), title, null, lobData.length));
            Anchor fileDownloader = new Anchor(downloadHandler, null);
            fileDownloader.add(downloadButton);
            long fileSize = lobData.length;
            String sizeText = fileSize + " Bytes";
            if (fileSize / 1024 > 0) {
                sizeText = Math.round(fileSize / 1024.0) + " kB";
                fileSize /= 1024;
            }
            if (fileSize / 1024 > 0) {
                sizeText = Math.round(fileSize / 1024.0) + " MB";
                fileSize /= 1024;
            }
            if (fileSize / 1024 > 0) {
                sizeText = Math.round(fileSize / 1024.0) + " GB";
            }
            Span sizeSpan = new Span(sizeText);
            getFooter().removeAll();
            if (!leftComponentList.isEmpty()) {
                buildButtonFooter(leftComponentList, fileDownloader, sizeSpan, buildCloseButton());
            } else {
                buildButtonFooter(fileDownloader, sizeSpan, buildCloseButton());
            }
        }
    }

    protected byte[] getLobData(String title) {
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        String sql = buildLobSelect(table.getPrimaryKeyColumns());
        byte[] array;
        if (platform.isBlob(column)) {
            array = sqlTemplate.queryForBlob(sql, column.getJdbcTypeCode(), column.getJdbcTypeName(), primaryKeys);
        } else {
            String results = sqlTemplate.queryForClob(sql, column.getJdbcTypeCode(), column.getJdbcTypeName(), primaryKeys);
            if (results != null) {
                array = results.getBytes();
            } else {
                array = null;
            }
        }
        return array;
    }

    protected String buildLobSelect(Column[] pkColumns) {
        StringBuilder sql = new StringBuilder("select ");
        DatabaseInfo dbInfo = platform.getDatabaseInfo();
        String quote = platform.getDdlBuilder().isDelimitedIdentifierModeOn() ? dbInfo.getDelimiterToken() : "";
        sql.append(quote);
        sql.append(column.getName());
        sql.append(quote);
        sql.append(" from ");
        sql.append(table.getQualifiedTableName(quote, dbInfo.getCatalogSeparator(),
                dbInfo.getSchemaSeparator()));
        sql.append(" where ");
        for (Column col : pkColumns) {
            sql.append(quote);
            sql.append(col.getName());
            sql.append(quote);
            sql.append("=? and ");
        }
        sql.delete(sql.length() - 5, sql.length());
        return sql.toString();
    }

    protected String buildLobUpdate(Column[] pkColumns) {
        StringBuilder sql = new StringBuilder("update ");
        DatabaseInfo dbInfo = platform.getDatabaseInfo();
        String quote = platform.getDdlBuilder().isDelimitedIdentifierModeOn() ? dbInfo.getDelimiterToken() : "";
        sql.append(table.getQualifiedTableName(quote, dbInfo.getCatalogSeparator(),
                dbInfo.getSchemaSeparator()));
        sql.append(" set ");
        sql.append(quote);
        sql.append(column.getName());
        sql.append(quote);
        sql.append("=? where ");
        for (Column col : pkColumns) {
            sql.append(quote);
            sql.append(col.getName());
            sql.append(quote);
            sql.append("=? and ");
        }
        sql.delete(sql.length() - 5, sql.length());
        return sql.toString();
    }

    public void selectAll() {
        textField.focus();
        textField.getElement().executeJs("this.inputElement.select()");
    }

    protected void updateTextField(String display, String value) {
        if (display.equals("Hex")) {
            textField.setValue(value);
        } else if (display.equals("Text")) {
            try {
                byte[] bytes = Hex.decodeHex(value.toCharArray());
                textField.setValue(new String(bytes));
            } catch (Exception e) {
                log.warn("Failed to decode hex string for display", e);
            }
        } else if (display.equals("Decimal")) {
            try {
                byte[] bytes = Hex.decodeHex(value.toCharArray());
                String newValue = "";
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    if (!newValue.equals("")) {
                        newValue += " ";
                    }
                    newValue += buffer.get() & 0xff;
                }
                textField.setValue(newValue);
            } catch (Exception e) {
                log.warn("Failed to decode hex string for display", e);
            }
        }
    }

    public static void show(String title, String value, boolean isEncodedInHex) {
        show(title, value, isEncodedInHex, null);
    }

    public static void show(String title, String value, boolean isEncodedInHex, Consumer<Boolean> shortcutToggler) {
        ReadOnlyTextAreaDialog dialog = new ReadOnlyTextAreaDialog(title, value, isEncodedInHex, shortcutToggler);
        dialog.showAtSize(.4);
    }

    public static void show(String title, String value, Table table, Object[] primaryKeys, IDatabasePlatform platform,
            boolean isEncodedInHex, boolean isLob) {
        ReadOnlyTextAreaDialog dialog = new ReadOnlyTextAreaDialog(title, value, table, primaryKeys, platform,
                isEncodedInHex, isLob, null);
        dialog.showAtSize(.45);
    }
}
