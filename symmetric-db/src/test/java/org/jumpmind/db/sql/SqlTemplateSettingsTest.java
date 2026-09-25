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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;

import org.jumpmind.db.sql.SqlTemplateSettings.JdbcLobHandling;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;

class SqlTemplateSettingsTest {
    @Test
    void testConstructor_defaults() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        assertEquals(1000, settings.getFetchSize());
        assertEquals(0, settings.getQueryTimeout());
        assertEquals(100, settings.getBatchSize());
        assertFalse(settings.isReadStringsAsBytes());
        assertFalse(settings.isTreatBinaryAsLob());
        assertEquals(-1, settings.getOverrideIsolationLevel());
        assertNull(settings.getLogSqlBuilder());
        assertEquals(ResultSet.TYPE_FORWARD_ONLY, settings.getResultSetType());
        assertFalse(settings.isRightTrimCharValues());
        assertFalse(settings.isAllowUpdatesWithResults());
        assertEquals(25, settings.getBatchBulkLoaderSize());
        assertFalse(settings.isAllowTriggerCreateOrReplace());
        assertEquals(JdbcLobHandling.PLAIN, settings.getJdbcLobHandling());
        assertNotNull(settings.getProperties());
        assertFalse(settings.isIncludeRowIdentifierAsColumn());
    }

    @Test
    void testFetchSize_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setFetchSize(500);
        assertEquals(500, settings.getFetchSize());
    }

    @Test
    void testQueryTimeout_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setQueryTimeout(30);
        assertEquals(30, settings.getQueryTimeout());
    }

    @Test
    void testBatchSize_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setBatchSize(200);
        assertEquals(200, settings.getBatchSize());
    }

    @Test
    void testReadStringsAsBytes_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setReadStringsAsBytes(true);
        assertTrue(settings.isReadStringsAsBytes());
    }

    @Test
    void testTreatBinaryAsLob_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setTreatBinaryAsLob(true);
        assertTrue(settings.isTreatBinaryAsLob());
    }

    @Test
    void testOverrideIsolationLevel_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setOverrideIsolationLevel(2);
        assertEquals(2, settings.getOverrideIsolationLevel());
    }

    @Test
    void testLogSqlBuilder_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        LogSqlBuilder logSqlBuilder = new LogSqlBuilder();
        settings.setLogSqlBuilder(logSqlBuilder);
        assertSame(logSqlBuilder, settings.getLogSqlBuilder());
    }

    @Test
    void testResultSetType_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE);
        assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE, settings.getResultSetType());
    }

    @Test
    void testRightTrimCharValues_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setRightTrimCharValues(true);
        assertTrue(settings.isRightTrimCharValues());
    }

    @Test
    void testAllowUpdatesWithResults_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setAllowUpdatesWithResults(true);
        assertTrue(settings.isAllowUpdatesWithResults());
    }

    @Test
    void testBatchBulkLoaderSize_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setBatchBulkLoaderSize(50);
        assertEquals(50, settings.getBatchBulkLoaderSize());
    }

    @Test
    void testAllowTriggerCreateOrReplace_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setAllowTriggerCreateOrReplace(true);
        assertTrue(settings.isAllowTriggerCreateOrReplace());
    }

    @Test
    void testJdbcLobHandling_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setJdbcLobHandling(JdbcLobHandling.STREAMLOB);
        assertEquals(JdbcLobHandling.STREAMLOB, settings.getJdbcLobHandling());
    }

    @Test
    void testProperties_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        TypedProperties properties = new TypedProperties();
        properties.setProperty("foo", "bar");
        settings.setProperties(properties);
        assertSame(properties, settings.getProperties());
    }

    @Test
    void testIncludeRowIdentifierAsColumn_setAndGet() {
        SqlTemplateSettings settings = new SqlTemplateSettings();
        settings.setIncludeRowIdentifierAsColumn(true);
        assertTrue(settings.isIncludeRowIdentifierAsColumn());
    }
}
