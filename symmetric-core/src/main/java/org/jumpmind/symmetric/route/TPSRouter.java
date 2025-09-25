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
package org.jumpmind.symmetric.route;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.csv.CsvWriter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import nl.cad.tpsparse.tps.TpsFile;
import nl.cad.tpsparse.tps.record.DataRecord;
import nl.cad.tpsparse.tps.record.FieldDefinitionRecord;
import nl.cad.tpsparse.tps.record.TableDefinitionRecord;
import nl.cad.tpsparse.tps.record.TableNameRecord;

public class TPSRouter extends AbstractFileParsingRouter implements IDataRouter, IBuiltInExtensionPoint {
    private ISymmetricEngine engine;
    private List<String> fields = new ArrayList<String>();
    private TpsFile tpsFile;

    public TPSRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @Override
    public ISymmetricEngine getEngine() {
        return this.engine;
    }

    @Override
    public List<String> parse(InputStream in, String fileName, int lineNumber, int tableId) {
        List<String> rows = new ArrayList<String>();
        TableDefinitionRecord table = tpsFile.getTableDefinitions(false).get(tableId);
        fields.clear();
        if (table != null && table.getFields() != null) {
            for (FieldDefinitionRecord field : table.getFields()) {
                fields.add(field.getFieldNameNoTable());
            }
            int currentLine = 1;
            for (DataRecord rec : tpsFile.getDataRecords(tableId, table, false)) {
                if (currentLine > lineNumber) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    CsvWriter writer = new CsvWriter(new OutputStreamWriter(out), ',');
                    writer.setEscapeMode(CsvWriter.ESCAPE_MODE_BACKSLASH);
                    writer.setTextQualifier('\"');
                    writer.setUseTextQualifier(true);
                    writer.setForceQualifier(true);
                    try {
                        int fieldPosition = 1;
                        for (Object val : rec.getValues()) {
                            String value = val.toString();
                            if (val instanceof Object[]) {
                                if (((Object[]) val).length > 0) {
                                    int position = 0;
                                    boolean multipleValues = false;
                                    for (Object elem : (Object[]) val) {
                                        // Take only first value in array for now
                                        if (position == 0) {
                                            value = elem.toString();
                                        } else if (elem instanceof Integer) {
                                            if (((Integer) elem) > 0) {
                                                multipleValues = true;
                                            }
                                        } else if (elem instanceof String) {
                                            if (((String) elem).length() > 0) {
                                                multipleValues = true;
                                            }
                                        } else if (elem instanceof Short) {
                                            if (((Short) elem) > 0) {
                                                multipleValues = true;
                                            }
                                        } else {
                                            log.warn("Unchecked array type in TPS parsing " + elem.getClass());
                                        }
                                        position++;
                                    }
                                    if (multipleValues) {
                                        log.debug("Line number " + currentLine + " in file " + fileName + ", field number " + fieldPosition
                                                + " contains array with multiple values");
                                    }
                                } else {
                                    value = "";
                                }
                            }
                            writer.write(removeIllegalCharacters(value), true);
                            fieldPosition++;
                        }
                    } catch (IOException ioe) {
                        log.info("Unable to create row data while parsing TPS file", ioe);
                    } catch (Exception e) {
                        log.info("parse error.");
                    }
                    writer.close();
                    rows.add(out.toString());
                }
                currentLine++;
            }
        }
        return rows;
    }

    protected String removeIllegalCharacters(String formattedData) {
        StringBuilder buff = new StringBuilder(formattedData.length());
        for (char c : formattedData.toCharArray()) {
            if (c >= 0 && c < 31) {
                if (c == '\n' || c == '\t' || c == '\r') {
                    buff.append(c);
                }
            } else {
                if (c != 127) {
                    buff.append(c);
                }
            }
        }
        return buff.toString();
    }

    @Override
    public String getColumnNames() {
        StringBuilder columns = new StringBuilder();
        try {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    columns.append(",");
                }
                columns.append(fields.get(i));
            }
        } catch (Exception e) {
            log.error("Unable to read column names for TPS file ", e);
        }
        return columns.toString();
    }

    @Override
    @SuppressFBWarnings("ITU_INAPPROPRIATE_TOSTRING_USE")
    public Map<Integer, String> getTableNames(String tableName, InputStream in) throws IOException {
        tpsFile = new TpsFile(in);
        Map<Integer, String> tableNames = new HashMap<Integer, String>();
        int tableNumber = 0;
        for (TableNameRecord tableNameRecord : ((TpsFile) tpsFile).getTableNameRecords()) {
            String tableStr = tableNameRecord.toString();
            String parsedName = tableStr.substring(tableStr.indexOf("(") + 1, tableStr.indexOf(","));
            if (!parsedName.startsWith("UNNAMED")) {
                tableNames.put(tableNameRecord.getTableNumber(), tableName + "_" + parsedName);
            }
            tableNumber = tableNameRecord.getTableNumber();
        }
        if (tableNames.size() == 0) {
            tableNames.put(tableNumber, tableName);
        }
        return tableNames;
    }
}
