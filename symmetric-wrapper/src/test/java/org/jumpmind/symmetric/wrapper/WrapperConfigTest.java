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
package org.jumpmind.symmetric.wrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.jumpmind.symmetric.wrapper.WrapperConfig.FailureAction;
import org.jumpmind.symmetric.wrapper.jna.WinsvcEx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrapperConfigTest {
    @TempDir
    private Path tempDir;

    @Test
    void testConstructor_resolvesAbsolutePaths() throws Exception {
        WrapperConfig config = newConfig();
        assertEquals(tempDir.resolve("sym_service.conf").toString(), config.getConfigFile());
        assertEquals(new File("sym.jar").getAbsolutePath(), config.getWrapperJarPath());
        assertEquals(tempDir.toFile(), config.getWorkingDirectory());
    }

    @Test
    void testConstructor_withMissingConfigFile() {
        assertThrows(FileNotFoundException.class, () -> new WrapperConfig(tempDir.toString(), tempDir.resolve("nope.conf").toString(), "sym.jar"));
    }

    @Test
    void testDefaults() throws Exception {
        WrapperConfig config = newConfig();
        assertEquals("logs/wrapper.log", config.getLogFile());
        assertEquals("tmp/wrapper.pid", config.getWrapperPidFile());
        assertEquals("tmp/server.pid", config.getServerPidFile());
        assertEquals(10485760L, config.getLogFileMaxSize());
        assertEquals(3, config.getLogFileMaxFiles());
        assertEquals("INFO", config.getLogFileLogLevel());
        assertEquals("symmetricds", config.getName());
        assertEquals("SymmetricDS", config.getDisplayName());
        assertEquals("SymmetricDS Database Synchronization", config.getDescription());
        assertEquals("", config.getFailureActionCommand());
        assertEquals(300, config.getFailureResetPeriod());
        assertEquals("java", config.getJavaCommand());
        assertEquals("java", config.getServiceCommand());
        assertEquals("", config.getRunAsUser());
        assertEquals("", config.getRunAsPassword());
        assertEquals("", config.getApplicationOutputStart());
        assertEquals("", config.getApplicationOutputRestart());
        assertEquals("256", config.getMaxMemory());
        assertTrue(config.isAutoStart());
        assertFalse(config.isDelayStart());
    }

    @Test
    void testDefaults_forListProperties() throws Exception {
        WrapperConfig config = newConfig();
        assertNull(config.getDependencies());
        assertNull(config.getOptions());
        assertTrue(config.getApplicationParameters().isEmpty());
        assertTrue(config.getFailureActions().isEmpty());
        assertEquals("", config.getClassPath());
    }

    @Test
    void testExplicitValues() throws Exception {
        WrapperConfig config = newConfig("wrapper.logfile=logs/sym.log", "wrapper.pidfile=/var/run/wrapper.pid",
                "wrapper.server.pidfile=/var/run/server.pid", "wrapper.logfile.maxfiles=7", "wrapper.logfile.loglevel=WARNING",
                "wrapper.name=corp", "wrapper.displayname=Corp Sync", "wrapper.description=Corp Synchronization",
                "wrapper.run.as.user=symadmin", "wrapper.run.as.password=secret", "wrapper.java.maxmemory=2048",
                "wrapper.app.output.start=Started SymmetricDS", "wrapper.app.output.restart=Restart requested");
        assertEquals("logs/sym.log", config.getLogFile());
        assertEquals("/var/run/wrapper.pid", config.getWrapperPidFile());
        assertEquals("/var/run/server.pid", config.getServerPidFile());
        assertEquals(7, config.getLogFileMaxFiles());
        assertEquals("WARNING", config.getLogFileLogLevel());
        assertEquals("corp", config.getName());
        assertEquals("Corp Sync", config.getDisplayName());
        assertEquals("Corp Synchronization", config.getDescription());
        assertEquals("symadmin", config.getRunAsUser());
        assertEquals("secret", config.getRunAsPassword());
        assertEquals("2048", config.getMaxMemory());
        assertEquals("Started SymmetricDS", config.getApplicationOutputStart());
        assertEquals("Restart requested", config.getApplicationOutputRestart());
    }

    @Test
    void testGetProperties_ignoresCommentsAndBlankLines() throws Exception {
        assertEquals("corp", newConfig("# wrapper.name=commented", "   ", "", "wrapper.name=corp").getName());
    }

    @Test
    void testGetProperties_ignoresIndentedComments() throws Exception {
        assertEquals("symmetricds", newConfig("   # wrapper.name=commented").getName());
    }

    @Test
    void testGetProperties_ignoresLinesWithoutEquals() throws Exception {
        assertEquals("symmetricds", newConfig("wrapper.name").getName());
    }

    @Test
    void testGetProperties_trimsWhitespaceAroundNameAndValue() throws Exception {
        assertEquals("corp", newConfig("  wrapper.name  =   corp   ").getName());
    }

    @Test
    void testGetProperties_keepsEmbeddedEquals() throws Exception {
        assertEquals("-Dfoo=bar", newConfig("wrapper.java.additional.1=-Dfoo=bar").getOptions().get(0));
    }

    @Test
    void testGetProperties_withEmptyValue() throws Exception {
        assertEquals("", newConfig("wrapper.name=").getName());
    }

    @Test
    void testGetProperties_usesFirstValueForScalarProperty() throws Exception {
        assertEquals("first", newConfig("wrapper.name=first", "wrapper.name=second").getName());
    }

    @Test
    void testGetProperties_stripsNumericSuffixFromKey() throws Exception {
        WrapperConfig config = newConfig("wrapper.java.additional.1=-Done", "wrapper.java.additional.2=-Dtwo",
                "wrapper.java.additional.10=-Dten");
        assertEquals(Arrays.asList("-Done", "-Dtwo", "-Dten"), config.getOptions());
    }

    @Test
    void testGetProperties_stripsNumericSuffixOfMoreThanTwoDigits() throws Exception {
        assertEquals(Arrays.asList("-Dhundred"), newConfig("wrapper.java.additional.100=-Dhundred").getOptions());
    }

    @Test
    void testGetProperties_keepsKeyThatDoesNotEndInDigits() throws Exception {
        assertEquals(Arrays.asList("-Dplain"), newConfig("wrapper.java.additional=-Dplain").getOptions());
    }

    @Test
    void testGetLogFileMaxSize_withMegabytes() throws Exception {
        assertEquals(5242880L, newConfig("wrapper.logfile.maxsize=5M").getLogFileMaxSize());
    }

    @Test
    void testGetLogFileMaxSize_withKilobytes() throws Exception {
        assertEquals(512000L, newConfig("wrapper.logfile.maxsize=500K").getLogFileMaxSize());
    }

    @Test
    void testGetLogFileMaxSize_withLowercaseSuffix() throws Exception {
        assertEquals(2097152L, newConfig("wrapper.logfile.maxsize=2m").getLogFileMaxSize());
    }

    @Test
    void testGetLogFileMaxSize_withoutSuffix() throws Exception {
        assertEquals(4096L, newConfig("wrapper.logfile.maxsize=4096").getLogFileMaxSize());
    }

    @Test
    void testGetLogFileMaxSize_withUnrecognizedSuffix() throws Exception {
        // Defect pinned, not endorsed: only K and M are recognized, so a G suffix silently means bytes
        assertEquals(1L, newConfig("wrapper.logfile.maxsize=1G").getLogFileMaxSize());
    }

    @Test
    void testGetLogFileMaxSize_withNonNumericValue() throws Exception {
        WrapperConfig config = newConfig("wrapper.logfile.maxsize=big");
        assertThrows(NumberFormatException.class, () -> config.getLogFileMaxSize());
    }

    @Test
    void testGetLogFileMaxFiles_withNonNumericValue() throws Exception {
        WrapperConfig config = newConfig("wrapper.logfile.maxfiles=many");
        assertThrows(NumberFormatException.class, () -> config.getLogFileMaxFiles());
    }

    @Test
    void testIsAutoStart_withDelayStartType() throws Exception {
        WrapperConfig config = newConfig("wrapper.ntservice.starttype=delay");
        assertFalse(config.isAutoStart());
        assertTrue(config.isDelayStart());
    }

    @Test
    void testIsAutoStart_withManualStartType() throws Exception {
        WrapperConfig config = newConfig("wrapper.ntservice.starttype=manual");
        assertFalse(config.isAutoStart());
        assertFalse(config.isDelayStart());
    }

    @Test
    void testIsAutoStart_ignoresCase() throws Exception {
        assertTrue(newConfig("wrapper.ntservice.starttype=AUTO").isAutoStart());
    }

    @Test
    void testGetDependencies() throws Exception {
        assertEquals(Arrays.asList("Tcpip", "Dnscache"),
                newConfig("wrapper.ntservice.dependency.1=Tcpip", "wrapper.ntservice.dependency.2=Dnscache").getDependencies());
    }

    @Test
    void testGetFailureActions_pairsTypesWithDelays() throws Exception {
        List<FailureAction> actions = newConfig("wrapper.ntservice.failure.action.type.1=restart",
                "wrapper.ntservice.failure.action.type.2=reboot", "wrapper.ntservice.failure.action.delay.1=10",
                "wrapper.ntservice.failure.action.delay.2=20").getFailureActions();
        assertEquals(2, actions.size());
        assertEquals(WinsvcEx.SC_ACTION_RESTART, actions.get(0).getType());
        assertEquals(10, actions.get(0).getDelay());
        assertEquals(WinsvcEx.SC_ACTION_REBOOT, actions.get(1).getType());
        assertEquals(20, actions.get(1).getDelay());
    }

    @Test
    void testGetFailureActions_withoutDelays_defaultsToZero() throws Exception {
        List<FailureAction> actions = newConfig("wrapper.ntservice.failure.action.type.1=restart").getFailureActions();
        assertEquals(1, actions.size());
        assertEquals(0, actions.get(0).getDelay());
    }

    @Test
    void testGetFailureActions_withFewerDelaysThanTypes() throws Exception {
        List<FailureAction> actions = newConfig("wrapper.ntservice.failure.action.type.1=restart",
                "wrapper.ntservice.failure.action.type.2=restart", "wrapper.ntservice.failure.action.delay.1=10").getFailureActions();
        assertEquals(10, actions.get(0).getDelay());
        assertEquals(0, actions.get(1).getDelay());
    }

    @Test
    void testGetFailureActions_withMoreThanThreeTypes() throws Exception {
        List<FailureAction> actions = newConfig("wrapper.ntservice.failure.action.type.1=restart",
                "wrapper.ntservice.failure.action.type.2=restart", "wrapper.ntservice.failure.action.type.3=restart",
                "wrapper.ntservice.failure.action.type.4=restart").getFailureActions();
        // Defect pinned, not endorsed: the computed cap of 3 is used only to size the list, so all four actions are returned
        assertEquals(4, actions.size());
    }

    @Test
    void testGetFailureActionCommand() throws Exception {
        assertEquals("/opt/sym/notify.sh",
                newConfig("wrapper.ntservice.failure.action.command=/opt/sym/notify.sh").getFailureActionCommand());
    }

    @Test
    void testGetFailureResetPeriod() throws Exception {
        assertEquals(600, newConfig("wrapper.ntservice.failure.reset.period=600").getFailureResetPeriod());
    }

    @Test
    void testFailureAction_mapsTypeNames() {
        assertEquals(WinsvcEx.SC_ACTION_RESTART, new FailureAction("RESTART", "0").getType());
        assertEquals(WinsvcEx.SC_ACTION_REBOOT, new FailureAction("Reboot", "0").getType());
        assertEquals(WinsvcEx.SC_ACTION_RUN_COMMAND, new FailureAction("run_command", "0").getType());
        assertEquals(WinsvcEx.SC_ACTION_NONE, new FailureAction("unknown", "0").getType());
    }

    @Test
    void testFailureAction_withNullType() {
        assertEquals(WinsvcEx.SC_ACTION_NONE, new FailureAction(null, "5").getType());
        assertEquals(5, new FailureAction(null, "5").getDelay());
    }

    @Test
    void testFailureAction_withNonNumericDelay() {
        assertThrows(NumberFormatException.class, () -> new FailureAction("restart", "soon"));
    }

    @Test
    void testGetJavaCommand_withServiceExecutable() throws Exception {
        assertEquals(System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe",
                newConfig("wrapper.java.command=..\\bin\\sym_service.exe").getJavaCommand());
    }

    @Test
    void testGetServiceCommand_keepsServiceExecutable() throws Exception {
        assertEquals("..\\bin\\sym_service.exe", newConfig("wrapper.java.command=..\\bin\\sym_service.exe").getServiceCommand());
    }

    @Test
    void testGetJavaCommand_withExplicitPath() throws Exception {
        assertEquals("/usr/bin/java", newConfig("wrapper.java.command=/usr/bin/java").getJavaCommand());
    }

    @Test
    void testGetClassPath_joinsEntriesWithPathSeparator() throws Exception {
        assertEquals("../patches" + File.pathSeparator + "../lib/*",
                newConfig("wrapper.java.classpath.1=../patches", "wrapper.java.classpath.2=../lib/*.jar").getClassPath());
    }

    @Test
    void testGetClassPath_withSingleEntry() throws Exception {
        assertEquals("../web/WEB-INF/classes", newConfig("wrapper.java.classpath.1=../web/WEB-INF/classes").getClassPath());
    }

    @Test
    void testGetApplicationParameters() throws Exception {
        assertEquals(Arrays.asList("--server", "--no-log-console"),
                newConfig("wrapper.app.parameter.1=--server", "wrapper.app.parameter.2=--no-log-console").getApplicationParameters());
    }

    @Test
    void testGetCommand_appendsMemoryUnits() throws Exception {
        List<String> cmd = newConfig("wrapper.java.initmemory=512", "wrapper.java.maxmemory=1024").getCommand(true);
        assertEquals("java", cmd.get(0));
        assertEquals("-Xms512M", cmd.get(1));
        assertEquals("-Xmx1024M", cmd.get(2));
        assertEquals("-cp", cmd.get(3));
        assertEquals("", cmd.get(4));
    }

    @Test
    void testGetCommand_keepsExplicitMemoryUnits() throws Exception {
        List<String> cmd = newConfig("wrapper.java.initmemory=512m", "wrapper.java.maxmemory=2G").getCommand(true);
        assertEquals("-Xms512m", cmd.get(1));
        assertEquals("-Xmx2GM", cmd.get(2));
    }

    @Test
    void testGetCommand_withDefaultMemory() throws Exception {
        List<String> cmd = newConfig().getCommand(true);
        assertEquals("-Xms256M", cmd.get(1));
        assertEquals("-Xmx256M", cmd.get(2));
    }

    @Test
    void testGetCommand_forConsole_dropsNoLogConsole() throws Exception {
        List<String> cmd = newConfig("wrapper.java.additional.1=-Dsun.net.inetaddr.ttl=0", "wrapper.app.parameter.1=--server",
                "wrapper.app.parameter.2=--no-log-console").getCommand(true);
        assertEquals(Arrays.asList("-Dsun.net.inetaddr.ttl=0", "--server"), cmd.subList(5, cmd.size()));
    }

    @Test
    void testGetCommand_forService_addsNoLogConsole() throws Exception {
        List<String> cmd = newConfig("wrapper.app.parameter.1=--server").getCommand(false);
        assertEquals(Arrays.asList("--server", "--no-log-console"), cmd.subList(5, cmd.size()));
    }

    @Test
    void testGetCommand_calledTwice_losesApplicationParameters() throws Exception {
        WrapperConfig config = newConfig("wrapper.app.parameter.1=--server", "wrapper.app.parameter.2=--no-log-console");
        config.getCommand(true);
        // Defect pinned, not endorsed: getCommand removes --no-log-console from the live property list, so later calls see a mutated config
        assertEquals(Arrays.asList("--server"), config.getApplicationParameters());
        assertEquals(Arrays.asList("--server", "--no-log-console"), config.getCommand(false).subList(5, 7));
    }

    private WrapperConfig newConfig(String... lines) throws IOException {
        Path configFile = tempDir.resolve("sym_service.conf");
        Files.write(configFile, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return new WrapperConfig(tempDir.toString(), configFile.toString(), "sym.jar");
    }
}
