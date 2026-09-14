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
package org.jumpmind.symmetric.file;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.Strings;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileTriggerFileModifiedListener extends FileAlterationListenerAdaptor {
    final protected Logger log = LoggerFactory.getLogger(getClass());
    protected FileTriggerRouter fileTriggerRouter;
    protected DirectorySnapshot snapshot;
    protected Date fromDate;
    protected Date toDate;
    protected FileModifiedCallback fileModifiedCallback;
    protected ProcessInfo processInfo;
    protected boolean isSyncOnCtlFile;
    protected boolean useCrc;
    protected ISymmetricEngine engine;
    protected Map<String, DirectorySnapshot> modifiedDirs = new HashMap<String, DirectorySnapshot>();
    protected long startTime = System.currentTimeMillis();
    protected long ts = startTime;
    protected long fileCount = 0;
    protected long changeCount = 0;

    public FileTriggerFileModifiedListener(FileTriggerRouter fileTriggerRouter, Date fromDate, Date toDate, ProcessInfo processInfo,
            boolean useCrc, FileModifiedCallback fileModifiedCallback, ISymmetricEngine engine) {
        this.fileTriggerRouter = fileTriggerRouter;
        this.snapshot = new DirectorySnapshot(fileTriggerRouter);
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.fileModifiedCallback = fileModifiedCallback;
        this.processInfo = processInfo;
        this.isSyncOnCtlFile = fileTriggerRouter.getFileTrigger().isSyncOnCtlFile();
        this.useCrc = useCrc;
        this.engine = engine;
        this.processInfo.setStatus(ProcessInfo.ProcessStatus.PROCESSING);
    }

    public void onStart(final FileAlterationObserver observer) {
        modifiedDirs.put(".", new DirectorySnapshot(fileTriggerRouter));
    }

    public void onFileCreate(File file) {
        log.debug("onFileCreate: " + file);
        if (isSyncOnCtlFile) {
            File ctlFile = engine.getFileSyncService().getControleFile(file);
            if (ctlFile.exists()) {
                log.debug("Control file detected: {}", file.getAbsolutePath());
                addSnapshot(file, LastEventType.CREATE, false);
            }
        } else {
            log.debug("File create detected: {}", file.getAbsolutePath());
            addSnapshot(file, LastEventType.CREATE, false);
        }
    }

    public void onDirectoryCreate(File directory) {
        log.debug("File create detected: {}", directory.getAbsolutePath());
        addSnapshot(directory, LastEventType.CREATE, true);
    }

    public void onStop(final FileAlterationObserver observer) {
        if (snapshot.size() > 0) {
            commit();
        }
        long scanTime = (System.currentTimeMillis() - startTime) / 1000;
        long modifiedDirStartTime = System.currentTimeMillis();
        long ts = modifiedDirStartTime;
        int modifiedDirCount = 0;
        int modifiedDirFileCount = 0;
        int modifiedDirChangeCount = 0;
        processInfo.setStatus(ProcessStatus.QUERYING);
        for (String relativeDir : modifiedDirs.keySet()) {
            DirectorySnapshot lastSnapshot = fileModifiedCallback.getLastDirectorySnapshot(relativeDir);
            DirectorySnapshot currentSnapshot = modifiedDirs.get(relativeDir);
            modifiedDirFileCount += currentSnapshot.size();
            DirectorySnapshot changesSinceLastSnapshot = lastSnapshot.diff(currentSnapshot);
            processInfo.setCurrentDataCount(processInfo.getCurrentDataCount() + lastSnapshot.size() + currentSnapshot.size());
            if (changesSinceLastSnapshot.size() > 0) {
                modifiedDirCount++;
                modifiedDirChangeCount += changesSinceLastSnapshot.size();
                fileModifiedCallback.commit(changesSinceLastSnapshot);
            }
            if (System.currentTimeMillis() - ts > 60000) {
                log.info("File tracker has been processing modified directories for {} seconds.  The following stats have been gathered: {}",
                        new Object[] { (System.currentTimeMillis() - modifiedDirStartTime) / 1000,
                                "{ modifiedDirCount=" + modifiedDirCount + " of " + modifiedDirs.size() +
                                        ", modifiedDirFileCount=" + modifiedDirFileCount + ", modifiedDirChangeCount= " + modifiedDirChangeCount + " }" });
                ts = System.currentTimeMillis();
            }
        }
        if (changeCount > 0 || modifiedDirChangeCount > 0) {
            String extra = ".";
            if (modifiedDirChangeCount > 0) {
                extra = String.format(", slow scan %d directories in %d seconds for %d changes.",
                        modifiedDirCount, (System.currentTimeMillis() - modifiedDirStartTime) / 1000, modifiedDirChangeCount);
            }
            log.info("File tracker fast scan {} files in {} seconds for {} changes" + extra,
                    new Object[] { fileCount, scanTime, changeCount });
        }
    }

    protected void addSnapshot(File file, LastEventType lastEventType, boolean isDir) {
        log.debug("addSnapshot: " + file);
        fileCount++;
        processInfo.incrementCurrentDataCount();
        FileSnapshot fileSnapshot = new FileSnapshot(fileTriggerRouter, file, lastEventType, useCrc);
        DirectorySnapshot modifiedDir = modifiedDirs.get(fileSnapshot.getRelativeDir());
        if (!isDir && modifiedDir != null) {
            // This file belongs to a directory that had a file add/delete, so we will process the directory later
            modifiedDir.add(fileSnapshot);
        } else {
            if (isDir) {
                // This is a directory that had a file add/delete, so we'll need to look for deletes later
                // Let's not save the beginning ./ in the front of the directory location, it doesn't match the value in the database.
                String relativeDir = Strings.CS.removeStart(fileSnapshot.getRelativeDir() + "/" + fileSnapshot.getFileName(), "./");
                modifiedDirs.put(relativeDir,
                        new DirectorySnapshot(fileTriggerRouter));
            } else {
                snapshot.add(fileSnapshot);
                changeCount++;
                if (snapshot.size() >= fileModifiedCallback.getCommitSize()) {
                    commit();
                }
            }
        }
        if (System.currentTimeMillis() - ts > 60000) {
            log.info("File tracker has been processing for {} seconds.  The following stats have been gathered: {}", new Object[] {
                    (System.currentTimeMillis() - startTime) / 1000,
                    "{ fileCount=" + fileCount + ", fileChangeCount=" + changeCount + " }" });
            ts = System.currentTimeMillis();
        }
    }

    protected void commit() {
        fileModifiedCallback.commit(snapshot);
        snapshot.clear();
    }

    public Map<String, DirectorySnapshot> getModifiedDirs() {
        return modifiedDirs;
    }

    static public class FileModifiedCallback {
        int commitSize;

        public FileModifiedCallback(int commitSize) {
            this.commitSize = commitSize;
        }

        public void commit(DirectorySnapshot dirSnapshot) {
        }

        public DirectorySnapshot getLastDirectorySnapshot(String relativeDir) {
            return null;
        }

        public int getCommitSize() {
            return commitSize;
        }
    }
}
