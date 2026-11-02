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

import java.util.Collection;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.model.StartupParameter;
import org.jumpmind.symmetric.service.IStartupParameterService;

/**
 * Copies explicitly configured global startup parameters into a target {@link Properties}, normally {@link System#getProperties()}, for code that still reads
 * them via {@code System.getProperty} (security service, HTTPS setup). Only missing target properties are set, because an existing one came from {@code -D} or
 * an imported environment variable, both of which outrank {@code symmetric-server.properties}. Defaults are never copied.
 */
public class StartupParameterExporter {
    private final Properties targetProperties;

    public StartupParameterExporter(Properties targetProperties) {
        this.targetProperties = targetProperties;
    }

    public void exportMissing(IStartupParameterService startupParameterService, Collection<String> keys) {
        for (String key : keys) {
            String configuredValue = findConfiguredValue(startupParameterService, key);
            if (StringUtils.isNotBlank(configuredValue) && StringUtils.isBlank(targetProperties.getProperty(key))) {
                targetProperties.setProperty(key, configuredValue);
            }
        }
    }

    private static String findConfiguredValue(IStartupParameterService startupParameterService, String key) {
        StartupParameter parameter = startupParameterService.getGlobalParameter(key);
        return parameter != null ? parameter.rawValue() : null;
    }
}
