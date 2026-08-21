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
package org.jumpmind.db.platform.postgresql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class PostgreSqlVariantDetectorTest {
    @Test
    void testIsAuroraPostgres_whenAuroraVersionFunctionSucceeds_returnsTrue() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select aurora_version()")).thenReturn(resultSet);
        assertTrue(PostgreSqlVariantDetector.isAuroraPostgres(connection));
    }

    @Test
    void testIsAuroraPostgres_whenAuroraVersionFunctionDoesNotExist_returnsFalse() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select aurora_version()"))
                .thenThrow(new SQLException("ERROR: function aurora_version() does not exist"));
        assertFalse(PostgreSqlVariantDetector.isAuroraPostgres(connection));
    }

    @Test
    void testIsAuroraPostgres_whenGenericSqlExceptionOccurs_returnsFalse() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select aurora_version()")).thenThrow(new SQLException("connection closed"));
        assertFalse(PostgreSqlVariantDetector.isAuroraPostgres(connection));
    }

    @Test
    void testIsAzurePostgres_whenAzureExtensionsGucSucceeds_returnsTrue() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show azure.extensions")).thenReturn(resultSet);
        assertTrue(PostgreSqlVariantDetector.isAzurePostgres(connection));
    }

    @Test
    void testIsAzurePostgres_whenAzureExtensionsGucDoesNotExist_returnsFalse() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show azure.extensions"))
                .thenThrow(new SQLException("ERROR: unrecognized configuration parameter \"azure.extensions\""));
        assertFalse(PostgreSqlVariantDetector.isAzurePostgres(connection));
    }

    @Test
    void testIsAzurePostgres_whenGenericSqlExceptionOccurs_returnsFalse() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show azure.extensions")).thenThrow(new SQLException("connection closed"));
        assertFalse(PostgreSqlVariantDetector.isAzurePostgres(connection));
    }

    @Test
    void testIsCloudSqlPostgres_whenCloudSqlIamAuthenticationGucSucceeds_returnsTrue() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show cloudsql.iam_authentication")).thenReturn(resultSet);
        assertTrue(PostgreSqlVariantDetector.isCloudSqlPostgres(connection));
    }

    @Test
    void testIsCloudSqlPostgres_whenCloudSqlIamAuthenticationGucDoesNotExist_returnsFalse() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show cloudsql.iam_authentication"))
                .thenThrow(new SQLException("ERROR: unrecognized configuration parameter \"cloudsql.iam_authentication\""));
        assertFalse(PostgreSqlVariantDetector.isCloudSqlPostgres(connection));
    }

    @Test
    void testIsCloudSqlPostgres_whenGenericSqlExceptionOccurs_returnsFalse() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show cloudsql.iam_authentication")).thenThrow(new SQLException("connection closed"));
        assertFalse(PostgreSqlVariantDetector.isCloudSqlPostgres(connection));
    }
}
