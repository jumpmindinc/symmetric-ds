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
package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SqlUtilsTest {
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(SqlUtils.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Exception> getOwnerMap(String fieldName) throws ReflectiveOperationException {
        Field field = SqlUtils.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<Object, Exception>) field.get(null);
    }

    @Test
    void testAddSqlTransaction_addsToOpenTransactions() {
        ISqlTransaction txMock = mock(ISqlTransaction.class);
        SqlUtils.addSqlTransaction(txMock);
        try {
            assertTrue(SqlUtils.getOpenTransactions().contains(txMock));
        } finally {
            SqlUtils.removeSqlTransaction(txMock);
        }
    }

    @Test
    void testAddSqlTransaction_whenCaptureOwnerTrue_capturesOwnerException() throws ReflectiveOperationException {
        SqlUtils.setCaptureOwner(true);
        ISqlTransaction txMock = mock(ISqlTransaction.class);
        try {
            SqlUtils.addSqlTransaction(txMock);
            assertTrue(getOwnerMap("sqlTransactionsOwnerMap").containsKey(txMock));
        } finally {
            SqlUtils.removeSqlTransaction(txMock);
            SqlUtils.setCaptureOwner(false);
        }
    }

    @Test
    void testAddSqlReadCursor_addsToOpenSqlReadCursors() {
        ISqlReadCursor<?> cursorMock = mock(ISqlReadCursor.class);
        SqlUtils.addSqlReadCursor(cursorMock);
        try {
            assertTrue(SqlUtils.getOpenSqlReadCursors().contains(cursorMock));
        } finally {
            SqlUtils.removeSqlReadCursor(cursorMock);
        }
    }

    @Test
    void testAddSqlReadCursor_whenCaptureOwnerTrue_capturesOwnerException() throws ReflectiveOperationException {
        SqlUtils.setCaptureOwner(true);
        ISqlReadCursor<?> cursorMock = mock(ISqlReadCursor.class);
        try {
            SqlUtils.addSqlReadCursor(cursorMock);
            assertTrue(getOwnerMap("sqlReadCursorsOwnerMap").containsKey(cursorMock));
        } finally {
            SqlUtils.removeSqlReadCursor(cursorMock);
            SqlUtils.setCaptureOwner(false);
        }
    }

    @Test
    void testRemoveSqlReadCursor_removesFromOpenSqlReadCursors() {
        ISqlReadCursor<?> cursorMock = mock(ISqlReadCursor.class);
        SqlUtils.addSqlReadCursor(cursorMock);
        SqlUtils.removeSqlReadCursor(cursorMock);
        assertFalse(SqlUtils.getOpenSqlReadCursors().contains(cursorMock));
    }

    @Test
    void testRemoveSqlReadCursor_whenCaptureOwnerTrue_removesOwnerException() throws ReflectiveOperationException {
        SqlUtils.setCaptureOwner(true);
        ISqlReadCursor<?> cursorMock = mock(ISqlReadCursor.class);
        try {
            SqlUtils.addSqlReadCursor(cursorMock);
            SqlUtils.removeSqlReadCursor(cursorMock);
            assertFalse(getOwnerMap("sqlReadCursorsOwnerMap").containsKey(cursorMock));
        } finally {
            SqlUtils.setCaptureOwner(false);
        }
    }

    @Test
    void testRemoveSqlTransaction_removesFromOpenTransactions() {
        ISqlTransaction txMock = mock(ISqlTransaction.class);
        SqlUtils.addSqlTransaction(txMock);
        SqlUtils.removeSqlTransaction(txMock);
        assertFalse(SqlUtils.getOpenTransactions().contains(txMock));
    }

    @Test
    void testRemoveSqlTransaction_whenCaptureOwnerTrue_removesOwnerException() throws ReflectiveOperationException {
        SqlUtils.setCaptureOwner(true);
        ISqlTransaction txMock = mock(ISqlTransaction.class);
        try {
            SqlUtils.addSqlTransaction(txMock);
            SqlUtils.removeSqlTransaction(txMock);
            assertFalse(getOwnerMap("sqlTransactionsOwnerMap").containsKey(txMock));
        } finally {
            SqlUtils.setCaptureOwner(false);
        }
    }

    @Test
    void testGetOpenTransactions_returnsDefensiveCopy() {
        List<ISqlTransaction> result = SqlUtils.getOpenTransactions();
        ISqlTransaction txMock = mock(ISqlTransaction.class);
        result.add(txMock);
        assertFalse(SqlUtils.getOpenTransactions().contains(txMock));
    }

    @Test
    void testGetOpenSqlReadCursors_returnsDefensiveCopy() {
        List<ISqlReadCursor<?>> result = SqlUtils.getOpenSqlReadCursors();
        ISqlReadCursor<?> cursorMock = mock(ISqlReadCursor.class);
        result.add(cursorMock);
        assertFalse(SqlUtils.getOpenSqlReadCursors().contains(cursorMock));
    }

    @Test
    void testLogOpenResources_whenCaptureOwnerTrue_logsTransactionOwnerStack() {
        SqlUtils.setCaptureOwner(true);
        ISqlTransaction txMock = mock(ISqlTransaction.class);
        SqlUtils.addSqlTransaction(txMock);
        try {
            SqlUtils.logOpenResources();
            assertTrue(appender.list.stream().anyMatch(event -> "The following stack contains the owner of an open database transaction"
                    .equals(event.getFormattedMessage())));
        } finally {
            SqlUtils.removeSqlTransaction(txMock);
            SqlUtils.setCaptureOwner(false);
        }
    }

    @Test
    void testLogOpenResources_whenCaptureOwnerTrue_logsReadCursorOwnerStack() {
        SqlUtils.setCaptureOwner(true);
        ISqlReadCursor<?> cursorMock = mock(ISqlReadCursor.class);
        SqlUtils.addSqlReadCursor(cursorMock);
        try {
            SqlUtils.logOpenResources();
            assertTrue(appender.list.stream().anyMatch(
                    event -> "The following stack contains the owner of an open read cursor".equals(event.getFormattedMessage())));
        } finally {
            SqlUtils.removeSqlReadCursor(cursorMock);
            SqlUtils.setCaptureOwner(false);
        }
    }

    @Test
    void testLogOpenResources_whenCaptureOwnerFalse_logsNothing() {
        ISqlTransaction txMock = mock(ISqlTransaction.class);
        SqlUtils.addSqlTransaction(txMock);
        try {
            SqlUtils.logOpenResources();
            assertTrue(appender.list.isEmpty());
        } finally {
            SqlUtils.removeSqlTransaction(txMock);
        }
    }

    @Test
    void escapeStringDoublesSingleQuotes() {
        assertEquals("O''Brien", SqlUtils.escapeString("O'Brien"));
    }

    @Test
    void escapeStringLeavesStringWithoutQuotesUnchanged() {
        assertEquals("hello", SqlUtils.escapeString("hello"));
    }

    @Test
    void escapeStringReturnsNullForNull() {
        assertNull(SqlUtils.escapeString(null));
    }

    @Test
    void sanitizeIdentifierRemovesDangerousChars() {
        assertEquals("abcde", SqlUtils.sanitizeIdentifier("a\"b'c/d;e"));
    }

    @Test
    void sanitizeIdentifierKeepsSpaces() {
        assertEquals("my table", SqlUtils.sanitizeIdentifier("my table")); // space stays
    }

    @Test
    void sanitizeIdentifierReturnsNullForNull() {
        assertNull(SqlUtils.sanitizeIdentifier(null));
    }

    @Test
    void sanitizeFunctionRemovesDangerousCharsAndSpaces() {
        assertEquals("mytable", SqlUtils.sanitizeFunction("my\"ta'b/le;"));
        assertEquals("ab", SqlUtils.sanitizeFunction("a b")); // space removed
    }

    @Test
    void sanitizeFunctionReturnsNullForNull() {
        assertNull(SqlUtils.sanitizeFunction(null));
    }

    @Test
    void testSanitizeFunction_truncatesToMaxLength() {
        String longName = "a".repeat(256);
        assertEquals(255, SqlUtils.sanitizeFunction(longName).length());
    }

    @Test
    void sanitizeIdentifierTruncatesToMaxLength() {
        String longName = "a".repeat(256); // 256 safe chars (none get stripped)
        assertEquals(255, SqlUtils.sanitizeIdentifier(longName).length());
    }

    @Test
    void sanitizeTablePrefixReplacesNonWordCharsWithUnderscore() {
        assertEquals("a_b", SqlUtils.sanitizeTablePrefix("a!b"));
    }

    @Test
    void sanitizeTablePrefixCollapsesConsecutiveUnderscores() {
        assertEquals("a_b", SqlUtils.sanitizeTablePrefix("a!!b")); // !! -> __ -> _
    }

    @Test
    void sanitizeTablePrefixLeavesCleanNameUnchanged() {
        assertEquals("sym", SqlUtils.sanitizeTablePrefix("sym"));
    }

    @Test
    void sanitizeTablePrefixReturnsNullForNull() {
        assertNull(SqlUtils.sanitizeTablePrefix(null));
    }

    @Test
    void testSanitizeTablePrefix_truncatesToMaxLength() {
        String longName = "a".repeat(40);
        assertEquals(32, SqlUtils.sanitizeTablePrefix(longName).length());
    }
}
