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
package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.writer.Conflict;
import org.jumpmind.symmetric.io.data.writer.Conflict.DetectConflict;
import org.jumpmind.symmetric.io.data.writer.Conflict.ResolveConflict;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterSettings;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractDataLoaderFactoryTest {
    private TestDataLoaderFactory factory;
    private IParameterService parameterService;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        when(parameterService.is(anyString())).thenReturn(false);
        when(parameterService.is(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(parameterService.getLong(anyString())).thenReturn(0L);
        when(parameterService.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(parameterService.getString(anyString())).thenReturn(null);
        when(parameterService.getString(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));
        factory = new TestDataLoaderFactory(parameterService);
    }

    @Test
    void buildSettingsWithNullConflicts_returnsNonNullSettings() {
        DatabaseWriterSettings settings = factory.buildParameterDatabaseWriterSettings(null, null);
        assertNotNull(settings);
    }

    @Test
    void buildSettingsWithEmptyConflicts_hasNoDefaultConflict() {
        DatabaseWriterSettings settings = factory.buildParameterDatabaseWriterSettings(null, Collections.emptyList());
        assertNull(settings.getDefaultConflictSetting());
    }

    @Test
    void buildSettingsWithDefaultConflict_setsDefaultConflictSetting() {
        Conflict defaultConflict = new Conflict();
        defaultConflict.setConflictId("default-conflict");
        List<Conflict> conflicts = Collections.singletonList(defaultConflict);
        DatabaseWriterSettings settings = factory.buildParameterDatabaseWriterSettings(null, conflicts);
        assertNotNull(settings.getDefaultConflictSetting());
        assertEquals("default-conflict", settings.getDefaultConflictSetting().getConflictId());
    }

    @Test
    void buildSettingsWithChannelConflict_registersInByChannelMap() {
        Conflict channelConflict = new Conflict();
        channelConflict.setConflictId("channel-conflict");
        channelConflict.setTargetChannelId("my-channel");
        List<Conflict> conflicts = Collections.singletonList(channelConflict);
        DatabaseWriterSettings settings = factory.buildParameterDatabaseWriterSettings(null, conflicts);
        assertNotNull(settings.getConflictSettingsByChannel());
        assertEquals(channelConflict, settings.getConflictSettingsByChannel().get("my-channel"));
        assertNull(settings.getDefaultConflictSetting());
    }

    @Test
    void buildSettingsWithTableConflict_registersInByTableMap() {
        Conflict tableConflict = new Conflict();
        tableConflict.setConflictId("table-conflict");
        tableConflict.setTargetTableName("my_table");
        List<Conflict> conflicts = Collections.singletonList(tableConflict);
        DatabaseWriterSettings settings = factory.buildParameterDatabaseWriterSettings(null, conflicts);
        assertNotNull(settings.getConflictSettingsByTable());
        assertNotNull(settings.getConflictSettingsByTable().get("my_table"));
        assertNull(settings.getDefaultConflictSetting());
    }

    @Test
    void buildSettingsWithConflictDefaultPkFallback_setsDefaultConflictWhenNonePresent() {
        when(parameterService.is(ParameterConstants.CONFLICT_DEFAULT_PK_WITH_FALLBACK)).thenReturn(true);
        DatabaseWriterSettings settings = factory.buildParameterDatabaseWriterSettings(null, Collections.emptyList());
        assertNotNull(settings.getDefaultConflictSetting());
        assertEquals(DetectConflict.USE_PK_DATA, settings.getDefaultConflictSetting().getDetectType());
        assertEquals(ResolveConflict.FALLBACK, settings.getDefaultConflictSetting().getResolveType());
    }

    @Test
    void buildSettingsWithMultipleDefaultConflicts_usesLastDefault() {
        Conflict first = new Conflict();
        first.setConflictId("first-default");
        Conflict second = new Conflict();
        second.setConflictId("second-default");
        List<Conflict> conflicts = Arrays.asList(first, second);
        DatabaseWriterSettings settings = factory.buildParameterDatabaseWriterSettings(null, conflicts);
        assertEquals("second-default", settings.getDefaultConflictSetting().getConflictId());
    }

    private static class TestDataLoaderFactory extends AbstractDataLoaderFactory {
        TestDataLoaderFactory(IParameterService parameterService) {
            this.parameterService = parameterService;
        }
    }
}
