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
package org.jumpmind.db.platform.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Time;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;

import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DuckDbDatabasePlatformTest {
    private DataSource dataSourceMock;
    private DuckDbDatabasePlatform platform;

    @BeforeEach
    void setUp() {
        dataSourceMock = mock(DataSource.class);
        platform = new DuckDbDatabasePlatform(dataSourceMock, new SqlTemplateSettings());
    }

    @Test
    void testGetDdlBuilder_returnsDuckDbDdlBuilder() {
        assertTrue(platform.getDdlBuilder() instanceof DuckDbDdlBuilder);
    }

    @Test
    void testGetDdlReader_returnsDuckDbDdlReader() {
        assertTrue(platform.getDdlReader() instanceof DuckDbDdlReader);
    }

    @Test
    void testCreateSqlTemplate_returnsDuckDbJdbcSqlTemplate() {
        assertTrue(platform.getSqlTemplate() instanceof DuckDbJdbcSqlTemplate);
    }

    @Test
    void testCreateSqlTemplateDirty_returnsReadUncommittedDuckDbJdbcSqlTemplate() {
        ISqlTemplate sqlTemplateDirty = platform.getSqlTemplateDirty();
        assertTrue(sqlTemplateDirty instanceof DuckDbJdbcSqlTemplate);
        assertEquals(Connection.TRANSACTION_READ_UNCOMMITTED, ((DuckDbJdbcSqlTemplate) sqlTemplateDirty).getIsolationLevel());
    }

    @Test
    void testGetName_returnsDuckDbConstant() {
        assertEquals(DatabaseNamesConstants.DUCKDB, platform.getName());
    }

    @Test
    void testGetClassName() {
        assertEquals(DuckDbDatabasePlatform.class.getName(), platform.getClassName());
    }

    @Test
    void testGetDefaultCatalog_queriesCurrentCatalogWhenNotCached() {
        DuckDbDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject("SELECT current_catalog()", String.class)).thenReturn("test");
        when(spyPlatform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        assertEquals("test", spyPlatform.getDefaultCatalog());
    }

    @Test
    void testGetDefaultSchema_queriesCurrentSchemaWhenNotCached() {
        DuckDbDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject("SELECT current_schema()", String.class)).thenReturn("analytics");
        when(spyPlatform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        assertEquals("analytics", spyPlatform.getDefaultSchema());
    }

    @Test
    void testGetDefaultSchema_cachesValueAfterFirstLookup() {
        DuckDbDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject("SELECT current_schema()", String.class)).thenReturn("analytics");
        when(spyPlatform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        spyPlatform.getDefaultSchema();
        spyPlatform.getDefaultSchema();
        verify(sqlTemplateMock).queryForObject("SELECT current_schema()", String.class);
    }

    @Test
    void testGetDefaultCatalog_cachesValueAfterFirstLookup() {
        DuckDbDatabasePlatform spyPlatform = spy(platform);
        ISqlTemplate sqlTemplateMock = mock(ISqlTemplate.class);
        when(sqlTemplateMock.queryForObject("SELECT current_catalog()", String.class)).thenReturn("test");
        when(spyPlatform.getSqlTemplate()).thenReturn(sqlTemplateMock);
        spyPlatform.getDefaultCatalog();
        spyPlatform.getDefaultCatalog();
        verify(sqlTemplateMock).queryForObject("SELECT current_catalog()", String.class);
    }

    @Test
    void testSupportsLimitOffset_returnsTrue() {
        assertTrue(platform.supportsLimitOffset());
    }

    @Test
    void testMassageForLimitOffset_appendsLimitAndOffset() {
        assertEquals("select * from item limit 10 offset 20", platform.massageForLimitOffset("select * from item", 10, 20));
    }

    @Test
    void testMassageForLimitOffset_stripsTrailingSemicolon() {
        assertEquals("select * from item limit 10 offset 20", platform.massageForLimitOffset("select * from item;", 10, 20));
    }

    @Test
    void testParseDateTimeWithDatePrefix() {
        assertEquals(Time.valueOf("13:46:56"), platform.parseDate(Types.TIME, "2026-10-29 13:46:56.496", false));
    }

    @Test
    void testParseDateTimeBlank() {
        assertNull(platform.parseDate(Types.TIME, " ", false));
    }

    @Test
    void testFormatTimeValueWithNull() {
        assertNull(platform.formatTimeValue(null));
    }

    @Test
    void testFormatTimeValueSimpleTime() {
        assertEquals("02:30:00", platform.formatTimeValue("02:30:00"));
    }

    @Test
    void testFormatTimeValueStripsMilliseconds() {
        assertEquals("18:00:00", platform.formatTimeValue("18:00:00.000"));
    }

    @Test
    void testFormatTimeValueExtractsTimeFromDatetime() {
        assertEquals("12:45:00", platform.formatTimeValue("1970-01-01 12:45:00.000"));
    }

    @Test
    void testFormatTimeValueDatePrefixNoMilliseconds() {
        assertEquals("15:30:00", platform.formatTimeValue("2000-06-15 15:30:00"));
    }

    @Test
    void testFormatTimeValueDecimalEdgeCases() {
        assertEquals("09:15:30", platform.formatTimeValue("09:15:30.1"));
        assertEquals("09:15:30", platform.formatTimeValue("09:15:30.12"));
        assertEquals("09:15:30", platform.formatTimeValue("09:15:30.123456789"));
    }

    @ParameterizedTest
    @CsvSource({
            "02:30:00, 02:30:00",
            "18:00:00.000, 18:00:00",
            "1970-01-01 12:45:00.000, 12:45:00",
            "23:59:59, 23:59:59",
            "00:00:00, 00:00:00",
            "12:00:00.123, 12:00:00",
            "2023-12-25 08:30:45.999, 08:30:45",
            "1999-01-01 00:00:00.000, 00:00:00"
    })
    void testFormatTimeValueParameterized(String input, String expected) {
        assertEquals(expected, platform.formatTimeValue(input));
    }
}
