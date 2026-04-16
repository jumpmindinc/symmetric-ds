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
package org.jumpmind.db.util;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.properties.TypedProperties;

/**
 * Convenience interface for building a {@link DataSource}.
 * 
 * The following pooling {@link DataSource} implementations are supported by this builder.
 * <ul>
 * <li>Apache DBCP2 ({@code org.apache.commons.dbcp2.BasicDataSource})</li>
 * <li>Hikari ({@code com.zaxxer.hikari.HikariDataSource})</li>
 * </ul>
 * <p>
 */
abstract class DataSourceBuilder {
    /**
     * Return a newly built {@link DataSource} instance.
     *
     * @param properties
     *            the data source properties
     * @param driverClassName
     *            the driver class name
     * @param user
     *            the user name
     * @param password
     *            the password
     * @return the built data source
     */
    abstract DataSource build(TypedProperties properties, String driverClassName, String user, String password);

    /**
     * Parses a {@code db.connection.properties} value into an ordered map of key-value pairs. Properties are delimited by {@code ;}. A literal equals sign
     * inside a value must be escaped as {@code ==}.
     *
     * @param connectionProperties
     *            the raw property value, may be blank or null
     * @return parsed key-value pairs in encounter order, or an empty map if the input is blank
     */
    protected Map<String, String> parseConnectionProperties(String connectionProperties) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(connectionProperties)) {
            return result;
        }
        for (String token : connectionProperties.split(";")) {
            String[] keyValue = token.replaceAll("==", "!!").split("=");
            if (keyValue != null && keyValue.length > 1) {
                result.put(keyValue[0], keyValue[1].replaceAll("!!", "="));
            }
        }
        return result;
    }

    /**
     * Splits a {@code db.init.sql} value into individual SQL statements. Statements are delimited by {@code ;}. A literal semicolon inside a statement must be
     * escaped as {@code ;;}.
     *
     * @param initSql
     *            the raw property value, may be blank or null
     * @return trimmed statements, or an empty array if the input is blank
     */
    protected String[] splitInitSql(String initSql) {
        if (StringUtils.isBlank(initSql)) {
            return new String[0];
        }
        String[] statements = initSql.replaceAll(";;", "!!").split(";");
        for (int i = 0; i < statements.length; i++) {
            statements[i] = statements[i].replaceAll("!!", ";").trim();
        }
        return statements;
    }
}
