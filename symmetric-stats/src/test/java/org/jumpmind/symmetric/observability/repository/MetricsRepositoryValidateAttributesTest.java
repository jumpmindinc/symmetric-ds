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
package org.jumpmind.symmetric.observability.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MetricsRepositoryValidateAttributesTest {
    private static Method validateAttributes;

    @BeforeAll
    static void setUp() throws Exception {
        validateAttributes = MetricsRepository.class.getDeclaredMethod("validateAttributes", List.class);
        validateAttributes.setAccessible(true);
    }

    private void invoke(List<MetricAttribute> attrs) throws Exception {
        try {
            validateAttributes.invoke(null, attrs);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MetricsRepositoryException mre) {
                throw mre;
            }
            throw e;
        }
    }

    @Test
    void nullList_throwsWithMinMessage() {
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class, () -> invoke(null));
        assertTrue(ex.getMessage().contains(String.valueOf(MetricsRepository.ATTR_MIN_VALUES)));
    }

    @Test
    void emptyList_throwsWithMinMessage() {
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class, () -> invoke(List.of()));
        assertTrue(ex.getMessage().contains(String.valueOf(MetricsRepository.ATTR_MIN_VALUES)));
    }

    @Test
    void singleValidAttr_doesNotThrow() {
        assertDoesNotThrow(() -> invoke(List.of(new MetricAttribute("channel", "default"))));
    }

    @Test
    void maxValidAttrs_doesNotThrow() {
        var attrs = List.of(
                new MetricAttribute("a", "1"),
                new MetricAttribute("b", "2"),
                new MetricAttribute("c", "3"));
        assertDoesNotThrow(() -> invoke(attrs));
    }

    @Test
    void fourthAttrInvalid_doesNotThrow() {
        var attrs = List.of(
                new MetricAttribute("a", "1"),
                new MetricAttribute("b", "2"),
                new MetricAttribute("c", "3"),
                new MetricAttribute("", "bad"));
        assertDoesNotThrow(() -> invoke(attrs));
    }

    @Test
    void nullName_throwsWithIndexAndNameMessage() {
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invoke(List.of(new MetricAttribute(null, "value"))));
        assertEquals("MetricAttribute at index 0 has null or empty name", ex.getMessage());
    }

    @Test
    void emptyName_throwsWithIndexAndNameMessage() {
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invoke(List.of(new MetricAttribute("", "value"))));
        assertEquals("MetricAttribute at index 0 has null or empty name", ex.getMessage());
    }

    @Test
    void nameTooLong_throwsWithIndexAndLengthMessage() {
        String longName = "x".repeat(MetricsRepository.ATTR_MAX_LENGTH + 1);
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invoke(List.of(new MetricAttribute(longName, "value"))));
        assertEquals("MetricAttribute at index 0 name exceeds " + MetricsRepository.ATTR_MAX_LENGTH + " characters", ex.getMessage());
    }

    @Test
    void nameAtMaxLength_doesNotThrow() {
        String maxName = "x".repeat(MetricsRepository.ATTR_MAX_LENGTH);
        assertDoesNotThrow(() -> invoke(List.of(new MetricAttribute(maxName, "value"))));
    }

    @Test
    void nullValue_throwsWithIndexAndValueMessage() {
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invoke(List.of(new MetricAttribute("name", null))));
        assertEquals("MetricAttribute at index 0 has null or empty value", ex.getMessage());
    }

    @Test
    void emptyValue_throwsWithIndexAndValueMessage() {
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invoke(List.of(new MetricAttribute("name", ""))));
        assertEquals("MetricAttribute at index 0 has null or empty value", ex.getMessage());
    }

    @Test
    void valueTooLong_throwsWithIndexAndLengthMessage() {
        String longValue = "x".repeat(MetricsRepository.ATTR_MAX_LENGTH + 1);
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invoke(List.of(new MetricAttribute("name", longValue))));
        assertEquals("MetricAttribute at index 0 value exceeds " + MetricsRepository.ATTR_MAX_LENGTH + " characters", ex.getMessage());
    }

    @Test
    void valueAtMaxLength_doesNotThrow() {
        String maxValue = "x".repeat(MetricsRepository.ATTR_MAX_LENGTH);
        assertDoesNotThrow(() -> invoke(List.of(new MetricAttribute("name", maxValue))));
    }

    @Test
    void invalidAttrAtIndex1_throwsWithCorrectIndex() {
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invoke(List.of(new MetricAttribute("a", "1"), new MetricAttribute("", "v"))));
        assertTrue(ex.getMessage().contains("index 1"));
    }

    @Test
    void invalidAttrAtIndex2_throwsWithCorrectIndex() {
        MetricsRepositoryException ex = assertThrows(MetricsRepositoryException.class,
                () -> invoke(List.of(
                        new MetricAttribute("a", "1"),
                        new MetricAttribute("b", "2"),
                        new MetricAttribute("", "v"))));
        assertTrue(ex.getMessage().contains("index 2"));
    }
}
