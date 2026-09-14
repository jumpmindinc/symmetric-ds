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
package org.jumpmind.db.platform.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.lang3.Strings;

/*
 * Detects which managed-cloud variant of MySQL a live JDBC connection is talking to, based on
 * SQL markers that only exist on that variant. Detection runs against the connection itself so it
 * works regardless of network topology or which JDBC driver established the connection.
 */
public final class MySqlVariantDetector {
    private MySqlVariantDetector() {
    }

    public static boolean isAuroraMySql(Connection connection) {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("select aurora_version()")) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean isCloudSqlMySql(Connection connection) {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("show variables like 'version_comment'")) {
            return rs.next() && Strings.CI.contains(rs.getString("Value"), "Google");
        } catch (SQLException e) {
            return false;
        }
    }
}
