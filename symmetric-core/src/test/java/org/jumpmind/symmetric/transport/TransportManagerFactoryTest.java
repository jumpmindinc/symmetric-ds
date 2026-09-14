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
package org.jumpmind.symmetric.transport;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.file.FileTransportManager;
import org.jumpmind.symmetric.transport.http.HttpTransportManager;
import org.jumpmind.symmetric.transport.internal.InternalTransportManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransportManagerFactoryTest {
    private TransportManagerFactory factory;
    private ISymmetricEngine engine;
    private IParameterService parameterService;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        factory = new TransportManagerFactory(engine);
    }

    @Test
    void create_withNoParameter_usesTransportTypeFromParameterService() {
        when(parameterService.getString(ParameterConstants.TRANSPORT_TYPE)).thenReturn(Constants.PROTOCOL_FILE);
        ITransportManager result = factory.create();
        assertInstanceOf(FileTransportManager.class, result);
    }

    @Test
    void create_withHttpTransport_returnsHttpTransportManager() {
        when(parameterService.getString(ServerConstants.HTTP_TRANSPORT_MANAGER_CLASS)).thenReturn(null);
        ITransportManager result = factory.create(Constants.PROTOCOL_HTTP);
        assertInstanceOf(HttpTransportManager.class, result);
    }

    @Test
    void create_withHttpTransportUpperCase_returnsHttpTransportManager() {
        when(parameterService.getString(ServerConstants.HTTP_TRANSPORT_MANAGER_CLASS)).thenReturn(null);
        ITransportManager result = factory.create(Constants.PROTOCOL_HTTP.toUpperCase());
        assertInstanceOf(HttpTransportManager.class, result);
    }

    @Test
    void create_withHybridTransport_returnsHybridTransportManager() {
        ITransportManager result = factory.create(Constants.PROTOCOL_HYBRID);
        assertInstanceOf(HybridTransportManager.class, result);
    }

    @Test
    void create_withHybridTransportMixedCase_returnsHybridTransportManager() {
        ITransportManager result = factory.create(StringUtils.capitalize(Constants.PROTOCOL_HYBRID));
        assertInstanceOf(HybridTransportManager.class, result);
    }

    @Test
    void create_withFileTransport_returnsFileTransportManager() {
        ITransportManager result = factory.create(Constants.PROTOCOL_FILE);
        assertInstanceOf(FileTransportManager.class, result);
    }

    @Test
    void create_withFileTransportUpperCase_returnsFileTransportManager() {
        ITransportManager result = factory.create(Constants.PROTOCOL_FILE.toUpperCase());
        assertInstanceOf(FileTransportManager.class, result);
    }

    @Test
    void create_withInternalTransport_returnsInternalTransportManager() {
        ITransportManager result = factory.create(Constants.PROTOCOL_INTERNAL);
        assertInstanceOf(InternalTransportManager.class, result);
    }

    @Test
    void create_withInternalTransportUpperCase_returnsInternalTransportManager() {
        ITransportManager result = factory.create(Constants.PROTOCOL_INTERNAL.toUpperCase());
        assertInstanceOf(InternalTransportManager.class, result);
    }

    @Test
    void create_withInvalidTransport_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> factory.create("invalid"));
    }

    @Test
    void create_withNullTransport_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> factory.create(null));
    }

    @Test
    void create_withEmptyTransport_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> factory.create(""));
    }
}
