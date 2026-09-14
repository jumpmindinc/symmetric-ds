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
package org.jumpmind.symmetric.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class FilterCriterion {
    public enum FilterOption {
        EQUALS("Equals", "=", "="), NOT_EQUALS("Not Equals", "!=", "<>"), GREATER("Greater Than", ">", ">"), GREATER_EQUAL("Greater Than or Equal", ">=",
                ">="), LESS("Less Than", "<", "<"), LESS_EQUAL("Less Than or Equal", "<=", "<="), IN_LIST("In List", "in", "in"), NOT_IN_LIST("Not In List",
                        "not in", "not in"), BETWEEN("Between", "between", "between"), CONTAINS("Contains", "contains", "like"), NOT_CONTAINS("Not Contains",
                                "not contains", "not like"), STARTS_WITH("Starts With", "starts with", "like");

        private String description;
        private String abbrev;
        private String sql;

        FilterOption(String description, String abbrev, String sql) {
            this.description = description;
            this.abbrev = abbrev;
            this.sql = sql;
        }

        @Override
        public String toString() {
            return description;
        }

        public String toAbbrev() {
            return abbrev;
        }

        public String toSql() {
            return sql;
        }
    }

    private String propertyId;
    private List<Object> values;
    private FilterOption option;

    public FilterCriterion(String propertyId, List<Object> values, FilterOption option) {
        this.propertyId = propertyId;
        this.values = values;
        this.option = option;
    }

    public FilterCriterion(String propertyId, Object value, FilterOption option) {
        this.propertyId = propertyId;
        this.values = new ArrayList<>(1);
        this.values.add(value);
        this.option = option;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }

    public FilterOption getOption() {
        return option;
    }

    public void setOption(FilterOption option) {
        this.option = option;
    }

    @Override
    public String toString() {
        return propertyId + " " + option.toAbbrev() + " " + values;
    }

    public String toAbbrevString() {
        StringBuilder sb = new StringBuilder();
        sb.append(propertyId);
        sb.append(" ");
        sb.append(option.toAbbrev());
        sb.append(" ");
        if (values.toString().length() > 20 && values.size() > 1) {
            sb.append("[");
            sb.append(values.size());
            sb.append(" values]");
        } else {
            sb.append(StringUtils.abbreviate(values.toString(), 20));
        }
        return sb.toString();
    }
}
