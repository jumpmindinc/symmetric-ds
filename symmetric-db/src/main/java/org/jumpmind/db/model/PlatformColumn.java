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

import java.io.Serializable;

public class PlatformColumn implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String type;
    private int size = -1;
    private int decimalDigits = -1;
    private String defaultValue;
    private String[] enumValues;
    private boolean userDefinedType;

    public PlatformColumn(String name, String type, int size, int decimalDigits, String defaultValue) {
        this.name = name;
        this.type = type;
        this.size = size;
        this.decimalDigits = decimalDigits;
        this.defaultValue = defaultValue;
    }

    public PlatformColumn(String name, String sqlType, String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        String spec = null;
        int startIndex = sqlType.indexOf("(");
        if (startIndex == -1) {
            type = sqlType;
        } else {
            type = sqlType.substring(0, startIndex).trim();
            int endIndex = sqlType.indexOf(")", startIndex);
            if (endIndex != -1) {
                spec = sqlType.substring(startIndex + 1, endIndex);
                if (++endIndex < sqlType.length()) {
                    type += sqlType.substring(endIndex);
                }
            }
        }
        if (spec != null && !spec.trim().equals("")) {
            int index = spec.indexOf(",");
            if (index == -1) {
                if (spec.equalsIgnoreCase("max")) {
                    size = Integer.MAX_VALUE;
                } else {
                    size = Integer.valueOf(spec.trim());
                }
            } else {
                size = Integer.valueOf(spec.substring(0, index).trim());
                if (++index < spec.length()) {
                    decimalDigits = Integer.valueOf(spec.substring(index).trim());
                }
            }
        }
    }

    public PlatformColumn() {
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setDecimalDigits(int decimalDigits) {
        this.decimalDigits = decimalDigits;
    }

    public int getDecimalDigits() {
        return decimalDigits;
    }

    public void setEnumValues(String[] enumValues) {
        this.enumValues = enumValues;
    }

    public String[] getEnumValues() {
        return enumValues;
    }

    public boolean isEnum() {
        return enumValues != null && enumValues.length > 0;
    }

    public boolean isUserDefinedType() {
        return userDefinedType;
    }

    public void setUserDefinedType(boolean userDefinedType) {
        this.userDefinedType = userDefinedType;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
