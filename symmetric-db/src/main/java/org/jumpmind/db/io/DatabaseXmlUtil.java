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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/*
 * This class provides functions to read and write database models from/to XML.
 */
public class DatabaseXmlUtil {
    private static final Logger log = LoggerFactory.getLogger(DatabaseXmlUtil.class);
    public static final String DTD_PREFIX = "http://db.apache.org/torque/dtd/database";
    private static final String ATTR_DESCRIPTION = "description";
    private static final String ATTR_DEFAULT = "default";
    private static final String ELEMENT_INDEX = "index";
    private static final String ELEMENT_UNIQUE = "unique";

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
                    default:
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
            TableParseContext context = new TableParseContext();
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT && !context.done) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        processTableStartTag(context, parser.getName(), catalog, schema, parser);
                        break;
                    case XmlPullParser.END_TAG:
                        processTableEndTag(context, parser.getName());
                        break;
                    default:
                        break;
                }
                if (!context.done) {
                    eventType = parser.next();
                }
            }
            return context.table;
        } catch (XmlPullParserException e) {
            throw new IoException(e);
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    private static void processTableStartTag(TableParseContext context, String name, String catalog, String schema,
            XmlPullParser parser) throws XmlPullParserException, IOException {
        if (name.equalsIgnoreCase("table")) {
            context.table = parseTableElement(catalog, schema, parser);
        } else if (name.equalsIgnoreCase("column")) {
            Column column = parseColumnElement(parser);
            if (context.table != null) {
                context.table.addColumn(column);
            }
        } else if (name.equalsIgnoreCase("platform-column")) {
            PlatformColumn pc = parsePlatformColumnElement(parser);
            if (context.table != null && context.table.getColumnCount() > 0) {
                context.table.getColumn(context.table.getColumnCount() - 1).addPlatformColumn(pc);
            }
        } else {
            processFkIndexOrTriggerStartTag(context, name, catalog, schema, parser);
        }
    }

    private static void processFkIndexOrTriggerStartTag(TableParseContext context, String name,
            String catalog, String schema, XmlPullParser parser) throws XmlPullParserException, IOException {
        if (name.equalsIgnoreCase("foreign-key")) {
            context.fk = parseForeignKeyElement(parser);
            context.table.addForeignKey(context.fk);
        } else if (name.equalsIgnoreCase("reference")) {
            context.fk.addReference(parseReferenceElement(parser));
        } else if (name.equalsIgnoreCase(ELEMENT_INDEX) || name.equalsIgnoreCase(ELEMENT_UNIQUE)) {
            context.index = parseIndexElement(name, parser);
            context.table.addIndex(context.index);
        } else if (name.equalsIgnoreCase("index-column") || name.equalsIgnoreCase("unique-column")) {
            IndexColumn ic = parseIndexColumnElement(parser);
            ic.setColumn(context.table.getColumnWithName(ic.getName()));
            if (context.index != null) {
                context.index.addColumn(ic);
            }
        } else if (name.equalsIgnoreCase("include-column")) {
            IndexColumn ic = parseIncludeColumnElement(parser);
            ic.setColumn(context.table.getColumnWithName(ic.getName()));
            if (context.index != null) {
                context.index.addIncludedColumn(ic);
            }
        } else if (name.equalsIgnoreCase("platform-index")) {
            if (context.index != null) {
                context.index.addPlatformIndex(parsePlatformIndexElement(parser));
            }
        } else {
            processTriggerStartTag(context, name, catalog, schema, parser);
        }
    }

    private static void processTriggerStartTag(TableParseContext context, String name,
            String catalog, String schema, XmlPullParser parser) throws XmlPullParserException, IOException {
        if (name.equalsIgnoreCase("trigger")) {
            context.trigger = parseTriggerElement(catalog, schema, parser);
            if (context.table != null) {
                context.table.addTrigger(context.trigger);
            }
        } else if (name.equalsIgnoreCase("platform-trigger")) {
            context.platformTrigger = parsePlatformTriggerElement(parser);
            if (context.trigger != null) {
                context.trigger.addPlatformTrigger(context.platformTrigger);
            }
        } else if (name.equalsIgnoreCase("trigger-text")) {
            context.platformTrigger.setTriggerText(parser.nextText());
        } else if (name.equalsIgnoreCase("function")) {
            context.function = parseFunctionElement(catalog, schema, parser);
            if (context.platformTrigger != null) {
                context.platformTrigger.setFunction(context.function);
            }
        } else if (name.equalsIgnoreCase("platform-function")) {
            context.platformFunction = parsePlatformFunctionElement(parser);
            if (context.function != null) {
                context.function.addPlatformFunction(context.platformFunction);
            }
        } else if (name.equalsIgnoreCase("function-text")) {
            context.platformFunction.setFunctionText(parser.nextText());
        }
    }

    private static void processTableEndTag(TableParseContext context, String name) {
        if (name.equalsIgnoreCase("table")) {
            context.done = true;
        } else if (name.equalsIgnoreCase(ELEMENT_INDEX) || name.equalsIgnoreCase(ELEMENT_UNIQUE)) {
            context.index = null;
        } else if (name.equalsIgnoreCase("foreign-key")) {
            context.fk = null;
        } else if (name.equalsIgnoreCase("trigger")) {
            context.trigger = null;
        } else if (name.equalsIgnoreCase("platform-trigger")) {
            context.platformTrigger = null;
        } else if (name.equalsIgnoreCase("function")) {
            context.function = null;
        } else if (name.equalsIgnoreCase("platform-function")) {
            context.platformFunction = null;
        }
    }

    private static Table parseTableElement(String catalog, String schema, XmlPullParser parser) {
        Table table = new Table();
        table.setCatalog(catalog);
        table.setSchema(schema);
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            String attrValue = parser.getAttributeValue(i);
            if (attrName.equalsIgnoreCase("name")) {
                table.setName(attrValue);
            } else if (attrName.equalsIgnoreCase(ATTR_DESCRIPTION)) {
                table.setDescription(attrValue);
            } else if (attrName.equalsIgnoreCase("logging")) {
                table.setLogging(!("false".equalsIgnoreCase(attrValue)));
            } else if (attrName.equalsIgnoreCase("compression")) {
                if (CompressionTypes.PAGE.name().equalsIgnoreCase(attrValue)) {
                    table.setCompressionType(CompressionTypes.PAGE);
                } else if (CompressionTypes.ROW.name().equalsIgnoreCase(attrValue)) {
                    table.setCompressionType(CompressionTypes.ROW);
                } else if (CompressionTypes.COLUMNSTORE.name().equalsIgnoreCase(attrValue)) {
                    table.setCompressionType(CompressionTypes.COLUMNSTORE);
                } else if (CompressionTypes.COLUMNSTORE_ARCHIVE.name().equals(attrValue)) {
                    table.setCompressionType(CompressionTypes.COLUMNSTORE_ARCHIVE);
                } else {
                    table.setCompressionType(CompressionTypes.NONE);
                }
            }
        }
        return table;
    }

    private static Column parseColumnElement(XmlPullParser parser) {
        Column column = new Column();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            applyColumnAttribute(column, parser.getAttributeName(i), parser.getAttributeValue(i));
        }
        if (column.isPersisted() && !column.isGenerated()) {
            log.warn("Database XML defines column {} as persisted but not generated. Persisted and generated should go together.",
                    column.getName());
        }
        return column;
    }

    private static void applyColumnAttribute(Column column, String attrName, String attrValue) {
        if (attrName.equalsIgnoreCase("name")) {
            column.setName(attrValue);
        } else if (attrName.equalsIgnoreCase("primaryKey")) {
            column.setPrimaryKey(FormatUtils.toBoolean(attrValue));
        } else if (attrName.equalsIgnoreCase("primaryKeySeq")) {
            column.setPrimaryKeySequence(Integer.parseInt(attrValue));
        } else if (attrName.equalsIgnoreCase("required")) {
            column.setRequired(FormatUtils.toBoolean(attrValue));
        } else if (attrName.equalsIgnoreCase("type")) {
            column.setMappedType(attrValue);
        } else if (attrName.equalsIgnoreCase("size")) {
            column.setSize(attrValue);
        } else if (attrName.equalsIgnoreCase(ATTR_DEFAULT)) {
            column.setDefaultValue(attrValue);
        } else if (attrName.equalsIgnoreCase("autoIncrement")) {
            column.setAutoIncrement(FormatUtils.toBoolean(attrValue));
        } else if (attrName.equalsIgnoreCase("autoUpdate")) {
            column.setAutoUpdate(FormatUtils.toBoolean(attrValue));
        } else if (attrName.equalsIgnoreCase("javaName")) {
            column.setJavaName(attrValue);
        } else if (attrName.equalsIgnoreCase(ATTR_DESCRIPTION)) {
            column.setDescription(attrValue);
        } else if (attrName.equalsIgnoreCase("unique")) {
            column.setUnique(FormatUtils.toBoolean(attrValue));
        } else if (attrName.equalsIgnoreCase("generated")) {
            column.setGenerated(FormatUtils.toBoolean(attrValue));
        } else if (attrName.equalsIgnoreCase("persisted")) {
            column.setPersisted(FormatUtils.toBoolean(attrValue));
        } else if (attrName.equalsIgnoreCase("expressionAsDefault")) {
            column.setExpressionAsDefaultValue(FormatUtils.toBoolean(attrValue));
        }
    }

    private static PlatformColumn parsePlatformColumnElement(XmlPullParser parser) {
        PlatformColumn pc = new PlatformColumn();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            String attrValue = parser.getAttributeValue(i);
            if (attrName.equalsIgnoreCase("name")) {
                pc.setName(attrValue);
            } else if (attrName.equalsIgnoreCase("type")) {
                pc.setType(attrValue);
            } else if (attrName.equalsIgnoreCase(ATTR_DEFAULT)) {
                pc.setDefaultValue(attrValue);
            } else if (attrName.equalsIgnoreCase("size") && isNotBlank(attrValue)) {
                pc.setSize(Integer.parseInt(attrValue));
            } else if (attrName.equalsIgnoreCase("decimalDigits") && isNotBlank(attrValue)) {
                pc.setDecimalDigits(Integer.parseInt(attrValue));
            } else if (attrName.equalsIgnoreCase("enumValues") && isNotBlank(attrValue)) {
                pc.setEnumValues(attrValue.split(","));
            } else if (attrName.equalsIgnoreCase("userDefinedType")) {
                pc.setUserDefinedType(Boolean.parseBoolean(attrValue));
            }
        }
        return pc;
    }

    private static ForeignKey parseForeignKeyElement(XmlPullParser parser) {
        ForeignKey fk = new ForeignKey();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            String attrValue = parser.getAttributeValue(i);
            if (attrName.equalsIgnoreCase("name")) {
                fk.setName(attrValue);
            } else if (attrName.equalsIgnoreCase("foreignTable")) {
                fk.setForeignTableName(attrValue);
            } else if (attrName.equalsIgnoreCase("foreignTableCatalog")) {
                fk.setForeignTableCatalog(attrValue);
            } else if (attrName.equalsIgnoreCase("foreignTableSchema")) {
                fk.setForeignTableSchema(attrValue);
            } else if (attrName.equalsIgnoreCase("foreignOnUpdateAction")) {
                fk.setOnUpdateAction(ForeignKey.getForeignKeyActionByForeignKeyActionName(attrValue));
            } else if (attrName.equalsIgnoreCase("foreignOnDeleteAction")) {
                fk.setOnDeleteAction(ForeignKey.getForeignKeyActionByForeignKeyActionName(attrValue));
            }
        }
        return fk;
    }

    private static Reference parseReferenceElement(XmlPullParser parser) {
        Reference ref = new Reference();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            String attrValue = parser.getAttributeValue(i);
            if (attrName.equalsIgnoreCase("local")) {
                ref.setLocalColumnName(attrValue);
            } else if (attrName.equalsIgnoreCase("foreign")) {
                ref.setForeignColumnName(attrValue);
            }
        }
        return ref;
    }

    private static IIndex parseIndexElement(String elementName, XmlPullParser parser) {
        IIndex index = elementName.equalsIgnoreCase("index") ? new NonUniqueIndex() : new UniqueIndex();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (parser.getAttributeName(i).equalsIgnoreCase("name")) {
                index.setName(parser.getAttributeValue(i));
            }
        }
        return index;
    }

    private static IndexColumn parseIndexColumnElement(XmlPullParser parser) {
        IndexColumn ic = new IndexColumn();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            String attrValue = parser.getAttributeValue(i);
            if (attrName.equalsIgnoreCase("name")) {
                ic.setName(attrValue);
            } else if (attrName.equalsIgnoreCase("size")) {
                ic.setSize(attrValue);
            }
        }
        return ic;
    }

    private static IndexColumn parseIncludeColumnElement(XmlPullParser parser) {
        IndexColumn ic = new IndexColumn();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (parser.getAttributeName(i).equalsIgnoreCase("name")) {
                ic.setName(parser.getAttributeValue(i));
            }
        }
        return ic;
    }

    private static PlatformIndex parsePlatformIndexElement(XmlPullParser parser) {
        PlatformIndex pi = new PlatformIndex();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            String attrValue = parser.getAttributeValue(i);
            if (attrName.equalsIgnoreCase("name")) {
                pi.setName(attrValue);
            } else if (attrName.equalsIgnoreCase("filter-condition")) {
                pi.setFilterCondition(attrValue);
            } else if (attrName.equalsIgnoreCase("compression")) {
                if (CompressionTypes.ROW.name().equalsIgnoreCase(attrValue)) {
                    pi.setCompressionType(CompressionTypes.ROW);
                } else if (CompressionTypes.PAGE.name().equalsIgnoreCase(attrValue)) {
                    pi.setCompressionType(CompressionTypes.PAGE);
                } else if (CompressionTypes.COLUMNSTORE.name().equalsIgnoreCase(attrValue)) {
                    pi.setCompressionType(CompressionTypes.COLUMNSTORE);
                } else if (CompressionTypes.COLUMNSTORE_ARCHIVE.name().equals(attrValue)) {
                    pi.setCompressionType(CompressionTypes.COLUMNSTORE_ARCHIVE);
                } else {
                    pi.setCompressionType(CompressionTypes.NONE);
                }
            }
        }
        return pi;
    }

    private static Trigger parseTriggerElement(String catalog, String schema, XmlPullParser parser) {
        Trigger trigger = new Trigger();
        trigger.setCatalogName(catalog);
        trigger.setSchemaName(schema);
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (parser.getAttributeName(i).equalsIgnoreCase("name")) {
                trigger.setName(parser.getAttributeValue(i));
            }
        }
        return trigger;
    }

    private static PlatformTrigger parsePlatformTriggerElement(XmlPullParser parser) {
        PlatformTrigger pt = new PlatformTrigger();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (parser.getAttributeName(i).equalsIgnoreCase("name")) {
                pt.setName(parser.getAttributeValue(i));
            }
        }
        return pt;
    }

    private static Function parseFunctionElement(String catalog, String schema, XmlPullParser parser) {
        Function function = new Function();
        function.setCatalogName(catalog);
        function.setSchemaName(schema);
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (parser.getAttributeName(i).equalsIgnoreCase("name")) {
                function.setFunctionName(parser.getAttributeValue(i));
            }
        }
        return function;
    }

    private static PlatformFunction parsePlatformFunctionElement(XmlPullParser parser) {
        PlatformFunction pf = new PlatformFunction();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (parser.getAttributeName(i).equalsIgnoreCase("name")) {
                pf.setName(parser.getAttributeValue(i));
            }
        }
        return pf;
    }

    public static View nextView(XmlPullParser parser, String catalog, String schema) {
        try {
            View view = null;
            boolean done = false;
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT && !done) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        view = processViewStartTag(view, catalog, schema, parser.getName(), parser);
                        break;
                    case XmlPullParser.END_TAG:
                        if (parser.getName().equalsIgnoreCase("view")) {
                            done = true;
                        }
                        break;
                    default:
                        break;
                }
                eventType = parser.next();
            }
            return view;
        } catch (XmlPullParserException | IOException e) {
            throw new IoException(e);
        }
    }

    private static View processViewStartTag(View view, String catalog, String schema, String name, XmlPullParser parser) {
        if (name.equalsIgnoreCase("view")) {
            view = new View();
            view.setCatalog(catalog);
            view.setSchema(schema);
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                applyViewAttribute(view, parser.getAttributeName(i), parser.getAttributeValue(i));
            }
        } else if (name.equalsIgnoreCase("column") && view != null) {
            view.addColumn(parseViewColumn(parser));
        }
        return view;
    }

    private static void applyViewAttribute(View view, String attrName, String attrValue) {
        if (attrName.equalsIgnoreCase("name")) {
            view.setName(attrValue);
        } else if (attrName.equalsIgnoreCase(ATTR_DESCRIPTION)) {
            view.setDescription(attrValue);
        }
    }

    private static Column parseViewColumn(XmlPullParser parser) {
        Column column = new Column();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            String attrValue = parser.getAttributeValue(i);
            if (attrName.equalsIgnoreCase("name")) {
                column.setName(attrValue);
            } else if (attrName.equalsIgnoreCase("type")) {
                column.setMappedType(attrValue);
            } else if (attrName.equalsIgnoreCase("size")) {
                column.setSize(attrValue);
            } else if (attrName.equalsIgnoreCase("required")) {
                column.setRequired(FormatUtils.toBoolean(attrValue));
            } else if (attrName.equalsIgnoreCase(ATTR_DEFAULT)) {
                column.setDefaultValue(attrValue);
            }
        }
        return column;
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
            if (!table.getLogging()) {
                output.write(" logging=\"false\"");
            }
            output.write(">\n");
            for (Column column : table.getColumns()) {
                writeColumn(column, output);
            }
            writeForeignKeyElements(table, output);
            writeIndexElements(table, output);
            writeTriggerElements(table, output);
            output.write("\t</table>\n");
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    private static void writeForeignKeyElements(Table table, Writer output) throws IOException {
        for (ForeignKey fk : table.getForeignKeys()) {
            String name = fk.getName() == null ? "" : fk.getName();
            output.write("\t\t<foreign-key name=\"" + StringEscapeUtils.escapeXml10(name) + "\" foreignTable=\""
                    + StringEscapeUtils.escapeXml10(fk.getForeignTableName()) + "\" foreignTableCatalog=\""
                    + StringEscapeUtils.escapeXml10(fk.getForeignTableCatalog() == null || fk.getForeignTableCatalog().equals(table.getCatalog())
                            ? ""
                            : fk.getForeignTableCatalog())
                    + "\" foreignTableSchema=\"" + StringEscapeUtils.escapeXml10(
                            fk.getForeignTableSchema() == null || fk.getForeignTableSchema().equals(table.getSchema())
                                    ? ""
                                    : fk.getForeignTableSchema()) + "\""
                    + writeForeignKeyOnUpdateClause(fk) + writeForeignKeyOnDeleteClause(fk) + ">\n");
            for (Reference ref : fk.getReferences()) {
                output.write("\t\t\t<reference local=\"" + StringEscapeUtils.escapeXml10(ref.getLocalColumnName())
                        + "\" foreign=\"" + StringEscapeUtils.escapeXml10(ref.getForeignColumnName()) + "\"/>\n");
            }
            output.write("\t\t</foreign-key>\n");
        }
    }

    private static void writeIndexElements(Table table, Writer output) throws IOException {
        for (IIndex index : table.getIndices()) {
            writeIndexElement(index, output);
        }
    }

    private static void writeIndexElement(IIndex index, Writer output) throws IOException {
        if (index.isUnique()) {
            output.write("\t\t<unique name=\"" + StringEscapeUtils.escapeXml10(index.getName()) + "\">\n");
            for (IndexColumn column : index.getColumns()) {
                output.write("\t\t\t<unique-column name=\"" + StringEscapeUtils.escapeXml10(column.getName()) + "\"/>\n");
            }
        } else {
            output.write("\t\t<index name=\"" + StringEscapeUtils.escapeXml10(index.getName()) + "\">\n");
            for (IndexColumn column : index.getColumns()) {
                output.write("\t\t\t<index-column name=\"" + StringEscapeUtils.escapeXml10(column.getName()) + "\"");
                if (column.getSize() != null) {
                    output.write(" size=\"" + column.getSize() + "\"");
                }
                output.write("/>\n");
            }
        }
        handleIncludeColumns(output, index);
        writePlatformIndexElements(index, output);
        output.write(index.isUnique() ? "\t\t</unique>\n" : "\t\t</index>\n");
    }

    private static void writePlatformIndexElements(IIndex index, Writer output) throws IOException {
        if (index.getPlatformIndexes() == null || index.getPlatformIndexes().isEmpty()) {
            return;
        }
        for (PlatformIndex platformIndex : index.getPlatformIndexes().values()) {
            boolean hasFilter = platformIndex.getFilterCondition() != null && !platformIndex.getFilterCondition().isEmpty();
            boolean hasCompression = platformIndex.getCompressionType() != CompressionTypes.NONE;
            if (hasFilter || hasCompression) {
                output.write("\t\t\t<platform-index name=\"" + StringEscapeUtils.escapeXml10(platformIndex.getName()) + "\"");
                if (hasFilter) {
                    output.write(" filter-condition=\"" + platformIndex.getFilterCondition() + "\"");
                }
                if (hasCompression) {
                    output.write(" compression=\"" + platformIndex.getCompressionType().name() + "\"");
                }
                output.write("/>\n");
            }
        }
    }

    private static void writeTriggerElements(Table table, Writer output) throws IOException {
        for (Trigger trigger : table.getTriggers()) {
            output.write("\t\t<trigger name=\"" + StringEscapeUtils.escapeXml10(trigger.getName()) + "\">\n");
            if (trigger.getPlatformTriggers() != null) {
                for (PlatformTrigger platformTrigger : trigger.getPlatformTriggers().values()) {
                    writePlatformTriggerElement(platformTrigger, output);
                }
            }
            output.write("\t\t</trigger>\n");
        }
    }

    private static void writePlatformTriggerElement(PlatformTrigger platformTrigger, Writer output) throws IOException {
        output.write("\t\t\t<platform-trigger name=\"" + StringEscapeUtils.escapeXml10(platformTrigger.getName()) + "\">\n");
        output.write("\t\t\t\t<trigger-text><![CDATA[" + platformTrigger.getTriggerText() + "]]></trigger-text>\n");
        Function function = platformTrigger.getFunction();
        if (function != null) {
            output.write("\t\t\t\t<function name=\"" + StringEscapeUtils.escapeXml10(function.getFunctionName()) + "\">\n");
            for (PlatformFunction platformFunction : function.getPlatformFunctions().values()) {
                output.write("\t\t\t\t\t<platform-function name=\"" + StringEscapeUtils.escapeXml10(platformFunction.getName()) + "\">\n");
                output.write("\t\t\t\t\t\t<function-text><![CDATA[" + platformFunction.getFunctionText() + "]]></function-text>\n");
                output.write("\t\t\t\t\t</platform-function>\n");
            }
            output.write("\t\t\t\t</function>\n");
        }
        output.write("\t\t\t</platform-trigger>\n");
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
        if (column.isPersisted()) {
            output.write(" persisted=\"true\"");
        }
        if (column.isExpressionAsDefaultValue()) {
            output.write(" expressionAsDefault=\"" + column.isExpressionAsDefaultValue() + "\"");
        }
        if (column.getPlatformColumns() != null && !column.getPlatformColumns().isEmpty()) {
            output.write(">\n");
            for (PlatformColumn platformColumn : column.getPlatformColumns().values()) {
                writePlatformColumn(column, platformColumn, output);
            }
            output.write("\t\t</column>\n");
        } else {
            output.write("/>\n");
        }
    }

    private static void writePlatformColumn(Column column, PlatformColumn platformColumn, Writer output) throws IOException {
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

    private static final class TableParseContext {
        Table table;
        ForeignKey fk;
        IIndex index;
        Trigger trigger;
        PlatformTrigger platformTrigger;
        Function function;
        PlatformFunction platformFunction;
        boolean done;
    }
}
