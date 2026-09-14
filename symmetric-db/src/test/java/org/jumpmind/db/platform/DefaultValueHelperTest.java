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
package org.jumpmind.db.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Types;

import org.junit.jupiter.api.Test;

class DefaultValueHelperTest {
    private final DefaultValueHelper helper = new DefaultValueHelper();

    @Test
    void testConvert_bitToNumericTargetReturnsOneOrZero() {
        assertEquals("1", helper.convert("1", Types.BIT, Types.INTEGER));
        assertEquals("0", helper.convert("0", Types.BIT, Types.INTEGER));
    }

    @Test
    void testConvert_dateToTimestampAppendsMidnight() {
        assertEquals("2026-01-15 00:00:00.0", helper.convert("2026-01-15", Types.DATE, Types.TIMESTAMP));
    }

    @Test
    void testConvert_timeToTimestampAppendsDate() {
        assertEquals("1970-01-01 13:14:15.0", helper.convert("13:14:15", Types.TIME, Types.TIMESTAMP));
    }

    @Test
    void testConvert_nullDefaultValueReturnsNull() {
        assertEquals(null, helper.convert(null, Types.TIME, Types.TIMESTAMP));
    }

    @Test
    void testConvert_dateToNonTimestampReturnsUnchanged() {
        assertEquals("2026-01-15", helper.convert("2026-01-15", Types.DATE, Types.VARCHAR));
    }

    @Test
    void testConvert_unhandledTypeReturnsUnchanged() {
        assertEquals("abc", helper.convert("abc", Types.VARCHAR, Types.VARCHAR));
    }

    @Test
    void testConvert_invalidTimeReturnsUnchanged() {
        assertEquals("not a time", helper.convert("not a time", Types.TIME, Types.TIMESTAMP));
    }

    @Test
    void testConvert_invalidDateReturnsUnchanged() {
        assertEquals("not a date", helper.convert("not a date", Types.DATE, Types.TIMESTAMP));
    }

    @Test
    void testConvertBoolean_bitToVarcharReturnsTrueString() {
        assertEquals("true", helper.convert("1", Types.BIT, Types.VARCHAR));
    }

    @Test
    void testConvertBoolean_bitToBooleanTargetReturnsTrueFalse() {
        assertEquals("true", helper.convert("1", Types.BIT, Types.BOOLEAN));
        assertEquals("false", helper.convert("0", Types.BIT, Types.BOOLEAN));
    }

    @Test
    void testConvertBoolean_booleanSourceToNumeric() {
        assertEquals("1", helper.convert("true", Types.BOOLEAN, Types.INTEGER));
    }
}
