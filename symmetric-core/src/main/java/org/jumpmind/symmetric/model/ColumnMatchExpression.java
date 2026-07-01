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
package org.jumpmind.symmetric.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.SyntaxParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColumnMatchExpression {
    public static final String EQUALS = "=";
    public static final String NOT_EQUALS = "!=";
    public static final String CONTAINS = "contains";
    public static final String NOT_CONTAINS = "not contains";
    public static final String HAS = "has";
    public static final String NOT_HAS = "not has";
    public static final String STARTS_WITH = "starts with";
    public static final String NOT_STARTS_WITH = "not starts with";
    public static final String ENDS_WITH = "ends with";
    public static final String NOT_ENDS_WITH = "not ends with";
    public static final String NULL_VALUE = "NULL";
    protected static Logger log = LoggerFactory.getLogger(ColumnMatchExpression.class);
    boolean hasEquals;
    boolean hasNotEquals;
    boolean hasContains;
    boolean hasNotContains;
    boolean hasHas;
    boolean hasNotHas;
    boolean hasStartsWith;
    boolean hasNotStartsWith;
    boolean hasEndsWith;
    boolean hasNotEndsWith;
    String[] tokens;
    String operator;

    public ColumnMatchExpression(String operator, String[] tokens) {
        this.tokens = tokens;
        this.operator = operator;
        if (operator.equals(EQUALS)) {
            hasEquals = true;
        } else if (operator.equals(NOT_EQUALS)) {
            hasNotEquals = true;
        } else if (operator.equals(CONTAINS)) {
            hasContains = true;
        } else if (operator.equals(NOT_CONTAINS)) {
            hasNotContains = true;
        } else if (operator.equals(HAS)) {
            hasHas = true;
        } else if (operator.equals(NOT_HAS)) {
            hasNotHas = true;
        } else if (operator.equals(STARTS_WITH)) {
            hasStartsWith = true;
        } else if (operator.equals(NOT_STARTS_WITH)) {
            hasNotStartsWith = true;
        } else if (operator.equals(ENDS_WITH)) {
            hasEndsWith = true;
        } else if (operator.equals(NOT_ENDS_WITH)) {
            hasNotEndsWith = true;
        }
    }

    public static List<ColumnMatchExpression> parse(String expression) throws SyntaxParsingException {
        List<ColumnMatchExpression> expressions = new ArrayList<ColumnMatchExpression>();
        if (!StringUtils.isBlank(expression)) {
            String[] operators = { NOT_EQUALS, EQUALS, NOT_CONTAINS, CONTAINS, NOT_HAS, HAS, NOT_STARTS_WITH,
                    STARTS_WITH, NOT_ENDS_WITH, ENDS_WITH };
            String[] expTokens = expression.split("\\s*(\\s+or|\\s+OR)?(\r\n|\r|\n)(or\\s+|OR\\s+)?\\s*" +
                    "|\\s+or\\s+" +
                    "|\\s+OR\\s+");
            if (expTokens != null) {
                for (String t : expTokens) {
                    if (!StringUtils.isBlank(t)) {
                        boolean isFound = false;
                        for (String operator : operators) {
                            if (t.contains(operator)) {
                                String[] tokens = t.split(operator);
                                if (tokens.length == 2) {
                                    tokens[0] = parseColumn(tokens[0]);
                                    tokens[1] = parseValue(tokens[1]);
                                    expressions.add(new ColumnMatchExpression(operator, tokens));
                                    isFound = true;
                                    break;
                                }
                            }
                        }
                        if (!isFound) {
                            log.warn("The provided column match expression was invalid: {}.  The full expression is {}.", t, expression);
                            throw new SyntaxParsingException("The provided column match expression was invalid: " + t + ".  The full expression is "
                                    + expression + ".");
                        }
                    }
                }
            }
        } else {
            log.warn("The provided column match expression is empty");
        }
        return expressions;
    }

    /**
     * Parse a column (the first half of a column match expression).
     */
    private static String parseColumn(String value) {
        return value.trim();
    }

    /**
     * Parse a value (the second half of a column match expression).
     */
    private static String parseValue(String value) {
        value = value.trim();
        // Check for ticks around the value.
        if (value.charAt(0) == '\''
                && value.charAt(value.length() - 1) == '\'') {
            // remove first and last tick
            value = value.substring(1, value.length() - 1);
            // replace all double ticks with a single tick only if value was surrounded with ticks
            value = value.replaceAll("''", "'");
        }
        return value;
    }

    public boolean run(String columnValue, String compareValue) {
        boolean result = false;
        if (hasEquals && ((columnValue == null && compareValue == null) ||
                (columnValue != null && columnValue.equals(compareValue)))) {
            result = true;
        } else if (hasNotEquals && ((columnValue == null && compareValue != null) ||
                (columnValue != null && !columnValue.equals(compareValue)))) {
            result = true;
        } else if (hasContains && columnValue != null && compareValue != null &&
                ArrayUtils.contains(compareValue.split(","), columnValue)) {
            result = true;
        } else if (hasNotContains && columnValue != null && compareValue != null &&
                !ArrayUtils.contains(compareValue.split(","), columnValue)) {
            result = true;
        } else if (hasHas && ((columnValue == null && compareValue == null) ||
                (columnValue != null && columnValue.contains(compareValue)))) {
            result = true;
        } else if (hasNotHas && !((columnValue == null && compareValue == null) ||
                (columnValue != null && columnValue.contains(compareValue)))) {
            result = true;
        } else if (hasStartsWith && ((columnValue == null && compareValue == null) ||
                (columnValue != null && columnValue.startsWith(compareValue)))) {
            result = true;
        } else if (hasNotStartsWith && !((columnValue == null && compareValue == null) ||
                (columnValue != null && columnValue.startsWith(compareValue)))) {
            result = true;
        } else if (hasEndsWith && ((columnValue == null && compareValue == null) ||
                (columnValue != null && columnValue.endsWith(compareValue)))) {
            result = true;
        } else if (hasNotEndsWith && !((columnValue == null && compareValue == null) ||
                (columnValue != null && columnValue.endsWith(compareValue)))) {
            result = true;
        }
        return result;
    }

    public String[] getTokens() {
        return tokens;
    }

    public String getOperator() {
        return operator;
    }

    public boolean hasEquals() {
        return hasEquals;
    }

    public boolean hasNotEquals() {
        return hasNotEquals;
    }

    public boolean hasContains() {
        return hasContains;
    }

    public boolean hasNotContains() {
        return hasNotContains;
    }

    public boolean isHasHas() {
        return hasHas;
    }

    public void setHasHas(boolean hasHas) {
        this.hasHas = hasHas;
    }

    public boolean isHasNotHas() {
        return hasNotHas;
    }

    public void setHasNotHas(boolean hasNotHas) {
        this.hasNotHas = hasNotHas;
    }

    public boolean isHasStartsWith() {
        return hasStartsWith;
    }

    public void setHasStartsWith(boolean hasStartsWith) {
        this.hasStartsWith = hasStartsWith;
    }

    public boolean isHasNotStartsWith() {
        return hasNotStartsWith;
    }

    public void setHasNotStartsWith(boolean hasNotStartsWith) {
        this.hasNotStartsWith = hasNotStartsWith;
    }

    public boolean isHasEndsWith() {
        return hasEndsWith;
    }

    public void setHasEndsWith(boolean hasEndsWith) {
        this.hasEndsWith = hasEndsWith;
    }

    public boolean isHasNotEndsWith() {
        return hasNotEndsWith;
    }

    public void setHasNotEndsWith(boolean hasNotEndsWith) {
        this.hasNotEndsWith = hasNotEndsWith;
    }
}