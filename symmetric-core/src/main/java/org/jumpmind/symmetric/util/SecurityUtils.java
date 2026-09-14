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
 * software distributed under the LICENSE is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.util;

import java.util.regex.Pattern;

public class SecurityUtils {
    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-]+$");
    private static final Pattern SAFE_EXTERNAL_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\- ]+$");
    private static final String REPLACE_LOG_WHITESPACE_CHARS = "[\\n\\r\\t]";
    private static final String REPLACE_NULL_IN_LOGS = "null";

    private SecurityUtils() {
    }

    public static Object[] sanitizeLogArguments(Object... originalLogArguments) {
        String[] resultLogArguments = new String[originalLogArguments.length];
        for (int i = 0; i < originalLogArguments.length; i++) {
            if (originalLogArguments[i] == null) {
                resultLogArguments[i] = REPLACE_NULL_IN_LOGS;
            } else {
                resultLogArguments[i] = sanitizeForLogging(originalLogArguments[i].toString());
            }
        }
        return resultLogArguments;
    }

    public static String sanitizeForLogging(String input) {
        if (input == null) {
            return REPLACE_NULL_IN_LOGS;
        }
        return input.replaceAll(REPLACE_LOG_WHITESPACE_CHARS, " ");
    }

    public static String sanitizeInternalIdentifier(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Identifier must not be null or empty");
        }
        if (!SAFE_IDENTIFIER_PATTERN.matcher(input).matches()) {
            throw new IllegalArgumentException("Identifier contains invalid characters: " + sanitizeForLogging(input));
        }
        return input;
    }

    public static String sanitizeGroupName(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("GroupName must not be null or empty");
        }
        return sanitizeInternalIdentifier(input);
    }

    public static String sanitizeNodeId(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("NodeId must not be null or empty");
        }
        return sanitizeInternalIdentifier(input);
    }

    public static String sanitizeExternalId(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("ExternalId must not be null or empty");
        }
        if (!SAFE_EXTERNAL_ID_PATTERN.matcher(input).matches()) {
            throw new IllegalArgumentException("ExternalId contains invalid characters: " + sanitizeForLogging(input));
        }
        return input;
    }
}
