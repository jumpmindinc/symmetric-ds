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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.DbFill;
import org.jumpmind.symmetric.io.data.DmlWeight;
import org.jumpmind.symmetric.service.IParameterService;

public class DbFillCommand extends AbstractCommandLauncher {
    private static final String OPTION_SCHEMA = "schema";
    private static final String OPTION_CATALOG = "catalog";
    private static final String OPTION_COUNT = "count";
    private static final String OPTION_CASCADE = "cascade";
    private static final String OPTION_CASCADE_SELECT = "select";
    private static final String OPTION_IGNORE_TABLES = "ignore";
    private static final String OPTION_PREFIXED_TABLES = "prefixed";
    private static final String OPTION_INTERVAL = "interval";
    private static final String OPTION_WEIGHTS = "weights";
    private static final String OPTION_CONTINUE = "continue";
    private static final String OPTION_PRINT = "print";
    private static final String OPTION_RAND = "rand";
    private static final String OPTION_REPEAT = "repeat";
    private static final String OPTION_COMMIT = "commit";
    private static final String OPTION_COMMIT_DELAY = "commit-delay";
    private static final String OPTION_ROLLBACK = "rollback";
    private static final String OPTION_TRUNCATE = "truncate";
    private static final String OPTION_MAX_TEXT_SIZE = "max-text-size";
    private static final String OPTION_MAX_BYTE_SIZE = "max-byte-size";

    public DbFillCommand() {
        super("dbfill", "[tablename...]", "DbFill.Option.");
    }

    public static void main(String[] args) {
        new DbFillCommand().execute(args);
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
        System.out.println("Fill database tables with random generated data.\n");
        super.printHelp(cmd, options);
    }

    @Override
    protected void buildOptions(Options options) {
        super.buildOptions(options);
        addOption(options, null, OPTION_SCHEMA, true);
        addOption(options, null, OPTION_CATALOG, true);
        addOption(options, null, OPTION_COUNT, true);
        addOption(options, null, OPTION_CASCADE, false);
        addOption(options, null, OPTION_CASCADE_SELECT, false);
        addOption(options, null, OPTION_IGNORE_TABLES, true);
        addOption(options, null, OPTION_PREFIXED_TABLES, true);
        addOption(options, null, OPTION_INTERVAL, true);
        addOption(options, null, OPTION_WEIGHTS, true);
        addOption(options, null, OPTION_CONTINUE, false);
        addOption(options, null, OPTION_PRINT, false);
        addOption(options, null, OPTION_RAND, false);
        addOption(options, null, OPTION_REPEAT, true);
        addOption(options, null, OPTION_COMMIT, true);
        addOption(options, null, OPTION_COMMIT_DELAY, true);
        addOption(options, null, OPTION_ROLLBACK, true);
        addOption(options, null, OPTION_TRUNCATE, false);
        addOption(options, null, OPTION_MAX_TEXT_SIZE, true);
        addOption(options, null, OPTION_MAX_BYTE_SIZE, true);
    }

    @Override
    protected boolean executeWithOptions(CommandLine line) throws Exception {
        DbFill dbFill = new DbFill(getDatabasePlatform(false));
        if (line.hasOption(OPTION_SCHEMA)) {
            dbFill.setSchema(line.getOptionValue(OPTION_SCHEMA));
        }
        if (line.hasOption(OPTION_CATALOG)) {
            dbFill.setCatalog(line.getOptionValue(OPTION_CATALOG));
        }
        if (line.hasOption(OPTION_COUNT)) {
            dbFill.setRecordCount(Integer.parseInt(line.getOptionValue(OPTION_COUNT)));
        }
        if (line.hasOption(OPTION_CASCADE)) {
            dbFill.setCascading(true);
        }
        if (line.hasOption(OPTION_CASCADE_SELECT)) {
            dbFill.setCascadingSelect(true);
        }
        if (line.hasOption(OPTION_INTERVAL)) {
            dbFill.setInterval(Integer.parseInt(line.getOptionValue(OPTION_INTERVAL)));
        }
        if (line.hasOption(OPTION_WEIGHTS)) {
            dbFill.setDmlWeight(new DmlWeight(line.getOptionValue(OPTION_WEIGHTS)));
        }
        if (line.hasOption(OPTION_DEBUG)) {
            dbFill.setDebug(true);
        }
        if (line.hasOption(OPTION_VERBOSE_CONSOLE)) {
            dbFill.setVerbose(true);
        }
        String ignore[] = null;
        if (line.hasOption(OPTION_IGNORE_TABLES)) {
            ignore = line.getOptionValue(OPTION_IGNORE_TABLES).split(",");
        }
        String prefixed[] = null;
        if (line.hasOption(OPTION_PREFIXED_TABLES)) {
            prefixed = line.getOptionValue(OPTION_PREFIXED_TABLES).split(",");
        }
        if (line.hasOption(OPTION_CONTINUE)) {
            dbFill.setContinueOnError(true);
        }
        if (line.hasOption(OPTION_PRINT)) {
            dbFill.setPrint(true);
        }
        if (line.hasOption(OPTION_RAND)) {
            dbFill.setUseRandomCount(true);
        }
        if (line.hasOption(OPTION_REPEAT)) {
            dbFill.setRepeat(Integer.parseInt(line.getOptionValue(OPTION_REPEAT)));
        }
        if (line.hasOption(OPTION_COMMIT)) {
            dbFill.setMaxRowsCommit(Integer.parseInt(line.getOptionValue(OPTION_COMMIT)));
        }
        if (line.hasOption(OPTION_COMMIT_DELAY)) {
            dbFill.setCommitDelay(Integer.parseInt(line.getOptionValue(OPTION_COMMIT_DELAY)));
        }
        if (line.hasOption(OPTION_ROLLBACK)) {
            dbFill.setPercentRollback(Integer.parseInt(line.getOptionValue(OPTION_ROLLBACK)));
        }
        if (line.hasOption(OPTION_TRUNCATE)) {
            dbFill.setTruncate(true);
        }
        if (line.hasOption(OPTION_MAX_TEXT_SIZE)) {
            dbFill.setMaxTextSize(Integer.parseInt(line.getOptionValue(OPTION_MAX_TEXT_SIZE)));
        }
        if (line.hasOption(OPTION_MAX_BYTE_SIZE)) {
            dbFill.setMaxByteSize(Integer.parseInt(line.getOptionValue(OPTION_MAX_BYTE_SIZE)));
        }
        // Ignore the Symmetric config tables.
        getSymmetricEngine();
        IParameterService parameterService = engine.getParameterService();
        String cfgPrefix = parameterService.getString(ParameterConstants.RUNTIME_CONFIG_TABLE_PREFIX);
        dbFill.setIgnore((String[]) ArrayUtils.add(ignore, cfgPrefix));
        dbFill.setPrefixed(prefixed);
        Map<String, DmlWeight> tableProperties = parseTableProperties();
        // If tables are provided in the property file, ignore the tables provided at the command line.
        String[] tableNames = null;
        if (tableProperties.size() != 0) {
            tableNames = tableProperties.keySet().toArray(new String[0]);
        } else {
            tableNames = line.getArgs();
        }
        if (!dbFill.getPrint()) {
            dbFill.fillTables(tableNames, tableProperties);
        } else {
            for (String tableName : tableNames) {
                Table table = (Table) platform.readRelationFromDatabase(dbFill.getCatalogToUse(), dbFill.getSchemaToUse(),
                        tableName);
                if (table != null) {
                    for (int i = 0; i < dbFill.getRecordCount(); i++) {
                        for (int j = 0; j < dbFill.getInsertWeight(); j++) {
                            String sql = dbFill.createDynamicRandomInsertSql(table);
                            System.out.println(sql);
                        }
                        for (int j = 0; j < dbFill.getUpdateWeight(); j++) {
                            String sql = dbFill.createDynamicRandomUpdateSql(table);
                            System.out.println(sql);
                        }
                        for (int j = 0; j < dbFill.getDeleteWeight(); j++) {
                            String sql = dbFill.createDynamicRandomDeleteSql(table);
                            System.out.println(sql);
                        }
                    }
                }
            }
        }
        return true;
    }

    private Map<String, DmlWeight> parseTableProperties() {
        Map<String, DmlWeight> tableProperties = new HashMap<String, DmlWeight>();
        Properties properties = engine.getProperties();
        Enumeration<Object> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = (String) properties.get(key);
            if (key.startsWith("fill.")) {
                String tableName = null;
                tableName = key.substring(key.lastIndexOf(".") + 1);
                DmlWeight dmlWeight = new DmlWeight(value);
                tableProperties.put(tableName, dmlWeight);
            }
        }
        return tableProperties;
    }
}
