/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jumpmind.symmetric.web.ReadinessUriHandler;
import org.junit.jupiter.api.Test;

public class ReadinessUriHandlerTest {
    private String invokePrepare(Map<String, Boolean> readiness, boolean alive) throws Exception {
        ReadinessUriHandler handler = new ReadinessUriHandler();
        Method m = ReadinessUriHandler.class.getDeclaredMethod("prepareReadinessJsonRes", Map.class, boolean.class);
        m.setAccessible(true);
        return (String) m.invoke(handler, readiness, alive);
    }

    @Test
    public void emptyMapReportsReadyOverall() throws Exception {
        String result = invokePrepare(Collections.emptyMap(), true);
        assertEquals("{\"engine_details\": [],{\"status\": \"READY\"}", result);
    }

    @Test
    public void singleReadyEngineReportsReady() throws Exception {
        Map<String, Boolean> readiness = new LinkedHashMap<>();
        readiness.put("engine-1", true);
        String result = invokePrepare(readiness, true);
        assertEquals(
                "{\"engine_details\": [{\"engine_name\": \"engine-1\", \"status\": \"READY\"}],{\"status\": \"READY\"}",
                result);
    }

    @Test
    public void singleNotReadyEngineReportsNotReady() throws Exception {
        Map<String, Boolean> readiness = new LinkedHashMap<>();
        readiness.put("engine-1", false);
        String result = invokePrepare(readiness, true);
        assertEquals(
                "{\"engine_details\": [{\"engine_name\": \"engine-1\", \"status\": \"NOT READY\"}],{\"status\": \"NOT READY\"}",
                result);
    }

    @Test
    public void allEnginesReadyMakesOverallReady() throws Exception {
        Map<String, Boolean> readiness = new LinkedHashMap<>();
        readiness.put("engine-1", true);
        readiness.put("engine-2", true);
        String result = invokePrepare(readiness, true);
        assertTrue(result.contains("\"engine_name\": \"engine-1\", \"status\": \"READY\""), result);
        assertTrue(result.contains("\"engine_name\": \"engine-2\", \"status\": \"READY\""), result);
        assertTrue(result.endsWith("{\"status\": \"READY\"}"), result);
        assertFalse(result.contains("NOT READY"), result);
    }

    @Test
    public void anyEngineNotReadyMakesOverallNotReady() throws Exception {
        Map<String, Boolean> readiness = new LinkedHashMap<>();
        readiness.put("engine-1", true);
        readiness.put("engine-2", false);
        String result = invokePrepare(readiness, true);
        assertTrue(result.contains("\"engine_name\": \"engine-1\", \"status\": \"READY\""), result);
        assertTrue(result.contains("\"engine_name\": \"engine-2\", \"status\": \"NOT READY\""), result);
        assertTrue(result.endsWith("{\"status\": \"NOT READY\"}"), result);
    }

    @Test
    public void allEnginesNotReadyMakesOverallNotReady() throws Exception {
        Map<String, Boolean> readiness = new LinkedHashMap<>();
        readiness.put("engine-1", false);
        readiness.put("engine-2", false);
        String result = invokePrepare(readiness, true);
        assertTrue(result.contains("\"engine_name\": \"engine-1\", \"status\": \"NOT READY\""), result);
        assertTrue(result.contains("\"engine_name\": \"engine-2\", \"status\": \"NOT READY\""), result);
        assertTrue(result.endsWith("{\"status\": \"NOT READY\"}"), result);
    }

    @Test
    public void notAliveMakesOverallNotReady() throws Exception {
        Map<String, Boolean> readiness = new LinkedHashMap<>();
        readiness.put("engine-1", true);
        readiness.put("engine-2", true);
        String result = invokePrepare(readiness, false);
        assertTrue(result.contains("\"engine_name\": \"engine-1\", \"status\": \"READY\""), result);
        assertTrue(result.contains("\"engine_name\": \"engine-2\", \"status\": \"READY\""), result);
        assertTrue(result.endsWith("{\"status\": \"NOT READY\"}"), result);
    }

    @Test
    public void notAliveWithEmptyMapReportsNotReady() throws Exception {
        String result = invokePrepare(Collections.emptyMap(), false);
        assertEquals("{\"engine_details\": [],{\"status\": \"NOT READY\"}", result);
    }
}
