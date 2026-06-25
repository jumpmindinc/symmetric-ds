package org.jumpmind.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExceptionUtilsTest {

	@Test
	void getRootMessageReturnsMessageWhenNoCause() {
	    Throwable ex = new RuntimeException("boom");

	    assertEquals("boom", ExceptionUtils.getRootMessage(ex));
	}

	@Test
	void getRootMessageReturnsDeepestCauseMessage() {
	    Throwable ex = new RuntimeException("outer", new IllegalStateException("inner"));

	    assertEquals("inner", ExceptionUtils.getRootMessage(ex));
	}
	@Test
	void unwrapMessagesJoinsTheChain() {
	    Throwable ex = new RuntimeException("outer", new IllegalStateException("inner"));

	    String result = ExceptionUtils.unwrapMessages(ex);

	    assertEquals("RuntimeException: outer\nIllegalStateException: inner", result);
	}

	@Test
	void unwrapMessagesOmitsBlankMessage() {
	    Throwable ex = new RuntimeException();   // no message -> getMessage() is null

	    assertEquals("RuntimeException", ExceptionUtils.unwrapMessages(ex));
	}
	@Test
	void isReturnsTrueWhenTopLevelMatches() {
	    Throwable ex = new IllegalStateException("x");

	    assertTrue(ExceptionUtils.is(ex, IllegalStateException.class));
	}

	@Test
	void isReturnsTrueWhenRootCauseMatches() {
	    Throwable ex = new RuntimeException("outer", new IllegalArgumentException("inner"));

	    assertTrue(ExceptionUtils.is(ex, IllegalArgumentException.class));   // matches the ROOT
	}

	@Test
	void isReturnsFalseWhenNoMatch() {
	    Throwable ex = new RuntimeException("x");

	    assertFalse(ExceptionUtils.is(ex, IllegalStateException.class));
	}

	@Test
	void isReturnsFalseForNullException() {
	    assertFalse(ExceptionUtils.is(null, RuntimeException.class));
	}

}
