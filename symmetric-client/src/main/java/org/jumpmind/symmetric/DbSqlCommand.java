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
package org.jumpmind.symmetric;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.dbcp2.BasicDataSource;
import org.h2.tools.Shell;

public class DbSqlCommand extends AbstractCommandLauncher {
    private static final String OPTION_SQL = "sql";
    private static final String OPTION_SQLFILE = "sqlfile";
    private static final String OPTION_USE_SYM_DB = "use-sym-db";
    private Options localOptions;

    public DbSqlCommand() {
        super("dbsql", "", "DbSql.Option.");
    }

    public static void main(String[] args) {
        new DbSqlCommand().execute(args);
    }

    @Override
    protected void printHelp(CommandLine cmd, Options options) {
        System.out.println(app + " version " + Version.version());
        System.out.println("Provides a sql shell for database interaction from the command line.\n");
        super.printHelp(cmd, options);
    }

    @Override
    protected void buildOptions(Options options) {
        super.buildOptions(options);
        addOption(options, null, OPTION_SQL, true);
        addOption(options, null, OPTION_SQLFILE, true);
        addOption(options, null, OPTION_USE_SYM_DB, false);
        // Need reference to it for later, if errors
        localOptions = options;
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
    protected boolean executeWithOptions(CommandLine line) throws Exception {
        BasicDataSource basicDataSource = getDatabasePlatform(false, line.hasOption(OPTION_USE_SYM_DB)).getDataSource();
        String url = basicDataSource.getUrl();
        String user = basicDataSource.getUserName();
        String password = basicDataSource.getPassword();
        String driver = basicDataSource.getDriverClassName();
        Shell shell = new Shell();
        if (line.hasOption(OPTION_SQL)) {
            String sql = line.getOptionValue(OPTION_SQL);
            shell.runTool("-url", url, "-user", user, "-password", password, "-driver", driver, "-sql", sql);
        } else if (line.hasOption(OPTION_SQLFILE)) {
            File file = new File(line.getOptionValue(OPTION_SQLFILE));
            if (file.exists()) {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new FileReader(file));
                    String sql = null;
                    while ((sql = br.readLine()) != null) {
                        sql = sql.trim();
                        if (sql.endsWith(";")) {
                            sql = sql.substring(0, sql.length() - 1);
                        }
                        if (sql.length() > 0) {
                            // Output the sql so the user knows the result of each sql statement
                            // The H2 shell tool outputs the result of the statement execution
                            System.out.println(sql);
                            shell.runTool("-url", url, "-user", user, "-password", password, "-driver", driver, "-sql", sql);
                        }
                    }
                } finally {
                    if (br != null) {
                        br.close();
                    }
                }
            } else {
                // Notify user about missing file name
                System.err.println("-------------------------------------------------------------------------------");
                System.err.println("File does not exist: " + file.getPath());
                System.err.println("-------------------------------------------------------------------------------");
                printHelp(line, localOptions);
            }
        } else {
            shell.runTool("-url", url, "-user", user, "-password", password, "-driver", driver);
        }
        return true;
    }
}
