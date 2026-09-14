/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.io.data.writer;

import java.math.BigDecimal;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.cassandra.CassandraPlatform;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class CassandraDatabaseWriter extends DynamicDefaultDatabaseWriter {
    private static final Logger log = LoggerFactory.getLogger(CassandraDatabaseWriter.class);
    protected Gson gson = new Gson();
    protected Session session;
    protected Map<String, Map<String, Table>> metaData = new HashMap<String, Map<String, Table>>();
    protected PreparedStatement pstmt;
    SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public CassandraDatabaseWriter(IDatabasePlatform symmetricPlatform,
            IDatabasePlatform targetPlatform, String prefix,
            IDatabaseWriterConflictResolver conflictResolver, DatabaseWriterSettings settings) {
        super(symmetricPlatform, targetPlatform, prefix, conflictResolver, settings);
        this.metaData = ((CassandraPlatform) targetPlatform).getMetaData();
        this.session = ((CassandraPlatform) targetPlatform).getSession();
    }

    @Override
    protected void prepare() {
        if (isSymmetricTable(this.targetTable)) {
            super.prepare();
        } else {
            pstmt = session.prepare(currentDmlStatement.getSql());
        }
    }

    @Override
    protected void prepare(String sql, CsvData data) {
        if (isSymmetricTable(this.targetTable) && !isUserSendSql(sql, data)) {
            super.prepare(sql, data);
        } else {
            pstmt = session.prepare(sql);
        }
    }

    /*
     * Checks if a send sql event type was for the sym_node table. If it is the send sql shoudl run against Cassandra tables otherwise it is an internal
     * Symmetric send sql.
     */
    protected boolean isUserSendSql(String sql, CsvData data) {
        return data.getDataEventType() == DataEventType.SQL
                && this.targetTable.getNameLowerCase().equals(this.getTablePrefix().toLowerCase() + "_node")
                && !sql.toLowerCase().contains("from " + this.getTablePrefix().toLowerCase() + "_node");
    }

    @Override
    public int prepareAndExecute(String sql, CsvData data) {
        if (isUserSendSql(sql, data)) {
            return session.execute(sql).wasApplied() ? 1 : 0;
        } else {
            return super.prepareAndExecute(sql, data);
        }
    }

    @Override
    protected int execute(CsvData data, String[] values) {
        if (isSymmetricTable(this.targetTable)) {
            return super.execute(data, values);
        }
        BoundStatement bstmt = pstmt.bind();
        currentDmlValues = getPlatform().getObjectValues(batch.getBinaryEncoding(), values,
                currentDmlStatement.getMetaData(), false, writerSettings.isFitToColumn());
        if (log.isDebugEnabled()) {
            log.debug("Submitting data [{}] with types [{}]",
                    dmlValuesToString(currentDmlValues, this.currentDmlStatement.getTypes()),
                    TypeMap.getJdbcTypeDescriptions(this.currentDmlStatement.getTypes()));
        }
        bindVariables(bstmt, this.currentDmlStatement.getColumns(), this.currentDmlStatement.getTypes(), values);
        return session.execute(bstmt).wasApplied() ? 1 : 0;
    }

    @Override
    protected Table lookupTableAtTarget(Table sourceTable) {
        if (sourceTable == null) {
            throw new RuntimeException("Source table is null");
        }
        if (isSymmetricTable(sourceTable)) {
            return super.lookupTableAtTarget(sourceTable);
        }
        String keyspace = sourceTable.getCatalog() == null ? sourceTable.getSchema() : sourceTable.getCatalog();
        Map<String, Table> tables = metaData.get(keyspace);
        Table returnTable = tables == null ? sourceTable : tables.get(sourceTable.getName());
        // ADD target table param is missing do not error
        if (returnTable == null) {
            throw new RuntimeException("Unable to find Cassandra target table " + sourceTable.getName() + " in keyspace " + keyspace);
        }
        return returnTable;
    }

    @Override
    protected boolean create(CsvData data) {
        return false;
    }

    @Override
    protected void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails) {
    }

    @Override
    protected void allowInsertIntoAutoIncrementColumns(boolean value, Table table) {
    }

    @SuppressWarnings("unchecked")
    protected void bindVariables(BoundStatement bstmt, Column[] columns, int[] types, String[] values) {
        // TODO data time mappings
        int i = 0;
        for (int type : types) {
            if (Types.INTEGER == type) {
                bstmt.setInt(i, Integer.parseInt(values[i]));
            } else if (Types.VARCHAR == type) {
                bstmt.setString(i, values[i]);
            } else if (Types.JAVA_OBJECT == type) {
                bstmt.setUUID(i, UUID.fromString(values[i]));
            } else if (Types.TIMESTAMP == type) {
                try {
                    bstmt.setTimestamp(i, tsFormat.parse(values[i]));
                } catch (ParseException e) {
                    throw new RuntimeException("Unable to bind timestamp column " + columns[i].getName() + " with value " + values[i], e);
                }
            } else if (Types.DATE == type) {
                try {
                    bstmt.setDate(i, LocalDate.fromMillisSinceEpoch(dateFormat.parse(values[i]).getTime()));
                } catch (ParseException e) {
                    throw new RuntimeException("Unable to bind date column " + columns[i].getName() + " with value " + values[i], e);
                }
            } else if (Types.TIME == type) {
                try {
                    bstmt.setTime(i, LocalTime.parse(values[i], timeFormat).toNanoOfDay());
                } catch (DateTimeParseException e) {
                    throw new RuntimeException("Unable to bind time column " + columns[i].getName() + " with value " + values[i], e);
                }
            } else if (Types.BOOLEAN == type) {
                bstmt.setBool(i, Boolean.parseBoolean(values[i]));
            } else if (Types.DECIMAL == type) {
                bstmt.setDecimal(i, new BigDecimal(values[i]));
            } else if (Types.DOUBLE == type) {
                bstmt.setDouble(i, Double.parseDouble(values[i]));
            } else if (Types.FLOAT == type) {
                bstmt.setFloat(i, Float.parseFloat(values[i]));
            } else if (Types.STRUCT == type) {
                bstmt.setList(i, parseList(columns[i], values[i]));
            } else if (Types.REF == type) {
                bstmt.setSet(i, parseSet(columns[i], values[i]));
            } else if (Types.OTHER == type) {
                bstmt.setMap(i, parseMap(columns[i], values[i]));
            }
            i++;
        }
    }

    @SuppressWarnings("rawtypes")
    protected List parseList(Column c, String val) {
        try {
            if (c.getDescription() != null) {
                if (c.getDescription().toLowerCase().equals("text") || c.getDescription().toLowerCase().equals("varchar")) {
                    return gson.fromJson(val, new TypeToken<List<String>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("int")) {
                    return gson.fromJson(val, new TypeToken<List<Integer>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("bigint")) {
                    return gson.fromJson(val, new TypeToken<List<Integer>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("smallint")) {
                    return gson.fromJson(val, new TypeToken<List<Integer>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("tinyint")) {
                    return gson.fromJson(val, new TypeToken<List<Integer>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("double")) {
                    return gson.fromJson(val, new TypeToken<List<Double>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("decimal")) {
                    return gson.fromJson(val, new TypeToken<List<BigDecimal>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("float")) {
                    return gson.fromJson(val, new TypeToken<List<Float>>() {
                    }.getType());
                }
            }
            return gson.fromJson(val, new TypeToken<List<Object>>() {
            }.getType());
        } catch (Exception e) {
            throw new RuntimeException("Unable to convert value to list, value=" + val, e);
        }
    }

    @SuppressWarnings("rawtypes")
    protected Set parseSet(Column c, String val) {
        try {
            if (c.getDescription() != null) {
                if (c.getDescription().toLowerCase().equals("text") || c.getDescription().toLowerCase().equals("varchar")) {
                    return gson.fromJson(val, new TypeToken<List<String>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("int")) {
                    return gson.fromJson(val, new TypeToken<List<Integer>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("bigint")) {
                    return gson.fromJson(val, new TypeToken<List<Integer>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("smallint")) {
                    return gson.fromJson(val, new TypeToken<List<Integer>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("tinyint")) {
                    return gson.fromJson(val, new TypeToken<List<Integer>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("double")) {
                    return gson.fromJson(val, new TypeToken<List<Double>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("decimal")) {
                    return gson.fromJson(val, new TypeToken<List<BigDecimal>>() {
                    }.getType());
                } else if (c.getDescription().toLowerCase().equals("float")) {
                    return gson.fromJson(val, new TypeToken<List<Float>>() {
                    }.getType());
                }
            }
            return gson.fromJson(val, new TypeToken<List<Object>>() {
            }.getType());
        } catch (Exception e) {
            throw new RuntimeException("Unable to convert value to set, value=" + val, e);
        }
    }

    @SuppressWarnings("rawtypes")
    protected Map parseMap(Column c, String val) {
        try {
            if (c.getDescription() != null) {
                // TODO find dynamic way to create map types based on column types
                String[] parts = c.getDescription().split(",");
                if (parts[0].equals(DataType.Name.INT.name()) &&
                        (parts[1].equals(DataType.Name.TEXT.name()) || parts[1].equals(DataType.Name.VARCHAR.name()))) {
                    return gson.fromJson(val, new TypeToken<Map<Integer, String>>() {
                    }.getType());
                } else if ((parts[0].equals(DataType.Name.TEXT.name()) || parts[0].equals(DataType.Name.VARCHAR.name())) &&
                        (parts[1].equals(DataType.Name.TEXT.name()) || parts[1].equals(DataType.Name.VARCHAR.name()))) {
                    return gson.fromJson(val, new TypeToken<Map<String, String>>() {
                    }.getType());
                }
            }
            return gson.fromJson(val, new TypeToken<Map<Object, Object>>() {
            }.getType());
        } catch (Exception e) {
            throw new RuntimeException("Unable to convert value to map, expecting JSON, value=" + val, e);
        }
    }
}
