package org.jumpmind.symmetric.job;

import static org.mockito.Mockito.*;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DefaultOfflineServerListenerTest {

	private IStatisticManager statisticManager;
	private INodeService nodeService;
	private IOutgoingBatchService outgoingBatchService;
	private DefaultOfflineServerListener listener;
	
	@BeforeEach 
	void setUp() {
		statisticManager = mock(IStatisticManager.class);
		nodeService = mock(INodeService.class);
		outgoingBatchService = mock(IOutgoingBatchService.class);
		
		listener = new DefaultOfflineServerListener(
				statisticManager, nodeService, outgoingBatchService);
	}
	
	@Test
	void clientNodeOfflineDisablesAndCleansUpNode() {
		
		//Arrange - a real node data object with an id
		Node node = new Node();
		node.setNodeId("100");
		
		//Act - run the method under test
		listener.clientNodeOffline(node);
		
		//Assert - verify it made the right calls on each dependency
		verify(statisticManager).incrementNodesDisabled(1);
		verify(nodeService).save(node);
		verify(outgoingBatchService).markAllAsSentForNode("100", true);
		verify(nodeService).deleteNodeSecurity("100");
		verify(nodeService).deleteNodeHost("100");
		
		//Assert - the side effect on the real node object
		assertFalse(node.isSyncEnabled());
		
	}

}
