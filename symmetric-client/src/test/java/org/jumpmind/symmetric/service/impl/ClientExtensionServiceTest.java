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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;

class ClientExtensionServiceTest {
    private ISymmetricEngine engine;
    private ApplicationContext springContext;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getNodeGroupId()).thenReturn("store");
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(returnsFirstArg());
        springContext = mock(ApplicationContext.class);
        when(springContext.getBeansOfType(IExtensionPoint.class)).thenReturn(Collections.<String, IExtensionPoint> emptyMap());
    }

    @Test
    void testRefresh_withoutASpringContextRegistersNothing() {
        ClientExtensionService extensionService = new ClientExtensionService(engine, null);
        extensionService.refresh();
        assertTrue(extensionService.getExtensionPointMetaData().isEmpty());
    }

    @Test
    void testRefresh_registersTheSpringBeans() {
        TestExtension extension = new TestExtension();
        when(springContext.getBeansOfType(IExtensionPoint.class)).thenReturn(Collections.<String, IExtensionPoint> singletonMap("springExtension",
                extension));
        ClientExtensionService extensionService = new ClientExtensionService(engine, springContext);
        extensionService.refresh();
        assertSame(extension, extensionService.getExtensionPointMap(ITestExtensionPoint.class).get("springExtension"));
    }

    @Test
    void testRefresh_alsoRegistersBeansFromTheParentBeanFactory() {
        TestExtension child = new TestExtension();
        TestExtension parent = new TestExtension();
        ListableBeanFactory parentFactory = mock(ListableBeanFactory.class);
        Map<String, IExtensionPoint> parentBeans = new HashMap<String, IExtensionPoint>();
        parentBeans.put("parentExtension", parent);
        when(parentFactory.getBeansOfType(IExtensionPoint.class)).thenReturn(parentBeans);
        when(springContext.getParentBeanFactory()).thenReturn(parentFactory);
        when(springContext.getBeansOfType(IExtensionPoint.class)).thenReturn(Collections.<String, IExtensionPoint> singletonMap("childExtension", child));
        ClientExtensionService extensionService = new ClientExtensionService(engine, springContext);
        extensionService.refresh();
        assertEquals(2, extensionService.getExtensionPointList(ITestExtensionPoint.class).size());
    }

    @Test
    void testRefresh_ignoresAParentBeanFactoryThatCannotBeListed() {
        when(springContext.getParentBeanFactory()).thenReturn(mock(BeanFactory.class));
        ClientExtensionService extensionService = new ClientExtensionService(engine, springContext);
        extensionService.refresh();
        assertTrue(extensionService.getExtensionPointMetaData().isEmpty());
    }

    @Test
    void testSetSpringContext_isUsedByTheNextRefresh() {
        TestExtension extension = new TestExtension();
        when(springContext.getBeansOfType(IExtensionPoint.class)).thenReturn(Collections.<String, IExtensionPoint> singletonMap("springExtension",
                extension));
        ClientExtensionService extensionService = new ClientExtensionService(engine, null);
        extensionService.setSpringContext(springContext);
        extensionService.refresh();
        assertEquals(1, extensionService.getExtensionPointList(ITestExtensionPoint.class).size());
    }

    private interface ITestExtensionPoint extends IExtensionPoint {
        String describe();
    }

    private static class TestExtension implements ITestExtensionPoint {
        @Override
        public String describe() {
            return "spring";
        }
    }
}
