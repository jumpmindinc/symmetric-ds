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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void testGetWrapperCommandQuote() {
        assertEquals("\"", new WindowsService().getWrapperCommandQuote());
    }

    @Test
    void testGetServiceCommand_withServiceExecutable() throws Exception {
        WindowsService service = newService("wrapper.java.command=..\\bin\\sym_service.exe");
        assertEquals(Arrays.asList("\"..\\bin\\sym_service.exe\"", "init", "\"" + service.getConfig().getConfigFile() + "\""),
                service.getServiceCommand());
    }

    @Test
    void testGetServiceCommand_withJavaCommand() throws Exception {
        WindowsService service = newService("wrapper.java.command=/usr/bin/java");
        List<String> cmd = service.getServiceCommand();
        assertEquals("\"/usr/bin/java\"", cmd.get(0));
        assertEquals("-jar", cmd.get(cmd.size() - 4));
        assertEquals("\"" + service.getConfig().getWrapperJarPath() + "\"", cmd.get(cmd.size() - 3));
        assertEquals("init", cmd.get(cmd.size() - 2));
        assertEquals("\"" + service.getConfig().getConfigFile() + "\"", cmd.get(cmd.size() - 1));
    }

    @Test
    void testGetServiceCommand_ignoresCaseOfServiceExecutable() throws Exception {
        assertEquals("init", newService("wrapper.java.command=..\\bin\\SYM_SERVICE.EXE").getServiceCommand().get(1));
    }

    private WindowsService newService(String... lines) throws IOException {
        Path configFile = tempDir.resolve("sym_service.conf");
        Files.write(configFile, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        WindowsService service = new WindowsService();
        service.config = new WrapperConfig(tempDir.toString(), configFile.toString(), "sym.jar");
        return service;
    }
}
