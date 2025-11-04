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
package org.jumpmind.symmetric.transport.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.transport.BatchBufferedWriter;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.web.WebConstants;

public class FileOutgoingTransport implements IOutgoingWithResponseTransport {
    String fileName;
    BatchBufferedWriter writer;
    OutputStream out;
    boolean open = true;
    Node remoteNode;
    String outgoingDir;
    private List<OutgoingBatch> processedBatches;

    public FileOutgoingTransport(Node remoteNode, Node localNode, String outgoingDir) {
        this.outgoingDir = outgoingDir;
        this.fileName = outgoingDir + File.separator + localNode.getNodeGroupId() + "-" + localNode.getNodeId() + "_to_" +
                remoteNode.getNodeGroupId() + "-" + remoteNode.getNodeId() + "_" + System.currentTimeMillis();
        this.remoteNode = remoteNode;
    }

    public String getOutgoingDir() {
        return outgoingDir;
    }

    @Override
    public BufferedWriter openWriter() {
        try {
            writer = new BatchBufferedWriter(new FileWriter(fileName + ".tmp"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return writer;
    }

    @Override
    public BufferedWriter getWriter() {
        return writer;
    }

    @Override
    public OutputStream openStream() {
        try {
            out = new FileOutputStream(fileName + ".tmp");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    @Override
    public BufferedReader readResponse() throws IOException {
        List<Long> batchIds = new ArrayList<Long>();
        if (processedBatches != null) {
            for (OutgoingBatch processedBatch : processedBatches) {
                batchIds.add(processedBatch.getBatchId());
            }
        } else if (writer != null) {
            batchIds = writer.getBatchIds();
        }
        StringBuilder resp = new StringBuilder();
        for (Long batchId : batchIds) {
            resp.append(WebConstants.ACK_BATCH_NAME).append(batchId).append("=").append(WebConstants.ACK_BATCH_OK).append("&");
            resp.append(WebConstants.ACK_NODE_ID).append(batchId).append("=").append(remoteNode.getNodeId()).append("&");
        }
        return new BufferedReader(new StringReader(resp.toString()));
    }

    @Override
    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
        }
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public NodeChannels getSuspendIgnoreChannelLists(IConfigurationService configurationService, String queue, Node targetNode) {
        return configurationService.getSuspendIgnoreChannelLists();
    }

    public void complete(boolean success) {
        if (!success) {
            new File(fileName + ".tmp").delete();
        } else if (writer != null) {
            new File(fileName + ".tmp").renameTo(new File(fileName + ".csv"));
        } else {
            new File(fileName + ".tmp").renameTo(new File(fileName + ".zip"));
        }
    }

    public List<OutgoingBatch> getProcessedBatches() {
        return processedBatches;
    }

    public void setProcessedBatches(List<OutgoingBatch> processedBatches) {
        this.processedBatches = processedBatches;
    }
}