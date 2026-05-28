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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.staging.api.ILineReader;
import org.jumpmind.symmetric.staging.api.ILineWriter;
import org.jumpmind.symmetric.staging.api.ResourceLocation;
import org.jumpmind.symmetric.staging.api.ResourceState;
import org.jumpmind.symmetric.staging.api.StagingKey;
import org.jumpmind.symmetric.staging.api.StagingOptions;
import org.jumpmind.symmetric.staging.memory.ThresholdSpillWriter;
import org.jumpmind.symmetric.staging.spi.AbstractStagingResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemStagingResource extends AbstractStagingResource {
    private static final Logger log = LoggerFactory.getLogger(FileSystemStagingResource.class);
    private final FileSystemBackend backend;
    private final FileSystemStagingManager manager;
    private final ResourceLocation filesystemLocation;
    private File file;
    private byte[] memorySnapshot;
    private OutputStream outputStream;
    private ThresholdSpillWriter spillWriter;
    private Map<Thread, InputStream> inputStreams;
    private Map<Thread, BufferedReader> readers;
    private BufferedWriter writer;

    public FileSystemStagingResource(StagingKey key, StagingOptions options,
            FileSystemBackend backend, FileSystemStagingManager manager,
            ResourceLocation filesystemLocation) {
        super(key, options,
                initialLocation(backend, key, filesystemLocation));
        this.backend = backend;
        this.manager = manager;
        this.filesystemLocation = filesystemLocation;
        File done = backend.buildFile(key, ResourceState.DONE);
        if (done.exists()) {
            this.state = ResourceState.DONE;
            this.file = done;
            refreshLastUpdateTime();
        } else {
            this.state = ResourceState.CREATE;
            this.file = backend.buildFile(key, ResourceState.CREATE);
            if (this.file.exists()) {
                refreshLastUpdateTime();
            }
        }
    }

    private static ResourceLocation initialLocation(FileSystemBackend backend, StagingKey key,
            ResourceLocation filesystemLocation) {
        return backend.findExistingFile(key) != null ? filesystemLocation : ResourceLocation.MEMORY;
    }

    @Override
    public synchronized void setState(ResourceState newState) {
        if (file != null && file.exists()) {
            File target = backend.buildFile(key, newState);
            if (!target.equals(file)) {
                closeReadersAndWriters();
                if (target.exists()) {
                    if (writer != null || outputStream != null) {
                        throw new IoException("Could not write '%s' it is currently being written to",
                                target.getAbsolutePath());
                    }
                    FileUtils.deleteQuietly(target);
                }
                if (!file.renameTo(target)) {
                    throw new IoException("Failed to rename %s to %s", file.getAbsolutePath(),
                            target.getAbsolutePath());
                }
                this.file = target;
            }
        }
        this.state = newState;
        refreshLastUpdateTime();
    }

    @Override
    public synchronized OutputStream openOutputStream() {
        return openOutputStream(false);
    }

    @Override
    public synchronized OutputStream openOutputStream(boolean append) {
        if (outputStream != null) {
            return outputStream;
        }
        try {
            outputStream = createPrimaryOutput(append);
            updateLocation(filesystemLocation, false);
            return outputStream;
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    @Override
    public synchronized BufferedWriter openWriter(Charset charset, long memoryThresholdBytes) {
        if (writer != null) {
            return writer;
        }
        ensureCleanWriteState();
        Charset effective = charset != null ? charset : options.getCharset();
        try {
            spillWriter = new ThresholdSpillWriter(memoryThresholdBytes, new ThresholdSpillWriter.SpillTarget() {
                @Override
                public OutputStream openSpillTarget() throws IOException {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    return new java.io.BufferedOutputStream(new java.io.FileOutputStream(file, false));
                }

                @Override
                public void onSpilled() {
                    updateLocation(filesystemLocation, false);
                }
            });
            writer = new BufferedWriter(new OutputStreamWriter(spillWriter, effective));
            return writer;
        } catch (Exception ex) {
            throw new IoException(ex);
        }
    }

    @Override
    public synchronized InputStream openInputStream() {
        refreshLastUpdateTime();
        Thread thread = Thread.currentThread();
        InputStream existing = inputStreams != null ? inputStreams.get(thread) : null;
        if (existing != null) {
            return existing;
        }
        InputStream stream = null;
        if (file != null && file.exists()) {
            try {
                stream = new java.io.BufferedInputStream(new java.io.FileInputStream(file));
            } catch (IOException ex) {
                throw new IoException(ex);
            }
        } else if (memorySnapshot != null && memorySnapshot.length > 0) {
            stream = new ByteArrayInputStream(memorySnapshot);
        } else {
            throw new IllegalStateException("No content for " + key.asPath());
        }
        if (inputStreams == null) {
            inputStreams = new HashMap<>();
        }
        inputStreams.put(thread, stream);
        return stream;
    }

    @Override
    public synchronized BufferedReader openReader(Charset charset) {
        refreshLastUpdateTime();
        Thread thread = Thread.currentThread();
        BufferedReader reader = readers != null ? readers.get(thread) : null;
        if (reader != null) {
            return reader;
        }
        Charset effective = charset != null ? charset : options.getCharset();
        try {
            InputStream raw = openInputStream();
            reader = new BufferedReader(new InputStreamReader(raw, effective));
            if (readers == null) {
                readers = new HashMap<>();
            }
            readers.put(thread, reader);
            return reader;
        } catch (Exception ex) {
            throw new IoException(ex);
        }
    }

    @Override
    public ILineReader openLineReader(Charset charset) {
        final BufferedReader reader = openReader(charset);
        return new ILineReader() {
            @Override
            public String readLine() throws IOException {
                return reader.readLine();
            }

            @Override
            public java.util.stream.Stream<String> lines() {
                return reader.lines();
            }

            @Override
            public void close() throws IOException {
                reader.close();
            }
        };
    }

    @Override
    public ILineWriter openLineWriter(Charset charset, long memoryThresholdBytes) {
        final BufferedWriter w = openWriter(charset, memoryThresholdBytes);
        return new ILineWriter() {
            @Override
            public void writeLine(String line) throws IOException {
                w.write(line);
                w.newLine();
            }

            @Override
            public void flush() throws IOException {
                w.flush();
            }

            @Override
            public void close() throws IOException {
                w.close();
            }
        };
    }

    @Override
    public File getFilesystemFile() {
        return file;
    }

    @Override
    public String getRemoteObjectKey() {
        return null;
    }

    @Override
    public long getSize() {
        if (file != null && file.exists()) {
            return file.length();
        }
        if (spillWriter != null && !spillWriter.isSpilled()) {
            return spillWriter.getBytesWritten();
        }
        if (memorySnapshot != null) {
            return memorySnapshot.length;
        }
        return 0L;
    }

    @Override
    public boolean exists() {
        return (file != null && file.exists() && file.length() > 0)
                || (memorySnapshot != null && memorySnapshot.length > 0)
                || (spillWriter != null && spillWriter.getBytesWritten() > 0);
    }

    @Override
    public boolean isInUse() {
        return referenceCount() > 0
                || (readers != null && !readers.isEmpty())
                || (inputStreams != null && !inputStreams.isEmpty())
                || writer != null
                || outputStream != null;
    }

    @Override
    public synchronized boolean delete() {
        close();
        boolean deleted = false;
        if (file != null && file.exists()) {
            FileUtils.deleteQuietly(file);
            deleted = !file.exists();
        }
        if (memorySnapshot != null) {
            memorySnapshot = null;
            deleted = true;
        }
        if (manager != null) {
            manager.removeResource(key);
        }
        return deleted;
    }

    @Override
    public synchronized void close() {
        refreshLastUpdateTime();
        Thread thread = Thread.currentThread();
        BufferedReader reader = readers != null ? readers.get(thread) : null;
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
            readers.remove(thread);
            if (readers.isEmpty()) {
                readers = null;
            }
        }
        InputStream input = inputStreams != null ? inputStreams.get(thread) : null;
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
            }
            inputStreams.remove(thread);
            if (inputStreams.isEmpty()) {
                inputStreams = null;
            }
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
        if (spillWriter != null) {
            if (!spillWriter.isSpilled()) {
                memorySnapshot = spillWriter.getMemorySnapshot();
                if (memorySnapshot != null) {
                    updateLocation(ResourceLocation.MEMORY, false);
                }
            }
            try {
                spillWriter.close();
            } catch (IOException ignored) {
            }
            spillWriter = null;
        }
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
            outputStream = null;
        }
    }

    @Override
    public synchronized void closeReaders() {
        if (readers != null) {
            for (BufferedReader r : readers.values()) {
                try {
                    r.close();
                } catch (IOException ignored) {
                }
            }
            readers = null;
        }
    }

    private void closeReadersAndWriters() {
        closeReaders();
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
            outputStream = null;
        }
        if (inputStreams != null) {
            for (InputStream s : inputStreams.values()) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
            inputStreams = null;
        }
    }

    private OutputStream createPrimaryOutput(boolean append) throws IOException {
        if (!append && file.exists()) {
            log.warn("openOutputStream had to delete {} because it already existed", file.getAbsolutePath());
            file.delete();
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return new java.io.BufferedOutputStream(new java.io.FileOutputStream(file, append));
    }

    private void ensureCleanWriteState() {
        if (file != null && file.exists()) {
            log.warn("openWriter had to delete {} because it already existed", file.getAbsolutePath());
            file.delete();
        }
        memorySnapshot = null;
    }

    @Override
    public String toString() {
        return (file != null && file.exists())
                ? file.getAbsolutePath()
                : String.format("%d bytes in memory", memorySnapshot != null ? memorySnapshot.length : 0);
    }
}
