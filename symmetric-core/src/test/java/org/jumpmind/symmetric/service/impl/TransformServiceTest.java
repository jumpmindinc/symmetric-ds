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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransformServiceTest {
    TransformService transformService;

    @BeforeEach
    public void setUp() throws Exception {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISymmetricDialect dialect = mock(ISymmetricDialect.class);
        IExtensionService extensionService = mock(IExtensionService.class);
        when(dialect.getPlatform()).thenReturn(platform);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        transformService = new TransformService(engine, dialect);
    }

    @Test
    public void testBuiltinTransforms() throws Exception {
        NodeGroupLink link = new NodeGroupLink();
        for (TransformTableNodeGroupLink transform : transformService.getConfigExtractTransforms(link)) {
            assertEquals(TransformPoint.EXTRACT, transform.getTransformPoint());
        }
        for (TransformTableNodeGroupLink transform : transformService.getConfigLoadTransforms(link)) {
            assertEquals(TransformPoint.LOAD, transform.getTransformPoint());
        }
    }
}
