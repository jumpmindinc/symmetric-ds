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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class FailedEngineInfoTest {
    private static final String ENGINE_NAME = "store-001";
    private static final String PROPERTY_FILE = "store-001.properties";

    @Test
    void testDefaultConstructor_leavesFieldsUnset() {
        FailedEngineInfo info = new FailedEngineInfo();
        assertNull(info.getEngineName());
        assertNull(info.getPropertyFileName());
        assertNull(info.getErrorMessage());
        assertNull(info.getException());
    }

    @Test
    void testConstructor_withEngineNameAndMessage() {
        FailedEngineInfo info = new FailedEngineInfo(ENGINE_NAME, PROPERTY_FILE, "could not connect");
        assertEquals(ENGINE_NAME, info.getEngineName());
        assertEquals(PROPERTY_FILE, info.getPropertyFileName());
        assertEquals("could not connect", info.getErrorMessage());
        assertNull(info.getException());
    }

    @Test
    void testConstructor_withException() {
        IllegalStateException cause = new IllegalStateException("bad state");
        FailedEngineInfo info = new FailedEngineInfo(ENGINE_NAME, PROPERTY_FILE, cause);
        assertSame(cause, info.getException());
        assertEquals(ENGINE_NAME, info.getEngineName());
    }

    @Test
    void testConstructor_withMessageAndException() {
        IllegalStateException cause = new IllegalStateException("bad state");
        FailedEngineInfo info = new FailedEngineInfo(ENGINE_NAME, PROPERTY_FILE, "could not connect", cause);
        assertEquals("could not connect", info.getErrorMessage());
        assertSame(cause, info.getException());
    }

    @Test
    void testSetters() {
        IllegalStateException cause = new IllegalStateException("bad state");
        FailedEngineInfo info = new FailedEngineInfo();
        info.setEngineName(ENGINE_NAME);
        info.setPropertyFileName(PROPERTY_FILE);
        info.setErrorMessage("could not connect");
        info.setException(cause);
        assertEquals(ENGINE_NAME, info.getEngineName());
        assertEquals(PROPERTY_FILE, info.getPropertyFileName());
        assertEquals("could not connect", info.getErrorMessage());
        assertSame(cause, info.getException());
    }
}
