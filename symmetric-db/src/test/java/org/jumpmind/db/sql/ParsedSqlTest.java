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
package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ParsedSqlTest {
    @Test
    void testGetOriginalSql() {
        ParsedSql parsedSql = new ParsedSql("select * from foo where id = :id");
        assertEquals("select * from foo where id = :id", parsedSql.getOriginalSql());
    }

    @Test
    void testAddNamedParameter_addsToParameterNamesAndIndexes() {
        ParsedSql parsedSql = new ParsedSql("select * from foo where id = :id");
        parsedSql.addNamedParameter("id", 27, 30);
        assertEquals(List.of("id"), parsedSql.getParameterNames());
        assertArrayEquals(new int[] { 27, 30 }, parsedSql.getParameterIndexes(0));
    }

    @Test
    void testGetParameterNames_returnsRepeatedOccurrences() {
        ParsedSql parsedSql = new ParsedSql("select * from foo where id = :id or parent_id = :id");
        parsedSql.addNamedParameter("id", 27, 30);
        parsedSql.addNamedParameter("id", 48, 51);
        assertEquals(List.of("id", "id"), parsedSql.getParameterNames());
    }

    @Test
    void testGetParameterIndexes_returnsIndexesForPosition() {
        ParsedSql parsedSql = new ParsedSql("select * from foo where a = :a and b = :b");
        parsedSql.addNamedParameter("a", 29, 31);
        parsedSql.addNamedParameter("b", 40, 42);
        assertArrayEquals(new int[] { 40, 42 }, parsedSql.getParameterIndexes(1));
    }

    @Test
    void testNamedParameterCount_setAndGet() {
        ParsedSql parsedSql = new ParsedSql("select 1");
        parsedSql.setNamedParameterCount(2);
        assertEquals(2, parsedSql.getNamedParameterCount());
    }

    @Test
    void testUnnamedParameterCount_setAndGet() {
        ParsedSql parsedSql = new ParsedSql("select 1");
        parsedSql.setUnnamedParameterCount(3);
        assertEquals(3, parsedSql.getUnnamedParameterCount());
    }

    @Test
    void testTotalParameterCount_setAndGet() {
        ParsedSql parsedSql = new ParsedSql("select 1");
        parsedSql.setTotalParameterCount(5);
        assertEquals(5, parsedSql.getTotalParameterCount());
    }

    @Test
    void testToString_returnsOriginalSql() {
        ParsedSql parsedSql = new ParsedSql("select * from foo");
        assertEquals("select * from foo", parsedSql.toString());
    }
}
