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
package org.jumpmind.symmetric.service;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.file.DirectorySnapshot;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.RemoteNodeStatuses;
import org.jumpmind.symmetric.transport.IOutgoingTransport;

public interface IFileSyncService {
    public void trackChanges(boolean force);

    public List<FileTrigger> getFileTriggers();

    public FileTrigger getFileTrigger(String triggerId);

    public void saveFileTrigger(FileTrigger fileTrigger);

    public void saveFileTriggerAsCopy(String originalId, FileTrigger fileTrigger);

    public void renameFileTrigger(String oldId, FileTrigger fileTrigger);

    public void saveFileTriggerRouter(FileTriggerRouter fileTriggerRouter);

    public void renameFileTriggerRouter(String oldTriggerId, String oldRouterId, FileTriggerRouter fileTriggerRouter);

    public void deleteFileTriggerRouter(String triggerId, String routerId);

    public void deleteFileTriggerRouter(FileTriggerRouter fileTriggerRouter);

    public void deleteFileTriggerRouter(ISqlTransaction transaction, FileTriggerRouter fileTriggerRouter);

    public void deleteAllFileTriggerRouters();

    public void deleteFileTrigger(FileTrigger fileTrigger);

    public void deleteAllFileTriggers();

    public void clearCache();

    public DirectorySnapshot getDirectorySnapshot(FileTriggerRouter fileTriggerRouter);

    public void save(List<FileSnapshot> changes);

    public void save(ISqlTransaction sqlTransaction, FileSnapshot snapshot);

    public List<FileTriggerRouter> getFileTriggerRoutersForCurrentNode(boolean refreshCache);

    public List<FileTriggerRouter> getFileTriggerRouters(boolean refreshCache);

    public List<FileTriggerRouter> getFileTriggerRoutersFromDb();

    public FileTriggerRouter getFileTriggerRouter(String triggerId, String routerId, boolean refreshCache);

    public void loadFilesFromPush(String nodeId, InputStream in, OutputStream out);

    public RemoteNodeStatuses pullFilesFromNodes(boolean force);

    public RemoteNodeStatuses pushFilesToNodes(boolean force);

    public List<OutgoingBatch> sendFiles(ProcessInfo processInfo, Node node, IOutgoingTransport outgoingTransport);

    public void acknowledgeFiles(OutgoingBatch outgoingBatch);

    public boolean refreshFromDatabase();

    public File getControleFile(File file);

    public Object[] getStagingPathComponents(OutgoingBatch fileSyncBatch);

    public int countEntriesInFileSnapshot();
}
