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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.jna.Platform;

class WrapperServiceTest {
    @TempDir
    private Path tempDir;
    private TestWrapperService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new TestWrapperService();
        service.loadConfig(tempDir.toString(), newConfigFile("wrapper.pidfile=" + pidFile("wrapper"),
                "wrapper.server.pidfile=" + pidFile("server")), "sym.jar");
    }

    @Test
    void testGetInstance_matchesPlatform() {
        WrapperService instance = WrapperService.getInstance();
        if (Platform.isWindows()) {
            assertInstanceOf(WindowsService.class, instance);
        } else if (Platform.isMac()) {
            assertInstanceOf(MacService.class, instance);
        } else {
            assertInstanceOf(UnixService.class, instance);
        }
    }

    @Test
    void testLoadConfig_setsConfigAndWorkingDirectory() {
        assertEquals(tempDir.toFile(), service.getConfig().getWorkingDirectory());
        assertEquals(tempDir.toFile().getAbsolutePath(), service.workingDirectory);
    }

    @Test
    void testLoadConfig_withMissingFile() {
        assertThrows(FileNotFoundException.class, () -> service.loadConfig(tempDir.toString(), tempDir.resolve("nope.conf").toString(), "sym.jar"));
    }

    @Test
    void testScrubCommand_masksKeyStorePassword() {
        assertEquals("java -Djavax.net.ssl.keyStorePassword=*** -jar sym.jar",
                service.scrubCommand("java -Djavax.net.ssl.keyStorePassword=changeit -jar sym.jar"));
    }

    @Test
    void testScrubCommand_masksTrustStorePassword() {
        assertEquals("java -Djavax.net.ssl.trustStorePassword=*** -jar sym.jar",
                service.scrubCommand("java -Djavax.net.ssl.trustStorePassword=changeit -jar sym.jar"));
    }

    @Test
    void testScrubCommand_masksBothPasswords() {
        assertEquals("-Djavax.net.ssl.keyStorePassword=*** -Djavax.net.ssl.trustStorePassword=***",
                service.scrubCommand("-Djavax.net.ssl.keyStorePassword=a -Djavax.net.ssl.trustStorePassword=b"));
    }

    @Test
    void testScrubCommand_masksOnlyTheFirstOccurrence() {
        // Defect pinned, not endorsed: replaceFirst leaves a repeated keystore password argument in the clear
        assertEquals("-Djavax.net.ssl.keyStorePassword=*** -Djavax.net.ssl.keyStorePassword=b",
                service.scrubCommand("-Djavax.net.ssl.keyStorePassword=a -Djavax.net.ssl.keyStorePassword=b"));
    }

    @Test
    void testScrubCommand_withoutPasswords() {
        assertEquals("java -jar sym.jar", service.scrubCommand("java -jar sym.jar"));
    }

    @Test
    void testCommandToString() {
        assertEquals("java -jar sym.jar ", service.commandToString(new ArrayList<String>(Arrays.asList("java", "-jar", "sym.jar"))));
    }

    @Test
    void testCommandToString_withEmptyCommand() {
        assertEquals("", service.commandToString(new ArrayList<String>()));
    }

    @Test
    void testGetWrapperCommand_withoutQuotes() {
        List<String> cmd = service.getWrapperCommand("exec", false);
        assertEquals("java", cmd.get(0));
        assertEquals("-Djava.io.tmpdir=" + expectedTmpDir(), cmd.get(cmd.size() - 5));
        assertEquals("-jar", cmd.get(cmd.size() - 4));
        assertEquals(service.getConfig().getWrapperJarPath(), cmd.get(cmd.size() - 3));
        assertEquals("exec", cmd.get(cmd.size() - 2));
        assertEquals(service.getConfig().getConfigFile(), cmd.get(cmd.size() - 1));
    }

    @Test
    void testGetWrapperCommand_withQuotes() {
        service.quote = "\"";
        List<String> cmd = service.getWrapperCommand("init", true);
        assertEquals("\"java\"", cmd.get(0));
        assertEquals("-Djava.io.tmpdir=\"" + expectedTmpDir() + "\"", cmd.get(cmd.size() - 5));
        assertEquals("\"" + service.getConfig().getConfigFile() + "\"", cmd.get(cmd.size() - 1));
    }

    @Test
    void testGetWrapperCommand_includesHeapLimitsOnlyOnWindows() {
        List<String> cmd = service.getWrapperCommand("exec", false);
        assertEquals(Platform.isWindows(), cmd.contains("-Xms16m") && cmd.contains("-Xmx64m"));
    }

    @Test
    void testGetWrapperCommandQuote_defaultsToNothing() {
        assertEquals("", new TestWrapperService().getWrapperCommandQuote());
    }

    @Test
    void testWritePidToFile_thenReadPidFromFile() {
        service.writePidToFile(4321, pidFile("wrapper"));
        assertEquals(4321, service.readPidFromFile(pidFile("wrapper")));
        assertEquals(4321, service.getWrapperPid());
    }

    @Test
    void testWritePidToFile_overwritesExistingFile() {
        service.writePidToFile(1111, pidFile("server"));
        service.writePidToFile(22, pidFile("server"));
        assertEquals(22, service.readPidFromFile(pidFile("server")));
        assertEquals(22, service.getServerPid());
    }

    @Test
    void testReadPidFromFile_withMissingFile() {
        assertEquals(0, service.readPidFromFile(pidFile("absent")));
    }

    @Test
    void testReadPidFromFile_withNonNumericContent() throws Exception {
        Files.write(tempDir.resolve("wrapper.pid"), "not-a-pid".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, service.readPidFromFile(pidFile("wrapper")));
    }

    @Test
    void testReadPidFromFile_withEmptyFile() throws Exception {
        Files.createFile(tempDir.resolve("empty.pid"));
        assertEquals(0, service.readPidFromFile(pidFile("empty")));
    }

    @Test
    void testDeletePidFile() {
        service.writePidToFile(99, pidFile("wrapper"));
        service.deletePidFile(pidFile("wrapper"));
        assertFalse(new File(pidFile("wrapper")).exists());
    }

    @Test
    void testDeletePidFile_withMissingFile() {
        service.deletePidFile(pidFile("absent"));
        assertFalse(new File(pidFile("absent")).exists());
    }

    @Test
    void testIsRunning_whenBothPidsAreRunning() {
        service.writePidToFile(11, pidFile("wrapper"));
        service.writePidToFile(22, pidFile("server"));
        service.runningPids.addAll(Arrays.asList(11, 22));
        assertTrue(service.isRunning());
    }

    @Test
    void testIsRunning_whenOnlyTheWrapperIsRunning() {
        service.writePidToFile(11, pidFile("wrapper"));
        service.writePidToFile(22, pidFile("server"));
        service.runningPids.add(11);
        assertFalse(service.isRunning());
    }

    @Test
    void testIsRunning_whenNoPidFilesExist() {
        assertFalse(service.isRunning());
    }

    @Test
    void testStart_whenAlreadyRunning() {
        markRunning();
        assertEquals(Constants.RC_SERVER_ALREADY_RUNNING, assertThrows(WrapperException.class, () -> service.start()).getErrorCode());
    }

    @Test
    void testConsole_whenAlreadyRunning() {
        markRunning();
        assertEquals(Constants.RC_SERVER_ALREADY_RUNNING, assertThrows(WrapperException.class, () -> service.console()).getErrorCode());
    }

    @Test
    void testStopProcesses_whenNotRunning() {
        assertEquals(Constants.RC_SERVER_NOT_RUNNING, assertThrows(WrapperException.class, () -> service.stopProcesses(false)).getErrorCode());
    }

    @Test
    void testStopProcesses_whenNotRunningAndStoppingAbandoned() {
        service.stopProcesses(true);
        assertTrue(service.killedPids.isEmpty());
    }

    @Test
    void testStopProcesses_killsWrapperAndServer() {
        markRunning();
        service.stopProcesses(false);
        assertEquals(Arrays.asList(11, 22), service.killedPids);
    }

    @Test
    void testStop_deletesPidFiles() {
        markRunning();
        service.stop();
        assertFalse(new File(pidFile("wrapper")).exists());
        assertFalse(new File(pidFile("server")).exists());
    }

    @Test
    void testStopProcess_whenProcessStops() {
        assertTrue(service.stopProcess(11, "wrapper"));
        assertEquals(Arrays.asList(11), service.killedPids);
    }

    @Test
    void testRestart_whenNotRunning_startsWithoutStopping() throws Exception {
        RecordingStartService recording = newRecordingService();
        recording.restart();
        assertTrue(recording.killedPids.isEmpty());
        assertEquals(1, recording.startCalls);
    }

    @Test
    void testRestart_whenRunning_stopsThenStarts() throws Exception {
        RecordingStartService recording = newRecordingService();
        recording.writePidToFile(11, pidFile("wrapper"));
        recording.writePidToFile(22, pidFile("server"));
        recording.runningPids.addAll(Arrays.asList(11, 22));
        recording.restart();
        assertEquals(Arrays.asList(11, 22), recording.killedPids);
        assertEquals(1, recording.startCalls);
    }

    @Test
    void testStatus_printsInstalledAndRunningState() {
        markRunning();
        service.installed = true;
        String output = captureOut(() -> service.status());
        assertTrue(output.contains("Installed: true"));
        assertTrue(output.contains("Running: true"));
        assertTrue(output.contains("Wrapper PID: 11"));
        assertTrue(output.contains("Wrapper Running: true"));
        assertTrue(output.contains("Server PID: 22"));
        assertTrue(output.contains("Server Running: true"));
    }

    @Test
    void testStatus_whenNotInstalledOrRunning() {
        String output = captureOut(() -> service.status());
        assertTrue(output.contains("Installed: false"));
        assertTrue(output.contains("Running: false"));
        assertTrue(output.contains("Wrapper PID: 0"));
    }

    @Test
    void testWaitForPid_whenProcessIsNotRunning() {
        assertFalse(service.waitForPid(11));
    }

    @Test
    void testInitEnvironment_leavesProcessBuilderAlone() {
        ProcessBuilder builder = new ProcessBuilder("java");
        service.initEnvironment(builder);
        assertEquals(Arrays.asList("java"), builder.command());
    }

    @Test
    void testInit_delegatesToExecJava() {
        TestWrapperService spied = new TestWrapperService();
        spied.init();
        assertTrue(spied.execJavaConsoleFlags.contains(Boolean.FALSE));
    }

    private RecordingStartService newRecordingService() throws IOException {
        RecordingStartService recording = new RecordingStartService();
        recording.loadConfig(tempDir.toString(), newConfigFile("wrapper.pidfile=" + pidFile("wrapper"),
                "wrapper.server.pidfile=" + pidFile("server")), "sym.jar");
        return recording;
    }

    private void markRunning() {
        service.writePidToFile(11, pidFile("wrapper"));
        service.writePidToFile(22, pidFile("server"));
        service.runningPids.addAll(Arrays.asList(11, 22));
    }

    private String pidFile(String name) {
        return tempDir.resolve(name + ".pid").toString();
    }

    private String newConfigFile(String... lines) throws IOException {
        Path configFile = tempDir.resolve("sym_service.conf");
        Files.write(configFile, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return configFile.toString();
    }

    private String expectedTmpDir() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        return tmpDir != null && tmpDir.endsWith("\\") ? tmpDir.substring(0, tmpDir.length() - 1) : tmpDir;
    }

    private String captureOut(Runnable runnable) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured));
            runnable.run();
        } finally {
            System.setOut(originalOut);
        }
        return captured.toString();
    }

    private static class RecordingStartService extends TestWrapperService {
        private int startCalls;

        @Override
        public void start() {
            startCalls++;
        }
    }

    private static class TestWrapperService extends WrapperService {
        final Set<Integer> runningPids = new HashSet<Integer>();
        final List<Integer> killedPids = new ArrayList<Integer>();
        private final List<Boolean> execJavaConsoleFlags = new ArrayList<Boolean>();
        private String workingDirectory;
        private String quote = "";
        private boolean installed;

        @Override
        protected void execJava(boolean isConsole) {
            execJavaConsoleFlags.add(Boolean.valueOf(isConsole));
        }

        @Override
        public void install() {
        }

        @Override
        public void uninstall() {
        }

        @Override
        public boolean isInstalled() {
            return installed;
        }

        @Override
        public boolean isPrivileged() {
            return false;
        }

        @Override
        protected String getWrapperCommandQuote() {
            return quote;
        }

        @Override
        protected boolean setWorkingDirectory(String dir) {
            workingDirectory = dir;
            return true;
        }

        @Override
        protected int getProcessPid(Process process) {
            return 0;
        }

        @Override
        protected int getCurrentPid() {
            return 1234;
        }

        @Override
        protected boolean isPidRunning(int pid) {
            return runningPids.contains(Integer.valueOf(pid));
        }

        @Override
        protected void killProcess(int pid, boolean isTerminate) {
            killedPids.add(Integer.valueOf(pid));
            runningPids.remove(Integer.valueOf(pid));
        }
    }
}
