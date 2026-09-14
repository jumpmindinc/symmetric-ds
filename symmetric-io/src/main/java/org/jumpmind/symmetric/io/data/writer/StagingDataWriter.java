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
package org.jumpmind.symmetric.io.data.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.util.FormatUtils;

public class StagingDataWriter extends AbstractProtocolDataWriter {
    private IStagingManager stagingManager;
    private String category;
    private Map<Batch, IStagedResource> stagedResources = new ConcurrentHashMap<Batch, IStagedResource>();
    private long memoryThresholdInBytes;
    private boolean acquireReference = false;

    public StagingDataWriter(long memoryThresholdInBytes, boolean acquireReference, String sourceNodeId, String category, IStagingManager stagingManager,
            boolean sendCaptureTime, boolean sendRowCaptureTime, IProtocolDataWriterListener... listeners) {
        this(sourceNodeId, category, stagingManager, sendCaptureTime, sendRowCaptureTime, toList(listeners));
        this.memoryThresholdInBytes = memoryThresholdInBytes;
        this.acquireReference = acquireReference;
    }

    public StagingDataWriter(String sourceNodeId, String category, IStagingManager stagingManager,
            boolean sendCaptureTime, boolean sendRowCaptureTime, List<IProtocolDataWriterListener> listeners) {
        super(sourceNodeId, listeners, false, sendCaptureTime, sendRowCaptureTime);
        this.category = category;
        this.stagingManager = stagingManager;
    }

    public static List<IProtocolDataWriterListener> toList(IProtocolDataWriterListener... listeners) {
        if (listeners != null) {
            ArrayList<IProtocolDataWriterListener> list = new ArrayList<IProtocolDataWriterListener>(
                    listeners.length);
            for (IProtocolDataWriterListener l : listeners) {
                list.add(l);
            }
            return list;
        } else {
            return new ArrayList<IProtocolDataWriterListener>(0);
        }
    }

    @Override
    protected void notifyEndBatch(Batch batch, IProtocolDataWriterListener listener) {
        listener.end(context, batch, getStagedResource(batch));
        stagedResources.remove(batch);
    }

    protected IStagedResource getStagedResource(Batch batch) {
        IStagedResource resource = stagedResources.get(batch);
        if (resource == null) {
            String location = batch.getStagedLocation();
            resource = stagingManager.find(category, location, batch.getBatchId());
            if (resource == null || resource.getState() == State.DONE) {
                log.debug("Creating staged resource for batch {}", batch.getNodeBatchId());
                resource = stagingManager.create(category, location, batch.getBatchId());
                if (acquireReference) {
                    resource.reference();
                }
            }
            stagedResources.put(batch, resource);
        }
        return resource;
    }

    @Override
    protected void endBatch(Batch batch) {
        super.endBatch(batch);
        IStagedResource resource = getStagedResource(batch);
        resource.close();
        flushNodeId = true;
        processedTables.clear();
        table = null;
    }

    @Override
    protected void print(Batch batch, String data) {
        if (log.isDebugEnabled() && data != null) {
            log.debug("Writing staging data: {}", FormatUtils.abbreviateForLogging(data));
        }
        IStagedResource resource = getStagedResource(batch);
        BufferedWriter writer = resource.getWriter(memoryThresholdInBytes);
        try {
            int size = data == null ? 0 : data.length();
            for (int i = 0; i < size; i = i + 1024) {
                int end = i + 1024;
                writer.append(data, i, end < size ? end : size);
            }
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }
}
