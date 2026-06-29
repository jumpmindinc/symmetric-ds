package org.jumpmind.symmetric.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BatchIdTest {

	@Test
	void toStringFormatsAsNodeDashBatch() {
		BatchId b = new BatchId(123, "node1");

		assertEquals("node1-123", b.toString());
	}

	@Test
	void constructorSetsFields() {
		BatchId b = new BatchId(123, "node1");

		assertEquals(123, b.getBatchId());
		assertEquals("node1", b.getNodeId());
	}

	@Test
	void settersUpdateFields() {
		BatchId b = new BatchId();

		b.setBatchId(7);
		b.setNodeId("node2");

		assertEquals(7, b.getBatchId());
		assertEquals("node2", b.getNodeId());
	}

	@Test
	void equalsTrueForSameInstance() {
		BatchId b = new BatchId(1, "b");
		assertTrue(b.equals(b));
	}

	@Test
	void equalsTrueForSameValues() {
		BatchId b = new BatchId(1, "b");
		BatchId a = new BatchId(1, "b");
		assertTrue(a.equals(b));
	}

	@Test
	void equalsFalseForNull() {
		BatchId b = new BatchId(1, "b");
		assertFalse(b.equals(null));
	}

	@Test
	void equalsFalseForDifferentType() {
		assertFalse(new BatchId(1, "a").equals("not a BatchId"));
	}

	@Test
	void equalsFalseForDifferentBatchId() {
		BatchId b = new BatchId(1, "b");
		BatchId a = new BatchId(2, "b");
		assertFalse(b.equals(a));
	}

	@Test
	void equalsFalseForDifferentNodeId() {
		BatchId b = new BatchId(1, "b");
		BatchId a = new BatchId(1, "a");
		assertFalse(b.equals(a));
	}

	@Test
	void equalsTrueWhenBothNodeIdsNull() {
		BatchId b = new BatchId(1, null);
		BatchId a = new BatchId(1, null);
		assertTrue(b.equals(a));
	}

	@Test
	void equalsFalseWhenThisNodeIdsNull() {
		BatchId b = new BatchId(1, null);
		BatchId a = new BatchId(1, "a");
		assertFalse(b.equals(a));
	}

	@Test
	void equalObjectsHaveEqualHashCodes() {
		BatchId b = new BatchId(1, "b");
		BatchId a = new BatchId(1, "b");

		assertEquals(a.hashCode(), b.hashCode());
	}

}
