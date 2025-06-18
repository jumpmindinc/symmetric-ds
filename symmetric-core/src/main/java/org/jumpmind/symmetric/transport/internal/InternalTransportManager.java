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
package org.jumpmind.symmetric.transport.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.ChannelMap;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.transport.AbstractTransportManager;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates interaction between two symmetric engines in the same JVM.
 */
public class InternalTransportManager extends AbstractTransportManager implements ITransportManager {
    private static final Logger log = LoggerFactory.getLogger(InternalTransportManager.class);
    protected ISymmetricEngine symmetricEngine;

    public InternalTransportManager(ISymmetricEngine engine) {
        super(engine.getExtensionService());
        this.symmetricEngine = engine;
    }

    public IIncomingTransport getFilePullTransport(Node remote, final Node local,
            String securityToken, Map<String, String> requestProperties, String registrationUrl)
            throws IOException {
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(remote.getSyncUrl(), null, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os)
                    throws Exception {
                IOutgoingTransport transport = new InternalOutgoingTransport(respOs, null);
                ProcessInfo processInfo = engine.getStatisticManager().newProcessInfo(
                        new ProcessInfoKey(engine.getNodeService().findIdentityNodeId(), local
                                .getNodeId(), ProcessType.FILE_SYNC_PULL_HANDLER));
                try {
                    engine.getFileSyncService().sendFiles(processInfo, local, transport);
                    processInfo.setStatus(ProcessStatus.OK);
                } catch (RuntimeException ex) {
                    processInfo.setStatus(ProcessStatus.ERROR);
                    throw ex;
                }
                transport.close();
            }
        });
        return new InternalIncomingTransport(respIs);
    }

    public IIncomingTransport getPullTransport(Node remote, final Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        final ChannelMap suspendIgnoreChannels = symmetricEngine.getConfigurationService()
                .getSuspendIgnoreChannelLists(remote.getNodeId());
        runAtClient(remote.getSyncUrl(), null, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os)
                    throws Exception {
                IOutgoingTransport transport = new InternalOutgoingTransport(respOs,
                        suspendIgnoreChannels, StandardCharsets.UTF_8.name());
                ProcessInfo processInfo = engine.getStatisticManager().newProcessInfo(
                        new ProcessInfoKey(engine.getNodeService().findIdentityNodeId(), local
                                .getNodeId(), ProcessType.PULL_HANDLER_EXTRACT));
                try {
                    engine.getDataExtractorService().extract(processInfo, local, transport);
                    processInfo.setStatus(ProcessStatus.OK);
                } catch (RuntimeException ex) {
                    processInfo.setStatus(ProcessStatus.ERROR);
                    throw ex;
                }
                transport.close();
            }
        });
        return new InternalIncomingTransport(respIs);
    }

    public IIncomingTransport getPingTransport(Node remote, Node local, String registrationUrl) throws IOException {
        return null;
    }

    public IOutgoingWithResponseTransport getPushTransport(final Node targetNode, final Node sourceNode,
            String securityToken, String registrationUrl) throws IOException {
        return getPushTransport(targetNode, sourceNode, securityToken, null, registrationUrl);
    }

    @Override
    public IOutgoingWithResponseTransport getPushTransport(final Node remote, final Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        final PipedOutputStream pushOs = new PipedOutputStream();
        final PipedInputStream pushIs = new PipedInputStream(pushOs);
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(remote.getSyncUrl(), pushIs, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os)
                    throws Exception {
                // This should be basically what the push servlet does ...
                engine.getDataLoaderService().loadDataFromPush(local, pushIs, respOs);
            }
        });
        return new InternalOutgoingWithResponseTransport(pushOs, respIs);
    }

    public IOutgoingWithResponseTransport getFilePushTransport(final Node targetNode, final Node sourceNode,
            String securityToken, String registrationUrl) throws IOException {
        final PipedOutputStream pushOs = new PipedOutputStream();
        final PipedInputStream pushIs = new PipedInputStream(pushOs);
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(targetNode.getSyncUrl(), pushIs, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os) {
                // This should be basically what the push servlet does ...
                engine.getFileSyncService().loadFilesFromPush(sourceNode.getNodeId(), is, os);
            }
        });
        return new InternalOutgoingWithResponseTransport(pushOs, respIs);
    }

    public IIncomingTransport getRegisterTransport(final Node client, String registrationUrl) throws IOException {
        return getRegisterTransport(client, registrationUrl, null);
    }

    public IIncomingTransport getRegisterTransport(final Node client, String registrationUrl, Map<String, String> requestProperties)
            throws IOException {
        final PipedOutputStream respOs = new PipedOutputStream();
        final PipedInputStream respIs = new PipedInputStream(respOs);
        runAtClient(registrationUrl, null, respOs, new IClientRunnable() {
            public void run(ISymmetricEngine engine, InputStream is, OutputStream os)
                    throws Exception {
                // This should be basically what the registration servlet does
                // ...
                engine.getRegistrationService().registerNode(client, os, false);
            }
        });
        return new InternalIncomingTransport(respIs);
    }

    public IOutgoingWithResponseTransport getRegisterPushTransport(Node remote, Node local) throws IOException {
        return null;
    }

    @Override
    public int sendCopyRequest(Node local) throws IOException {
        return -1;
    }

    @Override
    public int sendStatusRequest(Node local, Map<String, String> statuses) throws IOException {
        return -1;
    }

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local,
            String securityToken, String registrationUrl) throws IOException {
        return sendAcknowledgement(remote, list, local, securityToken, null, registrationUrl);
    }

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local,
            String securityToken, Map<String, String> requestProperties, String registrationUrl) throws IOException {
        try {
            if (list != null && list.size() > 0) {
                ISymmetricEngine remoteEngine = getTargetEngine(remote.getSyncUrl());
                for (String ackData : getAcknowledgementData(remote.requires13Compatiblity(), local.getNodeId(), list, -1, -1)) {
                    List<BatchAck> batches = readAcknowledgement(ackData);
                    for (BatchAck batchInfo : batches) {
                        remoteEngine.getAcknowledgeService().ack(batchInfo);
                    }
                }
            }
            return WebConstants.SC_OK;
        } catch (Exception ex) {
            log.error("", ex);
            return -1;
        }
    }

    public void writeAcknowledgement(OutputStream out, Node remote, List<IncomingBatch> list,
            Node local, String securityToken) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        for (String ackData : getAcknowledgementData(remote.requires13Compatiblity(), local.getNodeId(), list, -1, -1)) {
            pw.println(ackData);
        }
        pw.close();
    }

    private void runAtClient(final String url, final InputStream is, final OutputStream os,
            final IClientRunnable runnable) {
        new Thread() {
            public void run() {
                try {
                    ISymmetricEngine engine = getTargetEngine(url);
                    runnable.run(engine, is, os);
                } catch (Exception e) {
                    log.error("", e);
                } finally {
                    try {
                        if (is != null) {
                            is.close();
                        }
                    } catch (IOException e) {
                    }
                    try {
                        if (os != null) {
                            os.close();
                        }
                    } catch (IOException e) {
                    }
                }
            }
        }.start();
    }

    private ISymmetricEngine getTargetEngine(String url) {
        ISymmetricEngine engine = AbstractSymmetricEngine.findEngineByUrl(url);
        if (engine == null) {
            throw new NullPointerException(
                    "Could not find the engine reference for the following url: " + url);
        } else {
            return engine;
        }
    }

    interface IClientRunnable {
        public void run(ISymmetricEngine engine, InputStream is, OutputStream os) throws Exception;
    }

    @Override
    public IIncomingTransport getConfigTransport(Node remote, Node local, String securityToken,
            String symmetricVersion, String configVersion, String registrationUrl) throws IOException {
        return null;
    }

    @Override
    public IOutgoingWithResponseTransport getBandwidthPushTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl)
            throws IOException {
        return null;
    }

    @Override
    public IIncomingTransport getComparePullTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException {
        return null;
    }

    @Override
    public IOutgoingWithResponseTransport getComparePushTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException {
        return null;
    }
}
