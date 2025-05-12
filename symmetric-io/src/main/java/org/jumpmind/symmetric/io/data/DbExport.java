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
package org.jumpmind.symmetric.io.data;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jumpmind.db.io.DatabaseXmlUtil;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DdlBuilderFactory;
import org.jumpmind.db.platform.DmlStatementFactory;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.csv.CsvWriter;
import org.jumpmind.symmetric.io.IoVersion;

/**
 * Export the structure and data from database tables to file.
 */
public class DbExport {
    public enum Format {
        SQL, CSV, XML, SYM_XML, CSV_DQUOTE
    };

    public enum Compatible {
        DB2, DB2AS400, DB2ZOS, DERBY, FIREBIRD, FIREBIRD_DIALECT1, GREENPLUM, H2, HSQLDB, HSQLDB2, INFORMIX, INGRES, INTERBASE, MSSQL, MSSQL2000, MSSQL2005, MSSQL2008, MSSQL2016, MYSQL, ORACLE, ORACLE122, ORACLE23, POSTGRES, POSTGRES95, RAIMA, SYBASE, SQLITE, MARIADB, ASE, SQLANYWHERE, REDSHIFT, VOLTDB, NUODB, TIBERO, SINGLESTORE, OPENEDGE, GENERIC, SNOWFLAKE
    };

    private Format format = Format.SQL;
    private Compatible compatible;
    private boolean addDropTable;
    private boolean noCreateInfo;
    private boolean noIndices;
    private boolean noForeignKeys;
    private boolean noData;
    private boolean ignoreMissingTables;
    private boolean useVariableDates;
    private boolean comments;
    private String whereClause;
    private String[] excludeColumns;
    private String catalog;
    private String schema;
    private String dir;
    private int maxRows = Integer.MAX_VALUE;
    private boolean useQuotedIdentifiers = true;
    private boolean useJdbcTimestampFormat = true;
    private boolean useReadUncommitted;
    private IDatabasePlatform platform;
    private long rowCount;

    public DbExport(IDatabasePlatform platform) {
        this.platform = platform;
        compatible = Compatible.valueOf(platform.getName().toUpperCase());
    }

    public String exportTables() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exportTables(output);
        output.close();
        return output.toString();
    }

    public String exportTables(String[] tableNames) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exportTables(output, tableNames);
        output.close();
        return output.toString();
    }

    public String exportTables(Table[] tables) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exportTables(output, tables);
        output.close();
        return output.toString();
    }

    public void exportTables(OutputStream output) throws IOException {
        Database database = platform.readDatabase(getCatalogToUse(), getSchemaToUse(),
                new String[] { "TABLE" });
        exportTables(output, database.getTables());
    }

    public void exportTables(OutputStream output, String[] tableNames) throws IOException {
        ArrayList<Table> tableList = new ArrayList<Table>();
        for (String tableName : tableNames) {
            Table table = platform.readTableFromDatabase(getCatalogToUse(), getSchemaToUse(),
                    tableName);
            if (table != null) {
                tableList.add(table);
            } else if (!ignoreMissingTables) {
                throw new RuntimeException("Cannot find table " + tableName + " in catalog "
                        + getCatalogToUse() + " and schema " + getSchemaToUse());
            }
        }
        exportTables(output, tableList.toArray(new Table[tableList.size()]));
    }

    public void exportTable(OutputStream output, String tableName, String sql) throws IOException {
        Table table = platform
                .readTableFromDatabase(getCatalogToUse(), getSchemaToUse(), tableName);
        exportTables(output, new Table[] { table }, sql);
    }

    public void exportTables(OutputStream output, Table[] tables) throws IOException {
        exportTables(output, tables, null);
    }

    public void exportTables(OutputStream output, Table[] tables, String sql) throws IOException {
        for (int i = 0; i < tables.length; i++) {
            // if the table definition did not come from the database, then read
            // the table from the database
            if (!tables[i].containsJdbcTypes()) {
                tables[i] = platform.readTableFromDatabase(getCatalogToUse(), getSchemaToUse(),
                        tables[i].getName());
            }
            /* make a copy so if we zero out catalog and schema we don't effect the original */
            tables[i] = tables[i].copy();
        }
        WriterWrapper writerWrapper = null;
        rowCount = 0;
        try {
            writerWrapper = new WriterWrapper(output);
            tables = Database.sortByForeignKeys(tables);
            for (Table table : tables) {
                writeTable(writerWrapper, table, sql);
            }
        } finally {
            if (writerWrapper != null) {
                writerWrapper.close();
            }
        }
    }

    protected String getSchemaToUse() {
        if (StringUtils.isBlank(schema)) {
            return platform.getDefaultSchema();
        } else {
            return schema;
        }
    }

    protected String getCatalogToUse() {
        if (StringUtils.isBlank(catalog)) {
            return platform.getDefaultCatalog();
        } else {
            return catalog;
        }
    }

    private void removeExcludedColumns(Table table) {
        if (excludeColumns != null) {
            for (String columnName : excludeColumns) {
                int colIndex = table.getColumnIndex(columnName);
                if (colIndex != -1) {
                    table.removeColumn(colIndex);
                }
            }
        }
    }

    protected void writeTable(final WriterWrapper writerWrapper, Table table, String sql)
            throws IOException {
        removeExcludedColumns(table);
        writerWrapper.startTable(table);
        if (!noData) {
            if (sql == null) {
                if (excludeColumns == null || excludeColumns.length == 0) {
                    sql = platform.createDmlStatement(DmlType.SELECT_ALL, table, null).getSql();
                } else {
                    Column[] columnsToExport = getColumnsToExport(table);
                    sql = platform.createDmlStatement(DmlType.SELECT_ALL, table.getCatalog(), table.getSchema(), table.getName(),
                            table.getPrimaryKeyColumns(), columnsToExport, null, null).getSql();
                }
            }
            if (table.getColumnCount() > 0) {
                if (StringUtils.isNotBlank(whereClause)) {
                    sql = String.format("%s %s", sql, whereClause);
                }
                ISqlTemplate sqlTemplate = useReadUncommitted ? platform.getSqlTemplateDirty() : platform.getSqlTemplate();
                sqlTemplate.query(sql, new ISqlRowMapper<Object>() {
                    int rows = maxRows;

                    @Override
                    public Object mapRow(Row row) {
                        if (rows > 0) {
                            writerWrapper.writeRow(row);
                            rows--;
                        }
                        return Boolean.TRUE;
                    }
                });
            }
        }
        writerWrapper.finishTable(table);
    }

    protected Column[] getColumnsToExport(Table table) {
        Column[] tableColumns = table.getColumns();
        List<Column> columnsToExport = new ArrayList<Column>();
        for (int i = 0; i < tableColumns.length; i++) {
            boolean excluded = false;
            for (int j = 0; j < excludeColumns.length; j++) {
                if (tableColumns[i].getName().equalsIgnoreCase(excludeColumns[j])) {
                    excluded = true;
                }
            }
            if (!excluded) {
                try {
                    columnsToExport.add((Column) tableColumns[i].clone());
                } catch (CloneNotSupportedException e) {
                    // clone will always supported on the Column object
                }
            }
        }
        Column[] columnArray = new Column[columnsToExport.size()];
        columnArray = columnsToExport.toArray(columnArray);
        return columnArray;
    }

    protected Database getDatabase(Table table) {
        return getDatabase(new Table[] { table });
    }

    protected Database getDatabase(Table[] tables) {
        Database db = new Database();
        try {
            if (!noCreateInfo) {
                for (Table table : tables) {
                    Table newTable = (Table) table.clone();
                    if (noIndices) {
                        newTable.removeAllIndices();
                    }
                    if (noForeignKeys) {
                        newTable.removeAllForeignKeys();
                    }
                    db.addTable(newTable);
                }
            } else if (addDropTable) {
                for (Table table : tables) {
                    Table newTable = (Table) table.clone();
                    db.addTable(newTable);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return db;
    }

    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public Compatible getCompatible() {
        return compatible;
    }

    public void setCompatible(Compatible compatible) {
        this.compatible = compatible;
    }

    public boolean isAddDropTable() {
        return addDropTable;
    }

    public void setAddDropTable(boolean addDropTable) {
        this.addDropTable = addDropTable;
    }

    public boolean isNoCreateInfo() {
        return noCreateInfo;
    }

    public void setNoCreateInfo(boolean noCreateInfo) {
        this.noCreateInfo = noCreateInfo;
    }

    public boolean isNoData() {
        return noData;
    }

    public void setNoData(boolean noData) {
        this.noData = noData;
    }

    public void setUseQuotedIdentifiers(boolean useQuotedIdentifiers) {
        this.useQuotedIdentifiers = useQuotedIdentifiers;
    }

    public boolean isUseQuotedIdentifiers() {
        return useQuotedIdentifiers;
    }

    public void setWhereClause(String whereClause) {
        this.whereClause = whereClause;
    }

    public String getWhereClause() {
        return whereClause;
    }

    public void setExcludeColumns(String[] excludeColumns) {
        this.excludeColumns = excludeColumns;
    }

    public String[] getExcludeColumns() {
        return excludeColumns;
    }

    public boolean isComments() {
        return comments;
    }

    public void setComments(boolean comments) {
        this.comments = comments;
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

    public boolean isIgnoreMissingTables() {
        return ignoreMissingTables;
    }

    public void setIgnoreMissingTables(boolean ignoreMissingTables) {
        this.ignoreMissingTables = ignoreMissingTables;
    }

    public boolean isUseVariableDates() {
        return useVariableDates;
    }

    public void setUseVariableForDates(boolean useVariableDates) {
        this.useVariableDates = useVariableDates;
    }

    public boolean isNoIndices() {
        return noIndices;
    }

    public void setNoIndices(boolean noIndices) {
        this.noIndices = noIndices;
    }

    public boolean isNoForeignKeys() {
        return noForeignKeys;
    }

    public void setNoForeignKeys(boolean noForeignKeys) {
        this.noForeignKeys = noForeignKeys;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getDir() {
        return dir;
    }

    public void setUseJdbcTimestampFormat(boolean useJdbcTimestampFormat) {
        this.useJdbcTimestampFormat = useJdbcTimestampFormat;
    }

    public boolean isUseJdbcTimestampFormat() {
        return useJdbcTimestampFormat;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public boolean isUseReadUncommitted() {
        return useReadUncommitted;
    }

    public void setUseReadUncommitted(boolean useReadUncommitted) {
        this.useReadUncommitted = useReadUncommitted;
    }

    public long getRowCount() {
        return rowCount;
    }

    protected String getDatabaseName() {
        Compatible mappedCompatible = compatible;
        if (mappedCompatible == Compatible.MSSQL) {
            mappedCompatible = Compatible.MSSQL2000;
        }
        return mappedCompatible.toString().toLowerCase();
    }

    class WriterWrapper {
        final private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        private CsvWriter csvWriter;
        private Writer writer;
        private Table table;
        private DmlStatement insertSql;
        private boolean startedWriting = false;

        public WriterWrapper(OutputStream os) {
            if (StringUtils.isBlank(dir) && os != null) {
                try {
                    writer = new OutputStreamWriter(os, Charset.defaultCharset().name());
                } catch (UnsupportedEncodingException e) {
                    throw new IoException(e);
                }
            }
        }

        protected void startTable(Table table) {
            try {
                this.table = table;
                if (StringUtils.isNotBlank(dir)) {
                    startedWriting = false;
                    File directory = new File(dir);
                    if (!directory.exists()) {
                        directory.mkdirs();
                    }
                    File file = new File(dir, String.format("%s.%s", table.getName(), format
                            .toString().replace('_', '.').toLowerCase()));
                    FileUtils.deleteQuietly(file);
                    try {
                        writer = new FileWriter(file);
                    } catch (IOException e) {
                        throw new IoException(e);
                    }
                }
                if (!startedWriting) {
                    if (format == Format.SYM_XML) {
                        write("<batch xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" encoding=\"" + Charset.defaultCharset().name() + "\">\n");
                    } else if (format == Format.XML) {
                        write("<database xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" name=\"dbexport\"");
                        if (catalog != null && !catalog.equals(platform.getDefaultCatalog())) {
                            write(" catalog=\"" + catalog + "\"");
                        }
                        if (schema != null && !schema.equals(platform.getDefaultSchema())) {
                            write(" schema=\"" + schema + "\"");
                        }
                        write(" encoding=\"" + Charset.defaultCharset().name() + "\">\n");
                    }
                    startedWriting = true;
                }
                String databaseName = getDatabaseName();
                if (format == Format.CSV && csvWriter == null) {
                    csvWriter = new CsvWriter(writer, ',');
                    csvWriter.setEscapeMode(CsvWriter.ESCAPE_MODE_BACKSLASH);
                    csvWriter.setTextQualifier('\"');
                    csvWriter.setUseTextQualifier(true);
                    csvWriter.setForceQualifier(true);
                } else if (format == Format.CSV_DQUOTE && csvWriter == null) {
                    csvWriter = new CsvWriter(writer, ',');
                    csvWriter.setEscapeMode(CsvWriter.ESCAPE_MODE_DOUBLED);
                    csvWriter.setTextQualifier('\"');
                    csvWriter.setUseTextQualifier(true);
                    csvWriter.setForceQualifier(true);
                } else if (format == Format.SQL) {
                    if (table.getCatalog() != null
                            && table.getCatalog().equals(platform.getDefaultCatalog())) {
                        table.setCatalog(null);
                    }
                    if (table.getCatalog() == null && table.getSchema() != null
                            && table.getSchema().equals(platform.getDefaultSchema())) {
                        table.setSchema(null);
                    }
                    Table targetTable = table.copy();
                    DmlStatementOptions options = new DmlStatementOptions(DmlType.INSERT, targetTable);
                    if (excludeColumns == null || excludeColumns.length == 0) {
                        insertSql = DmlStatementFactory.getInstance().create(databaseName, options.quotedIdentifiers(useQuotedIdentifiers));
                    } else {
                        Column[] columnsToExport = getColumnsToExport(table);
                        insertSql = DmlStatementFactory.getInstance().create(databaseName, options.columns(columnsToExport).quotedIdentifiers(startedWriting));
                    }
                }
                if (!noCreateInfo) {
                    if (format == Format.SQL) {
                        IDdlBuilder target = DdlBuilderFactory.getInstance().create(databaseName);
                        target.setDelimitedIdentifierModeOn(useQuotedIdentifiers);
                        write(cleanupSQL(target.createTables(getDatabase(table), addDropTable)));
                    } else if (format == Format.XML) {
                        if (noIndices) {
                            table.removeAllIndices();
                        }
                        if (noForeignKeys) {
                            table.removeAllForeignKeys();
                        }
                        DatabaseXmlUtil.write(table, writer);
                    }
                } else if (addDropTable) {
                    if (format == Format.SQL) {
                        IDdlBuilder target = DdlBuilderFactory.getInstance().create(databaseName);
                        write(target.dropTables(getDatabase(table)));
                    }
                }
                writeComment("DbExport: "
                        + StringUtils.defaultString(IoVersion.getVersion().version()));
                writeComment("Catalog: " + StringUtils.defaultString(getCatalogToUse()));
                writeComment("Schema: " + StringUtils.defaultString(getSchemaToUse()));
                writeComment("Table: " + table.getName());
                writeComment("Table Columns: " + java.util.Arrays.deepToString(table.getColumnNames()));
                writeComment("Started on " + df.format(new Date()));
                if (format == Format.CSV || format == Format.CSV_DQUOTE) {
                    csvWriter.writeRecord(table.getColumnNames());
                } else if (!noData && format == Format.XML) {
                    write("<table_data name=\"", table.getName(), "\">\n");
                }
            } catch (IOException e) {
                throw new IoException(e);
            }
        }

        protected String cleanupSQL(String createTables) {
            // Avoid the unfortunate situation where we have a trigger definition ending in ;;
            // -- which works when put through the SqlScriptReader (which will trim off the second ;),
            // but doesn't work when exported as a script.
            String cleanedSQL = createTables.replaceAll("[;;\\s]+$", ";\n");
            return cleanedSQL;
        }

        protected void writeComment(String commentStr) {
            if (writer != null) {
                try {
                    if (comments) {
                        if (format == Format.CSV || format == Format.CSV_DQUOTE) {
                            write("# ", commentStr, "\n");
                        } else if (format == Format.XML) {
                            write("<!-- ", commentStr, " -->\n");
                        } else if (format == Format.SQL) {
                            write("-- ", commentStr, "\n");
                        }
                        writer.flush();
                    }
                } catch (IOException e) {
                    throw new IoException(e);
                }
            }
        }

        protected void writeRow(Row row) {
            Column[] columns = table.getColumns();
            String[] values = platform.getStringValues(BinaryEncoding.HEX, columns, row,
                    useVariableDates, false);
            try {
                if (format == Format.CSV || format == Format.CSV_DQUOTE) {
                    csvWriter.writeRecord(values, true);
                } else if (format == Format.SQL) {
                    write(insertSql.buildDynamicSql(BinaryEncoding.HEX, row, useVariableDates,
                            useJdbcTimestampFormat), "\n");
                } else if (format == Format.XML) {
                    write("\t<row>\n");
                    for (int i = 0; i < columns.length; i++) {
                        if (values[i] != null) {
                            write("\t\t<field name=\"", columns[i].getName(), "\">",
                                    StringEscapeUtils.escapeXml10(values[i]), "</field>\n");
                        } else {
                            write("\t\t<field name=\"", columns[i].getName(),
                                    "\" xsi:nil=\"true\" />\n");
                        }
                    }
                    write("\t</row>\n");
                } else if (format == Format.SYM_XML) {
                    write("\t<row entity=\"", table.getName(), "\" dml=\"I\">\n");
                    for (int i = 0; i < columns.length; i++) {
                        if (values[i] != null) {
                            write("\t\t<data key=\"", columns[i].getName(), "\">",
                                    StringEscapeUtils.escapeXml10(values[i]), "</data>\n");
                        } else {
                            write("\t\t<data key=\"", columns[i].getName(),
                                    "\" xsi:nil=\"true\" />\n");
                        }
                    }
                    write("\t</row>\n");
                }
                rowCount++;
            } catch (IOException e) {
                throw new IoException(e);
            }
        }

        protected void write(String... data) {
            for (String string : data) {
                try {
                    writer.write(string);
                } catch (IOException e) {
                    throw new IoException(e);
                }
            }
        }

        protected void finishTable(Table table) {
            if (!noData && format == Format.XML) {
                write("</table_data>\n");
            }
            if (StringUtils.isNotBlank(dir)) {
                close();
            }
        }

        public void close() {
            writeComment("Completed on " + df.format(new Date()));
            if (format == Format.SYM_XML) {
                write("</batch>\n");
            } else if (format == Format.XML) {
                write("</database>\n");
            }
            startedWriting = false;
            if (csvWriter != null) {
                csvWriter.flush();
                csvWriter.close();
                csvWriter = null;
            }
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
            }
            writer = null;
        }
    }
}