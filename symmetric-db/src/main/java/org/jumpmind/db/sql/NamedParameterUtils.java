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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper methods for named parameter parsing. Only intended for internal use within Spring's JDBC framework.
 * 
 * @author Thomas Risberg
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract class NamedParameterUtils {
    /**
     * Set of characters that qualify as parameter separators, indicating that a parameter name in a SQL String has ended.
     */
    private static final char[] PARAMETER_SEPARATORS = new char[] { '"', '\'', ':', '&', ',', ';',
            '(', ')', '|', '=', '+', '-', '*', '%', '/', '\\', '<', '>', '^' };
    /**
     * Set of characters that qualify as comment or quotes starting characters.
     */
    private static final String[] START_SKIP = new String[] { "'", "\"", "--", "/*" };
    /**
     * Set of characters that at are the corresponding comment or quotes ending characters.
     */
    private static final String[] STOP_SKIP = new String[] { "'", "\"", "\n", "*/" };
    // -------------------------------------------------------------------------
    // Core methods used by NamedParameterJdbcTemplate and SqlQuery/SqlUpdate
    // -------------------------------------------------------------------------

    /**
     * Parse the SQL statement and locate any placeholders or named parameters. Named parameters are substituted for a JDBC placeholder.
     * 
     * @param sql
     *            the SQL statement
     * @return the parsed statement, represented as ParsedSql instance
     */
    public static ParsedSql parseSqlStatement(String sql) {
        Set<String> namedParameters = new HashSet<String>();
        ParsedSql parsedSql = new ParsedSql(sql);
        char[] statement = sql.toCharArray();
        int namedParameterCount = 0;
        int unnamedParameterCount = 0;
        int totalParameterCount = 0;
        int i = 0;
        while (i < statement.length) {
            int skipToPosition = skipCommentsAndQuotes(statement, i);
            if (i != skipToPosition) {
                if (skipToPosition >= statement.length) {
                    break;
                }
                i = skipToPosition;
            }
            char c = statement[i];
            if (c == ':' || c == '&') {
                int j = i + 1;
                if (j < statement.length && statement[j] == ':' && c == ':') {
                    // Postgres-style "::" casting operator - to be skipped.
                    i = i + 2;
                    continue;
                }
                while (j < statement.length && !isParameterSeparator(statement[j])) {
                    j++;
                }
                if (j - i > 1) {
                    String parameter = sql.substring(i + 1, j);
                    if (namedParameters.add(parameter)) {
                        namedParameterCount++;
                    }
                    parsedSql.addNamedParameter(parameter, i, j);
                    totalParameterCount++;
                }
                i = j - 1;
            } else {
                if (c == '?') {
                    unnamedParameterCount++;
                    totalParameterCount++;
                }
            }
            i++;
        }
        parsedSql.setNamedParameterCount(namedParameterCount);
        parsedSql.setUnnamedParameterCount(unnamedParameterCount);
        parsedSql.setTotalParameterCount(totalParameterCount);
        return parsedSql;
    }

    /**
     * Skip over comments and quoted names present in an SQL statement
     * 
     * @param statement
     *            character array containing SQL statement
     * @param position
     *            current position of statement
     * @return next position to process after any comments or quotes are skipped
     */
    private static int skipCommentsAndQuotes(char[] statement, int position) {
        for (int i = 0; i < START_SKIP.length; i++) {
            if (statement[position] == START_SKIP[i].charAt(0)) {
                boolean match = true;
                for (int j = 1; j < START_SKIP[i].length(); j++) {
                    if (!(statement[position + j] == START_SKIP[i].charAt(j))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    int offset = START_SKIP[i].length();
                    for (int m = position + offset; m < statement.length; m++) {
                        if (statement[m] == STOP_SKIP[i].charAt(0)) {
                            boolean endMatch = true;
                            int endPos = m;
                            for (int n = 1; n < STOP_SKIP[i].length(); n++) {
                                if (m + n >= statement.length) {
                                    // last comment not closed properly
                                    return statement.length;
                                }
                                if (!(statement[m + n] == STOP_SKIP[i].charAt(n))) {
                                    endMatch = false;
                                    break;
                                }
                                endPos = m + n;
                            }
                            if (endMatch) {
                                // found character sequence ending comment or
                                // quote
                                return endPos + 1;
                            }
                        }
                    }
                    // character sequence ending comment or quote not found
                    return statement.length;
                }
            }
        }
        return position;
    }

    /**
     * Parse the SQL statement and locate any placeholders or named parameters. Named parameters are substituted for a JDBC placeholder and any select list is
     * expanded to the required number of placeholders. Select lists may contain an array of objects and in that case the placeholders will be grouped and
     * enclosed with parentheses. This allows for the use of "expression lists" in the SQL statement like:<br/>
     * select id, name, state from table where (name, age) in (('John', 35), ('Ann', 50))
     * <p>
     * The parameter values passed in are used to determine the number of placeholder to be used for a select list. Select lists should be limited to 100 or
     * fewer elements. A larger number of elements is not guaranteed to be supported by the database and is strictly vendor-dependent.
     * 
     * @param parsedSql
     *            the parsed representation of the SQL statement
     * @param paramSource
     *            the source for named parameters
     * @return the SQL statement with substituted parameters
     * @see #parseSqlStatement
     */
    public static String substituteNamedParameters(ParsedSql parsedSql,
            Map<String, ?> paramSource) {
        String originalSql = parsedSql.getOriginalSql();
        StringBuilder actualSql = new StringBuilder();
        List<String> paramNames = parsedSql.getParameterNames();
        int lastIndex = 0;
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = (String) paramNames.get(i);
            int[] indexes = parsedSql.getParameterIndexes(i);
            int startIndex = indexes[0];
            int endIndex = indexes[1];
            actualSql.append(originalSql.substring(lastIndex, startIndex));
            if (paramSource != null && paramSource.containsKey(paramName)) {
                Object value = paramSource.get(paramName);
                if (value instanceof Collection<?>) {
                    Iterator<?> entryIter = ((Collection<?>) value).iterator();
                    int k = 0;
                    while (entryIter.hasNext()) {
                        if (k > 0) {
                            actualSql.append(", ");
                        }
                        k++;
                        Object entryItem = entryIter.next();
                        if (entryItem instanceof Object[]) {
                            Object[] expressionList = (Object[]) entryItem;
                            actualSql.append("(");
                            for (int m = 0; m < expressionList.length; m++) {
                                if (m > 0) {
                                    actualSql.append(", ");
                                }
                                actualSql.append("?");
                            }
                            actualSql.append(")");
                        } else {
                            actualSql.append("?");
                        }
                    }
                } else {
                    actualSql.append("?");
                }
            } else {
                throw new InvalidSqlException("The parameter map passed in did not contain "
                        + "a key for the named parameter %s. \nKeys: %s \nSQL: %s",
                        paramName, paramSource != null ? paramSource.keySet() : "null", originalSql);
            }
            lastIndex = endIndex;
        }
        actualSql.append(originalSql.substring(lastIndex, originalSql.length()));
        return actualSql.toString();
    }

    /**
     * Convert a Map of named parameter values to a corresponding array.
     * 
     * @param parsedSql
     *            the parsed SQL statement
     * @param paramSource
     *            the source for named parameters
     * @return the array of values
     */
    public static Object[] buildValueArray(ParsedSql parsedSql, Map<String, ?> paramSource) {
        List<Object> paramArray = new ArrayList<Object>();
        if (parsedSql.getNamedParameterCount() > 0 && parsedSql.getUnnamedParameterCount() > 0) {
            throw new IllegalStateException(
                    "You can't mix named and traditional ? placeholders. You have "
                            + parsedSql.getNamedParameterCount() + " named parameter(s) and "
                            + parsedSql.getUnnamedParameterCount()
                            + " traditonal placeholder(s) in [" + parsedSql.getOriginalSql() + "]");
        }
        List<String> paramNames = parsedSql.getParameterNames();
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            Object value = paramSource.get(paramName);
            if (value instanceof Collection<?>) {
                Collection<?> collection = (Collection<?>) value;
                for (Object object : collection) {
                    paramArray.add(object);
                }
            } else {
                paramArray.add(value);
            }
        }
        return paramArray.toArray(new Object[paramArray.size()]);
    }

    /**
     * Determine whether a parameter name ends at the current position, that is, whether the given character qualifies as a separator.
     */
    private static boolean isParameterSeparator(char c) {
        if (Character.isWhitespace(c)) {
            return true;
        }
        for (char separator : PARAMETER_SEPARATORS) {
            if (c == separator) {
                return true;
            }
        }
        return false;
    }
}
