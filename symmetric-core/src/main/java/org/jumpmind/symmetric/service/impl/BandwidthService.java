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
package org.jumpmind.symmetric.service.impl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.service.IBandwidthService;
import org.jumpmind.symmetric.transport.BandwidthTestResults;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.http.HttpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * @see IBandwidthService
 */
public class BandwidthService implements IBandwidthService {
    public static final String Diagnostic_BandwidthFail = "Could not get Bandwidth";
    public static final String Diagnostic_BandwidthFailure = "%s";
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private ISymmetricEngine engine;

    public BandwidthService(ISymmetricEngine engine) {
        this.engine = engine;
    }

    public double getDownloadKbpsFor(String syncUrl, long sampleSize, long maxTestDuration) {
        return getDownloadKbpsForImpl(syncUrl, null, null, sampleSize, maxTestDuration);
    }

    public double getDownloadKbpsFor(Node remoteNode, Node localNode, long sampleSize, long maxTestDuration) {
        return getDownloadKbpsForImpl(remoteNode.getSyncUrl(), remoteNode, localNode, sampleSize, maxTestDuration);
    }

    protected double getDownloadKbpsForImpl(String syncUrl, Node remoteNode, Node localNode, long sampleSize, long maxTestDuration) {
        double downloadSpeed = -1d;
        try {
            BandwidthTestResults bw;
            if (remoteNode != null && localNode != null) {
                bw = getDownloadResultsFor(remoteNode, localNode, sampleSize, maxTestDuration);
            } else {
                bw = getDownloadResultsFor(syncUrl, sampleSize, maxTestDuration);
            }
            downloadSpeed = (int) bw.getKbps();
        } catch (SocketTimeoutException e) {
            log.warn("Socket timeout while attempting to contact {}", syncUrl);
        } catch (Exception e) {
            log.error("", e);
        }
        return downloadSpeed;
    }

    protected BandwidthTestResults getDownloadResultsFor(String syncUrl, long sampleSize,
            long maxTestDuration) throws IOException {
        byte[] buffer = new byte[1024];
        BandwidthTestResults bw = new BandwidthTestResults();
        URL u = URI.create(String.format("%s/bandwidth?direction=pull&sampleSize=%s", syncUrl, sampleSize)).toURL();
        bw.start();
        try (HttpConnection conn = new HttpConnection(u)) {
            try (InputStream is = conn.getInputStream()) {
                int r;
                while (-1 != (r = is.read(buffer)) && bw.getElapsed() <= maxTestDuration) {
                    bw.transmitted(r);
                }
            }
        }
        bw.stop();
        log.info("{} was calculated to have a download bandwidth of {} kbps", syncUrl, bw.getKbps());
        return bw;
    }

    protected BandwidthTestResults getDownloadResultsFor(Node remoteNode, Node localNode, long sampleSize,
            long maxTestDuration) throws IOException {
        IIncomingTransport incoming = null;
        try {
            NodeSecurity identitySecurity = engine.getNodeService().findNodeSecurity(localNode.getNodeId(), true);
            incoming = engine.getTransportManager().getBandwidthPullTransport(remoteNode, localNode, identitySecurity.getNodePassword(),
                    new HashMap<String, String>(), engine.getParameterService().getRegistrationUrl(), sampleSize);
            byte[] buffer = new byte[1024];
            BandwidthTestResults bw = new BandwidthTestResults();
            bw.start();
            try (InputStream is = incoming.openStream()) {
                int r;
                while (-1 != (r = is.read(buffer)) && bw.getElapsed() <= maxTestDuration) {
                    bw.transmitted(r);
                }
            }
            bw.stop();
            log.info("{} was calculated to have a download bandwidth of {} kbps", remoteNode.getSyncUrl(), bw.getKbps());
            return bw;
        } finally {
            if (incoming != null) {
                incoming.close();
            }
        }
    }

    public double getUploadKbpsFor(Node remoteNode, Node localNode, long sampleSize, long maxTestDuration) throws IOException {
        double uploadSpeed = -1d;
        try {
            BandwidthTestResults bwtr = getUploadResultsFor(remoteNode, localNode, sampleSize, maxTestDuration);
            uploadSpeed = bwtr.getKbps();
        } catch (SocketTimeoutException e) {
            log.error(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return uploadSpeed;
    }

    protected BandwidthTestResults getUploadResultsFor(Node remoteNode, Node localNode, long sampleSize, long maxTestDuration) throws IOException {
        IOutgoingWithResponseTransport outgoing = null;
        try {
            NodeSecurity identitySecurity = engine.getNodeService().findNodeSecurity(localNode.getNodeId(), true);
            outgoing = engine.getTransportManager().getBandwidthPushTransport(remoteNode, localNode,
                    identitySecurity.getNodePassword(), new HashMap<String, String>(), engine.getParameterService().getRegistrationUrl());
            outgoing.getSuspendIgnoreChannelLists(engine.getConfigurationService(), Constants.CHANNEL_DEFAULT, remoteNode);
            long startTime = System.currentTimeMillis();
            BufferedWriter writer = outgoing.openWriter();
            String stringToWriter = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789" +
                    "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";
            for (long i = 0l; i < sampleSize;) {
                writer.write(stringToWriter);
                i += stringToWriter.length();
                if (System.currentTimeMillis() - startTime > maxTestDuration) {
                    break;
                }
            }
            String response = outgoing.readResponse().readLine();
            BandwidthTestResults results = new Gson().fromJson(response, BandwidthTestResults.class);
            log.info("{} was calculated to have a upload bandwidth of {} kbps", remoteNode.getSyncUrl(), results.getKbps());
            return results;
        } finally {
            if (outgoing != null) {
                outgoing.close();
            }
        }
    }

    public List<BandwidthService.BandwidthResults> diagnoseDownloadBandwidth(Node localNode, Node remoteNode) {
        List<Long> downloadPayloadsList = new ArrayList<Long>();
        List<BandwidthService.BandwidthResults> downloadBandwidthResultsList = new ArrayList<BandwidthService.BandwidthResults>();
        String downloadPayloads = engine.getParameterService().getString("console.node.connection.diagnostic.download.bandwidth.payloads", "");
        if (downloadPayloads != null && downloadPayloads.length() > 0) {
            for (String s : Arrays.asList(downloadPayloads.split(","))) {
                downloadPayloadsList.add(Long.valueOf(s));
            }
        }
        for (Long payload : downloadPayloadsList) {
            BandwidthService.BandwidthResults bw = ((BandwidthService) engine.getBandwidthService()).new BandwidthResults();
            bw.setPayloadSize(payload);
            double dlSpeed = 0d;
            if (isPullEnabled(localNode, remoteNode)) {
                try {
                    dlSpeed = engine.getBandwidthService().getDownloadKbpsFor(remoteNode, localNode, payload, 5000);
                    bw.setKbps(dlSpeed);
                } catch (Exception e) {
                    bw.setFailure(true);
                    bw.setFailureMessage(Diagnostic_BandwidthFail);
                    bw.setException(e);
                }
            } else {
                bw.setFailure(true);
                bw.setFailureMessage("Pull is not enabled");
            }
            downloadBandwidthResultsList.add(bw);
        }
        return downloadBandwidthResultsList;
    }

    protected boolean isPullEnabled(Node localNode, Node remoteNode) {
        List<NodeGroupLink> groupLinks = engine.getConfigurationService().getNodeGroupLinks(false);
        for (NodeGroupLink link : groupLinks) {
            if (link.getSourceNodeGroupId().equals(remoteNode.getNodeGroupId())
                    && link.getTargetNodeGroupId().equals(localNode.getNodeGroupId())
                    && link.getDataEventAction().getShortName().equals("pull")) {
                return true;
            }
        }
        return false;
    }

    public List<BandwidthService.BandwidthResults> diagnoseUploadBandwidth(Node localNode, Node remoteNode) {
        List<Long> uploadPayloadsList = new ArrayList<Long>();
        List<BandwidthService.BandwidthResults> uploadBandwidthResultsList = new ArrayList<BandwidthService.BandwidthResults>();
        String uploadPayloads = engine.getParameterService().getString("console.node.connection.diagnostic.upload.bandwidth.payloads", "");
        if (uploadPayloads != null && uploadPayloads.length() > 0) {
            for (String s : Arrays.asList(uploadPayloads.split(","))) {
                uploadPayloadsList.add(Long.valueOf(s));
            }
        }
        for (Long payload : uploadPayloadsList) {
            BandwidthService.BandwidthResults bw = ((BandwidthService) engine.getBandwidthService()).new BandwidthResults();
            bw.setPayloadSize(payload);
            double dlSpeed = 0d;
            if (isPushEnabled(localNode, remoteNode)) {
                try {
                    dlSpeed = engine.getBandwidthService().getUploadKbpsFor(remoteNode, localNode, payload, 5000);
                    bw.setKbps(dlSpeed);
                } catch (Exception e) {
                    bw.setFailure(true);
                    bw.setFailureMessage(BandwidthService.Diagnostic_BandwidthFail);
                    bw.setException(e);
                }
            } else {
                bw.setFailure(true);
                bw.setFailureMessage("Push is not enabled");
            }
            uploadBandwidthResultsList.add(bw);
        }
        return uploadBandwidthResultsList;
    }

    protected boolean isPushEnabled(Node localNode, Node remoteNode) {
        List<NodeGroupLink> groupLinks = engine.getConfigurationService().getNodeGroupLinks(false);
        for (NodeGroupLink link : groupLinks) {
            if (link.getSourceNodeGroupId().equals(localNode.getNodeGroupId())
                    && link.getTargetNodeGroupId().equals(remoteNode.getNodeGroupId())
                    && link.getDataEventAction().getShortName().equals("push")) {
                return true;
            }
        }
        return false;
    }

    public class BandwidthResults {
        private long payloadSize;
        private double kbps;
        private boolean failure = false;
        private String failureMessage = null;
        private Exception exception = null;

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }

        public boolean isFailure() {
            return failure;
        }

        public void setFailure(boolean failure) {
            this.failure = failure;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        public long getPayloadSize() {
            return payloadSize;
        }

        public void setPayloadSize(long payloadSize) {
            this.payloadSize = payloadSize;
        }

        public double getKbps() {
            return kbps;
        }

        public void setKbps(double kbps) {
            this.kbps = kbps;
        }
    }
}
