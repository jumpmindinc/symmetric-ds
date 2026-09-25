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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.reader.ExtractDataReader;
import org.jumpmind.symmetric.io.data.reader.IExtractDataFilter;
import org.jumpmind.symmetric.io.data.reader.IExtractDataReaderSource;
import org.jumpmind.symmetric.io.data.reader.IRelationExtractDataFilter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtractDataReaderFactoryTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IExtensionService extensionService;
    private IDatabasePlatform platform;
    private IDatabasePlatform targetPlatform;
    private IExtractDataReaderSource source;
    private Node sourceNode;
    private Node targetNode;

    @SuppressWarnings("removal")
    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        parameterService = mock(IParameterService.class);
        extensionService = mock(IExtensionService.class);
        platform = mock(IDatabasePlatform.class);
        targetPlatform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn("H2");
        source = mock(IExtractDataReaderSource.class);
        sourceNode = new Node("source", "server");
        targetNode = new Node("target", "client");
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(symmetricDialect.getParameterService()).thenReturn(parameterService);
        when(extensionService.getExtensionPointList(IExtractDataFilter.class)).thenReturn(Collections.emptyList());
        when(extensionService.getExtensionPointList(IRelationExtractDataFilter.class)).thenReturn(Collections.emptyList());
    }

    @Test
    void testGetReader_returnsNonNullExtractDataReader() {
        ExtractDataReaderFactory factory = new ExtractDataReaderFactory(engine);
        ExtractDataReader reader = factory.getReader(platform, source, sourceNode, targetNode, targetPlatform);
        assertNotNull(reader);
    }

    @Test
    void testGetReader_withUnitypesConversionEnabled_setsIsUsingUnitypesTrue() throws Exception {
        when(parameterService.is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC)).thenReturn(true);
        ExtractDataReaderFactory factory = new ExtractDataReaderFactory(engine);
        ExtractDataReader reader = factory.getReader(platform, source, sourceNode, targetNode, targetPlatform);
        assertTrue(getIsUsingUnitypes(reader));
    }

    @Test
    void testGetReader_withUnitypesConversionDisabled_setsIsUsingUnitypesFalse() throws Exception {
        when(parameterService.is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC)).thenReturn(false);
        ExtractDataReaderFactory factory = new ExtractDataReaderFactory(engine);
        ExtractDataReader reader = factory.getReader(platform, source, sourceNode, targetNode, targetPlatform);
        assertFalse(getIsUsingUnitypes(reader));
    }

    @SuppressWarnings("removal")
    @Test
    void testGetReader_withLegacyInterfaceEnabled_usesExtractDataFilterExtensionPoint() {
        when(parameterService.is(ParameterConstants.EXTENSION_USE_LEGACY_INTERFACE)).thenReturn(true);
        ExtractDataReaderFactory factory = new ExtractDataReaderFactory(engine);
        factory.getReader(platform, source, sourceNode, targetNode, targetPlatform);
        verify(extensionService).getExtensionPointList(IExtractDataFilter.class);
        verify(extensionService, never()).getExtensionPointList(IRelationExtractDataFilter.class);
    }

    @SuppressWarnings("removal")
    @Test
    void testGetReader_withLegacyInterfaceDisabled_usesRelationExtractDataFilterExtensionPoint() {
        when(parameterService.is(ParameterConstants.EXTENSION_USE_LEGACY_INTERFACE)).thenReturn(false);
        ExtractDataReaderFactory factory = new ExtractDataReaderFactory(engine);
        factory.getReader(platform, source, sourceNode, targetNode, targetPlatform);
        verify(extensionService).getExtensionPointList(IRelationExtractDataFilter.class);
        verify(extensionService, never()).getExtensionPointList(IExtractDataFilter.class);
    }

    private boolean getIsUsingUnitypes(ExtractDataReader reader) throws Exception {
        Field field = ExtractDataReader.class.getDeclaredField("isUsingUnitypes");
        field.setAccessible(true);
        return field.getBoolean(reader);
    }
}
