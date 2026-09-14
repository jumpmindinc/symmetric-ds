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

import java.text.*;
import java.util.Date;

public class DBFField {
    private String name;
    private char type;
    private int length;
    private int decimalCount;
    private boolean validate;

    public DBFField(boolean validate, String s, char c, int i, int j) throws DBFException {
        this.validate = validate;
        if (this.validate) {
            if (s.length() > 10) {
                throw new DBFException("The field name is more than 10 characters long: " + s);
            }
            if (c != 'C' && c != 'N' && c != 'L' && c != 'D' && c != 'F') {
                throw new DBFException("The field type is not a valid. Got: " + c);
            }
            if (i < 1) {
                throw new DBFException("The field length should be a positive integer. Got: " + i);
            }
            if (c == 'C' && i >= 255) {
                throw new DBFException(
                        "The field length should be less than 255 characters for character fields. Got: " + i);
            }
            if (c == 'N' && i >= 21) {
                throw new DBFException("The field length should be less than 21 digits for numeric fields. Got: " + i);
            }
            if (c == 'L' && i != 1) {
                throw new DBFException("The field length should be 1 characater for logical fields. Got: " + i);
            }
            if (c == 'D' && i != 8) {
                throw new DBFException("The field length should be 8 characaters for date fields. Got: " + i);
            }
            if (c == 'F' && i >= 21) {
                throw new DBFException(
                        "The field length should be less than 21 digits for floating point fields. Got: " + i);
            }
            if (j < 0) {
                throw new DBFException("The field decimal count should not be a negative integer. Got: " + j);
            }
            if ((c == 'C' || c == 'L' || c == 'D') && j != 0) {
                throw new DBFException(
                        "The field decimal count should be 0 for character, logical, and date fields. Got: " + j);
            }
            if (j > i - 1) {
                throw new DBFException("The field decimal count should be less than the length - 1. Got: " + j);
            }
        }
        name = s;
        type = c;
        length = i;
        decimalCount = j;
        return;
    }

    public String getName() {
        return name;
    }

    public char getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public int getDecimalCount() {
        return decimalCount;
    }

    public String format(Object obj) throws DBFException {
        if (type == 'N' || type == 'F') {
            if (obj == null) {
                obj = Double.valueOf(0.0D);
            }
            if (obj instanceof Number) {
                Number number = (Number) obj;
                StringBuffer stringbuffer = new StringBuffer(getLength());
                for (int i = 0; i < getLength(); i++) {
                    stringbuffer.append("#");
                }
                if (getDecimalCount() > 0) {
                    stringbuffer.setCharAt(getLength() - getDecimalCount() - 1, '.');
                }
                DecimalFormat decimalformat = new DecimalFormat(stringbuffer.toString());
                String s1 = decimalformat.format(number);
                int k = getLength() - s1.length();
                if (k < 0) {
                    throw new DBFException("Value " + number + " cannot fit in pattern: '" + stringbuffer + "'.");
                }
                StringBuffer stringbuffer2 = new StringBuffer(k);
                for (int l = 0; l < k; l++) {
                    stringbuffer2.append(" ");
                }
                return stringbuffer2 + s1;
            } else {
                throw new DBFException("Expected a Number, got " + obj.getClass() + ".");
            }
        }
        if (type == 'C') {
            if (obj == null) {
                obj = "";
            }
            if (obj instanceof String) {
                String s = (String) obj;
                if (s.length() > getLength()) {
                    throw new DBFException("'" + obj + "' is longer than " + getLength() + " characters.");
                }
                StringBuffer stringbuffer1 = new StringBuffer(getLength() - s.length());
                for (int j = 0; j < getLength() - s.length(); j++) {
                    stringbuffer1.append(' ');
                }
                return s + stringbuffer1;
            } else {
                throw new DBFException("Expected a String, got " + obj.getClass() + ".");
            }
        }
        if (type == 'L') {
            if (obj == null) {
                obj = Boolean.valueOf(false);
            }
            if (obj instanceof Boolean) {
                Boolean boolean1 = (Boolean) obj;
                return boolean1.booleanValue() ? "Y" : "N";
            } else {
                throw new DBFException("Expected a Boolean, got " + obj.getClass() + ".");
            }
        }
        if (type == 'D') {
            if (obj == null) {
                obj = new Date();
            }
            if (obj instanceof Date) {
                Date date = (Date) obj;
                SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyyMMdd");
                return simpledateformat.format(date);
            } else {
                throw new DBFException("Expected a Date, got " + obj.getClass() + ".");
            }
        } else {
            throw new DBFException("Unrecognized DBFField type: " + type);
        }
    }

    public Object parse(String s) throws DBFException {
        s = s.trim();
        if (type == 'N' || type == 'F') {
            if (s.equals("")) {
                s = "0";
            }
            try {
                if (getDecimalCount() == 0) {
                    return Long.valueOf(s);
                } else {
                    return Double.valueOf(s);
                }
            } catch (NumberFormatException numberformatexception) {
                throw new DBFException(numberformatexception);
            }
        }
        if (type == 'C') {
            return s;
        }
        if (type == 'L') {
            if (s.equals("Y") || s.equals("y") || s.equals("T") || s.equals("t")) {
                return Boolean.valueOf(true);
            }
            if (s.equals("N") || s.equals("n") || s.equals("F") || s.equals("f")) {
                return Boolean.valueOf(false);
            } else {
                throw new DBFException("Unrecognized value for logical field: " + s);
            }
        }
        if (type == 'D') {
            SimpleDateFormat simpledateformat = new SimpleDateFormat("yyyyMMdd");
            try {
                if ("".equals(s)) {
                    return null;
                } else {
                    return simpledateformat.parse(s);
                }
            } catch (ParseException parseexception) {
                throw new DBFException(parseexception);
            }
        } else {
            throw new DBFException("Unrecognized DBFField type: " + type);
        }
    }

    public String toString() {
        return name;
    }
}
