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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.io.data.DbImport;
import org.jumpmind.symmetric.io.data.DbImport.Format;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;

/**
 * Import data from file to database tables.
 */
public class DbImportCommand extends AbstractCommandLauncher {
    private static final String OPTION_FORMAT = "format";
    private static final String OPTION_CATALOG = "catalog";
    private static final String OPTION_SCHEMA = "schema";
    private static final String OPTION_TABLE = "table";
    private static final String OPTION_USE_VARIABLE_DATES = "use-variable-dates";
    private static final String OPTION_COMMIT = "commit";
    private static final String OPTION_INTERVAL = "interval";
    private static final String OPTION_IGNORE = "ignore";
    private static final String OPTION_REPLACE = "replace";
    private static final String OPTION_FORCE = "force";
    private static final String OPTION_ALTER = "alter";
    private static final String OPTION_FILTER_CLASSES = "filter-classes";
    private static final String OPTION_DROP_IF_EXISTS = "drop-if-exists";
    private static final String OPTION_ALTER_CASE = "alter-case";
    private static final String OPTION_USE_SYM_DB = "use-sym-db";

    public DbImportCommand() {
        super("dbimport", "[file...]", "DbImport.Option.");
    }

    public static void main(String[] args) {
        new DbImportCommand().execute(args);
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
        System.out.println("Import data from file to database tables.\n");
        super.printHelp(cmd, options);
    }

    @Override
    protected void buildOptions(Options options) {
        super.buildOptions(options);
        addOption(options, null, OPTION_FORMAT, true);
        addOption(options, null, OPTION_CATALOG, true);
        addOption(options, null, OPTION_SCHEMA, true);
        addOption(options, null, OPTION_TABLE, true);
        addOption(options, null, OPTION_USE_VARIABLE_DATES, false);
        addOption(options, null, OPTION_COMMIT, true);
        addOption(options, null, OPTION_INTERVAL, true);
        addOption(options, null, OPTION_IGNORE, false);
        addOption(options, null, OPTION_REPLACE, false);
        addOption(options, null, OPTION_FORCE, false);
        addOption(options, null, OPTION_ALTER, false);
        addOption(options, null, OPTION_FILTER_CLASSES, true);
        addOption(options, null, OPTION_DROP_IF_EXISTS, false);
        addOption(options, null, OPTION_ALTER_CASE, false);
        addOption(options, null, OPTION_USE_SYM_DB, false);
    }

    @Override
    protected boolean executeWithOptions(CommandLine line) throws Exception {
        DbImport dbImport = new DbImport(getDatabasePlatform(true, line.hasOption(OPTION_USE_SYM_DB)));
        if (line.hasOption(OPTION_FORMAT)) {
            dbImport.setFormat(Format.valueOf(line.getOptionValue(OPTION_FORMAT).toUpperCase()));
        }
        if (line.hasOption(OPTION_CATALOG)) {
            dbImport.setCatalog(line.getOptionValue(OPTION_CATALOG));
        }
        if (line.hasOption(OPTION_SCHEMA)) {
            dbImport.setSchema(line.getOptionValue(OPTION_SCHEMA));
        }
        if (line.hasOption(OPTION_USE_VARIABLE_DATES)) {
            dbImport.setUseVariableForDates(true);
        }
        if (line.hasOption(OPTION_COMMIT)) {
            dbImport.setCommitRate(Long.parseLong(line.getOptionValue(OPTION_COMMIT)));
        }
        if (line.hasOption(OPTION_INTERVAL)) {
            dbImport.setInterval((Integer.parseInt(line.getOptionValue(OPTION_INTERVAL))));
        }
        if (line.hasOption(OPTION_ALTER_CASE)) {
            dbImport.setAlterCaseToMatchDatabaseDefaultCase(true);
        }
        if (line.hasOption(OPTION_DROP_IF_EXISTS)) {
            dbImport.setDropIfExists(true);
        }
        if (line.hasOption(OPTION_ALTER)) {
            dbImport.setAlterTables(true);
        }
        if (line.hasOption(OPTION_FILTER_CLASSES)) {
            String filters = line.getOptionValue(OPTION_FILTER_CLASSES);
            if (StringUtils.isNotBlank(filters)) {
                String[] clazzes = filters.split(",");
                for (String clazz : clazzes) {
                    if (StringUtils.isNotBlank(clazz)) {
                        IDatabaseWriterFilter databaseWriterFilter = (IDatabaseWriterFilter) Class
                                .forName(clazz.trim()).getDeclaredConstructor().newInstance();
                        dbImport.addDatabaseWriterFilter(databaseWriterFilter);
                    }
                }
            }
        }
        if (line.hasOption(OPTION_FORCE)) {
            dbImport.setForceImport(true);
        }
        if (line.hasOption(OPTION_REPLACE)) {
            dbImport.setReplaceRows(true);
        }
        if (line.hasOption(OPTION_IGNORE)) {
            dbImport.setIgnoreCollisions(true);
        }
        String[] args = line.getArgs();
        if (args.length == 0) {
            dbImport.importTables(System.in, line.getOptionValue(OPTION_TABLE));
        } else {
            for (String fileName : args) {
                if (!new File(fileName).exists()) {
                    throw new RuntimeException("Cannot find file " + fileName);
                }
            }
            for (String fileName : args) {
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileName));
                dbImport.importTables(in, line.getOptionValue(OPTION_TABLE));
                in.close();
            }
        }
        return true;
    }
}
