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
	void isPullEnabled_returnsTrueWhenMatchingPullLinkExists() {

		stubLinks(link("store", "corp", NodeGroupLinkAction.W));
		assertTrue(bandwidthService.isPullEnabled(local, remote));
	}

	@Test
	void isPullEnabled_returnsFalseWhenActionIsNotPull() {
		stubLinks(link("store", "corp", NodeGroupLinkAction.P));
		assertFalse(bandwidthService.isPullEnabled(local, remote));
	}

	@Test
	void isPullEnabled_returnsFalseWhenGroupsDoNotMatch() {
		stubLinks(link("corp", "store", NodeGroupLinkAction.W));
		assertFalse(bandwidthService.isPullEnabled(local, remote));
	}

	@Test
	void isPullEnabled_returnsFalseWhenNoLinks() {
		when(configurationService.getNodeGroupLinks(false)).thenReturn(Collections.emptyList());
		assertFalse(bandwidthService.isPullEnabled(local, remote));
	}

	@Test
	void isPushEnabled_returnsTrueWhenMatchingPushLinkExists() {
		stubLinks(link("corp", "store", NodeGroupLinkAction.P));
		assertTrue(bandwidthService.isPushEnabled(local, remote));

	}

	@Test
	void isPushEnabled_returnsFalseWhenActionIsRoutesOnly() {
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
	void diagnoseDownload_returnsEmptyListWhenNoPayloadsConfigured() {
		stubDownloadPayloads("");
		List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseDownloadBandwidth(local, remote);
		assertTrue(results.isEmpty());

	}

	@Test
	void diagnoseDownload_recordsKbpsForEachPayloadWhenPullEnabled() throws Exception {
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
	void diagnoseDownload_marksFailureWhenPullDisabled() {
		stubDownloadPayloads("1000");

		when(configurationService.getNodeGroupLinks(false)).thenReturn(Collections.emptyList());

		List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseDownloadBandwidth(local, remote);

		assertEquals(1, results.size());
		assertTrue(results.get(0).isFailure());
		assertEquals("Pull is not enabled", results.get(0).getFailureMessage());

	}

	@Test
	void capturesExceptionWhenSpeedTestThrows() throws Exception {
		stubDownloadPayloads("1000");
		stubLinks(link("store", "corp", NodeGroupLinkAction.W));

		doThrow(new RuntimeException("boom")).when(bandwidthService).getDownloadKbpsFor(remote, local, 1000L, 5000);

		List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseDownloadBandwidth(local, remote);

		assertTrue(results.get(0).isFailure());
		assertEquals(BandwidthService.Diagnostic_BandwidthFail, results.get(0).getFailureMessage());
		assertNotNull(results.get(0).getException());
	}

	@Test
	void diagnoseUpload_recordsKbpsWhenPushEnabled() throws Exception {
		stubUploadPayloads("1500");
		stubLinks(link("corp", "store", NodeGroupLinkAction.P));
		doReturn(768d).when(bandwidthService).getUploadKbpsFor(remote, local, 1500L, 5000);

		List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseUploadBandwidth(local, remote);

		assertEquals(1, results.size());
		assertEquals(768d, results.get(0).getKbps());
		assertFalse(results.get(0).isFailure());
	}

	@Test
	void diagnoseUpload_marksFailureWhenPushDisabled() {
		stubUploadPayloads("1500");
		when(configurationService.getNodeGroupLinks(false)).thenReturn(Collections.emptyList());

		List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseUploadBandwidth(local, remote);

		assertTrue(results.get(0).isFailure());
		assertEquals("Push is not enabled", results.get(0).getFailureMessage());
	}

	@Test
	void diagnoseUpload_capturesIOExceptionFromSpeedTest() throws Exception {
		stubUploadPayloads("1500");
		stubLinks(link("corp", "store", NodeGroupLinkAction.P));
		doThrow(new IOException("network down")).when(bandwidthService).getUploadKbpsFor(remote, local, 1500L, 5000);

		List<BandwidthService.BandwidthResults> results = bandwidthService.diagnoseUploadBandwidth(local, remote);

		assertTrue(results.get(0).isFailure());
		assertEquals(BandwidthService.Diagnostic_BandwidthFail, results.get(0).getFailureMessage());
		assertNotNull(results.get(0).getException());
	}
}
