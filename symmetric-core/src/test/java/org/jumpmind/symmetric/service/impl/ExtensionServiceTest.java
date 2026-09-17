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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.ext.ExtensionPointMetaData;
import org.jumpmind.symmetric.ext.INodeGroupExtensionPoint;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.model.Extension;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtensionServiceTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ISqlTemplate sqlTemplate;
    private ExtensionService extensionService;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getNodeGroupId()).thenReturn("store");
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(returnsFirstArg());
        extensionService = new ExtensionService(engine);
        extensionService.refresh();
    }

    @Test
    void testRefresh_withoutAnExtensionTableRegistersNothing() {
        assertTrue(extensionService.getExtensionPointMetaData().isEmpty());
        assertTrue(extensionService.getExtensionPointList(ITestExtensionPoint.class).isEmpty());
    }

    @Test
    void testAddExtensionPoint_registersUnderEveryExtensionInterface() {
        TestExtension extension = new TestExtension();
        extensionService.addExtensionPoint(extension);
        assertEquals(Collections.singletonList(extension), extensionService.getExtensionPointList(ITestExtensionPoint.class));
        assertEquals(extension, extensionService.getExtensionPointMap(ITestExtensionPoint.class).get(TestExtension.class.getCanonicalName()));
    }

    @Test
    void testAddExtensionPoint_withAnExplicitName() {
        TestExtension extension = new TestExtension();
        extensionService.addExtensionPoint("my-extension", extension);
        assertEquals(extension, extensionService.getExtensionPointMap(ITestExtensionPoint.class).get("my-extension"));
    }

    @Test
    void testAddExtensionPoint_survivesARefresh() {
        extensionService.addExtensionPoint(new TestExtension());
        extensionService.refresh();
        assertEquals(1, extensionService.getExtensionPointList(ITestExtensionPoint.class).size());
    }

    @Test
    void testRemoveExtensionPoint() {
        TestExtension extension = new TestExtension();
        extensionService.addExtensionPoint(extension);
        extensionService.removeExtensionPoint(extension);
        assertTrue(extensionService.getExtensionPointList(ITestExtensionPoint.class).isEmpty());
        assertTrue(extensionService.getExtensionPointMetaData().isEmpty());
    }

    @Test
    void testGetExtensionPoint_prefersAnExtensionThatIsNotBuiltIn() {
        extensionService.addExtensionPoint("built-in", new BuiltInTestExtension());
        TestExtension extension = new TestExtension();
        extensionService.addExtensionPoint("custom", extension);
        assertSame(extension, extensionService.getExtensionPoint(ITestExtensionPoint.class));
    }

    @Test
    void testGetExtensionPoint_fallsBackToTheBuiltInExtension() {
        BuiltInTestExtension extension = new BuiltInTestExtension();
        extensionService.addExtensionPoint("built-in", extension);
        assertSame(extension, extensionService.getExtensionPoint(ITestExtensionPoint.class));
    }

    @Test
    void testGetExtensionPoint_withNothingRegistered() {
        assertNull(extensionService.getExtensionPoint(ITestExtensionPoint.class));
    }

    @Test
    void testGetExtensionClassList_skipsNonExtensionInterfaces() {
        assertEquals(Collections.singletonList(ITestExtensionPoint.class), extensionService.getExtensionClassList(new TestExtension()));
        assertEquals(Collections.singletonList(ITestExtensionPoint.class), extensionService.getExtensionClassList(new NodeGroupExtension("store")));
    }

    @Test
    void testGetExtensionClassList_includesTheBuiltInMarker() {
        assertTrue(extensionService.getExtensionClassList(new BuiltInTestExtension()).contains(IBuiltInExtensionPoint.class));
    }

    @Test
    void testInitializeExtension_handsTheEngineToAnEngineAwareExtension() {
        EngineAwareExtension extension = new EngineAwareExtension();
        assertTrue(extensionService.initializeExtension(extension));
        assertSame(engine, extension.engine);
    }

    @Test
    void testInitializeExtension_withAMatchingNodeGroup() {
        assertTrue(extensionService.initializeExtension(new NodeGroupExtension("corp", "store")));
    }

    @Test
    void testInitializeExtension_withANonMatchingNodeGroup() {
        assertFalse(extensionService.initializeExtension(new NodeGroupExtension("corp")));
    }

    @Test
    void testInitializeExtension_withoutAnyNodeGroupRestriction() {
        assertTrue(extensionService.initializeExtension(new NodeGroupExtension((String[]) null)));
    }

    @Test
    void testRegisterExtension_recordsMetaDataForAnInstalledExtension() {
        TestExtension extension = new TestExtension();
        assertTrue(extensionService.registerExtension("custom", extension, false));
        List<ExtensionPointMetaData> metaData = extensionService.getExtensionPointMetaData();
        assertEquals(1, metaData.size());
        assertEquals("custom", metaData.get(0).getName());
        assertSame(extension, metaData.get(0).getExtensionPoint());
        assertTrue(metaData.get(0).isInstalled());
    }

    @Test
    void testRegisterExtension_recordsAnUninstalledExtensionThatDoesNotApplyToThisNodeGroup() {
        assertFalse(extensionService.registerExtension("custom", new NodeGroupExtension("corp"), false));
        assertTrue(extensionService.getExtensionPointMetaData().isEmpty());
    }

    @Test
    void testAddExtensionPointMetaData_skipsTheBuiltInMarkerForInstalledExtensions() {
        extensionService.addExtensionPoint("built-in", new BuiltInTestExtension());
        List<ExtensionPointMetaData> metaData = extensionService.getExtensionPointMetaData();
        assertEquals(1, metaData.size());
        assertEquals(ITestExtensionPoint.class, metaData.get(0).getType());
    }

    @Test
    void testAddExtensionPointMetaData_recordsAnExtensionThatInstalledNowhere() {
        extensionService.addExtensionPointMetaData(new TestExtension(), "custom", null, false);
        assertFalse(extensionService.getExtensionPointMetaData().get(0).isInstalled());
    }

    @Test
    void testGetExtensions_readsThroughTheTemplate() {
        when(sqlTemplate.query(anyString(), anyExtensionMapper())).thenReturn(Collections.<Extension> emptyList());
        assertTrue(extensionService.getExtensions().isEmpty());
    }

    @Test
    void testSaveExtension_insertsWhenTheUpdateMatchedNothing() {
        when(sqlTemplate.update(eq(sqlFor("updateExtensionSql")), any(Object[].class))).thenReturn(0);
        extensionService.saveExtension(newExtension("my-extension"));
        verify(sqlTemplate).update(eq(sqlFor("insertExtensionSql")), any(Object[].class));
    }

    @Test
    void testSaveExtension_refreshesWhenTheUpdateMatched() {
        when(sqlTemplate.update(eq(sqlFor("updateExtensionSql")), any(Object[].class))).thenReturn(1);
        extensionService.addExtensionPoint(new TestExtension());
        extensionService.saveExtension(newExtension("my-extension"));
        assertEquals(1, extensionService.getExtensionPointList(ITestExtensionPoint.class).size());
    }

    @Test
    void testSaveExtensionAsCopy_appendsTheFirstFreeSuffix() {
        Extension existing = newExtension("my-extension");
        Extension firstCopy = newExtension("my-extension_2");
        when(sqlTemplate.query(anyString(), anyExtensionMapper(), eq("my-extension%"))).thenReturn(Arrays.asList(existing, firstCopy));
        Extension extension = newExtension("my-extension");
        extensionService.saveExtensionAsCopy(extension);
        assertEquals("my-extension_3", extension.getExtensionId());
    }

    @Test
    void testSaveExtensionAsCopy_keepsTheIdWhenNothingElseUsesIt() {
        when(sqlTemplate.query(anyString(), anyExtensionMapper(), eq("my-extension%"))).thenReturn(Collections.<Extension> emptyList());
        Extension extension = newExtension("my-extension");
        extensionService.saveExtensionAsCopy(extension);
        assertEquals("my-extension", extension.getExtensionId());
    }

    @Test
    void testRenameExtension_deletesTheOldIdFirst() {
        extensionService.renameExtension("old-extension", newExtension("my-extension"));
        verify(sqlTemplate).update(sqlFor("deleteExtensionSql"), "old-extension");
    }

    @Test
    void testDeleteExtension() {
        extensionService.deleteExtension("my-extension");
        verify(sqlTemplate).update(sqlFor("deleteExtensionSql"), "my-extension");
    }

    @Test
    void testDeleteAllExtensions() {
        extensionService.deleteAllExtensions();
        verify(sqlTemplate).update(sqlFor("deleteAllExtensionsSql"));
    }

    @Test
    void testExtensionRowMapper_mapsEveryColumn() {
        Row row = new Row(10);
        row.put("extension_id", "my-extension");
        row.put("extension_type", Extension.EXTENSION_TYPE_JAVA);
        row.put("interface_name", ITestExtensionPoint.class.getName());
        row.put("node_group_id", "store");
        row.put("enabled", 1);
        row.put("extension_order", 3);
        row.put("extension_text", "public class Foo {}");
        row.put("create_time", Timestamp.valueOf("2023-11-14 17:13:20"));
        row.put("last_update_by", "system");
        row.put("last_update_time", Timestamp.valueOf("2024-01-02 03:04:05"));
        Extension extension = new ExtensionService.ExtensionRowMapper().mapRow(row);
        assertEquals("my-extension", extension.getExtensionId());
        assertEquals(Extension.EXTENSION_TYPE_JAVA, extension.getExtensionType());
        assertEquals(ITestExtensionPoint.class.getName(), extension.getInterfaceName());
        assertEquals("store", extension.getNodeGroupId());
        assertTrue(extension.isEnabled());
        assertEquals(3, extension.getExtensionOrder());
        assertEquals("public class Foo {}", extension.getExtensionText());
        assertEquals("system", extension.getLastUpdateBy());
    }

    @Test
    void testRegisterExtension_withoutAnyTextIsIgnored() {
        Extension extension = newExtension("my-extension");
        extension.setExtensionText(null);
        extensionService.registerExtension(extension);
        assertTrue(extensionService.getExtensionPointMetaData().isEmpty());
    }

    @Test
    void testRegisterExtension_withAnUnknownTypeIsIgnored() {
        Extension extension = newExtension("my-extension");
        extension.setExtensionType("groovy");
        extension.setExtensionText("println 'hello'");
        extensionService.registerExtension(extension);
        assertTrue(extensionService.getExtensionPointMetaData().isEmpty());
    }

    private Extension newExtension(String extensionId) {
        Extension extension = new Extension();
        extension.setExtensionId(extensionId);
        extension.setExtensionType(Extension.EXTENSION_TYPE_JAVA);
        extension.setInterfaceName(ITestExtensionPoint.class.getName());
        extension.setNodeGroupId("store");
        extension.setEnabled(true);
        extension.setExtensionOrder(1);
        extension.setExtensionText("public class Foo {}");
        extension.setLastUpdateBy("system");
        return extension;
    }

    private ISqlRowMapper<Extension> anyExtensionMapper() {
        return any();
    }

    private String sqlFor(String key) {
        return extensionService.getSql(key);
    }

    private interface ITestExtensionPoint extends IExtensionPoint {
        String describe();
    }

    private static class TestExtension implements ITestExtensionPoint {
        @Override
        public String describe() {
            return "custom";
        }
    }

    private static class BuiltInTestExtension implements ITestExtensionPoint, IBuiltInExtensionPoint {
        @Override
        public String describe() {
            return "built-in";
        }
    }

    private static class EngineAwareExtension implements ITestExtensionPoint, ISymmetricEngineAware {
        private ISymmetricEngine engine;

        @Override
        public String describe() {
            return "engine-aware";
        }

        @Override
        public void setSymmetricEngine(ISymmetricEngine engine) {
            this.engine = engine;
        }
    }

    private static class NodeGroupExtension implements ITestExtensionPoint, INodeGroupExtensionPoint {
        private final String[] nodeGroupIds;

        NodeGroupExtension(String... nodeGroupIds) {
            this.nodeGroupIds = nodeGroupIds;
        }

        @Override
        public String describe() {
            return "node-group";
        }

        @Override
        public String[] getNodeGroupIdsToApplyTo() {
            return nodeGroupIds;
        }
    }
}
