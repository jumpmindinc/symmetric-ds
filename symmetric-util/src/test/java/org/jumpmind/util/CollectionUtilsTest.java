package org.jumpmind.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CollectionUtilsTest {

	@Test
	void testToMapStringArrayTArray() {
		
		//Arrange the values needed by the method toMap
		String[] keys = {"a", "b"};
		String[] values = {"1", "2"};
		
		//Act - calling the method under test to capture what it returns
		Map<String, String> result = CollectionUtils.toMap(keys, values);
		
		//Assert - state what is expected to be true
		assertEquals(2, result.size()); //2 pairs went in
		assertEquals("1", result.get("a")); //key "a" maps to "1"
		assertEquals("2", result.get("b")); //key "b" maps to "2"Why
		
	}
	
	@Test
	void toMapReturnsEmptyWhenValuesShorterThanKeys () {
		
		String[] keys = {"a", "b", "c"}; //3 keys
		String[] values = {"1"};//only 1 value
		
		Map<String, String> result = CollectionUtils.toMap(keys, values);
		
		//Assert that the else branch ran since values > keys
		assertTrue(result.isEmpty());
		
	}
	
	@Test
	void toMapReturnsEmptyWhenKeysAreNull () {
		
		String[] values = {"1", "2"};//Null string array for keys
		
		Map<String, String> result = CollectionUtils.toMap(null, values);

		assertTrue(result.isEmpty());
		
	}

	@Test
	void testToCommaSeparatedValues() {
		
		List<String> list = Arrays.asList("a", "b", "c");
		
		String result = CollectionUtils.toCommaSeparatedValues(list);
		
		assertEquals("a,b,c", result);
		
	}
	
	@Test
	void testToCommaSeparatedValuesReturnsEmptyForNullList() {
		
		
		String result = CollectionUtils.toCommaSeparatedValues(null);
		
		assertEquals("", result);
		
	}
	
	@Test
	void testToCommaSeparatedValuesHandlesNullElement() {
		
		List<String> list = Arrays.asList("a", null , "c");
		
		String result = CollectionUtils.toCommaSeparatedValues(list);
		
		assertEquals("a,,c", result);
		
	}

}
