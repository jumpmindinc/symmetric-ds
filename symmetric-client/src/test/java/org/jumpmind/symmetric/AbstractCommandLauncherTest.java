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
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AbstractCommandLauncherTest {
    private static final String KEY_EXPORTER = "otel.metrics.exporter";
    private static final String KEY_ENDPOINT = "otel.exporter.otlp.metrics.endpoint";
    private static final String KEY_UNRELATED = "some.other.property";

    @AfterEach
    void clearOtelSystemProperties() {
        System.clearProperty(KEY_EXPORTER);
        System.clearProperty(KEY_ENDPOINT);
        System.clearProperty(KEY_UNRELATED);
    }

    @Test
    void otelPropertiesAreWrittenToSystem() {
        TypedProperties props = new TypedProperties();
        props.setProperty(KEY_EXPORTER, "otlp");
        props.setProperty(KEY_ENDPOINT, "http://localhost:4318/v1/metrics");
        props.setProperty(KEY_UNRELATED, "should-not-appear");
        AbstractCommandLauncher.propagateOtelPropertiesToSystem(props);
        assertEquals("otlp", System.getProperty(KEY_EXPORTER));
        assertEquals("http://localhost:4318/v1/metrics", System.getProperty(KEY_ENDPOINT));
        assertNull(System.getProperty(KEY_UNRELATED));
    }

    @Test
    void existingSystemPropertyIsOverwritten() {
        System.setProperty(KEY_EXPORTER, "prometheus");
        TypedProperties props = new TypedProperties();
        props.setProperty(KEY_EXPORTER, "otlp");
        AbstractCommandLauncher.propagateOtelPropertiesToSystem(props);
        assertEquals("otlp", System.getProperty(KEY_EXPORTER));
    }

    @Test
    void blankOtelPropertyIsNotWrittenToSystem() {
        TypedProperties props = new TypedProperties();
        props.setProperty(KEY_EXPORTER, "  ");
        AbstractCommandLauncher.propagateOtelPropertiesToSystem(props);
        assertNull(System.getProperty(KEY_EXPORTER));
    }
}
