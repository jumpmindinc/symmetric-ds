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
package org.jumpmind.symmetric.io.data.reader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.io.DatabaseXmlUtil;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataReader;
import org.jumpmind.util.Statistics;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class XmlDataReader extends AbstractDataReader implements IDataReader {
    protected Reader reader;
    protected DataContext context;
    protected Batch batch;
    protected Relation relation;
    protected String sourceNodeId;
    protected int lineNumber = 0;
    protected XmlPullParser parser;
    protected Statistics statistics = new Statistics();
    protected List<Object> next = new ArrayList<Object>();
    // These values will override the specification from the input stream
    protected String catalog;
    protected String schema;

    public XmlDataReader(InputStream is) {
        this(toReader(is));
    }

    public XmlDataReader(Reader reader) {
        this.reader = reader;
    }

    public void open(DataContext context) {
        try {
            this.lineNumber = 0;
            this.context = context;
            this.parser = XmlPullParserFactory.newInstance().newPullParser();
            this.parser.setInput(reader);
            readNext();
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }

    protected void readNext() {
        try {
            Map<String, String> rowData = new LinkedHashMap<String, String>();
            String columnName = null;
            CsvData data = null;
            Table table = null;
            String localCatalog = catalog;
            String localSchema = schema;
            int eventType = parser.next();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.TEXT:
                        if (columnName != null) {
                            rowData.put(columnName, parser.getText());
                            columnName = null;
                        }
                        break;
                    case XmlPullParser.START_TAG:
                        String name = parser.getName();
                        if ("row".equalsIgnoreCase(name)) {
                            data = new CsvData();
                            if (table != null) {
                                table.removeAllColumns();
                            }
                            data.setDataEventType(DataEventType.INSERT);
                        } else if ("field".equalsIgnoreCase(name)) {
                            boolean nullValue = false;
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if ("name".equalsIgnoreCase(attributeName)) {
                                    columnName = attributeValue;
                                } else if ("xsi:nil".equalsIgnoreCase(attributeName)) {
                                    nullValue = true;
                                }
                            }
                            if (nullValue) {
                                rowData.put(columnName, null);
                                columnName = null;
                            }
                        } else if ("table_data".equalsIgnoreCase(name)) {
                            Batch batch = new Batch();
                            batch.setBinaryEncoding(BinaryEncoding.BASE64);
                            next.add(batch);
                            table = new Table();
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if ("name".equalsIgnoreCase(attributeName)) {
                                    table.setName(attributeValue);
                                }
                            }
                            next.add(table);
                        } else if ("table".equalsIgnoreCase(name)) {
                            Batch batch = new Batch();
                            batch.setBinaryEncoding(BinaryEncoding.BASE64);
                            next.add(batch);
                            table = DatabaseXmlUtil.nextTable(parser);
                            next.add(table);
                            Database db = new Database();
                            db.setName("dbimport");
                            db.setCatalog(localCatalog);
                            db.setSchema(localSchema);
                            db.addTable(table);
                            String xml = DatabaseXmlUtil.toXml(db);
                            data = new CsvData(DataEventType.CREATE);
                            data.putCsvData(CsvData.ROW_DATA, CsvUtils.escapeCsvData(xml));
                            next.add(data);
                        } else if ("database".equalsIgnoreCase(name)) {
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attributeName = parser.getAttributeName(i);
                                String attributeValue = parser.getAttributeValue(i);
                                if ("catalog".equalsIgnoreCase(attributeName)) {
                                    localCatalog = StringUtils.isBlank(catalog) ? attributeValue : catalog;
                                } else if ("schema".equalsIgnoreCase(attributeName)) {
                                    localSchema = StringUtils.isBlank(schema) ? attributeValue : schema;
                                }
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        name = parser.getName();
                        if ("row".equalsIgnoreCase(name)) {
                            String[] columnNames = rowData.keySet().toArray(
                                    new String[rowData.size()]);
                            for (String colName : columnNames) {
                                table.addColumn(new Column(colName));
                            }
                            String[] columnValues = rowData.values().toArray(
                                    new String[rowData.size()]);
                            data.putParsedData(CsvData.ROW_DATA, columnValues);
                            if (this.relation == null || !this.relation.equals(table)) {
                                next.add(table);
                            }
                            next.add(data);
                            rowData = new LinkedHashMap<String, String>();
                        } else if ("table_data".equalsIgnoreCase(name)) {
                            if (batch != null) {
                                batch.setComplete(true);
                            }
                        } else if ("field".equalsIgnoreCase(name)) {
                            columnName = null;
                        }
                        break;
                }
                eventType = parser.next();
            }
        } catch (IOException ex) {
            throw new IoException(ex);
        } catch (XmlPullParserException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Batch nextBatch() {
        do {
            readNext();
            if (next.size() > 0) {
                Object o = next.remove(0);
                if (o instanceof Batch) {
                    batch = (Batch) o;
                    return batch;
                }
            }
        } while (next.size() > 0);
        return null;
    }

    public Relation nextRelation() {
        this.relation = null;
        do {
            readNext();
            if (next.size() > 0) {
                Object o = next.remove(0);
                if (o instanceof Relation) {
                    this.relation = (Relation) o;
                    break;
                }
            }
        } while (next.size() > 0);
        if (this.relation == null && batch != null) {
            batch.setComplete(true);
        }
        return this.relation;
    }

    public CsvData nextData() {
        readNext();
        if (next.size() > 0 && next.get(0) instanceof CsvData) {
            return (CsvData) next.remove(0);
        }
        return null;
    }

    public void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
        }
    }

    public Map<Batch, Statistics> getStatistics() {
        Map<Batch, Statistics> map = new HashMap<Batch, Statistics>(1);
        map.put(batch, statistics);
        return map;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
