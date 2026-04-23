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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.SqlScript;
import org.jumpmind.exception.IoException;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.security.KeystoreAliasException;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.security.SecurityServiceFactory.SecurityServiceType;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.ext.IConfigImportInterceptor;
import org.jumpmind.symmetric.io.data.DbExportUtils;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IPurgeService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.util.ConfigImportHelper;
import org.jumpmind.symmetric.util.ModuleException;
import org.jumpmind.symmetric.util.ModuleManager;
import org.jumpmind.symmetric.util.PropertiesUtil;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.FormatUtils;
import org.jumpmind.util.JarBuilder;
import org.jumpmind.util.ZipBuilder;

/**
 * Perform administration tasks with SymmetricDS.
 */
public class SymmetricAdmin extends AbstractCommandLauncher {
    private static final Log log = LogFactory.getLog(SymmetricAdmin.class);
    private static final String CMD_LIST_ENGINES = "list-engines";
    private static final String CMD_RELOAD_NODE = "reload-node";
    private static final String CMD_RELOAD_TABLE = "reload-table";
    private static final String CMD_EXPORT_BATCH = "export-batch";
    private static final String CMD_IMPORT_BATCH = "import-batch";
    private static final String CMD_RUN_JOB = "run-job";
    private static final String CMD_RUN_PURGE = "run-purge";
    private static final String CMD_ENCRYPT_TEXT = "encrypt-text";
    private static final String CMD_OBFUSCATE_TEXT = "obfuscate-text";
    private static final String CMD_UNOBFUSCATE_TEXT = "unobfuscate-text";
    private static final String CMD_CREATE_WAR = "create-war";
    private static final String CMD_CREATE_SYM_TABLES = "create-sym-tables";
    private static final String CMD_EXPORT_SYM_TABLES = "export-sym-tables";
    private static final String CMD_EXPORT_SYM_OBJECTS = "export-sym-objects";
    private static final String CMD_OPEN_REGISTRATION = "open-registration";
    private static final String CMD_REMOVE_NODE = "remove-node";
    private static final String CMD_SYNC_TRIGGERS = "sync-triggers";
    private static final String CMD_DROP_TRIGGERS = "drop-triggers";
    private static final String CMD_EXPORT_PROPERTIES = "export-properties";
    private static final String CMD_UNINSTALL = "uninstall";
    private static final String CMD_MODULE = "module";
    private static final String CMD_SEND_SQL = "send-sql";
    private static final String CMD_SEND_SCRIPT = "send-script";
    private static final String CMD_SEND_SCHEMA = "send-schema";
    private static final String CMD_BACKUP_FILE_CONFIGURATION = "backup-config";
    private static final String CMD_RESTORE_FILE_CONFIGURATION = "restore-config";
    private static final String CMD_IMPORT_CONFIG = "import-config";
    private static final String CMD_EXPORT_CONFIG = "export-config";
    private static final String CMD_IMPORT_CERT = "import-cert";
    private static final String CMD_TAKE_SNAPSHOT = "take-snapshot";
    private static final String[] NO_ENGINE_REQUIRED = { CMD_EXPORT_PROPERTIES, CMD_ENCRYPT_TEXT, CMD_OBFUSCATE_TEXT, CMD_UNOBFUSCATE_TEXT, CMD_LIST_ENGINES,
            CMD_MODULE, CMD_BACKUP_FILE_CONFIGURATION, CMD_RESTORE_FILE_CONFIGURATION, CMD_CREATE_WAR };
    private static final String OPTION_NODE = "node";
    private static final String OPTION_CATALOG = "catalog";
    private static final String OPTION_SCHEMA = "schema";
    private static final String OPTION_WHERE = "where";
    private static final String OPTION_FORCE = "force";
    private static final String OPTION_OUT = "out";
    private static final String OPTION_SYM = "sym";
    private static final String OPTION_NODE_GROUP = "node-group";
    private static final String OPTION_REVERSE = "reverse";
    private static final String OPTION_IN = "in";
    private static final String OPTION_EXCLUDE_INDICES = "exclude-indices";
    private static final String OPTION_EXCLUDE_FOREIGN_KEYS = "exclude-fk";
    private static final String OPTION_EXCLUDE_DEFAULTS = "exclude-defaults";
    private static final String OPTION_EXCLUDE_LOGBACK = "exclude-logback";
    private static final String OPTION_EXTERNAL_SECURITY = "external-security";
    private static final String OPTION_ALTERS = "alters";
    private static final String OPTION_EXCLUDE_TABLES = "exclude-tables";
    private static final String OPTION_FILE = "file";
    private static final String OPTION_ACCEPT_ALL = "accept-all";
    private static final int WIDTH = 120;
    private static final int PAD = 3;

    public SymmetricAdmin(String app, String argSyntax, String messageKeyPrefix) {
        super(app, argSyntax, messageKeyPrefix);
    }

    public static void main(String[] args) throws Exception {
        new SymmetricAdmin("symadmin", "<subcommand> [options] [args]", "SymAdmin.Option.")
                .execute(args);
    }

    @Override
    protected boolean printHelpIfNoOptionsAreProvided() {
        return true;
    }

    @Override
    protected boolean requiresPropertiesFile(CommandLine line) {
        String[] args = line.getArgs();
        if (args.length >= 1) {
            String cmd = args[0];
            return !ArrayUtils.contains(NO_ENGINE_REQUIRED, cmd);
        }
        return true;
    }

    @Override
    protected void printHelp(CommandLine line, Options options) {
        String[] args = line.getArgs();
        if (args.length > 1 && args[0].equals(HELP)) {
            printHelpCommand(line);
        } else {
            System.out.println(app + " version " + Version.version());
            System.out.println("Perform administration tasks with SymmetricDS.\n");
            System.out
                    .println("Usage: symadmin <subcommand> --engine [engine.name] [options] [args]");
            System.out
                    .println("       symadmin <subcommand> --properties [properties file] [options] [args]");
            System.out
                    .println("\nType 'symadmin help <subcommand>' for help on a specific subcommand.\n");
            System.out.println("Available subcommands:");
            PrintWriter pw = new PrintWriter(System.out);
            printHelpLine(pw, CMD_LIST_ENGINES);
            printHelpLine(pw, CMD_OPEN_REGISTRATION);
            printHelpLine(pw, CMD_REMOVE_NODE);
            printHelpLine(pw, CMD_RELOAD_NODE);
            printHelpLine(pw, CMD_RELOAD_TABLE);
            printHelpLine(pw, CMD_EXPORT_BATCH);
            printHelpLine(pw, CMD_IMPORT_BATCH);
            printHelpLine(pw, CMD_RUN_JOB);
            printHelpLine(pw, CMD_RUN_PURGE);
            printHelpLine(pw, CMD_ENCRYPT_TEXT);
            printHelpLine(pw, CMD_OBFUSCATE_TEXT);
            printHelpLine(pw, CMD_CREATE_WAR);
            printHelpLine(pw, CMD_CREATE_SYM_TABLES);
            printHelpLine(pw, CMD_EXPORT_SYM_TABLES);
            printHelpLine(pw, CMD_EXPORT_SYM_OBJECTS);
            printHelpLine(pw, CMD_SYNC_TRIGGERS);
            printHelpLine(pw, CMD_DROP_TRIGGERS);
            printHelpLine(pw, CMD_EXPORT_PROPERTIES);
            printHelpLine(pw, CMD_SEND_SQL);
            printHelpLine(pw, CMD_SEND_SCHEMA);
            printHelpLine(pw, CMD_SEND_SCRIPT);
            printHelpLine(pw, CMD_BACKUP_FILE_CONFIGURATION);
            printHelpLine(pw, CMD_RESTORE_FILE_CONFIGURATION);
            printHelpLine(pw, CMD_UNINSTALL);
            printHelpLine(pw, CMD_MODULE);
            printHelpLine(pw, CMD_IMPORT_CONFIG);
            printHelpLine(pw, CMD_EXPORT_CONFIG);
            printHelpLine(pw, CMD_IMPORT_CERT);
            printHelpLine(pw, CMD_TAKE_SNAPSHOT);
            pw.flush();
        }
    }

    private void printHelpLine(PrintWriter pw, String cmd) {
        String text = StringUtils.rightPad("   " + cmd, 23, " ")
                + Message.get("SymAdmin.Cmd." + cmd);
        new HelpFormatter().printWrapped(pw, 79, 25, text);
    }

    private void printHelpCommand(CommandLine line) {
        String[] args = line.getArgs();
        if (args.length > 1) {
            String cmd = args[1];
            HelpFormatter format = new HelpFormatter();
            PrintWriter writer = new PrintWriter(System.out);
            Options options = new Options();
            if (!Message.containsKey("SymAdmin.Usage." + cmd)) {
                System.err.println("ERROR: no help text for subcommand '" + cmd + "' was found.");
                System.err.println("For a list of subcommands, use " + app + " --" + HELP + "\n");
                return;
            }
            format.printWrapped(writer, WIDTH,
                    "Usage: " + app + " " + cmd + " " + Message.get("SymAdmin.Usage." + cmd) + "\n");
            format.printWrapped(writer, WIDTH, Message.get("SymAdmin.Help." + cmd));
            if (cmd.equals(CMD_SEND_SQL) || cmd.equals(CMD_SEND_SCHEMA)
                    || cmd.equals(CMD_RELOAD_TABLE) || cmd.equals(CMD_SEND_SCRIPT)) {
                addOption(options, "n", OPTION_NODE, true);
                addOption(options, "g", OPTION_NODE_GROUP, true);
            }
            if (cmd.equals(CMD_RELOAD_TABLE) || cmd.equals(CMD_SYNC_TRIGGERS) || cmd.equals(CMD_SEND_SQL) || cmd.equals(CMD_SEND_SCHEMA)) {
                addOption(options, "c", OPTION_CATALOG, true);
                addOption(options, "s", OPTION_SCHEMA, true);
            }
            if (cmd.equals(CMD_RELOAD_TABLE)) {
                addOption(options, "w", OPTION_WHERE, true);
            }
            if (cmd.equals(CMD_SYNC_TRIGGERS)) {
                addOption(options, "o", OPTION_OUT, true);
                addOption(options, "f", OPTION_FORCE, false);
            }
            if (cmd.equals(CMD_DROP_TRIGGERS)) {
                addOption(options, null, OPTION_SYM, false);
            }
            if (cmd.equals(CMD_RELOAD_NODE)) {
                addOption(options, "r", OPTION_REVERSE, false);
            }
            if (cmd.equals(CMD_REMOVE_NODE)) {
                addOption(options, "n", OPTION_NODE, true);
            }
            if (cmd.equals(CMD_BACKUP_FILE_CONFIGURATION)) {
                addOption(options, "o", OPTION_OUT, true);
            }
            if (cmd.equals(CMD_RESTORE_FILE_CONFIGURATION)) {
                addOption(options, "i", OPTION_IN, true);
            }
            if (cmd.equals(CMD_SEND_SCHEMA)) {
                addOption(options, null, OPTION_EXCLUDE_INDICES, false);
                addOption(options, null, OPTION_EXCLUDE_FOREIGN_KEYS, false);
                addOption(options, null, OPTION_EXCLUDE_DEFAULTS, false);
            }
            if (cmd.equals(CMD_CREATE_WAR)) {
                addOption(options, null, OPTION_EXCLUDE_LOGBACK, false);
                addOption(options, null, OPTION_EXTERNAL_SECURITY, false);
            }
            if (cmd.equals(CMD_EXPORT_SYM_TABLES)) {
                addOption(options, null, OPTION_ALTERS, false);
            }
            if (cmd.equals(CMD_EXPORT_SYM_OBJECTS)) {
                addOption(options, "x", OPTION_EXCLUDE_TABLES, false);
            }
            if (cmd.equals(CMD_SEND_SQL)) {
                addOption(options, "f", OPTION_FILE, true);
            }
            if (cmd.equals(CMD_IMPORT_CERT)) {
                addOption(options, null, OPTION_ACCEPT_ALL, false);
            }
            if (options.getOptions().size() > 0) {
                format.printWrapped(writer, WIDTH, "\nOptions:");
                format.printOptions(writer, WIDTH, options, PAD, PAD);
            }
            if (!ArrayUtils.contains(NO_ENGINE_REQUIRED, cmd)) {
                format.printWrapped(writer, WIDTH, "\nEngine options:");
                options = new Options();
                super.buildOptions(options);
                format.printOptions(writer, WIDTH, options, PAD, PAD);
                format.printWrapped(writer, WIDTH, "\nCrypto options:");
                options = new Options();
                buildCryptoOptions(options);
                format.printOptions(writer, WIDTH, options, PAD, PAD);
            }
            writer.flush();
        }
    }

    @Override
    protected void buildOptions(Options options) {
        super.buildOptions(options);
        addOption(options, "n", OPTION_NODE, true);
        addOption(options, "g", OPTION_NODE_GROUP, true);
        addOption(options, "c", OPTION_CATALOG, true);
        addOption(options, "s", OPTION_SCHEMA, true);
        addOption(options, "w", OPTION_WHERE, true);
        addOption(options, "f", OPTION_FORCE, false);
        addOption(options, "o", OPTION_OUT, true);
        addOption(options, null, OPTION_SYM, false);
        addOption(options, "r", OPTION_REVERSE, false);
        addOption(options, "i", OPTION_IN, true);
        addOption(options, null, OPTION_EXCLUDE_INDICES, false);
        addOption(options, null, OPTION_EXCLUDE_FOREIGN_KEYS, false);
        addOption(options, null, OPTION_EXCLUDE_DEFAULTS, false);
        addOption(options, null, OPTION_EXCLUDE_LOGBACK, false);
        addOption(options, null, OPTION_EXTERNAL_SECURITY, false);
        addOption(options, null, OPTION_ALTERS, false);
        addOption(options, "x", OPTION_EXCLUDE_TABLES, false);
        addOption(options, null, OPTION_FILE, true);
        addOption(options, null, OPTION_ACCEPT_ALL, false);
        buildCryptoOptions(options);
    }

    @Override
    protected boolean executeWithOptions(CommandLine line) throws Exception {
        List<String> args = line.getArgList();
        String cmd = args.remove(0);
        configureCrypto(line);
        if (cmd.equals(CMD_EXPORT_PROPERTIES)) {
            exportDefaultProperties(line, args);
            return true;
        } else if (cmd.equals(CMD_LIST_ENGINES)) {
            listEngines(line, args);
            return true;
        } else if (cmd.equals(CMD_CREATE_WAR)) {
            generateWar(line, args);
            return true;
        } else if (cmd.equals(CMD_EXPORT_SYM_TABLES)) {
            exportSymTables(line, args);
            return true;
        } else if (cmd.equals(CMD_EXPORT_SYM_OBJECTS)) {
            exportSymObjects(line, args);
            return true;
        } else if (cmd.equals(CMD_RUN_JOB)) {
            runJob(line, args);
            return true;
        } else if (cmd.equals(CMD_RUN_PURGE)) {
            runPurge(line, args);
            return true;
        } else if (cmd.equals(CMD_OPEN_REGISTRATION)) {
            openRegistration(line, args);
            return true;
        } else if (cmd.equals(CMD_REMOVE_NODE)) {
            removeNode(line, args);
            return true;
        } else if (cmd.equals(CMD_RELOAD_NODE)) {
            reloadNode(line, args);
            return true;
        } else if (cmd.equals(CMD_EXPORT_BATCH)) {
            exportBatch(line, args);
            return true;
        } else if (cmd.equals(CMD_SYNC_TRIGGERS)) {
            syncTrigger(line, args);
            return true;
        } else if (cmd.equals(CMD_DROP_TRIGGERS)) {
            dropTrigger(line, args);
            return true;
        } else if (cmd.equals(CMD_CREATE_SYM_TABLES)) {
            createSymTables();
            return true;
        } else if (cmd.equals(CMD_IMPORT_BATCH)) {
            importBatch(line, args);
            return true;
        } else if (cmd.equals(CMD_ENCRYPT_TEXT)) {
            encryptText(line, args);
            return true;
        } else if (cmd.equals(CMD_OBFUSCATE_TEXT)) {
            obfuscateText(line, args);
            return true;
        } else if (cmd.equals(CMD_UNOBFUSCATE_TEXT)) {
            unobfuscateText(line, args);
            return true;
        } else if (cmd.equals(CMD_SEND_SQL)) {
            sendSql(line, args);
            return true;
        } else if (cmd.equals(CMD_UNINSTALL)) {
            uninstall(line, args);
            return true;
        } else if (cmd.equals(CMD_RELOAD_TABLE)) {
            reloadTable(line, args);
            return true;
        } else if (cmd.equals(CMD_SEND_SCHEMA)) {
            sendSchema(line, args);
            return true;
        } else if (cmd.equals(CMD_SEND_SCRIPT)) {
            sendScript(line, args);
            return true;
        } else if (cmd.equals(CMD_MODULE)) {
            module(line, args);
            return true;
        } else if (cmd.equals(CMD_BACKUP_FILE_CONFIGURATION)) {
            backup(line, args);
            return true;
        } else if (cmd.equals(CMD_RESTORE_FILE_CONFIGURATION)) {
            restore(line, args);
            return true;
        } else if (cmd.equals(CMD_IMPORT_CONFIG)) {
            importConfig(line, args);
            return true;
        } else if (cmd.equals(CMD_EXPORT_CONFIG)) {
            exportConfig(line, args);
            return true;
        } else if (cmd.equals(CMD_IMPORT_CERT)) {
            importCert(line, args);
            return true;
        } else if (cmd.equals(CMD_TAKE_SNAPSHOT)) {
            takeSnapshot(line, args);
            return true;
        } else {
            throw new ParseException("ERROR: no subcommand '" + cmd + "' was found.");
        }
    }

    private String popArg(List<String> args, String argName) {
        if (args.size() == 0) {
            System.out.println("ERROR: Expected argument for: " + argName);
            System.exit(1);
        }
        return args.remove(0);
    }

    @Override
    protected String scrubCommandLine(String[] cmdArgs) {
        boolean scrubNextArg = false;
        for (int i = 0; i < cmdArgs.length; i++) {
            String arg = cmdArgs[i];
            if (scrubNextArg) {
                cmdArgs[i] = "***";
                scrubNextArg = false;
            } else if (arg != null && (arg.equals(CMD_ENCRYPT_TEXT) || arg.equals(CMD_OBFUSCATE_TEXT) || arg.equals(CMD_UNOBFUSCATE_TEXT))) {
                scrubNextArg = true;
            }
        }
        return super.scrubCommandLine(cmdArgs);
    }

    private void importConfig(CommandLine line, List<String> args) {
        String fileName = popArg(args, "file name");
        boolean isCsv = fileName.toLowerCase().endsWith(".csv");
        boolean isSql = fileName.toLowerCase().endsWith(".sql");
        if (!isCsv && !isSql) {
            System.err.println("ERROR: Expected a .csv or .sql file.");
            System.exit(1);
        }
        try {
            String content = FileUtils.readFileToString(new File(fileName), Charset.defaultCharset());
            try (ConfigImportHelper helper = new ConfigImportHelper(getSymmetricEngine().getTablePrefix())) {
                helper.loadContent(content, isCsv);
                if (!helper.containsNodeGroup(engine.getParameterService().getNodeGroupId())) {
                    log.error(String.format("ERROR: Imported configuration doesn't contain current node group (%s)",
                            engine.getParameterService().getNodeGroupId()));
                    System.exit(1);
                }
                IConfigImportInterceptor interceptor = getSymmetricEngine().getExtensionService()
                        .getExtensionPoint(IConfigImportInterceptor.class);
                if (interceptor != null) {
                    interceptor.interceptImport(helper.getEngine());
                }
                new SqlScript(helper.exportConfigAsSql(), getSymmetricEngine().getSqlTemplate(), true, null).execute();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void exportConfig(CommandLine line, List<String> args) {
        String fileName = popArg(args, "file name");
        try (FileWriter fw = new FileWriter(fileName)) {
            if (fileName.toLowerCase().endsWith(".csv")) {
                IDataExtractorService dataExtractorService = getSymmetricEngine().getDataExtractorService();
                Node me = getSymmetricEngine().getNodeService().findIdentity();
                dataExtractorService.extractConfigurationStandalone(me, fw, TableConstants.getConfigTablesExcludedFromExport());
            } else if (fileName.toLowerCase().endsWith(".sql")) {
                DbExportUtils.extractConfigurationStandalone(getSymmetricEngine().getDatabasePlatform(), TableConstants.getConfigTablesForExport(
                        getSymmetricEngine().getTablePrefix()), fw);
            } else {
                System.err.println("ERROR: Expected a .csv or .sql file.");
                System.exit(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void listEngines(CommandLine line, List<String> args) {
        System.out.println("Engines directory is " + new File(PropertiesUtil.getEnginesDir()).getAbsolutePath());
        System.out.println("The following engines and properties files are available:");
        int count = 0;
        File[] files = PropertiesUtil.findEnginePropertiesFiles();
        for (File file : files) {
            Properties properties = new Properties();
            try (FileInputStream is = new FileInputStream(file)) {
                properties.load(is);
                String name = properties.getProperty(ParameterConstants.ENGINE_NAME);
                System.out.println(name + " -> " + file.getAbsolutePath());
                count++;
            } catch (IOException ex) {
            }
        }
        System.out.println(count + " engines returned");
    }

    private void runJob(CommandLine line, List<String> args) throws Exception {
        String jobName = popArg(args, "job name");
        if (jobName.equals("pull")) {
            getSymmetricEngine().pull();
            getSymmetricEngine().getNodeCommunicationService().stop();
        } else if (jobName.equals("push")) {
            getSymmetricEngine().push();
            getSymmetricEngine().getNodeCommunicationService().stop();
        } else if (jobName.equals("route")) {
            getSymmetricEngine().route();
        } else if (jobName.equals("purge")) {
            getSymmetricEngine().purge();
        } else if (jobName.equals("heartbeat")) {
            getSymmetricEngine().heartbeat(false);
        } else {
            throw new ParseException("ERROR: no job named '" + jobName + "' was found.");
        }
    }

    private void runPurge(CommandLine line, List<String> args) {
        IPurgeService purgeService = getSymmetricEngine().getPurgeService();
        boolean all = args.contains("all") || args.size() == 0;
        if (args.contains("outgoing") || all) {
            purgeService.purgeOutgoing(true);
        }
        if (args.contains("incoming") || all) {
            purgeService.purgeIncoming(true);
        }
    }

    private void exportBatch(CommandLine line, List<String> args) throws Exception {
        IDataExtractorService dataExtractorService = getSymmetricEngine().getDataExtractorService();
        String nodeId = popArg(args, "Node ID");
        String batchId = popArg(args, "Batch ID");
        OutputStreamWriter writer = getWriter(args);
        dataExtractorService.extractBatchRange(writer, nodeId, Long.valueOf(batchId),
                Long.valueOf(batchId));
        writer.close();
    }

    private void importBatch(CommandLine line, List<String> args) throws Exception {
        IDataLoaderService service = getSymmetricEngine().getDataLoaderService();
        InputStream in = null;
        if (args.size() == 0) {
            in = System.in;
        } else {
            in = new FileInputStream(args.get(0));
        }
        service.loadDataFromPush(getSymmetricEngine().getNodeService().findIdentity(), in,
                System.out);
        System.out.flush();
        in.close();
    }

    private void encryptText(CommandLine line, List<String> args) {
        String plainText;
        if (args.size() != 0) {
            plainText = popArg(args, "Text");
        } else {
            @SuppressWarnings("resource")
            Scanner textScanner = new Scanner(System.in);
            System.out.print("Enter Text: ");
            plainText = textScanner.next();
        }
        ISecurityService service = createSecurityService();
        System.out.println(SecurityConstants.PREFIX_ENC + service.encrypt(plainText));
    }

    private ISecurityService createSecurityService() {
        TypedProperties properties = PropertiesUtil.createTypedPropertiesFactory(propertiesFile, new Properties()).reload();
        return SecurityServiceFactory.create(SecurityServiceType.SERVER, properties);
    }

    private void obfuscateText(CommandLine line, List<String> args) {
        String plainText = popArg(args, "Text");
        ISecurityService service = createSecurityService();
        System.out.println(SecurityConstants.PREFIX_OBF + service.obfuscate(plainText));
    }

    private void unobfuscateText(CommandLine line, List<String> args) {
        String obfText = popArg(args, "Text");
        ISecurityService service = createSecurityService();
        System.out.println(service.unobfuscate(obfText.substring(SecurityConstants.PREFIX_OBF.length())));
    }

    private void openRegistration(CommandLine line, List<String> args) {
        String nodeGroupId = popArg(args, "Node Group ID");
        String externalId = popArg(args, "External ID");
        String syncUrl = args.size() > 0 ? args.remove(0) : null;
        IRegistrationService registrationService = getSymmetricEngine().getRegistrationService();
        registrationService.openRegistration(nodeGroupId, externalId, syncUrl, null, null);
        System.out.println(String.format(
                "Opened registration for node group of '%s' external ID of '%s'", nodeGroupId,
                externalId));
    }

    private void removeNode(CommandLine line, List<String> args) {
        String node = popArg(args, "Node ID");
        getSymmetricEngine().removeAndCleanupNode(node);
        System.out.println(String.format("Removed node '%s' from engine '%s'", node, getSymmetricEngine().getEngineName()));
    }

    private void reloadNode(CommandLine line, List<String> args) {
        String nodeId = popArg(args, "Node ID");
        boolean reverse = line.hasOption(OPTION_REVERSE);
        IDataService dataService = getSymmetricEngine().getDataService();
        String message = dataService.reloadNode(nodeId, reverse, "symadmin");
        System.out.println(message);
    }

    private void backup(CommandLine line, List<String> args) throws IOException {
        String filename = line.getOptionValue(OPTION_OUT);
        if (filename == null) {
            filename = "symmetric-file-configuration-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".zip";
        }
        File jarFile = null;
        if (filename != null) {
            jarFile = new File(filename);
            if (jarFile.getParentFile() != null) {
                jarFile.getParentFile().mkdirs();
            }
        }
        List<String> listOfDirs = new ArrayList<String>();
        listOfDirs.add(PropertiesUtil.getEnginesDir());
        listOfDirs.add(AppUtils.getSymHome() + "/conf");
        listOfDirs.add(AppUtils.getSymHome() + "/patches");
        listOfDirs.add(AppUtils.getSymHome() + "/security");
        String parentDir = new File(DEFAULT_SERVER_PROPERTIES).getParent();
        if (parentDir != null) {
            if (listOfDirs.indexOf(parentDir) < 0) {
                // Need to add DEFAULT_SERVER_PROPERTIES to list of files to back up
                // because the file is specified outside of the SymmetricDS installation
                listOfDirs.add(DEFAULT_SERVER_PROPERTIES);
            }
        }
        File[] arrayOfFile = new File[listOfDirs.size()];
        for (int i = 0; i < listOfDirs.size(); i++) {
            arrayOfFile[i] = new File(listOfDirs.get(i));
        }
        System.out.println("Backing up files to " + filename);
        try {
            ZipBuilder builder = new ZipBuilder(new File(AppUtils.getSymHome()), jarFile, arrayOfFile);
            builder.build();
        } catch (Exception e) {
            throw new IoException("Failed to backup configuration files into archive", e);
        }
    }

    private void restore(CommandLine line, List<String> args) throws IOException {
        String filename = line.getOptionValue(OPTION_IN);
        if (filename == null) {
            throw new IoException("Input filename must be specified");
        }
        try (FileInputStream finput = new FileInputStream(filename); ZipInputStream zip = new ZipInputStream(finput)) {
            ZipEntry entry = null;
            for (entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                System.out.println("Restoring " + entry.getName());
                File fileToOpen = null;
                File f = new File(entry.getName());
                if (f.isAbsolute()) {
                    f.getParentFile().mkdirs();
                    fileToOpen = f;
                } else {
                    fileToOpen = new File(AppUtils.getSymHome(), entry.getName());
                }
                try (FileOutputStream foutput = new FileOutputStream(fileToOpen)) {
                    final byte buffer[] = new byte[4096];
                    int readCount;
                    while ((readCount = zip.read(buffer, 0, buffer.length)) > 0) {
                        foutput.write(buffer, 0, readCount);
                    }
                }
            }
        }
    }

    private void syncTrigger(CommandLine line, List<String> args) throws IOException {
        boolean genAlways = line.hasOption(OPTION_FORCE);
        String filename = line.getOptionValue(OPTION_OUT);
        String catalogName = line.getOptionValue(OPTION_CATALOG);
        String schemaName = line.getOptionValue(OPTION_SCHEMA);
        File file = null;
        if (filename != null) {
            file = new File(filename);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
        }
        ITriggerRouterService triggerService = getSymmetricEngine().getTriggerRouterService();
        StringBuilder sqlBuffer = new StringBuilder();
        if (args.size() == 0) {
            triggerService.syncTriggers(file != null ? sqlBuffer : null, genAlways);
        } else {
            for (String tablename : args) {
                Table table = platform.getTableFromCache(catalogName, schemaName, tablename, true);
                if (table != null) {
                    triggerService.syncTriggers(table, genAlways);
                } else {
                    System.out.println("Unable to find table " + tablename);
                }
            }
        }
        if (file != null) {
            FileUtils.writeStringToFile(file, sqlBuffer.toString(), Charset.defaultCharset(), false);
        }
    }

    private void dropTrigger(CommandLine line, List<String> args) throws IOException {
        ITriggerRouterService triggerService = getSymmetricEngine().getTriggerRouterService();
        if (args.size() == 0) {
            boolean includeSymTriggers = line.hasOption(OPTION_SYM);
            if (includeSymTriggers) {
                String prefix = getSymmetricEngine().getParameterService().getTablePrefix();
                System.out.println("Dropping all triggers, including " + prefix + " triggers...");
            } else {
                System.out.println("Dropping all user-configured triggers...");
            }
            triggerService.dropTriggers(includeSymTriggers);
        } else {
            for (String tablename : args) {
                System.out.println("Dropping trigger for table " + tablename);
                Set<String> tables = new HashSet<String>();
                tables.add(tablename);
                triggerService.dropTriggers(tables);
            }
        }
    }

    private void generateWar(CommandLine line, List<String> args) throws Exception {
        String warFileName = popArg(args, "Filename");
        final File workingDirectory = new File(AppUtils.getSymHome() + "/.war");
        FileUtils.deleteDirectory(workingDirectory);
        for (File file : FileUtils.listFiles(new File(AppUtils.getSymHome() + "/web/WEB-INF/lib"), FileFilterUtils.notFileFilter(
                FileFilterUtils.or(FileFilterUtils.prefixFileFilter("jetty-"), FileFilterUtils.prefixFileFilter("websocket-"))), null)) {
            FileUtils.copyToDirectory(file, new File(workingDirectory, "WEB-INF/lib"));
        }
        File instanceIdFile = new File(AppUtils.getSymHome() + "/conf/instance.uuid");
        if (instanceIdFile.canRead()) {
            FileUtils.copyToDirectory(instanceIdFile, new File(workingDirectory, "WEB-INF/classes"));
        }
        boolean useProperties = (line.hasOption(OPTION_PROPERTIES_FILE) || line.hasOption(OPTION_ENGINE)) &&
                propertiesFile != null && propertiesFile.exists();
        if (useProperties) {
            System.out.println("Copying symmetric.properties");
            FileUtils.copyFile(propertiesFile, new File(workingDirectory, "WEB-INF/classes/symmetric.properties"));
        }
        if (!line.hasOption(OPTION_EXTERNAL_SECURITY)) {
            System.out.println("Copying security files");
            FileUtils.copyToDirectory(new File(AppUtils.getSymHome() + "/security/keystore"), new File(workingDirectory, "WEB-INF/classes"));
            FileUtils.copyToDirectory(new File(AppUtils.getSymHome() + "/security/cacerts"), new File(workingDirectory, "WEB-INF/classes"));
            File restPropFile = new File(AppUtils.getSymHome() + "/security/rest.properties");
            if (restPropFile.exists()) {
                FileUtils.copyToDirectory(restPropFile, new File(workingDirectory, "WEB-INF/classes"));
            }
        }
        if (!line.hasOption(OPTION_EXCLUDE_LOGBACK)) {
            System.out.println("Copying logback files");
            FileUtils.copyToDirectory(new File(AppUtils.getSymHome() + "/conf/logback.xml"), new File(workingDirectory, "WEB-INF/classes"));
            for (File file : FileUtils.listFiles(new File(AppUtils.getSymHome() + "/lib"), FileFilterUtils.prefixFileFilter("logback-"), null)) {
                FileUtils.copyToDirectory(file, new File(workingDirectory, "WEB-INF/lib"));
            }
        }
        System.out.println("Copying symmetric-server.properties");
        Properties prop = new Properties();
        try (InputStream in = new FileInputStream(AppUtils.getSymHome() + "/conf/symmetric-server.properties")) {
            prop.load(in);
            prop.remove(ServerConstants.HOST_BIND_NAME);
            prop.remove(ServerConstants.HTTP_ENABLE);
            prop.remove(ServerConstants.HTTPS_ENABLE);
            prop.remove(ServerConstants.HTTPS2_ENABLE);
            prop.remove(ServerConstants.HTTP_PORT);
            prop.remove(ServerConstants.HTTPS_PORT);
            if (StringUtils.isNotBlank(System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD))) {
                ISecurityService service = createSecurityService();
                String password = SecurityConstants.PREFIX_OBF + service.obfuscate(System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD));
                prop.put(SecurityConstants.SYSPROP_KEYSTORE_PASSWORD, password);
            }
            try (FileOutputStream out = new FileOutputStream(new File(workingDirectory, "WEB-INF/classes/symmetric-server.properties"))) {
                prop.store(out, warFileName);
            }
        } catch (Exception e) {
            log.error("Failed to process symmetric-server.properties", e);
        }
        System.out.println("Building web archive");
        JarBuilder builder = new JarBuilder(workingDirectory, new File(warFileName),
                new File[] { workingDirectory }, Version.version());
        builder.build();
        System.out.println("Cleaning up");
        FileUtils.deleteDirectory(workingDirectory);
        System.out.println("Created " + warFileName);
        System.out.println("\nRemember to:");
        if (!line.hasOption(OPTION_EXCLUDE_LOGBACK)) {
            System.out.println("- Edit " + warFileName + ":/WEB-INF/classes/logback.xml to set the path and filename to the log file");
        }
        if (useProperties) {
            System.out.println("- Edit " + warFileName + ":/WEB-INF/classes/symmetric.properties to set sync.url to match web server");
        } else {
            System.out.println("- Set system property for engines directory: -Dsymmetric.engines.dir=/path/to/dir");
        }
        if (line.hasOption(OPTION_EXTERNAL_SECURITY)) {
            System.out.println("- Set system property for security file: -Dsym.keystore.file=/path/to/keystore");
            System.out.println("- Set system property for cacerts file: -Djavax.net.ssl.trustStore=/path/to/cacerts");
            System.out.println("- Set system property for REST file: -Dsym.rest.properties.file=/path/to/rest.properties");
        }
        if (line.hasOption(OPTION_EXCLUDE_LOGBACK)) {
            System.out.println("- Provide a SLF4J binding JAR for your logging framework");
        }
        System.out.println("- Provide JDBC driver JAR for your database");
    }

    private void exportSymTables(CommandLine line, List<String> args) throws IOException {
        OutputStreamWriter os = getWriter(args);
        if (line.hasOption(OPTION_ALTERS)) {
            os.write(getSymmetricEngine().getSymmetricDialect().getSymmetricDdlChanges());
        } else {
            os.write(getSymmetricEngine().getSymmetricDialect().getCreateSymmetricDDL());
        }
        os.close();
    }

    private void exportSymObjects(CommandLine line, List<String> args) throws IOException {
        ISymmetricEngine engine = getSymmetricEngine();
        ISymmetricDialect dialect = engine.getSymmetricDialect();
        OutputStreamWriter os = getWriter(args);
        os.write(dialect.getCreateRequiredDatabaseObjectsDDL());
        if (!line.hasOption(OPTION_EXCLUDE_TABLES)) {
            os.write(dialect.getCreateSymmetricDDL());
        }
        os.close();
    }

    private void exportDefaultProperties(CommandLine line, List<String> args) throws IOException {
        OutputStreamWriter os = getWriter(args);
        BufferedReader is = new BufferedReader(new InputStreamReader(
                SymmetricAdmin.class.getResourceAsStream("/symmetric-default.properties"),
                Charset.defaultCharset()));
        String str = is.readLine();
        while (str != null) {
            os.write(str);
            os.write(System.getProperty("line.separator"));
            str = is.readLine();
        }
        is.close();
        os.close();
    }

    private void createSymTables() {
        getSymmetricEngine().setupDatabase(true);
    }

    private void uninstall(CommandLine line, List<String> args) {
        getSymmetricEngine().uninstall();
    }

    private void sendSql(CommandLine line, List<String> args) {
        String tableName = popArg(args, "Table Name");
        String sql;
        String catalogName = line.getOptionValue(OPTION_CATALOG);
        String schemaName = line.getOptionValue(OPTION_SCHEMA);
        String fileName = line.getOptionValue(OPTION_FILE);
        if (fileName != null) {
            try {
                File sqlFile = new File(fileName);
                sql = FileUtils.readFileToString(sqlFile, Charset.defaultCharset());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            sql = popArg(args, "SQL");
        }
        for (Node node : getNodes(line)) {
            System.out.println("Sending SQL to node '" + node.getNodeId() + "'");
            getSymmetricEngine().getDataService().sendSQL(node.getNodeId(), catalogName,
                    schemaName, tableName, sql);
        }
    }

    private void reloadTable(CommandLine line, List<String> args) {
        String catalogName = line.getOptionValue(OPTION_CATALOG);
        String schemaName = line.getOptionValue(OPTION_SCHEMA);
        if (args.size() == 0) {
            System.out.println("ERROR: Expected argument for: Table Name");
            System.exit(1);
        }
        for (String tableName : args) {
            for (Node node : getNodes(line)) {
                System.out.println("Reloading table '" + tableName + "' to node '" + node.getNodeId() + "'");
                if (line.hasOption(OPTION_WHERE)) {
                    System.out.println(getSymmetricEngine().getDataService().reloadTable(node.getNodeId(), catalogName,
                            schemaName, tableName, line.getOptionValue(OPTION_WHERE)));
                } else {
                    System.out.println(getSymmetricEngine().getDataService().reloadTable(node.getNodeId(), catalogName,
                            schemaName, tableName));
                }
            }
        }
    }

    private void sendSchema(CommandLine line, List<String> args) {
        String catalog = line.getOptionValue(OPTION_CATALOG);
        String schema = line.getOptionValue(OPTION_SCHEMA);
        boolean excludeIndices = line.hasOption(OPTION_EXCLUDE_INDICES);
        boolean excludeForeignKeys = line.hasOption(OPTION_EXCLUDE_FOREIGN_KEYS);
        boolean excludeDefaults = line.hasOption(OPTION_EXCLUDE_DEFAULTS);
        Collection<Node> nodes = getNodes(line);
        if (args.size() == 0) {
            List<TriggerHistory> activeTriggerHistories = engine.getTriggerRouterService().getActiveTriggerHistories();
            for (TriggerHistory hist : activeTriggerHistories) {
                for (Node node : nodes) {
                    if ((catalog == null || catalog.equals(hist.getSourceCatalogName())) &&
                            (schema == null || schema.equals(hist.getSourceSchemaName()))) {
                        getSymmetricEngine().getDataService().sendSchema(node.getNodeId(), hist.getSourceCatalogName(),
                                hist.getSourceSchemaName(), hist.getSourceTableName(), false,
                                excludeIndices, true, excludeDefaults);
                    }
                }
            }
            if (!excludeForeignKeys) {
                for (TriggerHistory hist : activeTriggerHistories) {
                    for (Node node : nodes) {
                        if ((catalog == null || catalog.equals(hist.getSourceCatalogName())) &&
                                (schema == null || schema.equals(hist.getSourceSchemaName()))) {
                            getSymmetricEngine().getDataService().sendSchema(node.getNodeId(), hist.getSourceCatalogName(),
                                    hist.getSourceSchemaName(), hist.getSourceTableName(), false,
                                    excludeIndices, excludeForeignKeys, excludeDefaults);
                        }
                    }
                }
            }
        } else {
            for (String tableName : args) {
                for (Node node : nodes) {
                    getSymmetricEngine().getDataService().sendSchema(node.getNodeId(), catalog,
                            schema, tableName, false, excludeIndices, true, excludeDefaults);
                }
            }
            if (!excludeForeignKeys) {
                for (String tableName : args) {
                    for (Node node : nodes) {
                        getSymmetricEngine().getDataService().sendSchema(node.getNodeId(), catalog,
                                schema, tableName, false, excludeIndices, excludeForeignKeys, excludeDefaults);
                    }
                }
            }
        }
    }

    private void sendScript(CommandLine line, List<String> args) throws Exception {
        String scriptName = popArg(args, "Script Name");
        String scriptData = FileUtils.readFileToString(new File(scriptName), Charset.defaultCharset());
        for (Node node : getNodes(line)) {
            System.out.println("Sending script to node '" + node.getNodeId() + "'");
            getSymmetricEngine().getDataService().sendScript(node.getNodeId(), scriptData, false);
        }
    }

    private Collection<Node> getNodes(CommandLine line) {
        if (line.hasOption(OPTION_NODE_GROUP)) {
            return getSymmetricEngine().getNodeService().findEnabledNodesFromNodeGroup(
                    line.getOptionValue(OPTION_NODE_GROUP));
        } else if (line.hasOption(OPTION_NODE)) {
            Collection<Node> nodes = new ArrayList<Node>();
            String nodeId = line.getOptionValue(OPTION_NODE);
            Node node = getSymmetricEngine().getNodeService().findNode(nodeId);
            if (node == null) {
                System.out.println("ERROR: Unable to find node '" + nodeId + "'");
                System.exit(1);
            }
            nodes.add(node);
            return nodes;
        } else {
            System.out.println("ERROR: Must specify one option: " + OPTION_NODE + ", "
                    + OPTION_NODE_GROUP);
            System.exit(1);
        }
        return null;
    }

    private OutputStreamWriter getWriter(List<String> args) throws IOException {
        OutputStreamWriter os = null;
        if (args.size() == 0) {
            os = new OutputStreamWriter(System.out);
        } else {
            File file = new File(args.get(0));
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            os = new FileWriter(file);
        }
        return os;
    }

    private void module(CommandLine line, List<String> args) throws Exception {
        final String action = popArg(args, "Action");
        final String moduleArgName = "Module";
        try {
            ModuleManager mgr = ModuleManager.getInstance();
            if (action.equals("install")) {
                mgr.install(popArg(args, moduleArgName));
            } else if (action.equals("remove")) {
                mgr.remove(popArg(args, moduleArgName));
            } else if (action.equals("list-files")) {
                String module = popArg(args, moduleArgName);
                List<String> files = mgr.listFiles(module);
                System.out.println("Files associated with module " + module + ":");
                for (String file : files) {
                    System.out.println(file);
                }
            } else if (action.equals("list-deps")) {
                String module = popArg(args, moduleArgName);
                List<String> files = mgr.listDependencies(module);
                System.out.println("Files associated with module " + module + ":");
                for (String file : files) {
                    System.out.println(file);
                }
            } else if (action.equals("list")) {
                List<String> modules = mgr.list();
                System.out.println("Installed modules:");
                if (modules.size() == 0) {
                    System.out.println("<none>");
                } else {
                    for (String module : modules) {
                        System.out.println(module);
                    }
                }
            } else if (action.equals("list-all")) {
                List<String> modules = mgr.listAll();
                System.out.println("Available modules:");
                for (String module : modules) {
                    System.out.println(module);
                }
            } else if (action.equals("upgrade")) {
                System.out.println("Upgrading modules");
                mgr.upgradeAll();
            } else if (action.equals("list-upgrade")) {
                List<String> files = mgr.listUpgrade();
                System.out.println("Files that need upgraded:");
                for (String file : files) {
                    System.out.println(file);
                }
            } else if (action.equals("convert")) {
                System.out.println("Converting to modules");
                mgr.convertToModules();
            }
        } catch (ModuleException e) {
            if (!e.isLogged()) {
                System.err.println("ERROR: " + e.getMessage());
            }
            System.exit(1);
        }
    }

    private void importCert(CommandLine line, List<String> args) {
        String urlString = popArg(args, "URL");
        try {
            Certificate[] certs = SymmetricUtils.getCertificates(urlString);
            if (certs == null) {
                System.err.println("ERROR: URL must use HTTPS protocol");
                System.exit(1);
                return;
            }
            for (Certificate cert : certs) {
                if (cert instanceof X509Certificate) {
                    try {
                        String certString = FormatUtils.convertToPem((X509Certificate) cert);
                        importCert(certString.getBytes(), null, null, line.hasOption(OPTION_ACCEPT_ALL));
                    } catch (CertificateEncodingException e) {
                    }
                }
            }
        } catch (MalformedURLException e) {
            System.err.println("ERROR: URL is invalid");
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("ERROR: Failed to connect to URL");
            System.exit(1);
            return;
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            System.err.println("ERROR: Failed to initialize SSL context");
            System.exit(1);
            return;
        }
    }

    private void importCert(byte[] certData, String alias, String password, boolean acceptAll) {
        try {
            ISecurityService bouncyCastleSecurityService = SecurityServiceFactory.create(SecurityServiceType.SERVER,
                    new TypedProperties(System.getProperties()));
            TrustedCertificateEntry entry = bouncyCastleSecurityService.createTrustedCert(certData, "pem", alias, password);
            if (acceptAll) {
                getSymmetricEngine().getSecurityService().installTrustedCert(entry);
            } else {
                Certificate trustedCertificate = entry.getTrustedCertificate();
                String subject = "";
                String issuer = "";
                String keyType = "";
                String effective = "";
                String expiration = "";
                boolean isValid = false;
                if (trustedCertificate instanceof X509Certificate) {
                    X509Certificate x509Certificate = (X509Certificate) trustedCertificate;
                    subject = x509Certificate.getSubjectX500Principal().getName().replace(",", ", ");
                    issuer = x509Certificate.getIssuerX500Principal().getName().replace(",", ", ");
                    PublicKey publicKey = x509Certificate.getPublicKey();
                    if (publicKey instanceof RSAPublicKey) {
                        keyType = "RSA " + ((RSAPublicKey) publicKey).getModulus().bitLength() + "-bit";
                    } else if (publicKey instanceof DSAPublicKey) {
                        keyType = "DSA " + ((DSAPublicKey) publicKey).getParams().getP().bitLength() + "-bit";
                    }
                    Date effectiveDate = x509Certificate.getNotBefore();
                    if (effectiveDate != null) {
                        effective = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss aaa").format(effectiveDate);
                    }
                    Date expirationDate = x509Certificate.getNotAfter();
                    if (expirationDate != null) {
                        expiration = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss aaa").format(expirationDate);
                    }
                    Date now = new Date();
                    isValid = now.compareTo(effectiveDate) >= 0 && now.compareTo(expirationDate) <= 0;
                }
                System.out.println("Subject: " + subject);
                System.out.println("Issuer: " + issuer);
                System.out.println("Key Type: " + keyType);
                System.out.println("Effective Date: " + effective);
                System.out.println("Expiration Date: " + expiration);
                if (isValid) {
                    while (true) {
                        String answer = System.console().readLine("Accept this certificate? (Y/N): ");
                        if ("Y".equalsIgnoreCase(answer) || "YES".equalsIgnoreCase(answer)) {
                            getSymmetricEngine().getSecurityService().installTrustedCert(entry);
                            break;
                        } else if ("N".equalsIgnoreCase(answer) || "NO".equalsIgnoreCase(answer)) {
                            break;
                        } else {
                            System.err.println("ERROR: Invalid answer");
                        }
                    }
                } else {
                    System.out.println("This certificate is not valid!");
                }
            }
        } catch (KeystoreAliasException e) {
            System.out.println("Select which certificate to import");
            List<String> aliasList = e.getAliases();
            for (int i = 0; i < aliasList.size(); i++) {
                System.out.println((i + 1) + ". " + aliasList.get(i));
            }
            while (true) {
                try {
                    int selectedIndex = Integer.parseInt(System.console().readLine("Enter your selection: "));
                    importCert(certData, aliasList.get(selectedIndex - 1), password, acceptAll);
                    break;
                } catch (NumberFormatException ex) {
                    System.err.println("ERROR: Selection was not an integer");
                } catch (IndexOutOfBoundsException ex) {
                    System.err.println("ERROR: Invalid selection");
                }
            }
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause() instanceof UnrecoverableKeyException) {
                importCert(certData, alias, new String(System.console().readPassword("Enter password to access certificate: ")), acceptAll);
            } else {
                if (e.getMessage() != null) {
                    System.err.println("ERROR: Import failed: " + e.getMessage());
                } else {
                    System.err.println("ERROR: Import failed");
                }
                e.printStackTrace();
            }
        }
    }

    private void takeSnapshot(CommandLine line, List<String> args) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String secret = getSymmetricEngine().getSecurityService().nextSecureHexString(16);
        File secretFile = new File(tmpDir, secret);
        try {
            secretFile.createNewFile();
        } catch (IOException e) {
            System.err.println("ERROR: Failed to create shared secret file in the following directory: " + tmpDir);
            e.printStackTrace();
            System.exit(1);
            return;
        }
        String snapshotUrl = getSymmetricEngine().getSyncUrl();
        if (!snapshotUrl.endsWith("/")) {
            snapshotUrl += "/";
        }
        snapshotUrl += "snapshot";
        System.out.println("Contacting " + snapshotUrl);
        HttpURLConnection snapshotHttpUrlConnection = null;
        boolean success = false;
        try {
            URLConnection snapshotUrlConnection = new URL(snapshotUrl).openConnection();
            if (snapshotUrlConnection instanceof HttpURLConnection) {
                snapshotHttpUrlConnection = (HttpURLConnection) snapshotUrlConnection;
                snapshotHttpUrlConnection.setDoInput(true);
                snapshotHttpUrlConnection.setDoOutput(true);
                snapshotHttpUrlConnection.setRequestMethod("POST");
                snapshotHttpUrlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                System.out.println("Requesting snapshot");
                snapshotHttpUrlConnection.connect();
                snapshotHttpUrlConnection.getOutputStream().write(secret.getBytes(StandardCharsets.UTF_8));
                success = snapshotHttpUrlConnection.getResponseCode() == 200;
                BufferedReader httpReader = new BufferedReader(new InputStreamReader(snapshotHttpUrlConnection.getInputStream()));
                String httpOutputLine = null;
                while ((httpOutputLine = httpReader.readLine()) != null) {
                    System.out.println(httpOutputLine);
                }
                httpReader.close();
            }
        } catch (MalformedURLException e) {
            System.err.println("ERROR: Snapshot URL is invalid");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("ERROR: Exception when connecting to snapshot URL");
            e.printStackTrace();
        } finally {
            if (snapshotHttpUrlConnection != null) {
                snapshotHttpUrlConnection.disconnect();
            }
            secretFile.delete();
            if (success) {
                System.out.println("Success");
                System.exit(0);
            } else {
                System.out.println("Failed");
                System.exit(1);
            }
        }
    }
}
