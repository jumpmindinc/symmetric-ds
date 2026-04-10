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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Launch the SymmetricDS engine as a stand alone client or server.
 */
public class SymmetricLauncher extends AbstractCommandLauncher {
    private static final Logger log = LoggerFactory.getLogger(SymmetricLauncher.class);
    private static final String OPTION_PORT_SERVER = "port";
    private static final String OPTION_HOST_SERVER = "host";
    private static final String OPTION_SECURE_PORT_SERVER = "secure-port";
    private static final String OPTION_MAX_IDLE_TIME = "max-idle-time";
    private static final String OPTION_START_SERVER = "server";
    private static final String OPTION_START_CLIENT = "client";
    private static final String OPTION_START_SECURE_SERVER = "secure-server";
    private static final String OPTION_START_MIXED_SERVER = "mixed-server";

    public SymmetricLauncher(String app, String argSyntax, String messageKeyPrefix) {
        super(app, argSyntax, messageKeyPrefix);
    }

    public static void main(String... args) throws Exception {
        MDC.put("engineName", "startup");
        new SymmetricLauncher("sym", "", "Launcher.Option.").execute(args);
    }

    @Override
    protected boolean printHelpIfNoOptionsAreProvided() {
        return false;
    }

    @Override
    protected boolean requiresPropertiesFile(CommandLine line) {
        return false;
    }

    @Override
    protected void printHelp(CommandLine cmd, Options options) {
        System.out.println(app + " version " + Version.version());
        System.out.println("Launch the SymmetricDS engine as a standalone client or server.\n");
        super.printHelp(cmd, options);
    }

    @Override
    protected void buildOptions(Options options) {
        super.buildOptions(options);
        buildCryptoOptions(options);
        addOption(options, "S", OPTION_START_SERVER, false);
        addOption(options, "C", OPTION_START_CLIENT, false);
        addOption(options, "T", OPTION_START_SECURE_SERVER, false);
        addOption(options, "U", OPTION_START_MIXED_SERVER, false);
        addOption(options, "H", OPTION_HOST_SERVER, true);
        addOption(options, "P", OPTION_PORT_SERVER, true);
        addOption(options, "Q", OPTION_SECURE_PORT_SERVER, true);
        addOption(options, "I", OPTION_MAX_IDLE_TIME, true);
    }

    @Override
    protected boolean executeWithOptions(CommandLine line) throws Exception {
        String host = null;
        int httpPort = 0;
        int httpSecurePort = 0;
        String webDir = SymmetricWebServer.DEFAULT_WEBAPP_DIR;
        int maxIdleTime = SymmetricWebServer.DEFAULT_MAX_IDLE_TIME;
        configureCrypto(line);
        setContainerized(line.hasOption(OPTION_CONTAINER_MODE));
        if (isContainerized()) {
            log.info("Container mode enabled");
        }
        removeOldHeapDumps();
        if (line.hasOption(OPTION_HOST_SERVER)) {
            host = line.getOptionValue(OPTION_HOST_SERVER);
        }
        if (line.hasOption(OPTION_PORT_SERVER)) {
            httpPort = Integer.valueOf(line.getOptionValue(OPTION_PORT_SERVER));
        }
        if (line.hasOption(OPTION_SECURE_PORT_SERVER)) {
            httpSecurePort = Integer.valueOf(line.getOptionValue(OPTION_SECURE_PORT_SERVER));
        }
        if (line.hasOption(OPTION_MAX_IDLE_TIME)) {
            maxIdleTime = Integer.valueOf(line.getOptionValue(OPTION_MAX_IDLE_TIME));
        }
        if (line.hasOption(OPTION_START_CLIENT)) {
            getSymmetricEngine(false).start();
            System.out.println("Started");
        } else {
            SymmetricWebServer webServer = new SymmetricWebServer(chooseWebDir(line, webDir),
                    maxIdleTime, propertiesFile != null ? propertiesFile.getCanonicalPath() : null,
                    true);
            if (isNotBlank(host)) {
                webServer.setHost(host);
            }
            if (httpPort > 0) {
                webServer.setHttpPort(httpPort);
            }
            if (httpSecurePort > 0) {
                webServer.setHttpsPort(httpSecurePort);
            }
            if (line.hasOption(OPTION_START_MIXED_SERVER)) {
                webServer.setHttpEnabled(true);
                webServer.setHttpsEnabled(true);
                if (httpPort > 0) {
                    webServer.setHttpPort(httpPort);
                }
                if (httpSecurePort > 0) {
                    webServer.setHttpsPort(httpSecurePort);
                }
            } else if (line.hasOption(OPTION_START_SECURE_SERVER)) {
                webServer.setHttpEnabled(false);
                webServer.setHttpsEnabled(true);
                if (httpSecurePort > 0) {
                    webServer.setHttpsPort(httpSecurePort);
                }
            } else if (line.hasOption(OPTION_START_SERVER)) {
                webServer.setHttpEnabled(true);
                webServer.setHttpsEnabled(false);
            }
            webServer.start();
        }
        return true;
    }

    protected void removeOldHeapDumps() {
        String heapDumpPath = null;
        List<String> options = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String option : options) {
            if (option.startsWith("-XX:HeapDumpPath=")) {
                heapDumpPath = option.split("=")[1];
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Heap dump path is " + (heapDumpPath == null ? null : new File(heapDumpPath).getAbsolutePath()));
        }
        if (heapDumpPath != null) {
            File directory = new File(heapDumpPath);
            if (!directory.exists()) { // Happens if whole tmp directory is removed.
                return;
            }
            Collection<File> files = FileUtils.listFiles(directory, new String[] { "hprof" }, false);
            List<File> recentFiles = new ArrayList<File>();
            List<File> oldFiles = new ArrayList<File>();
            for (File file : files) {
                if (file.lastModified() < (System.currentTimeMillis() - 604800000)) {
                    oldFiles.add(file);
                } else {
                    recentFiles.add(file);
                }
            }
            Collections.sort(recentFiles, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return f1.lastModified() > f2.lastModified() ? -1 : 1;
                }
            });
            for (int i = 2; i < recentFiles.size(); i++) {
                oldFiles.add(recentFiles.get(i));
            }
            for (File file : oldFiles) {
                log.info("Removing old heap dump " + file.getName());
                file.delete();
            }
        }
    }

    protected String chooseWebDir(CommandLine line, String webDir) {
        return webDir;
    }
}
