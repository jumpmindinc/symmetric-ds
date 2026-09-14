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
package org.jumpmind.symmetric.util;

import java.util.Properties;

import org.jumpmind.symmetric.common.ParameterConstants;

public class ParametersUtil {
    static Properties deepCopy(Properties parameters) {
        Properties newParameters = new Properties();
        for (String key : parameters.stringPropertyNames()) {
            String value = parameters.getProperty(key);
            newParameters.setProperty(key, value);
        }
        return newParameters;
    }

    static void redactParameters(Properties parameters) {
        for (String name : ParameterConstants.REDACTED_PROPERTIES) {
            if (parameters.containsKey(name)) {
                parameters.put(name, ParameterConstants.REDACTED);
            }
        }
    }
}
