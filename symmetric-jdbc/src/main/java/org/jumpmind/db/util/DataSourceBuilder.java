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

import javax.sql.DataSource;

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
interface DataSourceBuilder {
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
    DataSource build(TypedProperties properties, String driverClassName, String user, String password);
}
