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
package org.jumpmind.symmetric.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;
import org.junit.jupiter.api.Test;

class ContextDefinitionTest {
    @Test
    void constructor_nonNullAttributes_contextIdIsSet() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("key", "value"));
        ContextDefinition def = new ContextDefinition(42L, attrs);
        assertEquals(42L, def.contextId());
    }

    @Test
    void constructor_nonNullAttributes_attributesAccessorReturnsExpected() {
        MetricAttributeList attrs = MetricAttributeList.of(
                new MetricAttribute("key1", "value1"),
                new MetricAttribute("key2", "value2"));
        ContextDefinition def = new ContextDefinition(1L, attrs);
        assertEquals(2, def.attributes().size());
        assertEquals("key1", def.attributes().get(0).name());
        assertEquals("value2", def.attributes().get(1).value());
    }

    @Test
    void constructor_withAttributes_returnedListIsUnmodifiable() {
        MetricAttributeList attrs = MetricAttributeList.of(new MetricAttribute("k", "v"));
        ContextDefinition def = new ContextDefinition(1L, attrs);
        var attributes = def.attributes();
        assertThrows(UnsupportedOperationException.class, () -> attributes.add(new MetricAttribute("x", "y")));
    }

    @Test
    void constructor_nullAttributes_returnsEmptyList() {
        ContextDefinition def = new ContextDefinition(1L, null);
        assertNotNull(def.attributes());
        assertTrue(def.attributes().isEmpty());
    }
}
