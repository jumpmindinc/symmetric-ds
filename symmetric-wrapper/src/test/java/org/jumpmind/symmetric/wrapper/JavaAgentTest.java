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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class JavaAgentTest {
    @TempDir
    private Path tempDir;

    @Test
    void testAddToClassPath_appendsTheJar() throws Exception {
        Instrumentation instrumentationMock = mock(Instrumentation.class);
        JavaAgent.premain(null, instrumentationMock);
        File jar = newJar("patch.jar");
        JavaAgent.addToClassPath(jar);
        ArgumentCaptor<JarFile> captor = ArgumentCaptor.forClass(JarFile.class);
        verify(instrumentationMock).appendToSystemClassLoaderSearch(captor.capture());
        assertEquals(jar.getAbsolutePath(), captor.getValue().getName());
    }

    @Test
    void testAddToClassPath_withMissingFile() {
        Instrumentation instrumentationMock = mock(Instrumentation.class);
        JavaAgent.premain("args", instrumentationMock);
        assertThrows(IOException.class, () -> JavaAgent.addToClassPath(tempDir.resolve("nope.jar").toFile()));
        verifyNoInteractions(instrumentationMock);
    }

    @Test
    void testAddToClassPath_withFileThatIsNotAJar() throws Exception {
        Instrumentation instrumentationMock = mock(Instrumentation.class);
        JavaAgent.premain(null, instrumentationMock);
        Path notAJar = Files.write(tempDir.resolve("notajar.jar"), "plain text".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> JavaAgent.addToClassPath(notAJar.toFile()));
        verify(instrumentationMock, never()).appendToSystemClassLoaderSearch(any(JarFile.class));
    }

    @Test
    void testPremain_replacesThePreviousInstrumentation() throws Exception {
        Instrumentation firstMock = mock(Instrumentation.class);
        Instrumentation secondMock = mock(Instrumentation.class);
        JavaAgent.premain(null, firstMock);
        JavaAgent.premain(null, secondMock);
        JavaAgent.addToClassPath(newJar("second.jar"));
        verify(secondMock).appendToSystemClassLoaderSearch(any(JarFile.class));
        verifyNoInteractions(firstMock);
    }

    private File newJar(String name) throws IOException {
        File jar = tempDir.resolve(name).toFile();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            out.putNextEntry(new JarEntry("marker.txt"));
            out.write("marker".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }
}
