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
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.io.DbCompare;
import org.jumpmind.symmetric.io.DbCompareConfig;
import org.jumpmind.symmetric.io.DbCompareReport;
import org.jumpmind.symmetric.io.DbCompareReport.TableReport;
import org.jumpmind.symmetric.util.TypedPropertiesFactory;

public class DbCompareCommand extends AbstractCommandLauncher {
    private Properties configProperties;

    public DbCompareCommand() {
        super("dbcompare", "[tablename...]", "DbCompare.Option.");
    }

    @Override
    protected boolean printHelpIfNoOptionsAreProvided() {
        return true;
    }

    @Override
    protected boolean requiresPropertiesFile(CommandLine line) {
        return false;
    }

    @Override
    protected boolean executeWithOptions(CommandLine line) throws Exception {
        DbCompareConfig config = new DbCompareConfig();
        String source = line.getOptionValue('s');
        if (source == null) {
            source = getOptionValue(OPTION_SOURCE, "source", line, config);
        }
        if (StringUtils.isEmpty(source)) {
            throw new ParseException("-source properties file is required.");
        }
        File sourceProperies = new File(source);
        if (!sourceProperies.exists()) {
            throw new SymmetricException("Source properties file '" + sourceProperies + "' does not exist.");
        }
        String target = line.getOptionValue('t');
        if (target == null) {
            target = getOptionValue(OPTION_TARGET, "target", line, config);
        }
        if (StringUtils.isEmpty(target)) {
            throw new ParseException("-target properties file is required.");
        }
        File targetProperties = new File(target);
        if (!targetProperties.exists()) {
            throw new SymmetricException("Target properties file '" + targetProperties + "' does not exist.");
        }
        config.setOutputSql(getOptionValue(OPTION_OUTPUT_SQL, "outputSql", line, config));
        config.setUseSymmetricConfig(Boolean.valueOf(getOptionValue(OPTION_USE_SYM_CONFIG, "useSymmetricConfig", line, config)));
        String excludedTableNames = getOptionValue(OPTION_EXCLUDE, "excludedTableNames", line, config);
        if (excludedTableNames != null) {
            config.setExcludedTableNames(Arrays.asList(excludedTableNames.split(",")));
        }
        String targetTables = getOptionValue(OPTION_TARGET_TABLES, "targetTableNames", line, config);
        if (targetTables != null) {
            config.setTargetTableNames(Arrays.asList(targetTables.split(",")));
        }
        config.setWhereClauses(parseProperty(line, DbCompareConfig.WHERE_CLAUSE));
        config.setOrderBySuffixes(parseProperty(line, DbCompareConfig.ORDER_BY_SUFFIX));
        config.setTablesToExcludedColumns(parseExcludedColumns(line));
        String sourceTables = getOptionValue(OPTION_SOURCE_TABLES, "sourceTableNames", line, config);
        if (sourceTables == null && !CollectionUtils.isEmpty(line.getArgList())) {
            config.setSourceTableNames(line.getArgList());
        } else if (sourceTables != null) {
            config.setSourceTableNames(Arrays.asList(sourceTables.split(",")));
        }
        String numericScaleArg = getOptionValue(OPTION_NUMERIC_SCALE, "numericScale", line, config);
        if (!StringUtils.isEmpty(numericScaleArg)) {
            try {
                config.setNumericScale(Integer.parseInt(numericScaleArg.trim()));
            } catch (NumberFormatException ex) {
                throw new RuntimeException("Failed to parse arg [" + numericScaleArg + "] ", ex);
            }
        }
        String dateTimeFormatArg = getOptionValue(OPTION_DATE_TIME_FORMAT, "dateTimeFormat", line, config);
        if (!isValidDateFormat(dateTimeFormatArg)) {
            System.err.println("The date time format value " + dateTimeFormatArg + " is not a valid date time format.");
            System.exit(1);
        }
        if (!StringUtils.isEmpty(dateTimeFormatArg)) {
            config.setDateTimeFormat(dateTimeFormatArg);
        }
        String continueAfterError = getOptionValue(OPTION_CONTINUE_AFTER_ERROR, "continueAfterError", line, config);
        if (!StringUtils.isEmpty(continueAfterError)) {
            config.setContinueAfterError(Boolean.parseBoolean(continueAfterError));
        }
        ISymmetricEngine sourceEngine = new ClientSymmetricEngine(sourceProperies);
        ISymmetricEngine targetEngine = new ClientSymmetricEngine(targetProperties);
        DbCompare dbCompare = new DbCompare(sourceEngine, targetEngine, config);
        DbCompareReport report = dbCompare.compare();
        for (TableReport tableReport : report.getTableReports()) {
            if (tableReport.getErrorRows() > 0) {
                if (tableReport.getThrowable() instanceof RuntimeException) {
                    throw (RuntimeException) tableReport.getThrowable();
                }
                throw new RuntimeException(tableReport.getThrowable());
            }
        }
        return false;
    }

    private boolean isValidDateFormat(String format) {
        try {
            if (format != null) {
                new SimpleDateFormat(format);
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    protected String getOptionValue(String optionName, String internalName, CommandLine line, DbCompareConfig config) {
        String optionValue = line.hasOption(optionName) ? line.getOptionValue(optionName) : null;
        if (optionValue == null) {
            Properties props = getConfigProperties(line);
            optionValue = props != null ? props.getProperty(optionName) : null;
            if (optionValue == null) {
                String optionNameUnderScore = optionName.replace('-', '_');
                optionValue = props != null ? props.getProperty(optionNameUnderScore) : null;
                if (optionValue != null) {
                    config.setConfigSource(internalName, line.getOptionValue(OPTION_CONFIG_PROPERTIES));
                }
            } else {
                config.setConfigSource(internalName, line.getOptionValue(OPTION_CONFIG_PROPERTIES));
            }
        } else {
            config.setConfigSource(internalName, "command-line");
        }
        return optionValue;
    }

    public static void main(String[] args) throws Exception {
        new DbCompareCommand().execute(args);
    }

    protected static void initFromServerProperties() {
    }

    private static final String OPTION_SOURCE = "source";
    private static final String OPTION_TARGET = "target";
    private static final String OPTION_EXCLUDE = "exclude";
    private static final String OPTION_SOURCE_TABLES = "source-tables";
    private static final String OPTION_TARGET_TABLES = "target-tables";
    private static final String OPTION_USE_SYM_CONFIG = "use-sym-config";
    private static final String OPTION_OUTPUT_SQL = "output-sql";
    private static final String OPTION_NUMERIC_SCALE = "numeric-scale";
    private static final String OPTION_DATE_TIME_FORMAT = "date-time-format";
    private static final String OPTION_CONFIG_PROPERTIES = "config";
    private static final String OPTION_CONTINUE_AFTER_ERROR = "continue-after-error";

    @Override
    protected void printHelp(CommandLine cmd, Options options) {
        System.out.println(app + " version " + Version.version());
        System.out.println("Compare tables from 2 databases.\n");
        super.printHelp(cmd, options);
    }

    @Override
    protected void buildOptions(Options options) {
        addCommonOption(options, "h", HELP, false);
        addOption(options, "s", OPTION_SOURCE, true);
        addOption(options, "t", OPTION_TARGET, true);
        addOption(options, null, OPTION_EXCLUDE, true);
        addOption(options, null, OPTION_TARGET_TABLES, true);
        addOption(options, null, OPTION_USE_SYM_CONFIG, true);
        addOption(options, null, OPTION_OUTPUT_SQL, true);
        addOption(options, null, OPTION_NUMERIC_SCALE, true);
        addOption(options, null, OPTION_DATE_TIME_FORMAT, true);
        addOption(options, null, OPTION_CONFIG_PROPERTIES, true);
        addOption(options, null, OPTION_CONTINUE_AFTER_ERROR, true);
    }

    protected Map<String, String> parseProperty(CommandLine line, String propertyName) {
        Properties props = getConfigProperties(line);
        Map<String, String> valueMap = new HashMap<String, String>();
        if (props != null) {
            for (Object key : props.keySet()) {
                String arg = key.toString();
                if (arg.endsWith(propertyName)) {
                    valueMap.put(arg, props.getProperty(arg));
                }
            }
        }
        return valueMap;
    }

    protected Map<String, List<String>> parseExcludedColumns(CommandLine line) {
        Properties props = getConfigProperties(line);
        Map<String, List<String>> tablesToExcludedColumns = new HashMap<String, List<String>>();
        if (props != null) {
            for (Object key : props.keySet()) {
                String arg = key.toString();
                if (arg.endsWith(DbCompareConfig.EXCLUDED_COLUMN)) {
                    List<String> excludedColumns = tablesToExcludedColumns.get(key);
                    if (excludedColumns == null) {
                        excludedColumns = new ArrayList<String>();
                        tablesToExcludedColumns.put(key.toString(), excludedColumns);
                    }
                    excludedColumns.addAll(Arrays.asList(props.getProperty(arg).split(",")));
                }
            }
        }
        return tablesToExcludedColumns;
    }

    protected Properties getConfigProperties(CommandLine line) {
        if (configProperties != null) {
            return configProperties;
        } else {
            String configPropertiesFile = line.getOptionValue(OPTION_CONFIG_PROPERTIES);
            if (!StringUtils.isEmpty(configPropertiesFile)) {
                Properties props = new Properties();
                try (FileInputStream in = new FileInputStream(configPropertiesFile)) {
                    props.load(in);
                    TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(props);
                    configProperties = props;
                    return configProperties;
                } catch (Exception ex) {
                    String qualifiedFileName = new File(configPropertiesFile).getAbsolutePath();
                    throw new SymmetricException("Could not load config properties file '" + configPropertiesFile +
                            "' at '" + qualifiedFileName + "' ", ex);
                }
            }
        }
        return null;
    }

    static String stripLeadingHyphens(String str) {
        if (str == null) {
            return null;
        }
        if (str.startsWith("--")) {
            return str.substring(2, str.length());
        } else if (str.startsWith("-")) {
            return str.substring(1, str.length());
        }
        return str;
    }
}
