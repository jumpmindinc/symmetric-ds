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

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import org.jumpmind.db.model.TypeMap;
import org.jumpmind.util.FormatUtils;

/**
 * Helper class for dealing with default values, e.g. converting them to other types.
 */
public class DefaultValueHelper {
    /**
     * Converts the given default value from the specified original to the target jdbc type.
     * 
     * @param defaultValue
     *            The default value
     * @param originalTypeCode
     *            The original type code
     * @param targetTypeCode
     *            The target type code
     * @return The converted default value
     */
    public String convert(String defaultValue, int originalTypeCode, int targetTypeCode) {
        String result = defaultValue;
        if (defaultValue != null) {
            switch (originalTypeCode) {
                case Types.BIT:
                    result = convertBoolean(defaultValue, targetTypeCode).toString();
                    break;
                case Types.DATE:
                    if (targetTypeCode == Types.TIMESTAMP) {
                        try {
                            Date date = Date.valueOf(result);
                            return new Timestamp(date.getTime()).toString();
                        } catch (IllegalArgumentException ex) {
                        }
                    }
                    break;
                case Types.TIME:
                    if (targetTypeCode == Types.TIMESTAMP) {
                        try {
                            Time time = Time.valueOf(result);
                            return new Timestamp(time.getTime()).toString();
                        } catch (IllegalArgumentException ex) {
                        }
                    }
                    break;
                default:
                    if (PlatformUtils.supportsJava14JdbcTypes()
                            && (originalTypeCode == PlatformUtils.determineBooleanTypeCode())) {
                        result = convertBoolean(defaultValue, targetTypeCode).toString();
                    }
                    break;
            }
        }
        return result;
    }

    /**
     * Converts a boolean default value to the given target type.
     * 
     * @param defaultValue
     *            The default value
     * @param targetTypeCode
     *            The target type code
     * @return The converted value
     */
    private Object convertBoolean(String defaultValue, int targetTypeCode) {
        boolean value = FormatUtils.toBoolean(defaultValue);
        Object result = null;
        if ((targetTypeCode == Types.BIT)
                || (PlatformUtils.supportsJava14JdbcTypes() && (targetTypeCode == PlatformUtils
                        .determineBooleanTypeCode()))) {
            result = value;
        } else if (TypeMap.isNumericType(targetTypeCode)) {
            result = (value ? Integer.valueOf(1) : Integer.valueOf(0));
        } else {
            result = Boolean.toString(value);
        }
        return result;
    }
}
