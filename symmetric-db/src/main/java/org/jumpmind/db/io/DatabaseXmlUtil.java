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
package org.jumpmind.db.io;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.CompressionTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.Function;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.PlatformFunction;
import org.jumpmind.db.model.PlatformIndex;
import org.jumpmind.db.model.PlatformTrigger;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.model.View;
import org.jumpmind.db.model.UniqueIndex;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.exception.IoException;
import org.jumpmind.util.FormatUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/*
 * This class provides functions to read and write database models from/to XML.
 */
public class DatabaseXmlUtil {
    public static final String DTD_PREFIX = "http://db.apache.org/torque/dtd/database";

    private DatabaseXmlUtil() {
    }

    /*
     * Reads the database model contained in the specified file.
     * 
     * @param filename The model file name
     * 
     * @return The database model
     */
    public static Database read(String filename) {
        return read(new File(filename));
    }

    /*
     * Reads the database model contained in the specified file.
     * 
     * @param file The model file
     * 
     * @return The database model
     */
    public static Database read(File file) {
        try (FileReader reader = new FileReader(file)) {
            return read(reader);
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    public static Database read(InputStream is) {
        try {
            return read(new InputStreamReader(is, StandardCharsets.UTF_8.name()));
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    /*
     * Reads the database model given by the reader.
     * 
     * @param reader The reader that returns the model XML
     * 
     * @return The database model
     */
    public static Database read(Reader reader) {
        return read(reader, true);
    }

    /*
     * Reads the database model given by the reader.
     * 
     * @param reader The reader that returns the model XML
     * 
     * @return The database model
     */
    public static Database read(Reader reader, boolean validate) {
        try {
            boolean done = false;
            Database database = null;
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(reader);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT && !done) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        database = new Database();
                        break;
                    case XmlPullParser.START_TAG:
                        String name = parser.getName();
                        if (name.equalsIgnoreCase("database")) {
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    database.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("catalog")) {
                                    database.setCatalog(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("schema")) {
                                    database.setSchema(attributeValue);
                                }
                            }
                        } else if (name.equalsIgnoreCase("table")) {
                            Table table = nextTable(parser, database.getCatalog(), database.getSchema());
                            if (table != null) {
                                database.addTable(table);
                            }
                        } else if (name.equalsIgnoreCase("view")) {
                            View view = nextView(parser, database.getCatalog(), database.getSchema());
                            if (view != null) {
                                database.addView(view);
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        name = parser.getName();
                        if (name.equalsIgnoreCase("database")) {
                            done = true;
                        }
                        break;
                }
                eventType = parser.next();
            }
            if (validate) {
                database.initialize();
            }
            return database;
        } catch (XmlPullParserException e) {
            throw new IoException(e);
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    public static Table nextTable(XmlPullParser parser) {
        return nextTable(parser, null, null);
    }

    public static Table nextTable(XmlPullParser parser, String catalog, String schema) {
        try {
            Table table = null;
            ForeignKey fk = null;
            IIndex index = null;
            Trigger trigger = null;
            PlatformTrigger platformTrigger = null;
            Function function = null;
            PlatformFunction platformFunction = null;
            boolean done = false;
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT && !done) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        String name = parser.getName();
                        if (name.equalsIgnoreCase("table")) {
                            table = new Table();
                            table.setCatalog(catalog);
                            table.setSchema(schema);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    table.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("description")) {
                                    table.setDescription(attributeValue);
                                }
                                if (attributeName.equalsIgnoreCase("logging")) {
                                    boolean logging = !("false".equalsIgnoreCase(attributeValue));
                                    table.setLogging(logging);
                                } else if (attributeName.equalsIgnoreCase("compression")) {
                                    if (CompressionTypes.PAGE.name().equalsIgnoreCase(attributeValue)) {
                                        table.setCompressionType(CompressionTypes.PAGE);
                                    } else if (CompressionTypes.ROW.name().equalsIgnoreCase(attributeValue)) {
                                        table.setCompressionType(CompressionTypes.ROW);
                                    } else if (CompressionTypes.COLUMNSTORE.name().equalsIgnoreCase(attributeValue)) {
                                        table.setCompressionType(CompressionTypes.COLUMNSTORE);
                                    } else if (CompressionTypes.COLUMNSTORE_ARCHIVE.name().equals(attributeValue)) {
                                        table.setCompressionType(CompressionTypes.COLUMNSTORE_ARCHIVE);
                                    } else {
                                        table.setCompressionType(CompressionTypes.NONE);
                                    }
                                }
                            }
                        } else if (name.equalsIgnoreCase("column")) {
                            Column column = new Column();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    column.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("primaryKey")) {
                                    column.setPrimaryKey(FormatUtils.toBoolean(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("primaryKeySeq")) {
                                    column.setPrimaryKeySequence(Integer.parseInt(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("required")) {
                                    column.setRequired(FormatUtils.toBoolean(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("type")) {
                                    column.setMappedType(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("size")) {
                                    column.setSize(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("default")) {
                                    column.setDefaultValue(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("autoIncrement")) {
                                    column.setAutoIncrement(FormatUtils.toBoolean(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("autoUpdate")) {
                                    column.setAutoUpdate(FormatUtils.toBoolean(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("javaName")) {
                                    column.setJavaName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("description")) {
                                    column.setDescription(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("unique")) {
                                    column.setUnique(FormatUtils.toBoolean(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("generated")) {
                                    column.setGenerated(FormatUtils.toBoolean(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("expressionAsDefault")) {
                                    column.setExpressionAsDefaultValue(FormatUtils.toBoolean(attributeValue));
                                }
                            }
                            if (table != null) {
                                table.addColumn(column);
                            }
                        } else if (name.equalsIgnoreCase("platform-column")) {
                            PlatformColumn platformColumn = new PlatformColumn();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    platformColumn.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("type")) {
                                    platformColumn.setType(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("default")) {
                                    platformColumn.setDefaultValue(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("size")) {
                                    if (isNotBlank(attributeValue)) {
                                        platformColumn.setSize(Integer.parseInt(attributeValue));
                                    }
                                } else if (attributeName.equalsIgnoreCase("decimalDigits")) {
                                    if (isNotBlank(attributeValue)) {
                                        platformColumn.setDecimalDigits(Integer.parseInt(attributeValue));
                                    }
                                } else if (attributeName.equalsIgnoreCase("enumValues")) {
                                    if (isNotBlank(attributeValue)) {
                                        platformColumn.setEnumValues(attributeValue.split(","));
                                    }
                                } else if (attributeName.equalsIgnoreCase("userDefinedType")) {
                                    platformColumn.setUserDefinedType(Boolean.parseBoolean(attributeValue));
                                }
                            }
                            if (table != null && table.getColumnCount() > 0) {
                                table.getColumn(table.getColumnCount() - 1).addPlatformColumn(platformColumn);
                            }
                        } else if (name.equalsIgnoreCase("foreign-key")) {
                            fk = new ForeignKey();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    fk.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("foreignTable")) {
                                    fk.setForeignTableName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("foreignTableCatalog")) {
                                    fk.setForeignTableCatalog(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("foreignTableSchema")) {
                                    fk.setForeignTableSchema(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("foreignOnUpdateAction")) {
                                    fk.setOnUpdateAction(ForeignKey.getForeignKeyActionByForeignKeyActionName(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("foreignOnDeleteAction")) {
                                    fk.setOnDeleteAction(ForeignKey.getForeignKeyActionByForeignKeyActionName(attributeValue));
                                }
                            }
                            table.addForeignKey(fk);
                        } else if (name.equalsIgnoreCase("reference")) {
                            Reference ref = new Reference();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("local")) {
                                    ref.setLocalColumnName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("foreign")) {
                                    ref.setForeignColumnName(attributeValue);
                                }
                            }
                            fk.addReference(ref);
                        } else if (name.equalsIgnoreCase("index")
                                || name.equalsIgnoreCase("unique")) {
                            if (name.equalsIgnoreCase("index")) {
                                index = new NonUniqueIndex();
                            } else {
                                index = new UniqueIndex();
                            }
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    index.setName(attributeValue);
                                }
                            }
                            table.addIndex(index);
                        } else if (name.equalsIgnoreCase("index-column")
                                || name.equalsIgnoreCase("unique-column")) {
                            IndexColumn indexColumn = new IndexColumn();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    indexColumn.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("size")) {
                                    indexColumn.setSize(attributeValue);
                                }
                            }
                            indexColumn.setColumn(table.getColumnWithName(indexColumn.getName()));
                            if (index != null) {
                                index.addColumn(indexColumn);
                            }
                        } else if (name.equalsIgnoreCase("include-column")) {
                            IndexColumn indexColumn = new IndexColumn();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    indexColumn.setName(attributeValue);
                                }
                            }
                            indexColumn.setColumn(table.getColumnWithName(indexColumn.getName()));
                            if (index != null) {
                                index.addIncludedColumn(indexColumn);
                            }
                        } else if (name.equalsIgnoreCase("platform-index")) {
                            PlatformIndex platformIndex = new PlatformIndex();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    platformIndex.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("filter-condition")) {
                                    platformIndex.setFilterCondition(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("compression")) {
                                    if (CompressionTypes.ROW.name().equalsIgnoreCase(attributeValue)) {
                                        platformIndex.setCompressionType(CompressionTypes.ROW);
                                    } else if (CompressionTypes.PAGE.name().equalsIgnoreCase(attributeValue)) {
                                        platformIndex.setCompressionType(CompressionTypes.PAGE);
                                    } else if (CompressionTypes.COLUMNSTORE.name().equalsIgnoreCase(attributeValue)) {
                                        platformIndex.setCompressionType(CompressionTypes.COLUMNSTORE);
                                    } else if (CompressionTypes.COLUMNSTORE_ARCHIVE.name().equals(attributeValue)) {
                                        platformIndex.setCompressionType(CompressionTypes.COLUMNSTORE_ARCHIVE);
                                    } else {
                                        platformIndex.setCompressionType(CompressionTypes.NONE);
                                    }
                                }
                            }
                            if (index != null) {
                                index.addPlatformIndex(platformIndex);
                            }
                        } else if (name.equalsIgnoreCase("trigger")) {
                            trigger = new Trigger();
                            trigger.setCatalogName(catalog);
                            trigger.setSchemaName(schema);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    trigger.setName(attributeValue);
                                }
                            }
                            if (table != null) {
                                table.addTrigger(trigger);
                            }
                        } else if (name.equalsIgnoreCase("platform-trigger")) {
                            platformTrigger = new PlatformTrigger();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    platformTrigger.setName(attributeValue);
                                }
                            }
                            if (trigger != null) {
                                trigger.addPlatformTrigger(platformTrigger);
                            }
                        } else if (name.equalsIgnoreCase("trigger-text")) {
                            String triggerText = parser.nextText();
                            platformTrigger.setTriggerText(triggerText);
                        } else if (name.equalsIgnoreCase("function")) {
                            function = new Function();
                            function.setCatalogName(catalog);
                            function.setSchemaName(schema);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    function.setFunctionName(attributeValue);
                                }
                            }
                            if (platformTrigger != null) {
                                platformTrigger.setFunction(function);
                            }
                        } else if (name.equalsIgnoreCase("platform-function")) {
                            platformFunction = new PlatformFunction();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    platformFunction.setName(attributeValue);
                                }
                            }
                            if (function != null) {
                                function.addPlatformFunction(platformFunction);
                            }
                        } else if (name.equalsIgnoreCase("function-text")) {
                            String functionText = parser.nextText();
                            platformFunction.setFunctionText(functionText);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        name = parser.getName();
                        if (name.equalsIgnoreCase("table")) {
                            done = true;
                        } else if (name.equalsIgnoreCase("index")
                                || name.equalsIgnoreCase("unique")) {
                            index = null;
                        } else if (name.equalsIgnoreCase("foreign-key")) {
                            fk = null;
                        } else if (name.equalsIgnoreCase("trigger")) {
                            trigger = null;
                        } else if (name.equalsIgnoreCase("platform-trigger")) {
                            platformTrigger = null;
                        } else if (name.equalsIgnoreCase("function")) {
                            function = null;
                        } else if (name.equalsIgnoreCase("platform-function")) {
                            platformFunction = null;
                        }
                        break;
                }
                if (!done) {
                    eventType = parser.next();
                }
            }
            return table;
        } catch (XmlPullParserException e) {
            throw new IoException(e);
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    public static View nextView(XmlPullParser parser, String catalog, String schema) {
        try {
            View view = null;
            boolean done = false;
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT && !done) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        String name = parser.getName();
                        if (name.equalsIgnoreCase("view")) {
                            view = new View();
                            view.setCatalog(catalog);
                            view.setSchema(schema);
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    view.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("description")) {
                                    view.setDescription(attributeValue);
                                }
                            }
                        } else if (name.equalsIgnoreCase("column") && view != null) {
                            Column column = new Column();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if (attributeName.equalsIgnoreCase("name")) {
                                    column.setName(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("type")) {
                                    column.setMappedType(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("size")) {
                                    column.setSize(attributeValue);
                                } else if (attributeName.equalsIgnoreCase("required")) {
                                    column.setRequired(FormatUtils.toBoolean(attributeValue));
                                } else if (attributeName.equalsIgnoreCase("default")) {
                                    column.setDefaultValue(attributeValue);
                                }
                            }
                            view.addColumn(column);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        name = parser.getName();
                        if (name.equalsIgnoreCase("view")) {
                            done = true;
                        }
                        break;
                }
                eventType = parser.next();
            }
            return view;
        } catch (XmlPullParserException | IOException e) {
            throw new IoException(e);
        }
    }

    /*
     * Writes the database model to the specified file.
     * 
     * @param model The database model
     * 
     * @param filename The model file name
     */
    public static void write(Database model, String filename) {
        try {
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new FileWriter(filename));
                write(model, writer);
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    /*
     * Writes the database model to the given output stream. Note that this method does not flush the stream.
     * 
     * @param model The database model
     * 
     * @param output The output stream
     */
    public static void write(Database model, OutputStream output) {
        Writer writer = new OutputStreamWriter(output);
        write(model, writer);
        try {
            writer.flush();
        } catch (IOException e) {
            throw new IoException(e);
        }
    }
    /*
     * Writes the database model to the given output writer. Note that this method does not flush the writer.
     * 
     * @param model The database model
     * 
     * @param output The output writer
     */

    public static void write(Database model, Writer output) {
        try {
            output.write("<?xml version=\"1.0\"?>\n<!DOCTYPE database SYSTEM \"" + DTD_PREFIX
                    + "\">\n");
            output.write("<database name=\"" + model.getName() + "\"");
            if (model.getCatalog() != null) {
                output.write(" catalog=\"" + model.getCatalog() + "\"");
            }
            if (model.getSchema() != null) {
                output.write(" schema=\"" + model.getSchema() + "\"");
            }
            if (model.getIdMethod() != null) {
                output.write(" defaultIdMethod=\"" + model.getIdMethod() + "\"");
            }
            output.write(">\n");
            for (Table table : model.getTables()) {
                write(table, output);
            }
            for (View view : model.getViews()) {
                write(view, output);
            }
            output.write("</database>\n");
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    public static String toXml(Table table) {
        StringWriter writer = new StringWriter();
        write(table, writer);
        return writer.toString();
    }

    public static String toXml(Database db) {
        StringWriter writer = new StringWriter();
        write(db, writer);
        return writer.toString();
    }

    public static boolean isOracle(Column column) {
        if (column.getPlatformColumns() != null) {
            Collection<PlatformColumn> platformColumns = column.getPlatformColumns()
                    .values();
            for (PlatformColumn col : platformColumns) {
                if (col.getName().equals(DatabaseNamesConstants.ORACLE) || col.getName().equals(DatabaseNamesConstants.ORACLE122) || col.getName().equals(
                        DatabaseNamesConstants.ORACLE23)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isMySql(Column column) {
        if (column.getPlatformColumns() != null) {
            Collection<PlatformColumn> platformColumns = column.getPlatformColumns().values();
            for (PlatformColumn col : platformColumns) {
                if (col.getName().equals(DatabaseNamesConstants.MYSQL)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void write(Table table, Writer output) {
        try {
            output.write("\t<table name=\"" + StringEscapeUtils.escapeXml10(table.getName()) + "\"");
            if (table.getCompressionType() != CompressionTypes.NONE) {
                output.write(" compression=\"" + table.getCompressionType().name() + "\"");
            }
            if (table.getLogging() != true) {
                output.write(" logging=\"false\"");
            }
            output.write(">\n");
            for (Column column : table.getColumns()) {
                writeColumn(column, output);
            }
            for (ForeignKey fk : table.getForeignKeys()) {
                String name = fk.getName() == null ? "" : fk.getName();
                output.write("\t\t<foreign-key name=\"" + StringEscapeUtils.escapeXml10(name) + "\" foreignTable=\""
                        + StringEscapeUtils.escapeXml10(fk.getForeignTableName()) + "\" foreignTableCatalog=\""
                        + StringEscapeUtils.escapeXml10(fk.getForeignTableCatalog() == null || fk.getForeignTableCatalog().equals(table.getCatalog())
                                ? ""
                                : fk.getForeignTableCatalog()) +
                        "\" foreignTableSchema=\"" + StringEscapeUtils.escapeXml10(fk.getForeignTableSchema() == null ||
                                fk.getForeignTableSchema().equals(table.getSchema()) ? "" : fk.getForeignTableSchema()) + "\""
                        +
                        writeForeignKeyOnUpdateClause(fk)
                        +
                        writeForeignKeyOnDeleteClause(fk)
                        +
                        ">\n");
                for (Reference ref : fk.getReferences()) {
                    output.write("\t\t\t<reference local=\"" + StringEscapeUtils.escapeXml10(ref.getLocalColumnName())
                            + "\" foreign=\"" + StringEscapeUtils.escapeXml10(ref.getForeignColumnName()) + "\"/>\n");
                }
                output.write("\t\t</foreign-key>\n");
            }
            for (IIndex index : table.getIndices()) {
                if (index.isUnique()) {
                    output.write("\t\t<unique name=\"" + StringEscapeUtils.escapeXml10(index.getName()) + "\">\n");
                    for (IndexColumn column : index.getColumns()) {
                        output.write("\t\t\t<unique-column name=\"" + StringEscapeUtils.escapeXml10(column.getName()) + "\"/>\n");
                    }
                    handleIncludeColumns(output, index);
                } else {
                    output.write("\t\t<index name=\"" + StringEscapeUtils.escapeXml10(index.getName()) + "\">\n");
                    for (IndexColumn column : index.getColumns()) {
                        output.write("\t\t\t<index-column name=\"" + StringEscapeUtils.escapeXml10(column.getName()) + "\"");
                        if (column.getSize() != null) {
                            output.write(" size=\"" + column.getSize() + "\"");
                        }
                        output.write("/>\n");
                    }
                    handleIncludeColumns(output, index);
                }
                if (index.getPlatformIndexes() != null && index.getPlatformIndexes().size() > 0) {
                    Map<String, PlatformIndex> platformIndexes = index.getPlatformIndexes();
                    for (String key : platformIndexes.keySet()) {
                        PlatformIndex platformIndex = platformIndexes.get(key);
                        if ((platformIndex.getFilterCondition() != null && platformIndex.getFilterCondition().length() > 0) ||
                                platformIndex.getCompressionType() != CompressionTypes.NONE) {
                            output.write("\t\t\t<platform-index name=\"" + StringEscapeUtils.escapeXml10(platformIndex.getName()) + "\"");
                            if (platformIndex.getFilterCondition() != null && platformIndex.getFilterCondition().length() > 0) {
                                output.write(" filter-condition=\"" + platformIndex.getFilterCondition() + "\"");
                            }
                            if (platformIndex.getCompressionType() != CompressionTypes.NONE) {
                                output.write(" compression=\"" + platformIndex.getCompressionType().name() + "\"");
                            }
                            output.write("/>\n");
                        }
                    }
                }
                if (index.isUnique()) {
                    output.write("\t\t</unique>\n");
                } else {
                    output.write("\t\t</index>\n");
                }
            }
            for (Trigger trigger : table.getTriggers()) {
                output.write("\t\t<trigger name=\"" + StringEscapeUtils.escapeXml10(trigger.getName()) + "\">\n");
                if (trigger.getPlatformTriggers() != null) {
                    Map<String, PlatformTrigger> platformTriggers = trigger.getPlatformTriggers();
                    for (String key : platformTriggers.keySet()) {
                        PlatformTrigger platformTrigger = platformTriggers.get(key);
                        String triggerText = platformTrigger.getTriggerText();
                        output.write("\t\t\t<platform-trigger name=\"" + StringEscapeUtils.escapeXml10(platformTrigger.getName()) + "\">\n");
                        output.write("\t\t\t\t<trigger-text>");
                        output.write("<![CDATA[");
                        output.write(triggerText);
                        output.write("]]>");
                        output.write("</trigger-text>\n");
                        Function function = platformTrigger.getFunction();
                        if (function != null) {
                            output.write("\t\t\t\t<function name=\"" + StringEscapeUtils.escapeXml10(function.getFunctionName()) + "\">\n");
                            Map<String, PlatformFunction> platformFunctions = function.getPlatformFunctions();
                            for (String functionKey : platformFunctions.keySet()) {
                                PlatformFunction platformFunction = platformFunctions.get(functionKey);
                                String functionText = platformFunction.getFunctionText();
                                output.write("\t\t\t\t\t<platform-function name=\"" + StringEscapeUtils.escapeXml10(platformFunction.getName()) + "\">\n");
                                output.write("\t\t\t\t\t\t<function-text>");
                                output.write("<![CDATA[");
                                output.write(functionText);
                                output.write("]]>");
                                output.write("</function-text>\n");
                                output.write("\t\t\t\t\t</platform-function>\n");
                            }
                            output.write("\t\t\t\t</function>\n");
                        }
                        output.write("\t\t\t</platform-trigger>\n");
                    }
                }
                output.write("\t\t</trigger>\n");
            }
            output.write("\t</table>\n");
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    public static void write(View view, Writer output) {
        try {
            output.write("\t<view name=\"" + StringEscapeUtils.escapeXml10(view.getName()) + "\"");
            if (isNotBlank(view.getDescription())) {
                output.write(" description=\"" + StringEscapeUtils.escapeXml10(view.getDescription()) + "\"");
            }
            output.write(">\n");
            for (Column column : view.getColumns()) {
                writeColumn(column, output);
            }
            output.write("\t</view>\n");
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    private static void writeColumn(Column column, Writer output) throws IOException {
        output.write("\t\t<column name=\"" + StringEscapeUtils.escapeXml10(column.getName()) + "\"");
        if (column.isPrimaryKey()) {
            output.write(" primaryKey=\"" + column.isPrimaryKey() + "\"");
            output.write(" primaryKeySeq=\"" + column.getPrimaryKeySequence() + "\"");
        }
        if (column.isRequired()) {
            output.write(" required=\"" + column.isRequired() + "\"");
        }
        if (column.getMappedType() != null) {
            if (isOracle(column) && column.getMappedType().equalsIgnoreCase("date")) {
                output.write(" type=\"" + TypeMap.TIMESTAMP + "\"");
            } else {
                output.write(" type=\"" + column.getMappedType() + "\"");
            }
        }
        if (column.getSize() != null) {
            output.write(" size=\"" + column.getSize() + "\"");
        }
        if (column.getDefaultValue() != null) {
            output.write(" default=\"" + StringEscapeUtils.escapeXml10(column.getDefaultValue()) + "\"");
        }
        if (column.isAutoIncrement()) {
            output.write(" autoIncrement=\"" + column.isAutoIncrement() + "\"");
        }
        if (column.isAutoUpdate()) {
            output.write(" autoUpdate=\"" + column.isAutoUpdate() + "\"");
        }
        if (column.getJavaName() != null) {
            output.write(" javaName=\"" + column.getJavaName() + "\"");
        }
        if (column.isUnique()) {
            output.write(" unique=\"" + column.isUnique() + "\"");
        }
        if (column.isGenerated()) {
            output.write(" generated=\"" + column.isGenerated() + "\"");
        }
        if (column.isExpressionAsDefaultValue()) {
            output.write(" expressionAsDefault=\"" + column.isExpressionAsDefaultValue() + "\"");
        }
        if (column.getPlatformColumns() != null && column.getPlatformColumns().size() > 0) {
            Collection<PlatformColumn> platformColumns = column.getPlatformColumns().values();
            output.write(">\n");
            for (PlatformColumn platformColumn : platformColumns) {
                output.write("\t\t\t<platform-column name=\"" + platformColumn.getName() + "\"");
                output.write(" type=\"" + StringEscapeUtils.escapeXml10(platformColumn.getType()) + "\"");
                if (platformColumn.getSize() > 0 || (platformColumn.getSize() == 0 && isMySql(column)
                        && column.getMappedType().equalsIgnoreCase("varchar"))) {
                    output.write(" size=\"" + platformColumn.getSize() + "\"");
                }
                if (platformColumn.getDecimalDigits() > 0) {
                    output.write(" decimalDigits=\"" + platformColumn.getDecimalDigits() + "\"");
                }
                if (platformColumn.getDefaultValue() != null) {
                    output.write(" default=\"" + StringEscapeUtils.escapeXml10(platformColumn.getDefaultValue()) + "\"");
                }
                if (platformColumn.getEnumValues() != null && platformColumn.getEnumValues().length > 0) {
                    output.write(" enumValues=\"");
                    boolean writeComma = false;
                    for (String enumValue : platformColumn.getEnumValues()) {
                        if (writeComma) {
                            output.write(",");
                        }
                        output.write(enumValue);
                        writeComma = true;
                    }
                    output.write("\"");
                }
                if (platformColumn.isUserDefinedType()) {
                    output.write(" userDefinedType=\"" + platformColumn.isUserDefinedType() + "\"");
                }
                output.write("/>\n");
            }
            output.write("\t\t</column>\n");
        } else {
            output.write("/>\n");
        }
    }

    public static String writeForeignKeyOnUpdateClause(ForeignKey fk) {
        // No need to output action for RESTRICT and NO ACTION since that is the default in every database that supports foreign keys
        StringBuilder sb = new StringBuilder();
        if (fk.getOnUpdateAction() != ForeignKeyAction.RESTRICT && fk.getOnUpdateAction() != ForeignKeyAction.NOACTION) {
            sb.append(" foreignOnUpdateAction=\"" +
                    StringEscapeUtils.escapeXml10(fk.getOnUpdateAction().getForeignKeyActionName()) + "\"");
        }
        return sb.toString();
    }

    public static String writeForeignKeyOnDeleteClause(ForeignKey fk) {
        // No need to output action for RESTRICT and NO ACTION since that is the default in every database that supports foreign keys
        StringBuilder sb = new StringBuilder();
        if (fk.getOnDeleteAction() != ForeignKeyAction.RESTRICT && fk.getOnDeleteAction() != ForeignKeyAction.NOACTION) {
            sb.append(" foreignOnDeleteAction=\"" +
                    StringEscapeUtils.escapeXml10(fk.getOnDeleteAction().getForeignKeyActionName()) + "\"");
        }
        return sb.toString();
    }

    private static void handleIncludeColumns(Writer output, IIndex index) throws IOException {
        for (IndexColumn column : index.getIncludedColumns()) {
            output.write("\t\t\t<include-column name=\"" + StringEscapeUtils.escapeXml10(column.getName()) + "\"/>\n");
        }
    }
}
