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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IExtensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaDataRouterTest {
    private ISymmetricEngine engine;
    private IExtensionService extensionService;
    private JavaDataRouter router;
    private SimpleRouterContext context;
    private DataMetaData dataMetaData;
    private Set<Node> nodes;

    @BeforeEach
    void setUp() {
        extensionService = mock(IExtensionService.class);
        engine = newEngine(extensionService);
        router = new JavaDataRouter(engine);
        context = new SimpleRouterContext();
        dataMetaData = mock(DataMetaData.class);
        when(dataMetaData.getRouter()).thenReturn(newConfiguredRouter());
        nodes = new HashSet<>(Collections.singletonList(new Node("store-1", "store")));
    }

    @Test
    void testCodeStartAndEndWrapTheExpression() {
        assertTrue(JavaDataRouter.CODE_START.contains("class JavaDataRouterExt extends JavaDataRouter"));
        assertTrue(JavaDataRouter.CODE_START.contains("routeToNodes"));
        assertTrue(JavaDataRouter.CODE_END.trim().endsWith("}"));
    }

    @Test
    void testIsDmlOnly_isFalse() {
        assertFalse(router.isDmlOnly());
    }

    @Test
    void testDefaultConstructor_leavesEngineUnset() {
        assertFalse(new JavaDataRouter().isDmlOnly());
    }

    @Test
    void testSetSymmetricEngine_readsDialectFromEngine() {
        JavaDataRouter bare = new JavaDataRouter();
        bare.setSymmetricEngine(engine);
        verify(engine, times(2)).getSymmetricDialect();
    }

    @Test
    void testRouteToNodes_delegatesToCompiledRouter() throws Exception {
        IDataRouter compiled = mock(IDataRouter.class);
        Set<String> expected = new HashSet<>(Collections.singletonList("store-1"));
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        when(compiled.routeToNodes(context, dataMetaData, nodes, false, false, null)).thenReturn(expected);
        assertEquals(expected, router.routeToNodes(context, dataMetaData, nodes, false, false, null));
    }

    @Test
    void testRouteToNodes_recordsExecutionTime() throws Exception {
        IDataRouter compiled = mock(IDataRouter.class);
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        when(compiled.routeToNodes(context, dataMetaData, nodes, false, false, null)).thenReturn(new HashSet<String>());
        router.routeToNodes(context, dataMetaData, nodes, false, false, null);
        assertTrue(context.getStat("javarouter.exec.ms") >= 0);
    }

    @Test
    void testRouteToNodes_routesToNobodyWhenCompilationFails() throws Exception {
        when(extensionService.getCompiledClass(anyString())).thenThrow(new RuntimeException("cannot compile"));
        assertTrue(router.routeToNodes(context, dataMetaData, nodes, false, false, null).isEmpty());
    }

    @Test
    void testRouteToNodes_routesToNobodyWhenCompiledRouterThrows() throws Exception {
        IDataRouter compiled = mock(IDataRouter.class);
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        when(compiled.routeToNodes(context, dataMetaData, nodes, false, false, null)).thenThrow(new RuntimeException("boom"));
        assertTrue(router.routeToNodes(context, dataMetaData, nodes, false, false, null).isEmpty());
    }

    @Test
    void testGetCompiledClass_cachesCompiledRouterInContext() throws Exception {
        IDataRouter compiled = mock(IDataRouter.class);
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        Router configuredRouter = dataMetaData.getRouter();
        assertSame(compiled, router.getCompiledClass(context, configuredRouter));
        assertSame(compiled, router.getCompiledClass(context, configuredRouter));
        verify(extensionService, times(1)).getCompiledClass(anyString());
    }

    @Test
    void testGetCompiledClass_wrapsExpressionInGeneratedClass() throws Exception {
        IDataRouter compiled = mock(IDataRouter.class);
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        router.getCompiledClass(context, dataMetaData.getRouter());
        verify(extensionService).getCompiledClass(JavaDataRouter.CODE_START + "return null;" + JavaDataRouter.CODE_END);
    }

    @Test
    void testRouteToNodes_withEmptyNodeSet() throws Exception {
        IDataRouter compiled = mock(IDataRouter.class);
        Set<Node> noNodes = new HashSet<>();
        when(extensionService.getCompiledClass(anyString())).thenReturn(compiled);
        when(compiled.routeToNodes(context, dataMetaData, noNodes, false, false, (TriggerRouter) null)).thenReturn(new HashSet<String>());
        assertTrue(router.routeToNodes(context, dataMetaData, noNodes, false, false, null).isEmpty());
    }

    private ISymmetricEngine newEngine(IExtensionService extensionService) {
        ISymmetricEngine mockedEngine = mock(ISymmetricEngine.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(mockedEngine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(mockedEngine.getExtensionService()).thenReturn(extensionService);
        return mockedEngine;
    }

    private Router newConfiguredRouter() {
        Router configuredRouter = new Router();
        configuredRouter.setRouterId("java-router");
        configuredRouter.setRouterExpression("return null;");
        return configuredRouter;
    }
}
