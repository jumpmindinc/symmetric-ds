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
package org.jumpmind.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JarBuilder {
    private File baseDir;
    private File[] sourceFiles;
    private File outputFile;
    private String version;

    public JarBuilder(File baseDir, File outputFile, File[] sourceFiles, String version) {
        this.sourceFiles = sourceFiles;
        this.outputFile = outputFile;
        this.baseDir = baseDir;
        this.version = version;
    }

    public void build() throws IOException {
        this.outputFile.delete();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, version);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        JarOutputStream target = new JarOutputStream(new FileOutputStream(outputFile), manifest);
        for (File file : sourceFiles) {
            add(file, target);
        }
        target.close();
    }

    private String massageJarEntryName(File source) {
        String name = source.getPath();
        if (baseDir != null && name.startsWith(baseDir.getPath())) {
            if (name.length() > baseDir.getPath().length()) {
                name = name.substring(baseDir.getPath().length() + 1);
            } else {
                name = "";
            }
        }
        name = name.replace("\\", "/");
        if (name.equals("META-INF/MANIFEST.MF")) {
            name = "";
        }
        return name;
    }

    private void add(File source, JarOutputStream target) throws IOException {
        if (source.isDirectory()) {
            String name = massageJarEntryName(source);
            if (name.trim().length() != 0) {
                if (!name.endsWith("/")) {
                    name += "/";
                }
                JarEntry entry = new JarEntry(name);
                entry.setTime(source.lastModified());
                target.putNextEntry(entry);
                target.closeEntry();
            }
            File[] files = source.listFiles();
            if (files != null) {
                for (File nestedFile : files) {
                    add(nestedFile, target);
                }
            }
        } else {
            JarEntry entry = new JarEntry(massageJarEntryName(source));
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
                byte[] buffer = new byte[1024];
                while (true) {
                    int count = in.read(buffer);
                    if (count == -1) {
                        break;
                    }
                    target.write(buffer, 0, count);
                }
                target.closeEntry();
            }
        }
    }
}