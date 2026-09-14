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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.io.IOException;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BandwidthServiceTest {
    private ISymmetricEngine engine;
    private IConfigurationService configurationService;
    private BandwidthService bandwidthService;
    private IParameterService parameterService;
    private Node local;
    private Node remote;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        configurationService = mock(IConfigurationService.class);
        parameterService = mock(IParameterService.class);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getParameterService()).thenReturn(parameterService);
        bandwidthService = spy(new BandwidthService(engine));
        when(engine.getBandwidthService()).thenReturn(bandwidthService);
        local = new Node();
        local.setNodeGroupId("corp");
        remote = new Node();
        remote.setNodeGroupId("store");
    }

    private NodeGroupLink link(String source, String target, NodeGroupLinkAction action) {
        NodeGroupLink l = new NodeGroupLink();
        l.setSourceNodeGroupId(source);
        l.setTargetNodeGroupId(target);
        l.setDataEventAction(action);
        return l;
    }

    private void stubLinks(NodeGroupLink links) {
        when(configurationService.getNodeGroupLinks(false)).thenReturn(Arrays.asList(links));
    }

    @Test
    void testIsPullEnabled_returnsTrueWhenMatchingPullLinkExists() {
        stubLinks(link("store", "corp", NodeGroupLinkAction.W));
        assertTrue(bandwidthService.isPullEnabled(local, remote));
    }

    @Test
    void testIsPullEnabled_returnsFalseWhenActionIsNotPull() {
        stubLinks(link("store", "corp", NodeGroupLinkAction.P));
        assertFalse(bandwidthService.isPullEnabled(local, remote));
    }

    @Test
    void testIsPullEnabled_returnsFalseWhenGroupsDoNotMatch() {
        stubLinks(link("corp", "store", NodeGroupLinkAction.W));
        assertFalse(bandwidthService.isPullEnabled(local, remote));
    }

    @Test
    void testIsPullEnabled_returnsFalseWhenNoLinks() {
        when(configurationService.getNodeGroupLinks(false)).thenReturn(Collections.emptyList());
        assertFalse(bandwidthService.isPullEnabled(local, remote));
    }

    @Test
    void testIsPushEnabled_returnsTrueWhenMatchingPushLinkExists() {
        stubLinks(link("corp", "store", NodeGroupLinkAction.P));
        assertTrue(bandwidthService.isPushEnabled(local, remote));
    }

    @Test
    void testIsPushEnabled_returnsFalseWhenActionIsRoutesOnly() {
        stubLinks(link("corp", "store", NodeGroupLinkAction.R));
        assertFalse(bandwidthService.isPushEnabled(local, remote));
    }

    private void stubDownloadPayloads(String csv) {
        when(parameterService.getString("console.node.connection.diagnostic.download.bandwidth.payloads", ""))
                .thenReturn(csv);
    }

    private void stubUploadPayloads(String csv) {
        when(parameterService.getString("console.node.connection.diagnostic.upload.bandwidth.payloads", ""))
                .thenReturn(csv);
    }

    @Test
    void testDiagnoseDownload_returnsEmptyListWhenNoPayloadsConfigured() {
        stubDownloadPayloads("");
        List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseDownloadBandwidth(local, remote);
        assertTrue(results.isEmpty());
    }

    @Test
    void testDiagnoseDownload_recordsKbpsForEachPayloadWhenPullEnabled() {
        stubDownloadPayloads("1000,2000");
        stubLinks(link("store", "corp", NodeGroupLinkAction.W));
        doReturn(512d).when(bandwidthService).getDownloadKbpsFor(remote, local, 1000L, 5000);
        doReturn(1024d).when(bandwidthService).getDownloadKbpsFor(remote, local, 2000L, 5000);
        List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseDownloadBandwidth(local, remote);
        assertEquals(2, results.size());
        assertEquals(1000L, results.get(0).getPayloadSize());
        assertEquals(512d, results.get(0).getKbps());
        assertFalse(results.get(0).isFailure());
        assertEquals(1024d, results.get(1).getKbps());
    }

    @Test
    void testDiagnoseDownload_marksFailureWhenPullDisabled() {
        stubDownloadPayloads("1000");
        when(configurationService.getNodeGroupLinks(false)).thenReturn(Collections.emptyList());
        List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseDownloadBandwidth(local, remote);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isFailure());
        assertEquals("Pull is not enabled", results.get(0).getFailureMessage());
    }

    @Test
    void testDiagnoseDownload_capturesExceptionWhenSpeedTestThrows() {
        stubDownloadPayloads("1000");
        stubLinks(link("store", "corp", NodeGroupLinkAction.W));
        doThrow(new RuntimeException("boom")).when(bandwidthService).getDownloadKbpsFor(remote, local, 1000L, 5000);
        List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseDownloadBandwidth(local, remote);
        assertTrue(results.get(0).isFailure());
        assertEquals(BandwidthService.Diagnostic_BandwidthFail, results.get(0).getFailureMessage());
        assertNotNull(results.get(0).getException());
    }

    @Test
    void testDiagnoseUpload_recordsKbpsWhenPushEnabled() throws Exception {
        stubUploadPayloads("1500");
        stubLinks(link("corp", "store", NodeGroupLinkAction.P));
        doReturn(768d).when(bandwidthService).getUploadKbpsFor(remote, local, 1500L, 5000);
        List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseUploadBandwidth(local, remote);
        assertEquals(1, results.size());
        assertEquals(768d, results.get(0).getKbps());
        assertFalse(results.get(0).isFailure());
    }

    @Test
    void testDiagnoseUpload_marksFailureWhenPushDisabled() {
        stubUploadPayloads("1500");
        when(configurationService.getNodeGroupLinks(false)).thenReturn(Collections.emptyList());
        List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseUploadBandwidth(local, remote);
        assertTrue(results.get(0).isFailure());
        assertEquals("Push is not enabled", results.get(0).getFailureMessage());
    }

    @Test
    void testDiagnoseUpload_capturesIOExceptionFromSpeedTest() throws Exception {
        stubUploadPayloads("1500");
        stubLinks(link("corp", "store", NodeGroupLinkAction.P));
        doThrow(new IOException("network down")).when(bandwidthService).getUploadKbpsFor(remote, local, 1500L, 5000);
        List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseUploadBandwidth(local, remote);
        assertTrue(results.get(0).isFailure());
        assertEquals(BandwidthService.Diagnostic_BandwidthFail, results.get(0).getFailureMessage());
        assertNotNull(results.get(0).getException());
    }
}
