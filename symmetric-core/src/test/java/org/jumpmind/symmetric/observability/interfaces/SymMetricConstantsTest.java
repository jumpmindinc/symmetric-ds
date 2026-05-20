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
package org.jumpmind.symmetric.observability.interfaces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Constructor;

import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.junit.jupiter.api.Test;

class SymMetricConstantsTest {
    @Test
    void privateConstructor_coverableViaReflection() throws Exception {
        Constructor<SymMetricConstants> ctor = SymMetricConstants.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    @Test
    void instrumentType_allFiveValues_definedAndNonNull() {
        InstrumentType[] values = InstrumentType.values();
        assertEquals(5, values.length);
        for (InstrumentType t : values) {
            assertNotNull(t);
        }
    }

    @Test
    void metricUnitConstants_areNonNull() {
        assertNotNull(SymMetricConstants.METRIC_UNIT_PERCENT);
        assertNotNull(SymMetricConstants.METRIC_UNIT_CONNECTIONS);
        assertNotNull(SymMetricConstants.METRIC_UNIT_ROWS);
        assertNotNull(SymMetricConstants.METRIC_UNIT_BATCHES);
    }
}
