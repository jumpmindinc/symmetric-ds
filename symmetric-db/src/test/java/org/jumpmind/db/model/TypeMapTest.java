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
package org.jumpmind.db.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.sql.Types;

class TypeMapTest {
    @Test
    void TestGetJdbcTypeCode_LooksUpByName() {
        assertEquals(Types.VARCHAR, TypeMap.getJdbcTypeCode("VARCHAR"));
    }

    @Test
    void TestGetJdbcTypeCode_IsCaseInsensitive() {
        assertEquals(Types.VARCHAR, TypeMap.getJdbcTypeCode("varchar"));
    }

    @Test
    void TestGetJdbcTypeCode_ReturnsNullForUnknown() {
        assertNull(TypeMap.getJdbcTypeCode("NOT_A_REAL_TYPE"));
    }

    @Test
    void TestGetJdbcTypeName_LookUpByCode() {
        assertEquals("VARCHAR", TypeMap.getJdbcTypeName(Types.VARCHAR));
    }

    @Test
    void TestGetJdbcTypeName_FallsBackToCodeStringForUnknown() {
        assertEquals("999", TypeMap.getJdbcTypeName(999));
    }

    @Test
    void TestIsNumericType_TrueForInteger() {
        assertTrue(TypeMap.isNumericType(Types.INTEGER));
    }

    @Test
    void TestIsNumericType_FalseForVarChar() {
        assertFalse(TypeMap.isNumericType(Types.VARCHAR));
    }

    @Test
    void TestIsTextType_TrueForVarChar() {
        assertTrue(TypeMap.isTextType(Types.VARCHAR));
    }

    @Test
    void TestIsTextType_FalseForInteger() {
        assertFalse(TypeMap.isTextType(Types.INTEGER));
    }

    @Test
    void TestIsSpecialType_TrueForArray() {
        assertTrue(TypeMap.isSpecialType(Types.ARRAY));
    }

    @Test
    void TestIsSpecialType_FalseForBlob() {
        assertFalse(TypeMap.isSpecialType(Types.BLOB));
    }

    @Test
    void TestIsBinaryType_TrueForBlob() {
        assertTrue(TypeMap.isBinaryType(Types.BLOB));
    }

    @Test
    void TestIsBinaryType_FalseForArray() {
        assertFalse(TypeMap.isBinaryType(Types.ARRAY));
    }

    @Test
    void TestIsDateTimeType_TrueForDate() {
        assertTrue(TypeMap.isDateTimeType(Types.DATE));
    }

    @Test
    void TestIsDateTimeType_FalseForDecimal() {
        assertFalse(TypeMap.isDateTimeType(Types.DECIMAL));
    }

    @Test
    void TestGetJdbcTypeDescriptions_JoinsNamesWithCommaSpace() {
        int[] array = { Types.VARCHAR, Types.INTEGER };
        String result = TypeMap.getJdbcTypeDescriptions(array);
        assertEquals("VARCHAR, INTEGER", result);
    }

    @Test
    void TestGetJdbcTypeDescriptions_SingleTypeHasNoComma() {
        int[] array = { Types.VARCHAR };
        String result = TypeMap.getJdbcTypeDescriptions(array);
        assertEquals("VARCHAR", result);
    }

    @Test
    void TestGetJdbcTypeDescriptions_EmptyArrayReturnsEmptyString() {
        int[] array = {};
        String result = TypeMap.getJdbcTypeDescriptions(array);
        assertEquals("", result);
    }

    @Test
    void TestGetJdbcTypeDescriptions_UsesCodeStringForUnknownTypes() {
        int[] array = { 999, 9999 };
        String result = TypeMap.getJdbcTypeDescriptions(array);
        assertEquals("999, 9999", result);
    }
}
