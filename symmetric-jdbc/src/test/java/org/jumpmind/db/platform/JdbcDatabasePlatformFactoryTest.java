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
package org.jumpmind.db.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.jumpmind.db.platform.greenplum.GreenplumPlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlDatabasePlatform;
import org.junit.jupiter.api.Test;

class JdbcDatabasePlatformFactoryTest {
    private final JdbcDatabasePlatformFactory factory = new JdbcDatabasePlatformFactory();

    private Connection createNonGreenplumConnection() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet greenplumResultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(GreenplumPlatform.SQL_GET_GREENPLUM_COUNT)).thenReturn(greenplumResultSet);
        when(greenplumResultSet.next()).thenReturn(true);
        when(greenplumResultSet.getInt(1)).thenReturn(0);
        return connection;
    }

    private void stubAuroraVersionQuery(Connection connection, boolean isAurora) throws Exception {
        Statement statement = connection.createStatement();
        if (isAurora) {
            when(statement.executeQuery("select aurora_version()")).thenReturn(mock(ResultSet.class));
        } else {
            when(statement.executeQuery("select aurora_version()"))
                    .thenThrow(new SQLException("function aurora_version() does not exist"));
        }
    }

    private DatabaseVersion newPostgresVersion(String protocol, String productName) {
        DatabaseVersion nameVersion = new DatabaseVersion();
        nameVersion.setProtocol(protocol);
        nameVersion.setName(productName);
        nameVersion.setVersion(15);
        nameVersion.setMinorVersion(0);
        return nameVersion;
    }

    @Test
    void testDetermineDatabaseNameVersionSubprotocol_auroraDetected_setsAuroraName() throws Exception {
        Connection connection = createNonGreenplumConnection();
        stubAuroraVersionQuery(connection, true);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        DatabaseVersion nameVersion = newPostgresVersion(PostgreSqlDatabasePlatform.JDBC_SUBPROTOCOL, "PostgreSQL");
        factory.determineDatabaseNameVersionSubprotocol(null, connection, metaData, nameVersion);
        assertEquals(DatabaseNamesConstants.AURORA_POSTGRESQL, nameVersion.getName());
    }

    @Test
    void testDetermineDatabaseNameVersionSubprotocol_vanillaPostgres95_unaffected() throws Exception {
        Connection connection = createNonGreenplumConnection();
        stubAuroraVersionQuery(connection, false);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseMajorVersion()).thenReturn(15);
        when(metaData.getDatabaseMinorVersion()).thenReturn(0);
        DatabaseVersion nameVersion = newPostgresVersion(PostgreSqlDatabasePlatform.JDBC_SUBPROTOCOL, "PostgreSQL");
        factory.determineDatabaseNameVersionSubprotocol(null, connection, metaData, nameVersion);
        assertEquals(DatabaseNamesConstants.POSTGRESQL95, nameVersion.getName());
    }

    @Test
    void testDetermineDatabaseNameVersionSubprotocol_awsWrapperPostgres_stillDetectsAurora() throws Exception {
        Connection connection = createNonGreenplumConnection();
        stubAuroraVersionQuery(connection, true);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        DatabaseVersion nameVersion = newPostgresVersion(JdbcDatabasePlatformFactory.AWS_JDBC_WRAPPER_SUBPROTOCOL, "PostgreSQL");
        factory.determineDatabaseNameVersionSubprotocol(null, connection, metaData, nameVersion);
        assertEquals(DatabaseNamesConstants.AURORA_POSTGRESQL, nameVersion.getName());
    }

    @Test
    void testDetermineDatabaseNameVersionSubprotocol_awsWrapperNonPostgres_notTreatedAsPostgres() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        DatabaseVersion nameVersion = newPostgresVersion(JdbcDatabasePlatformFactory.AWS_JDBC_WRAPPER_SUBPROTOCOL, "MySQL");
        factory.determineDatabaseNameVersionSubprotocol(null, connection, metaData, nameVersion);
        assertEquals("MySQL", nameVersion.getName());
    }
}
