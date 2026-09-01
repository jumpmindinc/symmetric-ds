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
package org.jumpmind.symmetric.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.collections4.iterators.ReverseListIterator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSyncZipDataWriter implements IDataWriter {
    private static final Logger log = LoggerFactory.getLogger(FileSyncZipDataWriter.class);
    protected long byteCount;
    protected long maxBytesToSync;
    protected int compressionLevel;
    protected IFileSyncService fileSyncService;
    protected IStagedResource stagedResource;
    protected ZipOutputStream zos;
    protected Table snapshotTable;
    protected Batch batch;
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();
    protected List<FileSnapshot> snapshotEvents;
    protected DataContext context;
    protected INodeService nodeService;
    protected IExtensionService extensionService;
    protected IConfigurationService configurationService;
    protected boolean batchInError;
    protected ISymmetricEngine engine;

    public FileSyncZipDataWriter(long maxBytesToSync, int compressionLevel, IStagedResource stagedResource, ISymmetricEngine engine) {
        this.engine = engine;
        this.maxBytesToSync = maxBytesToSync;
        this.compressionLevel = compressionLevel;
        this.fileSyncService = engine.getFileSyncService();
        this.stagedResource = stagedResource;
        this.nodeService = engine.getNodeService();
        this.extensionService = engine.getExtensionService();
        this.configurationService = engine.getConfigurationService();
    }

    public void open(DataContext context) {
        this.context = context;
    }

    @Override
    public void close() {
        // no-op as this is called at batch boundaries, but this writer can handle multiple batches.
    }

    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }

    public void start(Batch batch) {
        this.batch = batch;
        this.statistics.put(batch, new Statistics());
        this.snapshotEvents = new ArrayList<FileSnapshot>();
    }

    public boolean start(Table table) {
        this.snapshotTable = table;
        return true;
    }

    public void write(CsvData data) {
        DataEventType eventType = data.getDataEventType();
        if (eventType == DataEventType.INSERT || eventType == DataEventType.UPDATE) {
            if (filterInitialLoad(data)) {
                return;
            }
            if (eventType == DataEventType.INSERT) {
                statistics.get(this.batch).increment(DataWriterStatisticConstants.INSERTCOUNT);
            } else {
                statistics.get(this.batch).increment(DataWriterStatisticConstants.UPDATECOUNT);
            }
            Map<String, String> columnData = data.toColumnNameValuePairs(
                    snapshotTable.getColumnNames(), CsvData.ROW_DATA);
            Map<String, String> oldColumnData = data.toColumnNameValuePairs(
                    snapshotTable.getColumnNames(), CsvData.OLD_DATA);
            FileSnapshot snapshot = new FileSnapshot();
            snapshot.setTriggerId(columnData.get("TRIGGER_ID"));
            snapshot.setRouterId(columnData.get("ROUTER_ID"));
            try {
                snapshot.setFileModifiedTime(Long.parseLong(columnData.get("FILE_MODIFIED_TIME")));
            } catch (NumberFormatException nfe) {
                log.info("File modified time was not a number : " + columnData.get("FILE_MODIFIED_TIME") + " for file " + columnData.get("FILE_NAME"));
            }
            try {
                snapshot.setCrc32Checksum(columnData.get("CRC32_CHECKSUM") == null ? 0 : Long.parseLong(columnData.get("CRC32_CHECKSUM")));
            } catch (NumberFormatException nfe) {
                log.info("Checksum was not a number : " + columnData.get("CRC32_CHECKSUM") + " for file " + columnData.get("FILE_NAME"));
            }
            String oldChecksum = oldColumnData.get("CRC32_CHECKSUM");
            if (StringUtils.isNotBlank(oldChecksum)) {
                snapshot.setOldCrc32Checksum(Long.parseLong(oldChecksum));
            }
            try {
                snapshot.setFileSize(Long.parseLong(columnData.get("FILE_SIZE")));
            } catch (NumberFormatException nfe) {
                log.info("File size was not a number : " + columnData.get("FILE_SIZE") + " for file " + columnData.get("FILE_NAME"));
            }
            snapshot.setLastUpdateBy(columnData.get("LAST_UPDATE_BY"));
            snapshot.setFileName(columnData.get("FILE_NAME"));
            snapshot.setRelativeDir(columnData.get("RELATIVE_DIR"));
            snapshot.setLastEventType(LastEventType.fromCode(columnData.get("LAST_EVENT_TYPE")));
            snapshotEvents.add(snapshot);
        } else if (eventType == DataEventType.RELOAD) {
            String targetNodeId = context.getBatch().getTargetNodeId();
            Node targetNode = nodeService.findNode(targetNodeId, true);
            List<FileTriggerRouter> fileTriggerRouters = fileSyncService
                    .getFileTriggerRoutersForCurrentNode(false);
            for (FileTriggerRouter fileTriggerRouter : fileTriggerRouters) {
                if (fileTriggerRouter.isEnabled()
                        && fileTriggerRouter.isInitialLoadEnabled()
                        && fileTriggerRouter.getRouter().getNodeGroupLink().getTargetNodeGroupId()
                                .equals(targetNode.getNodeGroupId())) {
                    DirectorySnapshot directorySnapshot = fileSyncService
                            .getDirectorySnapshot(fileTriggerRouter);
                    snapshotEvents.addAll(directorySnapshot);
                }
            }
        }
    }

    public void end(Table table) {
    }

    public void end(Batch batch, boolean inError) {
        try {
            if (!inError) {
                if (zos == null) {
                    zos = new ZipOutputStream(stagedResource.getOutputStream());
                    zos.setLevel(compressionLevel);
                }
                FileSyncZipScript script = createFileSyncZipScript(batch.getTargetNodeId());
                script.buildScriptStart(batch);
                Map<String, LastEventType> entriesByLastEventType = new HashMap<String, LastEventType>();
                Map<String, String> entriesByLastRouterId = new HashMap<String, String>();
                List<IFileSourceTracker> fileTrackers = extensionService.getExtensionPointList(IFileSourceTracker.class);
                // Need to use the validation values from the later files (crc32, file size), so work the list backwards
                ReverseListIterator<FileSnapshot> iterator = new ReverseListIterator<FileSnapshot>(snapshotEvents);
                while (iterator.hasNext()) {
                    FileSnapshot snapshot = iterator.next();
                    FileTriggerRouter triggerRouter = fileSyncService.getFileTriggerRouter(
                            snapshot.getTriggerId(), snapshot.getRouterId(), false);
                    if (triggerRouter != null) {
                        LastEventType eventType = snapshot.getLastEventType();
                        FileTrigger fileTrigger = triggerRouter.getFileTrigger();
                        String targetBaseDir = ((triggerRouter.getTargetBaseDir() == null) ? null : triggerRouter.getTargetBaseDir().replace('\\', '/'));
                        if (StringUtils.isBlank(targetBaseDir)) {
                            targetBaseDir = ((fileTrigger.getBaseDir() == null) ? null : fileTrigger.getBaseDir().replace('\\', '/'));
                        }
                        targetBaseDir = StringEscapeUtils.escapeJava(targetBaseDir);
                        StringBuilder entryName = new StringBuilder(Long.toString(batch
                                .getBatchId()));
                        entryName.append("/");
                        if (!snapshot.getRelativeDir().equals(".")) {
                            entryName.append(snapshot.getRelativeDir()).append("/");
                        }
                        entryName.append(snapshot.getFileName());
                        File file = null;
                        IFileSourceTracker fileTracker = null;
                        for (IFileSourceTracker tracker : fileTrackers) {
                            if (tracker.handlesDir(fileTrigger.getBaseDir())) {
                                fileTracker = tracker;
                                break;
                            }
                        }
                        if (fileTracker != null) {
                            file = fileTracker.createSourceFile(snapshot);
                        } else {
                            file = fileTrigger.createSourceFile(snapshot);
                        }
                        if (file.isDirectory()) {
                            entryName.append("/");
                        }
                        String targetFile = "targetBaseDir + \"/\" + targetRelativeDir + \"/\" + targetFileName";
                        LastEventType previousEventForEntry = entriesByLastEventType.get(entryName.toString());
                        boolean addFileToZip = true;
                        if (previousEventForEntry != null) {
                            // There could be a delete between a MODIFY and a CREATE, but the delete is not added to the
                            // entriesByLastEventType data structure
                            if ((previousEventForEntry == eventType)
                                    || (previousEventForEntry == LastEventType.CREATE && eventType == LastEventType.MODIFY)
                                    || (previousEventForEntry == LastEventType.MODIFY && eventType == LastEventType.CREATE)) {
                                addFileToZip = false;
                            }
                        }
                        String lastRouterId = entriesByLastRouterId.get(entryName.toString());
                        boolean addFileToScript = !snapshot.getRouterId().equals(lastRouterId);
                        if (addFileToZip) {
                            if (eventType != LastEventType.DELETE) {
                                if (file.exists()) {
                                    byteCount += file.length();
                                    ZipEntry entry = new ZipEntry(entryName.toString());
                                    if (fileTracker == null) {
                                        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                                        // note: as of 8/21 getting the creation time won't work on unix file systems EVEN IF THEY HAVE EXT4
                                        // you also cannot set the creation time on unix systems (birth date) using setCreationTime, so this only works for
                                        // windows
                                        entry.setCreationTime(attr.creationTime());
                                    }
                                    entry.setSize(file.length());
                                    entry.setTime(file.lastModified());
                                    zos.putNextEntry(entry);
                                    if (file.isFile()) {
                                        try (InputStream fis = fileTracker != null ? fileTracker.getInputStream(file) : new FileInputStream(file)) {
                                            IOUtils.copy(fis, zos);
                                        }
                                    }
                                    zos.closeEntry();
                                    entriesByLastEventType.put(entryName.toString(), eventType);
                                    entriesByLastRouterId.put(entryName.toString(), snapshot.getRouterId());
                                } else {
                                    log.warn(
                                            "Could not find the {} file to package for synchronization.  Skipping it.",
                                            file.getAbsolutePath());
                                }
                            }
                        }
                        if (addFileToScript) {
                            script.buildScriptFileSnapshot(batch, snapshot, triggerRouter, fileTrigger,
                                    file, targetBaseDir, targetFile);
                        }
                    } else {
                        log.error(
                                "Could not locate the file trigger ({}) router ({}) to process a snapshot event.  The event will be ignored",
                                snapshot.getTriggerId(), snapshot.getRouterId());
                    }
                }
                script.buildScriptEnd(batch);
                ZipEntry entry = new ZipEntry(batch.getBatchId() + "/" + script.getScriptFileName(batch));
                zos.putNextEntry(entry);
                IOUtils.write(script.getScript().toString(), zos, Charset.defaultCharset());
                zos.closeEntry();
                entry = new ZipEntry(batch.getBatchId() + "/batch-info.txt");
                zos.putNextEntry(entry);
                IOUtils.write(batch.getChannelId(), zos, Charset.defaultCharset());
                zos.closeEntry();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            batchInError = true;
            if (e instanceof IOException) {
                throw new IoException(e);
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public void finish() {
        try {
            if (zos != null) {
                zos.finish();
                try {
                    zos.close();
                } catch (IOException e) {
                }
            }
        } catch (IOException e) {
            throw new IoException(e);
        } finally {
            if (stagedResource != null) {
                if (!batchInError) {
                    stagedResource.setState(IStagedResource.State.DONE);
                    stagedResource.close();
                } else {
                    stagedResource.delete();
                    batchInError = false;
                }
            }
        }
    }

    public boolean readyToSend() {
        return byteCount > maxBytesToSync;
    }

    protected FileSyncZipScript createFileSyncZipScript(String targetNodeId) {
        FileSyncZipScript script = null;
        if (isCClient(targetNodeId)) {
            script = new BashFileSyncZipScript();
        } else {
            for (IFileSyncScriptCreator creator : extensionService.getExtensionPointList(IFileSyncScriptCreator.class)) {
                script = creator.create(targetNodeId);
                break;
            }
            if (script == null) {
                script = new BeanShellFileSyncZipScript(engine);
            }
        }
        return script;
    }

    protected boolean isCClient(String nodeId) {
        boolean cclient = false;
        Node node = nodeService.findNode(nodeId, true);
        if (node != null) {
            cclient = Strings.CS.equals(node.getDeploymentType(), Constants.DEPLOYMENT_TYPE_CCLIENT);
        }
        return cclient;
    }

    protected boolean filterInitialLoad(CsvData data) {
        Channel channel = configurationService.getChannel(batch.getChannelId());
        if (channel.isReloadFlag()) {
            List<FileTriggerRouter> fileTriggerRouters = fileSyncService
                    .getFileTriggerRoutersForCurrentNode(false);
            Map<String, String> columnData = data.toColumnNameValuePairs(
                    snapshotTable.getColumnNames(), CsvData.ROW_DATA);
            String triggerId = columnData.get("TRIGGER_ID");
            String routerId = columnData.get("ROUTER_ID");
            for (FileTriggerRouter fileTriggerRouter : fileTriggerRouters) {
                if (fileTriggerRouter.getTriggerId().equals(triggerId)
                        && fileTriggerRouter.getRouterId().equals(routerId)) {
                    if (!fileTriggerRouter.isEnabled() || !fileTriggerRouter.isInitialLoadEnabled()) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }
        return false;
    }
}
