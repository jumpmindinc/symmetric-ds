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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.jumpmind.symmetric.wrapper.jna.CLibrary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnixServiceTest {
    private static final String SERVICE_NAME = "symtest-unix";
    @TempDir
    private Path tempDir;
    private UnixService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new UnixService();
        service.loadConfig(tempDir.toString(), newConfigFile("wrapper.name=" + SERVICE_NAME,
                "wrapper.pidfile=" + tempDir.resolve("wrapper.pid"), "wrapper.server.pidfile=" + tempDir.resolve("server.pid")), "sym.jar");
    }

    @Test
    void testGetInitdRunFile() {
        assertEquals("/etc/init.d/" + SERVICE_NAME, service.getInitdRunFile());
    }

    @Test
    void testGetSystemdScriptFile_fallsBackToEtcWhenLibFileIsAbsent() {
        assertEquals("/etc/systemd/system/" + SERVICE_NAME + ".service", service.getSystemdScriptFile());
    }

    @Test
    void testGetServiceCommand() {
        assertEquals(Arrays.asList("/etc/init.d/" + SERVICE_NAME, "start"), service.getServiceCommand("start"));
    }

    @Test
    void testGetSystemdCommand() {
        assertEquals(Arrays.asList("systemctl", "enable", SERVICE_NAME), service.getSystemdCommand("enable", SERVICE_NAME));
    }

    @Test
    void testIsInstalled_whenNeitherScriptExists() {
        assertFalse(service.isInstalled());
    }

    @Test
    void testGetCurrentPid() {
        assertEquals(CLibrary.INSTANCE.getpid(), service.getCurrentPid());
        assertTrue(service.getCurrentPid() > 0);
    }

    @Test
    void testGetProcessPid_withRunningProcess() throws Exception {
        Process process = new ProcessBuilder("sleep", "5").start();
        try {
            assertEquals((int) process.pid(), service.getProcessPid(process));
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void testGetProcessPid_whenPidIsUnavailable() {
        Process processMock = mock(Process.class);
        assertEquals(0, service.getProcessPid(processMock));
    }

    @Test
    void testIsPidRunning_withZeroPid() {
        assertFalse(service.isPidRunning(0));
    }

    @Test
    void testIsPidRunning_withCurrentPid() {
        assertTrue(service.isPidRunning(service.getCurrentPid()));
    }

    @Test
    void testKillProcess_withTerminate() throws Exception {
        Process process = new ProcessBuilder("sleep", "30").start();
        service.killProcess((int) process.pid(), true);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertFalse(process.isAlive());
    }

    @Test
    void testSetWorkingDirectory_withMissingDirectory() {
        assertFalse(service.setWorkingDirectory(tempDir.resolve("nope").toString()));
    }

    @Test
    void testStopProcesses_whenNotInstalledAndNotRunning() {
        assertEquals(Constants.RC_SERVER_NOT_RUNNING, assertThrows(WrapperException.class, () -> service.stopProcesses(false)).getErrorCode());
    }

    @Test
    void testGetRunCommandDir_matchesTheHost() {
        if (new File("/etc/init.d/rc0.d").exists()) {
            assertEquals("/etc/init.d", service.getRunCommandDir());
        } else if (new File("/etc/rc0.d").exists()) {
            assertEquals("/etc", service.getRunCommandDir());
        } else {
            assertEquals(Constants.RC_MISSING_INIT_FOLDER,
                    assertThrows(WrapperException.class, () -> service.getRunCommandDir()).getErrorCode());
        }
    }

    private String newConfigFile(String... lines) throws IOException {
        Path configFile = tempDir.resolve("sym_service.conf");
        Files.write(configFile, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return configFile.toString();
    }
}
