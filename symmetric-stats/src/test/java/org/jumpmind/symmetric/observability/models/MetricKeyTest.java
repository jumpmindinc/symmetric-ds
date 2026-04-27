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
package org.jumpmind.symmetric.observability.models;

import org.junit.jupiter.api.Test;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricKeyTest {
    private static final long KEY_A = 1L;
    private static final long KEY_B = 2L;
    private static final MetricFactType FLOAT64 = MetricFactType.FLOAT64;
    private static final InstrumentType GAUGE = InstrumentType.DOUBLE_GAUGE;

    @Test
    void sameInstance_returnsTrue() {
        MetricKey key = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertTrue(key.equalsOnCompositeKey(key));
    }

    @Test
    void differentKeyField_sameOtherFields_returnsTrue() {
        MetricKey a = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_B, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertTrue(a.equalsOnCompositeKey(b));
    }

    @Test
    void caseInsensitiveHostname_returnsTrue() {
        MetricKey a = new MetricKey(KEY_A, "HOST1", "engine1", "metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertTrue(a.equalsOnCompositeKey(b));
    }

    @Test
    void caseInsensitiveEngineName_returnsTrue() {
        MetricKey a = new MetricKey(KEY_A, "host1", "Engine1", "metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertTrue(a.equalsOnCompositeKey(b));
    }

    @Test
    void caseInsensitiveMetricId_returnsTrue() {
        MetricKey a = new MetricKey(KEY_A, "host1", "engine1", "Metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertTrue(a.equalsOnCompositeKey(b));
    }

    @Test
    void differentHostname_returnsFalse() {
        MetricKey a = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host2", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertFalse(a.equalsOnCompositeKey(b));
    }

    @Test
    void differentEngineName_returnsFalse() {
        MetricKey a = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host1", "engine2", "metric1", FLOAT64, GAUGE, true);
        assertFalse(a.equalsOnCompositeKey(b));
    }

    @Test
    void differentMetricId_returnsFalse() {
        MetricKey a = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host1", "engine1", "metric2", FLOAT64, GAUGE, true);
        assertFalse(a.equalsOnCompositeKey(b));
    }

    @Test
    void nullArgument_returnsFalse() {
        MetricKey a = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertFalse(a.equalsOnCompositeKey(null));
    }

    @Test
    void nullHostnameOnThis_returnsFalse() {
        MetricKey a = new MetricKey(KEY_A, null, "engine1", "metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertFalse(a.equalsOnCompositeKey(b));
    }

    @Test
    void nullEngineNameOnThis_returnsFalse() {
        MetricKey a = new MetricKey(KEY_A, "host1", null, "metric1", FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertFalse(a.equalsOnCompositeKey(b));
    }

    @Test
    void nullMetricIdOnThis_returnsFalse() {
        MetricKey a = new MetricKey(KEY_A, "host1", "engine1", null, FLOAT64, GAUGE, true);
        MetricKey b = new MetricKey(KEY_A, "host1", "engine1", "metric1", FLOAT64, GAUGE, true);
        assertFalse(a.equalsOnCompositeKey(b));
    }
}
