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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpsertSqlAssertions {
    static final String CREATE_TIME = "create_time";
    private static final Pattern INSERT_COLUMNS = Pattern.compile("insert\\s+into\\s+\\S+\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGNED_COLUMN = Pattern.compile("(\\w+)\\s*=\\s*\\?");

    private UpsertSqlAssertions() {
    }

    static List<String> getInsertColumns(String insertSql) {
        Matcher matcher = INSERT_COLUMNS.matcher(insertSql);
        if (!matcher.find()) {
            throw new AssertionError("No column list in " + insertSql);
        }
        List<String> columns = new ArrayList<>();
        for (String column : matcher.group(1).split(",")) {
            columns.add(column.trim().toLowerCase());
        }
        return columns;
    }

    static List<String> getUpdateColumns(String updateSql) {
        Matcher matcher = ASSIGNED_COLUMN.matcher(updateSql);
        List<String> columns = new ArrayList<>();
        while (matcher.find()) {
            columns.add(matcher.group(1).toLowerCase());
        }
        return columns;
    }

    static int getCreateTimeIndex(String insertSql) {
        return getInsertColumns(insertSql).indexOf(CREATE_TIME);
    }

    static void assertUpdateMatchesInsertWithoutCreateTime(String insertSql, String updateSql, Object[] updateValues, int[] updateTypes) {
        List<String> expectedColumns = getInsertColumns(insertSql);
        expectedColumns.remove(CREATE_TIME);
        assertEquals(expectedColumns, getUpdateColumns(updateSql));
        long placeholderCount = updateSql.chars().filter(character -> character == '?').count();
        assertEquals(placeholderCount, updateValues.length, "values for " + Arrays.toString(updateValues));
        assertEquals(placeholderCount, updateTypes.length);
    }
}
