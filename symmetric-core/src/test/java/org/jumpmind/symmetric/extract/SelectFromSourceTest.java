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
package org.jumpmind.symmetric.extract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectFromSourceTest {
    private ISymmetricEngine engine;
    private IDatabasePlatform platform;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        platform = mock(IDatabasePlatform.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IParameterService parameterService = mock(IParameterService.class);
        IDataService dataService = mock(IDataService.class);
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        IConfigurationService configurationService = mock(IConfigurationService.class);
        INodeService nodeService = mock(INodeService.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getDataService()).thenReturn(dataService);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getExtensionService()).thenReturn(extensionService);
    }

    @Test
    void testNew_populatesFieldsFromEngine() {
        TestableSelectFromSource source = new TestableSelectFromSource(engine);
        assertSame(engine, source.engine);
        assertSame(platform, source.platform);
        assertSame(engine.getSymmetricDialect(), source.symmetricDialect);
        assertSame(engine.getParameterService(), source.parameterService);
        assertSame(engine.getDataService(), source.dataService);
        assertSame(engine.getTriggerRouterService(), source.triggerRouterService);
        assertSame(engine.getConfigurationService(), source.configurationService);
        assertSame(engine.getNodeService(), source.nodeService);
        assertSame(engine.getExtensionService(), source.extensionService);
    }

    @Test
    void testGetBatch() {
        TestableSelectFromSource source = new TestableSelectFromSource(engine);
        Batch batch = mock(Batch.class);
        source.batch = batch;
        assertSame(batch, source.getBatch());
    }

    @Test
    void testGetSourceRelation() {
        TestableSelectFromSource source = new TestableSelectFromSource(engine);
        Table table = new Table("source_table");
        source.sourceRelation = table;
        assertSame(table, source.getSourceRelation());
    }

    @Test
    void testGetTargetRelation() {
        TestableSelectFromSource source = new TestableSelectFromSource(engine);
        Table table = new Table("target_table");
        source.targetRelation = table;
        assertSame(table, source.getTargetRelation());
    }

    @Test
    void testHasLobsThatNeedExtract_withLobMarker_returnsTrue() {
        TestableSelectFromSource source = new TestableSelectFromSource(engine);
        Table table = buildTableWithLobColumn();
        CsvData data = new CsvData(DataEventType.INSERT, new String[] { "1", "\b" });
        assertTrue(source.hasLobsThatNeedExtract(table, data));
    }

    @Test
    void testHasLobsThatNeedExtract_withoutLobMarker_returnsFalse() {
        TestableSelectFromSource source = new TestableSelectFromSource(engine);
        Table table = buildTableWithLobColumn();
        CsvData data = new CsvData(DataEventType.INSERT, new String[] { "1", "some text" });
        assertFalse(source.hasLobsThatNeedExtract(table, data));
    }

    @Test
    void testHasLobsThatNeedExtract_withoutLobColumns_returnsFalse() {
        TestableSelectFromSource source = new TestableSelectFromSource(engine);
        Table table = new Table("no_lob_table");
        table.addColumn(new Column("id"));
        when(platform.isLob(any(Column.class))).thenReturn(false);
        CsvData data = new CsvData(DataEventType.INSERT, new String[] { "1" });
        assertFalse(source.hasLobsThatNeedExtract(table, data));
    }

    private Table buildTableWithLobColumn() {
        Table table = new Table("lob_table");
        table.addColumn(new Column("id"));
        table.addColumn(new Column("blob_col"));
        when(platform.isLob(any(Column.class))).thenAnswer(invocation -> "blob_col".equals(((Column) invocation.getArgument(0)).getName()));
        return table;
    }

    private static class TestableSelectFromSource extends SelectFromSource {
        TestableSelectFromSource(ISymmetricEngine engine) {
            super(engine);
        }

        @Override
        public CsvData next() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean requiresLobsSelectedFromSource(CsvData data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }
    }
}
