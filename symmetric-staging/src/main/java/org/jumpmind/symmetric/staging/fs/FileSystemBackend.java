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
package org.jumpmind.symmetric.staging.fs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jumpmind.symmetric.staging.api.ResourceState;
import org.jumpmind.symmetric.staging.api.StagingKey;
import org.jumpmind.symmetric.staging.spi.LockBroker;
import org.jumpmind.symmetric.staging.spi.StorageBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemBackend implements StorageBackend {
    private static final Logger log = LoggerFactory.getLogger(FileSystemBackend.class);
    private final File rootDirectory;
    private final LockFileBroker lockBroker;

    public FileSystemBackend(File rootDirectory) {
        this.rootDirectory = rootDirectory;
        if (!rootDirectory.exists() && !rootDirectory.mkdirs() && !rootDirectory.exists()) {
            throw new IllegalStateException("Cannot create staging directory " + rootDirectory.getAbsolutePath());
        }
        this.lockBroker = new LockFileBroker(rootDirectory);
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public File buildFile(StagingKey key, ResourceState state) {
        return new File(rootDirectory, key.asPath() + "." + state.getExtensionName());
    }

    public File findExistingFile(StagingKey key) {
        File done = buildFile(key, ResourceState.DONE);
        if (done.exists()) {
            return done;
        }
        File create = buildFile(key, ResourceState.CREATE);
        if (create.exists()) {
            return create;
        }
        return null;
    }

    @Override
    public OutputStream rawOutput(StagingKey key, boolean append) throws IOException {
        File file = buildFile(key, ResourceState.CREATE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Cannot create parent directory " + parent.getAbsolutePath());
        }
        return new BufferedOutputStream(new FileOutputStream(file, append));
    }

    @Override
    public InputStream rawInput(StagingKey key) throws IOException {
        File file = findExistingFile(key);
        if (file == null) {
            throw new IOException("No staged file found for " + key);
        }
        return new BufferedInputStream(new FileInputStream(file));
    }

    @Override
    public boolean exists(StagingKey key) {
        return findExistingFile(key) != null;
    }

    @Override
    public boolean delete(StagingKey key) {
        boolean deleted = false;
        File done = buildFile(key, ResourceState.DONE);
        if (done.exists()) {
            deleted |= FileUtils.deleteQuietly(done);
        }
        File create = buildFile(key, ResourceState.CREATE);
        if (create.exists()) {
            deleted |= FileUtils.deleteQuietly(create);
        }
        return deleted;
    }

    @Override
    public long size(StagingKey key) {
        File file = findExistingFile(key);
        return file == null ? 0L : file.length();
    }

    @Override
    public Iterable<StagingKey> list() {
        List<StagingKey> keys = new ArrayList<>();
        if (!rootDirectory.exists()) {
            return Collections.emptyList();
        }
        listRecursive(rootDirectory, keys);
        return keys;
    }

    private void listRecursive(File directory, List<StagingKey> accumulator) {
        Path dirPath = directory.toPath();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            for (Path entry : stream) {
                File entryFile = entry.toFile();
                if (entryFile.isDirectory()) {
                    listRecursive(entryFile, accumulator);
                } else {
                    String name = entryFile.getName();
                    if (name.endsWith(".create") || name.endsWith(".done")) {
                        accumulator.add(toKey(entryFile));
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Error listing staging directory {}: {}", directory.getAbsolutePath(), ex.getMessage());
        }
    }

    private StagingKey toKey(File file) {
        String absolute = file.getAbsolutePath().replace('\\', '/');
        String rootAbsolute = rootDirectory.getAbsolutePath().replace('\\', '/');
        String relative = absolute.substring(rootAbsolute.length());
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        int dot = relative.lastIndexOf('.');
        if (dot > 0) {
            relative = relative.substring(0, dot);
        }
        return StagingKey.ofPath(relative);
    }

    @Override
    public void writeSidecar(StagingKey key, String suffix, byte[] payload) throws IOException {
        File sidecar = new File(rootDirectory, key.asPath() + suffix);
        File parent = sidecar.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Cannot create parent directory " + parent.getAbsolutePath());
        }
        FileUtils.writeByteArrayToFile(sidecar, payload);
    }

    @Override
    public byte[] readSidecar(StagingKey key, String suffix) throws IOException {
        File sidecar = new File(rootDirectory, key.asPath() + suffix);
        if (!sidecar.exists()) {
            return null;
        }
        return FileUtils.readFileToByteArray(sidecar);
    }

    @Override
    public boolean deleteSidecar(StagingKey key, String suffix) {
        File sidecar = new File(rootDirectory, key.asPath() + suffix);
        if (!sidecar.exists()) {
            return false;
        }
        return FileUtils.deleteQuietly(sidecar);
    }

    @Override
    public LockBroker lockBroker() {
        return lockBroker;
    }
}
