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
package org.jumpmind.symmetric.route.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.jupiter.api.Test;

class DBFFieldTest {
    @Test
    void testConstructorSetsFieldProperties() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'C', 10, 0);
        assertEquals("NAME", field.getName());
        assertEquals('C', field.getType());
        assertEquals(10, field.getLength());
        assertEquals(0, field.getDecimalCount());
    }

    @Test
    void testConstructorSkipsValidationWhenNotValidating() throws DBFException {
        DBFField field = new DBFField(false, "A_NAME_TOO_LONG_FOR_VALIDATION", 'X', -1, -1);
        assertEquals("A_NAME_TOO_LONG_FOR_VALIDATION", field.getName());
        assertEquals('X', field.getType());
        assertEquals(-1, field.getLength());
        assertEquals(-1, field.getDecimalCount());
    }

    @Test
    void testConstructorRejectsNameLongerThanTenCharacters() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME_TOO_LONG", 'C', 10, 0));
    }

    @Test
    void testConstructorRejectsInvalidType() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'X', 10, 0));
    }

    @Test
    void testConstructorRejectsNonPositiveLength() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'C', 0, 0));
    }

    @Test
    void testConstructorRejectsCharacterLengthTooLarge() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'C', 255, 0));
    }

    @Test
    void testConstructorRejectsNumericLengthTooLarge() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'N', 21, 0));
    }

    @Test
    void testConstructorRejectsLogicalLengthOtherThanOne() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'L', 2, 0));
    }

    @Test
    void testConstructorRejectsDateLengthOtherThanEight() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'D', 10, 0));
    }

    @Test
    void testConstructorRejectsFloatingPointLengthTooLarge() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'F', 21, 0));
    }

    @Test
    void testConstructorRejectsNegativeDecimalCount() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'N', 10, -1));
    }

    @Test
    void testConstructorRejectsDecimalCountForNonNumericTypes() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'C', 10, 1));
    }

    @Test
    void testConstructorRejectsDecimalCountNotLessThanLengthMinusOne() {
        assertThrows(DBFException.class, () -> new DBFField(true, "NAME", 'N', 10, 10));
    }

    @Test
    void testFormatPadsCharacterFieldWithTrailingSpaces() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'C', 10, 0);
        assertEquals("hi        ", field.format("hi"));
    }

    @Test
    void testFormatUsesEmptyStringForNullCharacterValue() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'C', 5, 0);
        assertEquals("     ", field.format(null));
    }

    @Test
    void testFormatRejectsCharacterValueLongerThanFieldLength() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'C', 5, 0);
        assertThrows(DBFException.class, () -> field.format("too long"));
    }

    @Test
    void testFormatRejectsNonStringForCharacterField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'C', 5, 0);
        assertThrows(DBFException.class, () -> field.format(Integer.valueOf(1)));
    }

    @Test
    void testFormatPadsNumericFieldWithLeadingSpaces() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 5, 0);
        assertEquals("   12", field.format(Long.valueOf(12)));
    }

    @Test
    void testFormatIncludesDecimalPointForNumericFieldWithDecimals() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 6, 2);
        assertEquals("  12.3", field.format(Double.valueOf(12.3)));
    }

    @Test
    void testFormatUsesZeroForNullNumericValue() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 3, 0);
        assertEquals("  0", field.format(null));
    }

    @Test
    void testFormatRejectsNumberThatDoesNotFitInLength() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 3, 0);
        assertThrows(DBFException.class, () -> field.format(Long.valueOf(12345)));
    }

    @Test
    void testFormatRejectsNonNumberForNumericField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 5, 0);
        assertThrows(DBFException.class, () -> field.format("not a number"));
    }

    @Test
    void testFormatConvertsTrueBooleanToY() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'L', 1, 0);
        assertEquals("Y", field.format(Boolean.TRUE));
    }

    @Test
    void testFormatConvertsFalseBooleanToN() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'L', 1, 0);
        assertEquals("N", field.format(Boolean.FALSE));
    }

    @Test
    void testFormatUsesFalseForNullLogicalValue() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'L', 1, 0);
        assertEquals("N", field.format(null));
    }

    @Test
    void testFormatRejectsNonBooleanForLogicalField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'L', 1, 0);
        assertThrows(DBFException.class, () -> field.format("not a boolean"));
    }

    @Test
    void testFormatConvertsDateToYyyyMmDd() throws Exception {
        DBFField field = new DBFField(true, "NAME", 'D', 8, 0);
        Date date = new SimpleDateFormat("yyyyMMdd").parse("20250131");
        assertEquals("20250131", field.format(date));
    }

    @Test
    void testFormatRejectsNonDateForDateField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'D', 8, 0);
        assertThrows(DBFException.class, () -> field.format("not a date"));
    }

    @Test
    void testParseTrimsAndReturnsStringForCharacterField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'C', 10, 0);
        assertEquals("hi", field.parse("  hi  "));
    }

    @Test
    void testParseReturnsLongForNumericFieldWithoutDecimals() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 5, 0);
        assertEquals(Long.valueOf(42), field.parse("  42  "));
    }

    @Test
    void testParseReturnsZeroForBlankNumericField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 5, 0);
        assertEquals(Long.valueOf(0), field.parse("   "));
    }

    @Test
    void testParseReturnsDoubleForNumericFieldWithDecimals() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 6, 2);
        assertEquals(Double.valueOf(12.3), field.parse(" 12.30 "));
    }

    @Test
    void testParseRejectsInvalidNumber() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'N', 5, 0);
        assertThrows(DBFException.class, () -> field.parse("not a number"));
    }

    @Test
    void testParseReturnsTrueForYesValuesInLogicalField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'L', 1, 0);
        assertTrue((Boolean) field.parse("Y"));
        assertTrue((Boolean) field.parse("y"));
        assertTrue((Boolean) field.parse("T"));
        assertTrue((Boolean) field.parse("t"));
    }

    @Test
    void testParseReturnsFalseForNoValuesInLogicalField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'L', 1, 0);
        assertFalse((Boolean) field.parse("N"));
        assertFalse((Boolean) field.parse("n"));
        assertFalse((Boolean) field.parse("F"));
        assertFalse((Boolean) field.parse("f"));
    }

    @Test
    void testParseRejectsUnrecognizedLogicalValue() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'L', 1, 0);
        assertThrows(DBFException.class, () -> field.parse("X"));
    }

    @Test
    void testParseReturnsDateForDateField() throws Exception {
        DBFField field = new DBFField(true, "NAME", 'D', 8, 0);
        Date expected = new SimpleDateFormat("yyyyMMdd").parse("20250131");
        assertEquals(expected, field.parse("20250131"));
    }

    @Test
    void testParseReturnsNullForBlankDateField() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'D', 8, 0);
        assertEquals(null, field.parse("        "));
    }

    @Test
    void testParseRejectsInvalidDate() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'D', 8, 0);
        assertThrows(DBFException.class, () -> field.parse("notadate"));
    }

    @Test
    void testToStringReturnsFieldName() throws DBFException {
        DBFField field = new DBFField(true, "NAME", 'C', 10, 0);
        assertEquals("NAME", field.toString());
    }
}
