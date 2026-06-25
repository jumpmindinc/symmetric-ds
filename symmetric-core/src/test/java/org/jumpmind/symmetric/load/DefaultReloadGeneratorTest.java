package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

class DefaultReloadGeneratorTest {
	
	private ISymmetricEngine engine;
	private ITriggerRouterService triggerRouterService;
	private DefaultReloadGenerator generator;
	
	@BeforeEach
	void setUp() {
		engine = mock(ISymmetricEngine.class);
		triggerRouterService = mock(ITriggerRouterService.class);
		
		//engine hands back the service mock
		when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
		
		generator = new DefaultReloadGenerator(engine);
		
		
	}
	

	@Test
	void returnsTriggerHistoriesFromService() {
		
		
		//the service returns the mock list
		List<TriggerHistory> expected = new ArrayList<>();
		expected.add(new TriggerHistory());
		when(triggerRouterService.getActiveTriggerHistories()).thenReturn(expected);
		
		//targetNode isn't used by the method, so null is fine
		List<TriggerHistory> result = generator.getActiveTriggerHistories(null);
		
		//Assert the exact list from the service came back
		assertSame(expected, result);
		
	}

}
