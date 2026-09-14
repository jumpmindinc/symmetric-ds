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
package org.jumpmind.symmetric.io.data.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.Test;

class AbstractColumnTransformTest {
    private static class TestColumnTransform extends AbstractColumnTransform {
        IParameterService getParameterService() {
            return parameterService;
        }
    }

    @Test
    void testGetName_returnsAbstractByDefault() {
        assertEquals("abstract", new TestColumnTransform().getName());
    }

    @Test
    void testIsParameterServiceRequired_returnsFalseByDefault() {
        assertFalse(new TestColumnTransform().isParameterServiceRequired());
    }

    @Test
    void testSetParameterService_storesParameterServiceForSubclassUse() {
        TestColumnTransform transform = new TestColumnTransform();
        IParameterService parameterService = mock(IParameterService.class);
        transform.setParameterService(parameterService);
        assertSame(parameterService, transform.getParameterService());
    }
}
