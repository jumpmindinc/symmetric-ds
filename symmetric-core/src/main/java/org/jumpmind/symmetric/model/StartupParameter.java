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
package org.jumpmind.symmetric.model;

import java.util.Objects;

import org.jumpmind.properties.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A parameter resolved before a database connection exists (JVM system property, environment variable, or a properties file), along with its inferred type,
 * default value, and where it was resolved from.
 */
public record StartupParameter(String name, Type type, String rawValue, String defaultValue, Source source) {

    private static final Logger log = LoggerFactory.getLogger(StartupParameter.class);
    public enum Type {
        STRING, INT, BOOLEAN, DOUBLE
    }

    public enum Source {
        JVM_SYSTEM_PROPERTY, ENVIRONMENT_VARIABLE, SYMMETRIC_SERVER_PROPERTIES, ENGINE_PROPERTIES_FILE, SYMMETRIC_PROPERTIES_FILE, DEFAULT
    }

    public String asString() {
        return rawValue != null ? rawValue : defaultValue;
    }

    public boolean matchesDefault() {
        return Objects.equals(rawValue, defaultValue);
    }

    public int asInt() {
        String value = asString();
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                TypedProperties.logPropertiesException(log, name, value);
            }
        }
        return 0;
    }

    public boolean asBoolean() {
        String value = asString();
        if (value == null) {
            return false;
        }
        value = value.trim();
        return value.equals("1") || Boolean.parseBoolean(value);
    }

    public double asDouble() {
        String value = asString();
        if (value != null) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException ex) {
                TypedProperties.logPropertiesException(log, name, value);
            }
        }
        return 0;
    }
}
