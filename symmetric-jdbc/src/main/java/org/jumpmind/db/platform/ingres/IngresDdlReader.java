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
package org.jumpmind.db.platform.ingres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.View;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.IConnectionCallback;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;

public class IngresDdlReader extends AbstractJdbcDdlReader {
    public IngresDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern(null);
        setDefaultColumnPattern(null);
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        String typeName = (String) values.get("TYPE_NAME");
        if (typeName != null && typeName.equalsIgnoreCase("INTEGER1")) {
            return Types.TINYINT;
        } else if (typeName != null && typeName.equalsIgnoreCase("INTEGER2")) {
            return Types.SMALLINT;
        } else if (typeName != null && typeName.equalsIgnoreCase("INTEGER4")) {
            return Types.INTEGER;
        } else if (typeName != null && typeName.equalsIgnoreCase("INTEGER8")) {
            return Types.BIGINT;
        } else if (typeName != null && typeName.equalsIgnoreCase("LONG VARCHAR")) {
            return Types.LONGVARCHAR;
        } else if (typeName != null && typeName.equalsIgnoreCase("LONG NVARCHAR")) {
            return Types.LONGNVARCHAR;
        } else if (typeName != null && typeName.equalsIgnoreCase("TEXT")) {
            return Types.VARCHAR;
        } else if (typeName != null && typeName.equalsIgnoreCase("LONG BYTE")) {
            return Types.LONGVARBINARY;
        } else {
            return super.mapUnknownJdbcTypeForColumn(values);
        }
    }

    @Override
    public Database readRelations(final String catalog, final String schema, final String[] relationTypes) {
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplateDirty();
        ISqlTransaction transaction = sqlTemplate.startSqlTransaction();
        Database database = null;
        try {
            database = postprocessModelFromDatabase(((JdbcSqlTransaction) transaction)
                    .executeCallback(new IConnectionCallback<Database>() {
                        public Database execute(Connection connection) throws SQLException {
                            Database db = new Database();
                            db.setName(Table.getFullyQualifiedPrefix(catalog, schema));
                            db.setCatalog(catalog);
                            db.setSchema(schema);
                            for (Relation relation : readRelations(connection, catalog, schema, relationTypes)) {
                                if (relation instanceof View view) {
                                    db.addView(view);
                                } else if (relation instanceof Table table) {
                                    db.addTable(table);
                                }
                            }
                            db.initialize();
                            return db;
                        }
                    }));
            transaction.commit();
        } catch (Throwable e) {
            if (transaction != null) {
                transaction.rollback();
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
        return database;
    }

    @Override
    public Relation readRelation(final String catalog, final String schema, final String relationName) {
        Relation relation = null;
        try {
            log.debug("reading table: " + relationName);
            JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplateDirty();
            ISqlTransaction transaction = sqlTemplate.startSqlTransaction();
            try {
                relation = postprocessRelationFromDatabase(((JdbcSqlTransaction) transaction).executeCallback(new IConnectionCallback<Relation>() {
                    public Relation execute(Connection connection) throws SQLException {
                        DatabaseMetaDataWrapper metaData = new DatabaseMetaDataWrapper();
                        metaData.setMetaData(connection.getMetaData());
                        metaData.setCatalog(catalog);
                        metaData.setSchemaPattern(schema);
                        metaData.setRelationTypes(null);
                        ResultSet tableData = null;
                        try {
                            log.debug("getting table metadata for {}", relationName);
                            tableData = metaData.getTables(getRelationNamePattern(StringUtils.lowerCase(relationName)));
                            log.debug("done getting table metadata for {}", relationName);
                            if (tableData != null && tableData.next()) {
                                Map<String, Object> values = readMetaData(tableData, initColumnsForTable());
                                return readRelation(connection, metaData, values);
                            } else {
                                close(tableData);
                                tableData = metaData.getTables(getRelationNamePattern(StringUtils.upperCase(relationName)));
                                if (tableData != null && tableData.next()) {
                                    Map<String, Object> values = readMetaData(tableData, initColumnsForTable());
                                    return readRelation(connection, metaData, values);
                                }
                                log.debug("table {} not found", relationName);
                                return null;
                            }
                        } finally {
                            close(tableData);
                        }
                    }
                }));
            } catch (Throwable e) {
                if (transaction != null) {
                    transaction.rollback();
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(e);
                }
            } finally {
                if (transaction != null) {
                    transaction.close();
                }
            }
        } catch (SqlException e) {
            if (e.getMessage() != null && Strings.CI.contains(e.getMessage(), "does not exist")) {
                return null;
            } else {
                log.error("Failed to get metadata for {}", Table.getFullyQualifiedName(catalog, schema, relationName));
                throw e;
            }
        }
        return relation;
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        Relation relation = super.readRelation(connection, metaData, values);
        String schema = relation.getSchema();
        // select column_name, column_always_ident, column_bydefault_ident
        // from iicolumns
        // where table_name='test_uppercase_table' and (column_always_ident='Y' OR column_bydefault_ident='Y')
        // column types that can have identity generators are int and bigint
        // only one column per table can have identity generator
        boolean setTableOwner = schema != null && schema.length() > 0 ? true : false;
        StringBuilder sql = new StringBuilder("select column_name,  column_always_ident, column_bydefault_ident, column_default_val ")
                .append("from iicolumns ")
                .append("where table_name=? ")
                .append(schema != null && schema.length() > 0 ? " and table_owner=? " : "");
        Object[] args = new Object[] { relation.getName() };
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection.prepareStatement(sql.toString());
            ps.setString(1, relation.getName());
            if (setTableOwner) {
                ps.setString(2, schema);
            }
            ps.setQueryTimeout(((IngresDatabasePlatform) platform).getSettings().getQueryTimeout());
            rs = ps.executeQuery();
            if (relation instanceof Table table) {
                processAdditionalColumnInformation(table, rs);
            }
        } catch (SQLException e) {
            log.error(sql.toString(), args, e);
            throw e;
        } finally {
            close(rs);
            close(ps);
        }
        return relation;
    }

    @Override
    protected void readIndex(DatabaseMetaDataWrapper metaData, Map<String, Object> values,
            Map<String, IIndex> knownIndices) throws SQLException {
        super.readIndex(metaData, values, knownIndices);
        // remove system generated indexes
        // NON_UNIQUE=false, non_unique=false,
        // TABLE_NAME=sym_parameter, table_name=sym_parameter,
        // COLUMN_NAME=external_id, column_name=external_id,
        // INDEX_NAME=$sym_p_u000183f200000000, index_name=$sym_p_u000183f200000000,
        // TYPE=3, type=3,
        // asc_or_desc=A, cardinality=0,
        // filter_condition=null, pages=0, table_schem=ingres,
        // ORDINAL_POSITION=1, ordinal_position=1, table_cat=null,
        // index_qualifier=ingres
        // select count(*) from iiindexes
        // where base_name='sym_channel' and index_owner='ingres' and system_use='G';
        StringBuilder sql = new StringBuilder("select count(*) from iiindexes ")
                .append("where index_name=? ")
                .append("and index_owner=? and system_use=? ");
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplate();
        Object[] args = new Object[] { values.get("INDEX_NAME"), values.get("index_qualifier"), "G" };
        int count = sqlTemplate.queryForInt(sql.toString(), args);
        if (count > 0) {
            knownIndices.remove(values.get("INDEX_NAME"));
        }
    }

    private void processAdditionalColumnInformation(Table table, ResultSet rs) throws SQLException {
        while (rs.next()) {
            String columnName = StringUtils.trim(rs.getString("COLUMN_NAME"));
            String columnAlwaysIdent = StringUtils.trim(rs.getString("COLUMN_ALWAYS_IDENT"));
            String columnBydefaultIdent = StringUtils.trim(rs.getString("COLUMN_BYDEFAULT_IDENT"));
            String columnDefaultVal = StringUtils.trim(rs.getString("COLUMN_DEFAULT_VAL"));
            for (Column column : table.getColumnsAsList()) {
                if (column.getName().equalsIgnoreCase(columnName)) {
                    if ((columnAlwaysIdent != null && columnAlwaysIdent.equalsIgnoreCase("Y")) ||
                            columnBydefaultIdent != null && columnBydefaultIdent.equalsIgnoreCase("Y")) {
                        column.setAutoIncrement(true);
                    } else if (columnDefaultVal != null) {
                        column.setDefaultValue(columnDefaultVal);
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected void readForeignKeyUpdateRule(Map<String, Object> values, ForeignKey fk) {
        StringBuilder result = new StringBuilder();
        getForeignKeyRule(values, result);
        String findInString = " ON UPDATE ";
        if (result.length() > 0) {
            int i = result.indexOf(" ON UPDATE ");
            if (i > -1) {
                i += findInString.length();
                setForeignKeyAction(result.substring(i).trim().toUpperCase(), fk, true);
            }
        }
    }

    @Override
    protected void readForeignKeyDeleteRule(Map<String, Object> values, ForeignKey fk) {
        StringBuilder result = new StringBuilder();
        getForeignKeyRule(values, result);
        String findInString = " ON DELETE ";
        int i = result.indexOf(" ON DELETE ");
        if (i > -1) {
            i += findInString.length();
            setForeignKeyAction(result.substring(i).trim().toUpperCase(), fk, true);
        }
    }

    private void getForeignKeyRule(Map<String, Object> values, StringBuilder result) {
        StringBuilder sql = new StringBuilder("select text_segment from iiconstraints ")
                .append("where constraint_type='R' and constraint_name=? ")
                .append("and schema_name=? and table_name=? ")
                .append("order by text_sequence");
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplate();
        Object[] args = new Object[] { values.get("FK_NAME"), values.get("PKTABLE_SCHEM"), values.get("PKTABLE_NAME") };
        List<Row> rows = sqlTemplate.query(sql.toString(), args);
        for (Row row : rows) {
            result.append(row.getString("TEXT_SEGMENT"));
        }
    }

    private void setForeignKeyAction(String s, ForeignKey fk, boolean onUpdate) {
        if (s.startsWith(ForeignKeyAction.CASCADE.getForeignKeyActionName())) {
            if (onUpdate) {
                fk.setOnUpdateAction(ForeignKeyAction.CASCADE);
            } else {
                fk.setOnDeleteAction(ForeignKeyAction.CASCADE);
            }
        } else if (s.startsWith(ForeignKeyAction.RESTRICT.getForeignKeyActionName())) {
            if (onUpdate) {
                fk.setOnUpdateAction(ForeignKeyAction.RESTRICT);
            } else {
                fk.setOnDeleteAction(ForeignKeyAction.RESTRICT);
            }
        } else if (s.startsWith(ForeignKeyAction.SETNULL.getForeignKeyActionName())) {
            if (onUpdate) {
                fk.setOnUpdateAction(ForeignKeyAction.SETNULL);
            } else {
                fk.setOnDeleteAction(ForeignKeyAction.SETNULL);
            }
        } else if (s.startsWith(ForeignKeyAction.SETDEFAULT.getForeignKeyActionName())) {
            if (onUpdate) {
                fk.setOnUpdateAction(ForeignKeyAction.SETDEFAULT);
            } else {
                fk.setOnDeleteAction(ForeignKeyAction.SETDEFAULT);
            }
        } else if (s.startsWith(ForeignKeyAction.NOACTION.getForeignKeyActionName())) {
            if (onUpdate) {
                fk.setOnUpdateAction(ForeignKeyAction.NOACTION);
            } else {
                fk.setOnDeleteAction(ForeignKeyAction.NOACTION);
            }
        } else {
            if (onUpdate) {
                fk.setOnUpdateAction(ForeignKeyAction.NOACTION);
            } else {
                fk.setOnDeleteAction(ForeignKeyAction.NOACTION);
            }
        }
    }
}
