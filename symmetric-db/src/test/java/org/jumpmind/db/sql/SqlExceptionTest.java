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
		
		assertEquals(1234, outer.getErrorCode());
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
		
		assertEquals("08001", outer.getSQLState());
	}
	
	@Test
	void getSQLStateReturnsNullWhenRootIsNotSqlException() {
		Throwable notSql = new RuntimeException("not sql exception");
		SqlException outer = new SqlException("outer", notSql);
		
		assertEquals(null, outer.getSQLState());
	}
}
