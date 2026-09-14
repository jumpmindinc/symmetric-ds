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
package org.jumpmind.symmetric.wrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.wrapper.Constants.Status;
import org.junit.jupiter.api.Test;

class ConstantsTest {
    @Test
    void testStatus_values() {
        assertEquals(4, Status.values().length);
        assertEquals(Status.START_PENDING, Status.valueOf("START_PENDING"));
        assertEquals(Status.RUNNING, Status.valueOf("RUNNING"));
        assertEquals(Status.STOP_PENDING, Status.valueOf("STOP_PENDING"));
        assertEquals(Status.STOPPED, Status.valueOf("STOPPED"));
    }

    @Test
    void testReturnCodes_areUnique() throws Exception {
        Map<Integer, String> byValue = new HashMap<Integer, String>();
        for (Field field : Constants.class.getDeclaredFields()) {
            if (field.getName().startsWith("RC_") && Modifier.isStatic(field.getModifiers())) {
                int value = field.getInt(null);
                String previous = byValue.put(value, field.getName());
                assertNull(previous, "RC code " + value + " used by both " + previous + " and " + field.getName());
            }
        }
        assertEquals(23, byValue.size());
    }

    @Test
    void testReturnCodes_arePositive() throws Exception {
        for (Field field : Constants.class.getDeclaredFields()) {
            if (field.getName().startsWith("RC_") && Modifier.isStatic(field.getModifiers())) {
                assertTrue(field.getInt(null) > 0, field.getName() + " must be a positive exit code");
            }
        }
    }

    @Test
    void testReturnCodes_wellKnownValues() {
        assertEquals(1, Constants.RC_BAD_USAGE);
        assertEquals(2, Constants.RC_INVALID_ARGUMENT);
        assertEquals(3, Constants.RC_MISSING_CONFIG_FILE);
        assertEquals(4, Constants.RC_FAIL_READ_CONFIG_FILE);
        assertEquals(24, Constants.RC_ALREADY_RUNNING);
    }
}
