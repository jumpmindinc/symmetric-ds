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
package org.jumpmind.db.platform.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.db.sql.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractSqlRowMapperTest {
    private NameMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NameMapper();
    }

    @Test
    void testIntValue_withInteger() {
        assertEquals(7, mapper.intValue(7));
    }

    @Test
    void testIntValue_withNumericString() {
        assertEquals(7, mapper.intValue("7"));
    }

    @Test
    void testIntValue_withNull() {
        assertEquals(0, mapper.intValue(null));
    }

    @Test
    void testIntValue_withNonNumericString() {
        assertEquals(0, mapper.intValue("abc"));
    }

    @Test
    void testIntValue_withNegativeValue() {
        assertEquals(-3, mapper.intValue("-3"));
    }

    @Test
    void testBooleanValue_withPositiveValue() {
        assertTrue(mapper.booleanValue(1));
    }

    @Test
    void testBooleanValue_withZero() {
        assertFalse(mapper.booleanValue(0));
    }

    @Test
    void testBooleanValue_withNegativeValue() {
        assertFalse(mapper.booleanValue(-1));
    }

    @Test
    void testBooleanValue_withNull() {
        assertFalse(mapper.booleanValue(null));
    }

    @Test
    void testBooleanValue_withNonNumericString() {
        assertFalse(mapper.booleanValue("yes"));
    }

    private static class NameMapper extends AbstractSqlRowMapper<String> {
        @Override
        public String mapRow(Row row) {
            return row.getString("name");
        }
    }
}
