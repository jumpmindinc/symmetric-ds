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
package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Properties;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.Test;

class ParametersUtilTest {
    @Test
    void deepCopyTest() {
        Properties originalParameters = new Properties();
        originalParameters.setProperty("key1", "value1");
        originalParameters.setProperty("key2", "value2");
        Properties newParameters = ParametersUtil.deepCopy(originalParameters);
        assertEquals(originalParameters.stringPropertyNames(), newParameters.stringPropertyNames());
        for (String key : originalParameters.stringPropertyNames()) {
            String oldValue = originalParameters.getProperty(key);
            String newValue = newParameters.getProperty(key);
            assertEquals(oldValue, newValue);
        }
    }

    @Test
    void redactParametersTest() {
        Properties originalParameters = new Properties();
        for (String name : ParameterConstants.REDACTED_PROPERTIES) {
            originalParameters.put(name, "secret");
        }
        originalParameters.put("ordinary.key1", "ordinary.value1");
        originalParameters.put("ordinary.key2", "ordinary.value2");
        Properties parameters = ParametersUtil.deepCopy(originalParameters);
        ParametersUtil.redactParameters(parameters);
        assertEquals(originalParameters.stringPropertyNames(), parameters.stringPropertyNames());
        for (String key : parameters.stringPropertyNames()) {
            String value = parameters.getProperty(key);
            if (Arrays.asList(ParameterConstants.REDACTED_PROPERTIES).contains(key)) {
                assertEquals(ParameterConstants.REDACTED, value);
            } else {
                String originalValue = originalParameters.getProperty(key);
                assertEquals(originalValue, value);
            }
        }
    }
}
