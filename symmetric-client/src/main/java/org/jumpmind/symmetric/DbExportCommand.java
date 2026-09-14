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
package org.jumpmind.symmetric;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.io.data.DbExport;
import org.jumpmind.symmetric.io.data.DbExport.Compatible;
import org.jumpmind.symmetric.io.data.DbExport.Format;

/**
 * Export the structure and data from database tables to file.
 */
public class DbExportCommand extends AbstractCommandLauncher {
    private static final String OPTION_FORMAT = "format";
    private static final String OPTION_COMPATIBLE = "compatible";
    private static final String OPTION_ADD_DROP_TABLE = "add-drop-table";
    private static final String OPTION_NO_CREATE_INFO = "no-create-info";
    private static final String OPTION_NO_INDICES = "no-indices";
    private static final String OPTION_NO_FOREIGN_KEYS = "no-foreign-keys";
    private static final String OPTION_NO_DATA = "no-data";
    private static final String OPTION_USE_VARIABLE_DATES = "use-variable-dates";
    private static final String OPTION_NO_QUALIFIERS = "no-qualifiers";
    private static final String OPTION_USE_JDBC_TIMESTAMP_FORMAT = "use-jdbc-timestamp-format";
    private static final String OPTION_SQL = "sql";
    private static final String OPTION_COMMENTS = "comments";
    private static final String OPTION_SCHEMA = "schema";
    private static final String OPTION_CATALOG = "catalog";
    private static final String OPTION_DIR = "dir";
    private static final String OPTION_WHERE = "where";
    private static final String OPTION_EXCLUDE_COLUMNS = "exclude-columns";
    private static final String OPTION_USE_SYM_DB = "use-sym-db";
    private static final String OPTION_USE_READ_UNCOMMITTED = "use-read-uncommitted";

    public DbExportCommand() {
        super("dbexport", "[tablename...]", "DbExport.Option.");
    }

    public static void main(String[] args) {
        new DbExportCommand().execute(args);
    }

    @Override
    protected boolean printHelpIfNoOptionsAreProvided() {
        return false;
    }

    @Override
    protected boolean requiresPropertiesFile(CommandLine line) {
        return true;
    }

    @Override
    protected void printHelp(CommandLine cmd, Options options) {
        System.out.println(app + " version " + Version.version());
        System.out.println("Export the structure and data from database tables to file.\n");
        super.printHelp(cmd, options);
    }

    @Override
    protected void buildOptions(Options options) {
        super.buildOptions(options);
        addOption(options, null, OPTION_FORMAT, true);
        addOption(options, null, OPTION_DIR, true);
        addOption(options, null, OPTION_COMPATIBLE, true);
        addOption(options, null, OPTION_SCHEMA, true);
        addOption(options, null, OPTION_CATALOG, true);
        addOption(options, null, OPTION_ADD_DROP_TABLE, false);
        addOption(options, null, OPTION_NO_CREATE_INFO, false);
        addOption(options, null, OPTION_NO_INDICES, false);
        addOption(options, null, OPTION_NO_FOREIGN_KEYS, false);
        addOption(options, null, OPTION_NO_DATA, false);
        addOption(options, null, OPTION_USE_VARIABLE_DATES, false);
        addOption(options, null, OPTION_USE_JDBC_TIMESTAMP_FORMAT, true);
        addOption(options, null, OPTION_NO_QUALIFIERS, false);
        addOption(options, null, OPTION_SQL, true);
        addOption(options, null, OPTION_WHERE, true);
        addOption(options, "i", OPTION_COMMENTS, false);
        addOption(options, null, OPTION_EXCLUDE_COLUMNS, true);
        addOption(options, null, OPTION_USE_SYM_DB, false);
        addOption(options, "u", OPTION_USE_READ_UNCOMMITTED, false);
    }

    @Override
    protected boolean executeWithOptions(CommandLine line) throws Exception {
        DbExport dbExport = new DbExport(getDatabasePlatform(false, line.hasOption(OPTION_USE_SYM_DB)));
        if (line.hasOption(OPTION_DIR)) {
            String dir = line.getOptionValue(OPTION_DIR);
            if (new File(dir).exists()) {
                dbExport.setDir(line.getOptionValue(OPTION_DIR));
            } else {
                throw new ParseException(String.format("The directory does not exist: %s", dir));
            }
        }
        if (line.hasOption(OPTION_FORMAT)) {
            dbExport.setFormat(Format.valueOf(line.getOptionValue(OPTION_FORMAT).toUpperCase()));
            if ((dbExport.getFormat() == Format.CSV || dbExport.getFormat() == Format.CSV_DQUOTE) && line.getArgs().length > 1
                    && StringUtils.isBlank(dbExport.getDir())) {
                throw new ParseException(
                        "When exporting multiple tables to CSV format you must designate a directory where the files will be written");
            }
        }
        if (line.hasOption(OPTION_COMPATIBLE)) {
            String compatibleStr = line.getOptionValue(OPTION_COMPATIBLE).toUpperCase();
            Compatible compatible = null;
            for (Compatible c : Compatible.values()) {
                if (c.name().equals(compatibleStr)) {
                    compatible = c;
                    break;
                }
            }
            if (compatible != null) {
                dbExport.setCompatible(compatible);
            } else {
                throw new SymmetricException("Invalid compatible database option: %s", line.getOptionValue(OPTION_COMPATIBLE));
            }
        }
        if (line.hasOption(OPTION_ADD_DROP_TABLE)) {
            dbExport.setAddDropTable(true);
        }
        if (line.hasOption(OPTION_NO_CREATE_INFO)) {
            dbExport.setNoCreateInfo(true);
        }
        if (line.hasOption(OPTION_NO_INDICES)) {
            dbExport.setNoIndices(true);
        }
        if (line.hasOption(OPTION_NO_FOREIGN_KEYS)) {
            dbExport.setNoForeignKeys(true);
        }
        if (line.hasOption(OPTION_NO_DATA)) {
            dbExport.setNoData(true);
        }
        if (line.hasOption(OPTION_USE_VARIABLE_DATES)) {
            dbExport.setUseVariableForDates(true);
        }
        if (line.hasOption(OPTION_USE_JDBC_TIMESTAMP_FORMAT)) {
            dbExport.setUseJdbcTimestampFormat("true".equalsIgnoreCase(line.getOptionValue(OPTION_USE_JDBC_TIMESTAMP_FORMAT)));
        }
        if (line.hasOption(OPTION_USE_READ_UNCOMMITTED)) {
            dbExport.setUseReadUncommitted(true);
        }
        if (line.hasOption(OPTION_NO_QUALIFIERS)) {
            dbExport.setUseQuotedIdentifiers(false);
        }
        if (line.hasOption(OPTION_COMMENTS)) {
            dbExport.setComments(true);
        }
        if (line.hasOption(OPTION_SCHEMA)) {
            dbExport.setSchema(line.getOptionValue(OPTION_SCHEMA));
        }
        if (line.hasOption(OPTION_CATALOG)) {
            dbExport.setCatalog(line.getOptionValue(OPTION_CATALOG));
        }
        if (line.hasOption(OPTION_WHERE)) {
            dbExport.setWhereClause(line.getOptionValue(OPTION_WHERE));
        }
        if (line.hasOption(OPTION_EXCLUDE_COLUMNS)) {
            dbExport.setExcludeColumns(line.getOptionValue(OPTION_EXCLUDE_COLUMNS).split(","));
        }
        String[] args = line.getArgs();
        if (line.hasOption(OPTION_SQL)) {
            if (args.length != 1) {
                throw new ParseException(
                        "When specifying a SQL statement, a table name argument must be provided.");
            }
            dbExport.exportTable(System.out, args[0], line.getOptionValue(OPTION_SQL));
        } else if (args.length == 0) {
            dbExport.exportTables(System.out);
        } else {
            dbExport.exportTables(System.out, args);
        }
        return true;
    }
}
