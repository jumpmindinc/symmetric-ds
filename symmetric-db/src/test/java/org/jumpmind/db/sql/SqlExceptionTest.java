package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

class SqlExceptionTest {

	@Test
	void getRootCauseReturnsItselfWithNoCause() {
		SqlException ex = new SqlException("boom");
		
		assertSame(ex, ex.getRootCause());
	}
	
	@Test
	void getRootCauseReturnsSingleCause() {
		Throwable inner = new RuntimeException("inner");
		SqlException ex = new SqlException("outer", inner);
		
		assertSame(inner, ex.getRootCause());
	}
	
	@Test
	void getRootCauseReturnsDeepestInChain() {
		Throwable inner = new RuntimeException("inner");
		Throwable middle = new RuntimeException("middle", inner);
		SqlException ex = new SqlException("outer", middle);
		
		assertSame(inner, ex.getRootCause());
	}
	
	@Test
	void getRootMessageReturnsDeepestMessage () {
		Throwable inner = new RuntimeException("boom");
		SqlException ex = new SqlException("outer", inner);
		
		assertEquals("boom", ex.getRootMessage());
	}
	
	@Test
	void getRootMessageReturnsOwnMessageWhenNoCause () {
		SqlException ex = new SqlException("boom");
		
		assertEquals("boom", ex.getRootMessage());
	}
	
	@Test
	void getErrorCodeReturnsCodeWhenRootIsSqlException () {
		SQLException inner = new SQLException("db error", "08001", 1234);
		SqlException outer = new SqlException("outer", inner);
		
		assertEquals(outer.getErrorCode(), 1234);
	}

	@Test
	void getErrorCodeReturnsMinusOneWhenRootIsNotSqlException () {
		Throwable notSql = new RuntimeException("not sql exception");
		SqlException outer = new SqlException("outer", notSql);
		
		assertEquals(outer.getErrorCode(), -1);
	}
	@Test
	void getErrorCodeReturnsMinusOneWhenNoCause () {
		SqlException outer = new SqlException("outer"); 
		
		assertEquals(outer.getErrorCode(), -1);
	}
	
	@Test
	void getSQLStateReturnsStateWhenRootIsSqlException() {
		SQLException inner = new SQLException("db error", "08001", 1234);
		SqlException outer = new SqlException("outer", inner);
		
		assertEquals(outer.getSQLState(), "08001");
	}
	
	@Test
	void getSQLStateReturnsNullWhenRootIsNotSqlException() {
		Throwable notSql = new RuntimeException("not sql exception");
		SqlException outer = new SqlException("outer", notSql);
		
		assertEquals(outer.getSQLState(), null);
	}
}
