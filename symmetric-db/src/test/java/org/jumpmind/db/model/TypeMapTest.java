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
package org.jumpmind.db.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.sql.Types;

class TypeMapTest {
    @Test
    void testGetJdbcTypeCode_looksUpByName() {
        assertEquals(Types.VARCHAR, TypeMap.getJdbcTypeCode("VARCHAR"));
    }

    @Test
    void testGetJdbcTypeCode_isCaseInsensitive() {
        assertEquals(Types.VARCHAR, TypeMap.getJdbcTypeCode("varchar"));
    }

    @Test
    void testGetJdbcTypeCode_returnsNullForUnknown() {
        assertNull(TypeMap.getJdbcTypeCode("NOT_A_REAL_TYPE"));
    }

    @Test
    void testGetJdbcTypeName_lookUpByCode() {
        assertEquals("VARCHAR", TypeMap.getJdbcTypeName(Types.VARCHAR));
    }

    @Test
    void testGetJdbcTypeName_fallsBackToCodeStringForUnknown() {
        assertEquals("999", TypeMap.getJdbcTypeName(999));
    }

    @Test
    void testIsNumericType_trueForInteger() {
        assertTrue(TypeMap.isNumericType(Types.INTEGER));
    }

    @Test
    void testIsNumericType_falseForVarChar() {
        assertFalse(TypeMap.isNumericType(Types.VARCHAR));
    }

    @Test
    void testIsTextType_trueForVarChar() {
        assertTrue(TypeMap.isTextType(Types.VARCHAR));
    }

    @Test
    void testIsTextType_falseForInteger() {
        assertFalse(TypeMap.isTextType(Types.INTEGER));
    }

    @Test
    void testIsSpecialType_trueForArray() {
        assertTrue(TypeMap.isSpecialType(Types.ARRAY));
    }

    @Test
    void testIsSpecialType_falseForBlob() {
        assertFalse(TypeMap.isSpecialType(Types.BLOB));
    }

    @Test
    void testIsBinaryType_trueForBlob() {
        assertTrue(TypeMap.isBinaryType(Types.BLOB));
    }

    @Test
    void testIsBinaryType_falseForArray() {
        assertFalse(TypeMap.isBinaryType(Types.ARRAY));
    }

    @Test
    void testIsDateTimeType_trueForDate() {
        assertTrue(TypeMap.isDateTimeType(Types.DATE));
    }

    @Test
    void testIsDateTimeType_falseForDecimal() {
        assertFalse(TypeMap.isDateTimeType(Types.DECIMAL));
    }

    @Test
    void testGetJdbcTypeDescriptions_joinsNamesWithCommaSpace() {
        int[] array = { Types.VARCHAR, Types.INTEGER };
        String result = TypeMap.getJdbcTypeDescriptions(array);
        assertEquals("VARCHAR, INTEGER", result);
    }

    @Test
    void testGetJdbcTypeDescriptions_singleTypeHasNoComma() {
        int[] array = { Types.VARCHAR };
        String result = TypeMap.getJdbcTypeDescriptions(array);
        assertEquals("VARCHAR", result);
    }

    @Test
    void testGetJdbcTypeDescriptions_emptyArrayReturnsEmptyString() {
        int[] array = {};
        String result = TypeMap.getJdbcTypeDescriptions(array);
        assertEquals("", result);
    }

    @Test
    void testGetJdbcTypeDescriptions_usesCodeStringForUnknownTypes() {
        int[] array = { 999, 9999 };
        String result = TypeMap.getJdbcTypeDescriptions(array);
        assertEquals("999, 9999", result);
    }
}
