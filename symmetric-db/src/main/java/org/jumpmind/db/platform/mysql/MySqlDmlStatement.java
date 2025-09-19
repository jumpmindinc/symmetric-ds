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
package org.jumpmind.db.platform.mysql;

import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;

public class MySqlDmlStatement extends DmlStatement {
    public MySqlDmlStatement(DmlStatementOptions options) {
        super(options);
    }

    @Override
    protected void appendColumnParameter(StringBuilder sql, Column column) {
        if (Strings.CI.equals(column.getJdbcTypeName(), TypeMap.GEOMETRY) ||
                Strings.CI.equals(column.getJdbcTypeName(), TypeMap.GEOGRAPHY)) {
            sql.append("geomfromtext(?)").append(",");
        } else {
            super.appendColumnParameter(sql, column);
        }
    }

    @Override
    protected void appendColumnEquals(StringBuilder sql, Column column) {
        if (Strings.CI.equals(column.getJdbcTypeName(), TypeMap.GEOMETRY) ||
                Strings.CI.equals(column.getJdbcTypeName(), TypeMap.GEOGRAPHY)) {
            sql.append(quote).append(column.getName()).append(quote).append(" = ").append("geomfromtext(?)");
        } else {
            super.appendColumnEquals(sql, column);
        }
    }

    @Override
    protected String escapeText(String value) {
        value = super.escapeText(value);
        value = value.replace("\\", "\\\\");
        value = value.replace("$", "\\$");
        return value;
    }
}
