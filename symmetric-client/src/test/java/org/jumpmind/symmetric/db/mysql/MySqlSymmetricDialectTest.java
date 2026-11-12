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
package org.jumpmind.symmetric.db.mysql;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.mysql.MySqlDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
<<<<<<< HEAD
import org.jumpmind.db.util.BasicDataSourcePropertyConstants;
=======
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.SequenceIdentifier;
>>>>>>> 6f58903f22 (SYM-8109: MySQL findMaxDataId() returns 0 when lower_case_table_names=0 because the auto_increment lookup uses mixed-CASE table name sym_DATA (#1294))
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MySqlSymmetricDialectTest {
    @Test
    void doesTriggerExistOnPlatform_skipsCaseInsensitiveFallback_whenExactMatchFound() {
        IDatabasePlatform platform = createPlatform("8.0.30");
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        when(platform.isMetadataIgnoreCase()).thenReturn(true);
        when(sqlTemplate.queryForInt(contains("event_object_table = ?"), any(Object[].class))).thenReturn(1);
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(createParameterService(), platform);
        boolean exists = dialect.doesTriggerExistOnPlatform(null, "SymmetricRoot", null, "test_all_caps", "SYM_ON_I_FOR_8000_TSTRTGRP");
        assertTrue(exists);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(sqlTemplate, times(1)).queryForInt(sqlCaptor.capture(), argsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("trigger_name = ? and event_object_table = ?"));
        assertArrayEquals(new Object[] { "SYM_ON_I_FOR_8000_TSTRTGRP", "test_all_caps" }, argsCaptor.getValue());
    }

    @Test
    void doesTriggerExistOnPlatform_fallsBackToCaseInsensitiveMatch_whenExactMatchMissesAndMetadataIgnoresCase() {
        IDatabasePlatform platform = createPlatform("8.0.30");
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        when(platform.isMetadataIgnoreCase()).thenReturn(true);
        when(sqlTemplate.queryForInt(contains("event_object_table = ?"), any(Object[].class))).thenReturn(0);
        when(sqlTemplate.queryForInt(contains("lower(event_object_table) = lower(?)"), any(Object[].class))).thenReturn(1);
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(createParameterService(), platform);
        boolean exists = dialect.doesTriggerExistOnPlatform(null, "SymmetricRoot", null, "TEST_ALL_CAPS", "SYM_ON_I_FOR_8000_TSTRTGRP");
        assertTrue(exists);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(sqlTemplate, times(2)).queryForInt(sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getAllValues().get(0).contains("trigger_name = ? and event_object_table = ?"));
        assertTrue(sqlCaptor.getAllValues().get(1).contains("trigger_name = ? and lower(event_object_table) = lower(?)"));
    }

    @Test
    void doesTriggerExistOnPlatform_skipsCaseInsensitiveFallback_whenMetadataIsCaseSensitive() {
        IDatabasePlatform platform = createPlatform("8.0.30");
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        when(platform.isMetadataIgnoreCase()).thenReturn(false);
        when(sqlTemplate.queryForInt(anyString(), any(Object[].class))).thenReturn(0);
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(createParameterService(), platform);
        boolean exists = dialect.doesTriggerExistOnPlatform(null, "SymmetricRoot", null, "TEST_ALL_CAPS", "SYM_ON_I_FOR_8000_TSTRTGRP");
        assertFalse(exists);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(sqlTemplate, times(1)).queryForInt(sqlCaptor.capture(), any(Object[].class));
        assertFalse(sqlCaptor.getValue().contains("lower(event_object_table)"));
    }

<<<<<<< HEAD
=======
    @Test
    void getSyncTriggersExpression_capturesOnlyWhenVariableIsNull() {
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(createParameterService(), createPlatform("8.0.30"));
        assertEquals("@sync_triggers_disabled is null", dialect.getSyncTriggersExpression());
        assertEquals("coalesce(@sync_triggers_disabled, 0) != 2", dialect.getSyncTriggersOnIncomingExpression());
    }

    @Test
    void isConvertZeroDateToNull_isFalse_whenUrlOmitsZeroDateTimeBehavior() {
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(createParameterService(), createPlatform("8.0.30"));
        assertFalse(dialect.isConvertZeroDateToNull());
    }

    @Test
    void isConvertZeroDateToNull_isTrue_whenUrlConvertsZeroDatesAndSqlModeAllowsZeroDates() {
        IParameterService parameterService = createParameterService();
        when(parameterService.getString(DataSourceProperties.DB_POOL_URL)).thenReturn("jdbc:mysql://localhost/test?zeroDateTimeBehavior=convertToNull");
        IDatabasePlatform platform = createPlatform("8.0.30");
        when(platform.getSqlTemplate().queryForString("select @@sql_mode")).thenReturn("");
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(parameterService, platform);
        assertTrue(dialect.isConvertZeroDateToNull());
    }

    @Test
    void getCharacterSet_returnsConfiguredMasterCollation() {
        IParameterService parameterService = createParameterService();
        when(parameterService.getString(eq(ParameterConstants.DB_MASTER_COLLATION), anyString())).thenReturn("latin1");
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(parameterService, createPlatform("8.0.30"));
        assertEquals("latin1", dialect.getCharacterSet());
    }

    @Test
    void getCurrentSequenceValue_queriesLowerCaseDataTableName_notEnumCasing() {
        IDatabasePlatform platform = createPlatform("8.0.30");
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        when(platform.getDefaultCatalog()).thenReturn("testdb");
        when(sqlTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1000L);
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(createParameterService(), platform);
        dialect.getCurrentSequenceValue(SequenceIdentifier.DATA);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(sqlTemplate, times(1)).queryForObject(anyString(), eq(Long.class), argsCaptor.capture());
        assertArrayEquals(new Object[] { "testdb", "sym_data" }, argsCaptor.getValue());
    }

    @Test
    void getCurrentSequenceValue_returnsAutoIncrementValue_whenRowFound() {
        IDatabasePlatform platform = createPlatform("8.0.30");
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        when(sqlTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5001L);
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(createParameterService(), platform);
        assertEquals(5001L, dialect.getCurrentSequenceValue(SequenceIdentifier.DATA));
    }

    @Test
    void getCurrentSequenceValue_returnsNegativeOne_whenNoRowFoundForTableName() {
        IDatabasePlatform platform = createPlatform("8.0.30");
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        when(sqlTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(null);
        MySqlSymmetricDialect dialect = new MySqlSymmetricDialect(createParameterService(), platform);
        assertEquals(-1L, dialect.getCurrentSequenceValue(SequenceIdentifier.DATA));
    }

>>>>>>> 6f58903f22 (SYM-8109: MySQL findMaxDataId() returns 0 when lower_case_table_names=0 because the auto_increment lookup uses mixed-CASE table name sym_DATA (#1294))
    private IParameterService createParameterService() {
        IParameterService parameterService = mock(ParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getString(BasicDataSourcePropertyConstants.DB_POOL_URL)).thenReturn("jdbc:mysql://localhost/test");
        return parameterService;
    }

    private IDatabasePlatform createPlatform(String productVersion) {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.getDatabaseProductVersion()).thenReturn(productVersion);
        when(sqlTemplate.queryForString(anyString())).thenReturn("InnoDB");
        when(platform.getName()).thenReturn(DatabaseNamesConstants.MYSQL);
        when(platform.getDdlBuilder()).thenReturn(new MySqlDdlBuilder());
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        return platform;
    }
}
