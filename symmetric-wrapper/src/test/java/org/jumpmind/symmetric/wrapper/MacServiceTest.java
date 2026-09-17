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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MacServiceTest {
    private static final String SERVICE_NAME = "symtest-mac";
    private static final String PLIST = "/Library/LaunchDaemons/com.jumpmind." + SERVICE_NAME + ".plist";
    @TempDir
    private Path tempDir;
    private MacService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new MacService();
        service.loadConfig(tempDir.toString(), newConfigFile("wrapper.name=" + SERVICE_NAME,
                "wrapper.pidfile=" + tempDir.resolve("wrapper.pid"), "wrapper.server.pidfile=" + tempDir.resolve("server.pid")), "sym.jar");
    }

    @Test
    void testGetLaunchDaemonsName() {
        assertEquals("com.jumpmind.symmetricds", service.getLaunchDaemonsName("symmetricds"));
    }

    @Test
    void testGetLaunchDaemonsFileName() {
        assertEquals("com.jumpmind.symmetricds.plist", service.getLaunchDaemonsFileName("symmetricds"));
    }

    @Test
    void testGetLaunchDaemonCmd_withOverride() {
        assertEquals(Arrays.asList("launchctl", "load", "-w", PLIST), service.getLaunchDaemonCmd("load", "-w", PLIST));
    }

    @Test
    void testGetLaunchDaemonCmd_withoutOverride() {
        assertEquals(Arrays.asList("launchctl", "stop", "com.jumpmind." + SERVICE_NAME),
                service.getLaunchDaemonCmd("stop", null, "com.jumpmind." + SERVICE_NAME));
    }

    @Test
    void testGetLaunchDaemonCmd_withEmptyOverride() {
        assertEquals(Arrays.asList("launchctl", "stop", "com.jumpmind." + SERVICE_NAME),
                service.getLaunchDaemonCmd("stop", "", "com.jumpmind." + SERVICE_NAME));
    }

    @Test
    void testGetLaunchDaemonLoadCommand() {
        assertEquals(Arrays.asList("launchctl", "load", "-w", PLIST), service.getLaunchDaemonLoadCommand());
    }

    @Test
    void testGetLaunchDaemonUnloadCmd() {
        assertEquals(Arrays.asList("launchctl", "unload", "-w", PLIST), service.getLaunchDaemonUnloadCmd());
    }

    @Test
    void testGetLaunchDaemonStartCmd() {
        assertEquals(Arrays.asList("launchctl", "start", "com.jumpmind." + SERVICE_NAME), service.getLaunchDaemonStartCmd());
    }

    @Test
    void testGetLaunchDaemonStopCmd() {
        assertEquals(Arrays.asList("launchctl", "stop", "com.jumpmind." + SERVICE_NAME), service.getLaunchDaemonStopCmd());
    }

    @Test
    void testGetPsCommand() {
        assertEquals(Arrays.asList("/bin/ps", "-p", "4321", "-opid=,comm="), service.getPsCommand(4321));
    }

    @Test
    void testIsInstalled_whenPlistIsAbsent() {
        assertFalse(service.isInstalled());
    }

    @Test
    void testIsPidRunning_withZeroPid() {
        assertFalse(service.isPidRunning(0));
    }

    @Test
    void testStopProcesses_whenNotInstalledAndNotRunning() {
        assertEquals(Constants.RC_SERVER_NOT_RUNNING, assertThrows(WrapperException.class, () -> service.stopProcesses(false)).getErrorCode());
    }

    private String newConfigFile(String... lines) throws IOException {
        Path configFile = tempDir.resolve("sym_service.conf");
        Files.write(configFile, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return configFile.toString();
    }
}
