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
package org.jumpmind.symmetric.io.stage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.jumpmind.exception.IoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StagedResource implements IStagedResource {
    private static final Logger log = LoggerFactory.getLogger(StagedResource.class);
    private AtomicInteger references = new AtomicInteger(0);
    protected File directory;
    protected File file;
    protected String path;
    protected StringBuilder memoryBuffer;
    protected long lastUpdateTime;
    protected long generationTime;
    protected State state;
    protected OutputStream outputStream = null;
    protected Map<Thread, InputStream> inputStreams = null;
    protected Map<Thread, BufferedReader> readers = null;
    protected BufferedWriter writer;
    protected StagingManager stagingManager;

    public StagedResource(File directory, String path, StagingManager stagingManager) {
        this.directory = directory;
        this.path = path;
        this.stagingManager = stagingManager;
        lastUpdateTime = System.currentTimeMillis();
        File doneFile = buildFile(State.DONE);
        if (doneFile.exists()) { // Only call exists once for done files. This can be expensive on some SAN type devices.
            this.state = State.DONE;
            this.file = doneFile;
            lastUpdateTime = file.lastModified();
        } else {
            this.state = State.CREATE;
            this.file = buildFile(state);
            if (file.exists()) {
                lastUpdateTime = file.lastModified();
            }
        }
        this.generationTime = lastUpdateTime;
    }

    protected static String toPath(File directory, File file) {
        String path = file.getAbsolutePath();
        path = path.replaceAll("\\\\", "/");
        path = path.substring(directory.getAbsolutePath().length(), file.getAbsolutePath().length());
        int extensionIndex = path.lastIndexOf(".");
        if (extensionIndex > 0) {
            path = path.substring(1, extensionIndex);
            return path;
        } else {
            throw new IllegalStateException("Expected an extension of .done or .create at the end of the path and did not find it: " + path);
        }
    }

    @Override
    public void reference() {
        references.incrementAndGet();
        log.debug("Increased reference to {} for {} by {}", references, path, Thread.currentThread().getName());
    }

    @Override
    public void dereference() {
        references.decrementAndGet();
        log.debug("Decreased reference to {} for {} by {}", references, path, Thread.currentThread().getName());
    }

    public boolean isInUse() {
        return references.get() > 0 || (readers != null && readers.size() > 0) || writer != null ||
                (inputStreams != null && inputStreams.size() > 0) ||
                outputStream != null;
    }

    public boolean isFileResource() {
        return file != null && file.exists();
    }

    public boolean isMemoryResource() {
        return memoryBuffer != null && memoryBuffer.length() > 0;
    }

    protected final File buildFile(State state) {
        return new File(directory, String.format("%s.%s", path, state.getExtensionName()));
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        if (file != null && file.exists()) {
            File newFile = buildFile(state);
            if (!newFile.equals(file)) {
                closeInternal();
                if (newFile.exists()) {
                    if (writer != null || outputStream != null) {
                        throw new IoException("Could not write '{}' it is currently being written to", newFile.getAbsolutePath());
                    }
                    if (!FileUtils.deleteQuietly(newFile)) {
                        log.warn("Failed to delete '{}' in preparation for renaming '{}'", newFile.getAbsolutePath(), file.getAbsoluteFile());
                        if (readers != null && readers.size() > 0) {
                            for (Thread thread : readers.keySet()) {
                                BufferedReader reader = readers.get(thread);
                                log.warn("Closing unwanted reader for '{}' that had been created on thread '{}'", newFile.getAbsolutePath(), thread.getName());
                                try {
                                    if (reader != null) {
                                        reader.close();
                                    }
                                } catch (IOException e) {
                                }
                            }
                        }
                        readers = null;
                        if (!FileUtils.deleteQuietly(newFile)) {
                            log.warn("Failed to delete '{}' for a second time", newFile.getAbsolutePath());
                        }
                    }
                }
                if (!file.renameTo(newFile)) {
                    handleFailedRename(file, newFile);
                }
            }
        }
        refreshLastUpdateTime();
        this.state = state;
        this.file = buildFile(state);
        if (state == State.DONE && this.file.exists() && !this.file.setLastModified(generationTime)) {
            log.warn(
                    "Failed to pin the last-modified time of '{}' to its generation time; a future re-lookup of this resource may derive a different generation time",
                    this.file.getAbsolutePath());
        }
    }

    protected void handleFailedRename(File oldFile, File newFile) {
        String msg = null;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
        int tries = 5;
        while (!newFile.exists() && tries-- > 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
        }
        if (newFile.exists()) {
            if (isSameFile(oldFile, newFile)) {
                msg = String.format("Had trouble renaming file.  The destination file already exists, and is the same size - will proceed. " +
                        "Source file: (%s size: %s lastModified: %s) Target file: (%s size: %s lastModified: %s)",
                        oldFile, oldFile.length(), dateFormat.format(oldFile.lastModified()),
                        newFile, newFile.length(), dateFormat.format(newFile.lastModified()));
                FileUtils.deleteQuietly(oldFile);
                log.info(msg);
                return;
            } else {
                msg = String.format("Had trouble renaming file.  The destination file already exists, but is not the same size. " +
                        "Source file: (%s size: %s lastModified: %s) Target file: (%s size: %s lastModified: %s)",
                        oldFile, oldFile.length(), dateFormat.format(oldFile.lastModified()),
                        newFile, newFile.length(), dateFormat.format(newFile.lastModified()));
            }
        } else {
            msg = String.format("Had trouble renaming file. The destination file does not appear to exist. " +
                    "Source file: (%s size: %s lastModified: %s) Target file: (%s)",
                    oldFile, oldFile.length(), dateFormat.format(oldFile.lastModified()),
                    newFile);
        }
        log.warn(msg);
        throw new IllegalStateException(msg);
    }

    protected boolean isSameFile(File oldFile, File newFile) {
        return (oldFile.length() == newFile.length());
    }

    public synchronized BufferedReader getReader() {
        refreshLastUpdateTime();
        Thread thread = Thread.currentThread();
        BufferedReader reader = readers != null ? readers.get(thread) : null;
        if (reader == null) {
            if (file != null && file.exists()) {
                try {
                    reader = createReader();
                    createReadersMap();
                    readers.put(thread, reader);
                } catch (IOException ex) {
                    throw new IoException(ex);
                }
            } else if (memoryBuffer != null && memoryBuffer.length() > 0) {
                reader = new BufferedReader(new StringReader(memoryBuffer.toString()));
                createReadersMap();
                readers.put(thread, reader);
            } else {
                throw new IllegalStateException(
                        "There is no content to read.  Memory buffer was empty and "
                                + file.getAbsolutePath() + " was not found.");
            }
        }
        return reader;
    }

    protected BufferedReader createReader() throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8.name()));
    }

    private synchronized final void createReadersMap() {
        if (readers == null) {
            readers = new HashMap<Thread, BufferedReader>(path.contains("common") ? 10 : 1);
        }
    }

    private synchronized final void closeReadersMap() {
        if (readers != null && readers.size() == 0) {
            readers = null;
        }
    }

    private synchronized final void createInputStreamsMap() {
        if (inputStreams == null) {
            inputStreams = new HashMap<Thread, InputStream>(path.contains("common") ? 10 : 1);
        }
    }

    private synchronized final void closeInputStreamsMap() {
        if (inputStreams != null && inputStreams.size() == 0) {
            inputStreams = null;
        }
    }

    public void close() {
        refreshLastUpdateTime();
        closeInternal();
    }

    private void closeInternal() {
        Thread thread = Thread.currentThread();
        BufferedReader reader = readers != null ? readers.get(thread) : null;
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
            }
            readers.remove(thread);
            closeReadersMap();
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
            }
            writer = null;
        }
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
            }
            outputStream = null;
        }
        InputStream inputStream = inputStreams != null ? inputStreams.get(thread) : null;
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
            }
            inputStreams.remove(thread);
            closeInputStreamsMap();
        }
    }

    public void closeReaders() {
        if (readers != null) {
            for (BufferedReader reader : readers.values()) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
            readers = null;
        }
    }

    public OutputStream getOutputStream() {
        return getOutputStream(false);
    }

    public OutputStream getOutputStream(boolean append) {
        refreshLastUpdateTime();
        try {
            if (outputStream == null && file != null) {
                if (!append && file.exists()) {
                    log.warn("getOutputStream had to delete {} because it already existed",
                            file.getAbsolutePath());
                    file.delete();
                }
                file.getParentFile().mkdirs();
                outputStream = createOutputStream(append);
            }
            return outputStream;
        } catch (FileNotFoundException e) {
            throw new IoException(e);
        }
    }

    protected OutputStream createOutputStream(boolean append) throws FileNotFoundException {
        return new BufferedOutputStream(new FileOutputStream(file, append));
    }

    public synchronized InputStream getInputStream() {
        refreshLastUpdateTime();
        Thread thread = Thread.currentThread();
        InputStream reader = inputStreams != null ? inputStreams.get(thread) : null;
        if (reader == null) {
            if (file != null && file.exists()) {
                try {
                    reader = createInputStream();
                    createInputStreamsMap();
                    inputStreams.put(thread, reader);
                } catch (IOException ex) {
                    throw new IoException(ex);
                }
            } else if (memoryBuffer != null && memoryBuffer.length() > 0) {
                reader = new ByteArrayInputStream(memoryBuffer.toString().getBytes(StandardCharsets.UTF_8));
                createInputStreamsMap();
                inputStreams.put(thread, reader);
            } else {
                throw new IllegalStateException("There is no content to read. "
                        + file.getAbsolutePath() + " was not found.");
            }
        }
        return reader;
    }

    protected InputStream createInputStream() throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    public BufferedWriter getWriter(long threshold) {
        return getWriter(threshold, false);
    }

    @Override
    public BufferedWriter getWriter(long threshold, boolean append) {
        refreshLastUpdateTime();
        if (writer == null) {
            if (!append) {
                if (file != null && file.exists()) {
                    log.warn("getWriter had to delete {} because it already existed.", file.getAbsolutePath());
                    file.delete();
                } else if (this.memoryBuffer != null) {
                    log.warn("We had to delete the memory buffer for {} because it already existed", getPath());
                    this.memoryBuffer = null;
                }
                this.memoryBuffer = threshold > 0 ? new StringBuilder() : null;
            } else {
                this.memoryBuffer = null;
            }
            writer = createWriter(threshold, append);
        }
        return writer;
    }

    protected BufferedWriter createWriter(long threshold) {
        return createWriter(threshold, false);
    }

    protected BufferedWriter createWriter(long threshold, boolean append) {
        return new BufferedWriter(new ThresholdFileWriter(threshold, this.memoryBuffer, file, append));
    }

    public long getSize() {
        if (file != null && file.exists()) {
            return file.length();
        } else if (memoryBuffer != null) {
            return memoryBuffer.length();
        } else {
            return 0;
        }
    }

    public boolean exists() {
        return (file != null && file.exists() && file.length() > 0) || (memoryBuffer != null && memoryBuffer.length() > 0);
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void refreshLastUpdateTime() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    @Override
    public long getGenerationTime() {
        return generationTime;
    }

    /**
     * Stamps {@code generationTime} to now, discarding whatever value the constructor derived from a stale file that previously occupied this path. Called by
     * {@link StagingManager#create(Object...)} after it deletes such a file, so a freshly-created resource's identity doesn't get inherited from unrelated
     * prior content.
     */
    void resetGenerationTime() {
        this.generationTime = System.currentTimeMillis();
    }

    public boolean delete() {
        close();
        boolean deleted = false;
        if (file != null && file.exists()) {
            FileUtils.deleteQuietly(file);
            deleted = !file.exists();
        }
        if (memoryBuffer != null) {
            memoryBuffer = null;
            deleted = true;
        }
        stagingManager.removeResourcePath(path);
        if (deleted) {
            if (log.isDebugEnabled() && path.contains("outgoing")) {
                log.debug("Deleted staging resource {}", path);
            }
        }
        return deleted;
    }

    public File getFile() {
        return file;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return (file != null && file.exists()) ? file.getAbsolutePath()
                : String.format("%d bytes in memory",
                        memoryBuffer != null ? memoryBuffer.length() : 0);
    }
}
