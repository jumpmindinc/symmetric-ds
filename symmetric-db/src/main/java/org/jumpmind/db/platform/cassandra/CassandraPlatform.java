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
package org.jumpmind.db.platform.cassandra;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ColumnMetadata;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TableMetadata;

public class CassandraPlatform extends AbstractDatabasePlatform {
    Map<String, Map<String, Table>> metaData = new HashMap<String, Map<String, Table>>();
    protected Session session;
    protected Cluster cluster;

    public CassandraPlatform(SqlTemplateSettings settings, String contactPoint) {
        super(settings);
        cluster = Cluster.builder().addContactPoint(contactPoint).build();
        this.session = cluster.connect();
        buildMetaData();
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.CASSANDRA;
    }

    @Override
    public String getClassName() {
        return CassandraPlatform.class.getName();
    }

    @Override
    public String getDefaultSchema() {
        return null;
    }

    @Override
    public String getDefaultCatalog() {
        return null;
    }

    @Override
    public <T> T getDataSource() {
        return null;
    }

    @Override
    public boolean isLob(Column column) {
        return false;
    }

    @Override
    public IDdlBuilder getDdlBuilder() {
        return new CassandraDdlBuilder();
    }

    @Override
    public IDdlReader getDdlReader() {
        return new CassandraDdlReader(this);
    }

    @Override
    public ISqlTemplate getSqlTemplate() {
        return new CassandraSqlTemplate();
    }

    @Override
    public ISqlTemplate getSqlTemplateDirty() {
        return new CassandraSqlTemplate();
    }

    @Override
    public DmlStatement createDmlStatement(DmlStatementOptions options) {
        return new CassandraDMLStatement(options.databaseInfo(getDatabaseInfo()));
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Map<String, Map<String, Table>> getMetaData() {
        return metaData;
    }

    public void setMetaData(Map<String, Map<String, Table>> metaData) {
        this.metaData = metaData;
    }

    protected final void buildMetaData() {
        for (KeyspaceMetadata keystoreMeta : cluster.getMetadata().getKeyspaces()) {
            metaData.put(keystoreMeta.getName(), new HashMap<String, Table>());
            for (TableMetadata tableMeta : keystoreMeta.getTables()) {
                Table table = new Table();
                table.setName(tableMeta.getName());
                table.setSchema(keystoreMeta.getName());
                List<ColumnMetadata> pkColumns = tableMeta.getPrimaryKey();
                for (ColumnMetadata columnMeta : tableMeta.getColumns()) {
                    Column column = new Column();
                    column.setName(columnMeta.getName());
                    column.setMappedTypeCode(getMappedTypeCode(columnMeta.getType().getName().name()));
                    if (columnMeta.getType().getTypeArguments() != null) {
                        StringBuilder types = new StringBuilder();
                        for (DataType dt : columnMeta.getType().getTypeArguments()) {
                            if (types.length() > 0) {
                                types.append(",");
                            }
                            types.append(dt.getName().name());
                            column.setDescription(types.toString());
                        }
                    }
                    for (ColumnMetadata pkMeta : pkColumns) {
                        if (pkMeta.equals(columnMeta)) {
                            column.setPrimaryKey(true);
                        }
                    }
                    table.addColumn(column);
                }
                metaData.get(keystoreMeta.getName()).put(table.getName(), table);
            }
        }
    }

    protected int getMappedTypeCode(String dataType) {
        /*
         * Unsupported Types ================= ASCII(1), BLOB(3), COUNTER(5), INET(16), VARINT(14), TIMEUUID(15), CUSTOM(0), UDT(48, ProtocolVersion.V3),
         * TUPLE(49, ProtocolVersion.V3),
         */
        if (dataType.equals(DataType.Name.INT.name())) {
            return Types.INTEGER;
        } else if (dataType.equals(DataType.Name.BIGINT.name())) {
            return Types.BIGINT;
        } else if (dataType.equals(DataType.Name.SMALLINT.name())) {
            return Types.SMALLINT;
        } else if (dataType.equals(DataType.Name.TINYINT.name())) {
            return Types.TINYINT;
        } else if (dataType.equals(DataType.Name.BOOLEAN.name())) {
            return Types.BOOLEAN;
        } else if (dataType.equals(DataType.Name.DECIMAL.name())) {
            return Types.DECIMAL;
        } else if (dataType.equals(DataType.Name.DOUBLE.name())) {
            return Types.DOUBLE;
        } else if (dataType.equals(DataType.Name.FLOAT.name())) {
            return Types.FLOAT;
        } else if (dataType.equals(DataType.Name.TIMESTAMP.name())) {
            return Types.TIMESTAMP;
        } else if (dataType.equals(DataType.Name.DATE.name())) {
            return Types.DATE;
        } else if (dataType.equals(DataType.Name.TIME.name())) {
            return Types.TIME;
        } else if (dataType.equals(DataType.Name.VARCHAR.name()) || dataType.equals(DataType.Name.TEXT.name())) {
            return Types.VARCHAR;
        } else if (dataType.equals(DataType.Name.UUID.name())) {
            return Types.JAVA_OBJECT;
        } else if (dataType.equals(DataType.Name.LIST.name())) {
            return Types.STRUCT;
        } else if (dataType.equals(DataType.Name.SET.name())) {
            return Types.REF;
        } else if (dataType.equals(DataType.Name.MAP.name())) {
            return Types.OTHER;
        }
        return Types.VARCHAR;
    }
}
