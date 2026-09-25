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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractJavaDriverSqlTemplateTest {
    private TestableAbstractJavaDriverSqlTemplate template;

    @BeforeEach
    void setUp() {
        template = new TestableAbstractJavaDriverSqlTemplate();
    }

    @Test
    void testGetDatabaseProductName_returnsSubclassValue() {
        assertEquals("TestDB", template.getDatabaseProductName());
    }

    @Test
    void testQueryForBlob_returnsNull() {
        assertNull(template.queryForBlob("select blob_col", 0, "BLOB"));
    }

    @Test
    void testQueryForClob_returnsNull() {
        assertNull(template.queryForClob("select clob_col", 0, "CLOB"));
    }

    @Test
    void testQueryForObject_withClass_returnsNull() {
        assertNull(template.queryForObject("select 1", String.class));
    }

    @Test
    void testQueryForMap_withParams_returnsNull() {
        assertNull(template.queryForMap("select 1"));
    }

    @Test
    void testQueryForCursor_withParamsAndTypes_returnsNull() {
        assertNull(template.queryForCursor("select 1", row -> row, null, null));
    }

    @Test
    void testQueryForCursor_withReturnLobObjects_returnsNull() {
        assertNull(template.queryForCursor("select 1", row -> row, true));
    }

    @Test
    void testUpdate_withListenerAndSqlArray_returnsZero() {
        assertEquals(0, template.update(true, true, 0, (ISqlResultsListener) null, "update foo"));
    }

    @Test
    void testUpdate_withListenerAndStatementSource_returnsZero() {
        assertEquals(0, template.update(true, true, false, false, 0, null, (ISqlStatementSource) null));
    }

    @Test
    void testUpdate_withCommitRateAndSqlArray_returnsZero() {
        assertEquals(0, template.update(true, true, 0, "update foo"));
    }

    @Test
    void testUpdate_withValuesAndTypes_returnsZero() {
        assertEquals(0, template.update("update foo set bar = ?", new Object[] { 1 }, new int[] { 4 }));
    }

    @Test
    void testTestConnection_doesNotThrow() {
        assertDoesNotThrow(template::testConnection);
    }

    @Test
    void testIsUniqueKeyViolation_returnsFalse() {
        assertFalse(template.isUniqueKeyViolation(new RuntimeException("boom")));
    }

    @Test
    void testIsDataTruncationViolation_returnsFalse() {
        assertFalse(template.isDataTruncationViolation(new RuntimeException("boom")));
    }

    @Test
    void testIsForeignKeyViolation_returnsFalse() {
        assertFalse(template.isForeignKeyViolation(new RuntimeException("boom")));
    }

    @Test
    void testStartSqlTransaction_returnsNull() {
        assertNull(template.startSqlTransaction());
    }

    @Test
    void testStartSqlTransaction_withAutoCommit_returnsNull() {
        assertNull(template.startSqlTransaction(true));
    }

    @Test
    void testGetDatabaseMajorVersion_returnsZero() {
        assertEquals(0, template.getDatabaseMajorVersion());
    }

    @Test
    void testGetDatabaseMinorVersion_returnsZero() {
        assertEquals(0, template.getDatabaseMinorVersion());
    }

    @Test
    void testGetDatabaseProductVersion_returnsNull() {
        assertNull(template.getDatabaseProductVersion());
    }

    @Test
    void testGetDriverName_returnsNull() {
        assertNull(template.getDriverName());
    }

    @Test
    void testGetDriverVersion_returnsNull() {
        assertNull(template.getDriverVersion());
    }

    @Test
    void testGetSqlKeywords_returnsNull() {
        assertNull(template.getSqlKeywords());
    }

    @Test
    void testSupportsGetGeneratedKeys_returnsFalse() {
        assertFalse(template.supportsGetGeneratedKeys());
    }

    @Test
    void testIsStoresUpperCaseIdentifiers_returnsFalse() {
        assertFalse(template.isStoresUpperCaseIdentifiers());
    }

    @Test
    void testIsStoresLowerCaseIdentifiers_returnsFalse() {
        assertFalse(template.isStoresLowerCaseIdentifiers());
    }

    @Test
    void testIsStoresMixedCaseQuotedIdentifiers_returnsFalse() {
        assertFalse(template.isStoresMixedCaseQuotedIdentifiers());
    }

    @Test
    void testInsertWithGeneratedKey_returnsZero() {
        assertEquals(0L, template.insertWithGeneratedKey("insert into foo values (?)", "id", "seq",
                new Object[] { 1 }, new int[] { 4 }));
    }

    @Test
    void testIsForeignKeyChildExistsViolation_returnsFalse() {
        assertFalse(template.isForeignKeyChildExistsViolation(new RuntimeException("boom")));
    }

    private static class TestableAbstractJavaDriverSqlTemplate extends AbstractJavaDriverSqlTemplate {
        @Override
        public String getDatabaseProductName() {
            return "TestDB";
        }
    }
}
