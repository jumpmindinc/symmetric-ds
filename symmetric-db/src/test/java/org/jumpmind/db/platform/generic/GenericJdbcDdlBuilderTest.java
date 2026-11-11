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
package org.jumpmind.db.platform.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.SqlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenericJdbcDdlBuilderTest {
    private static final String DATABASE_NAME = "generic";
    private IDatabasePlatform platform;
    private DataSource dataSource;
    private Connection connection;
    private DatabaseMetaData metaData;
    private ResultSet typeInfo;

    @BeforeEach
    void setUp() throws SQLException {
        platform = mock(IDatabasePlatform.class);
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        metaData = mock(DatabaseMetaData.class);
        typeInfo = mock(ResultSet.class);
        when(platform.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTypeInfo()).thenReturn(typeInfo);
    }

    @Test
    void testMapsLongVarcharToTypeReportedByDatabase() throws SQLException {
        when(typeInfo.next()).thenReturn(true, false);
        when(typeInfo.getString("TYPE_NAME")).thenReturn("TEXT");
        when(typeInfo.getInt("DATA_TYPE")).thenReturn(Types.LONGVARCHAR);
        GenericJdbcDdlBuilder builder = new GenericJdbcDdlBuilder(DATABASE_NAME, platform);
        assertEquals("TEXT", builder.getDatabaseInfo().getNativeType(Types.LONGVARCHAR));
    }

    @Test
    void testClosesConnectionAndTypeInfo() throws SQLException {
        when(typeInfo.next()).thenReturn(false);
        new GenericJdbcDdlBuilder(DATABASE_NAME, platform);
        verify(typeInfo, times(3)).close();
        verify(connection).close();
    }

    @Test
    void testDisablesTriggersAndDelimitedIdentifiers() throws SQLException {
        when(typeInfo.next()).thenReturn(false);
        GenericJdbcDdlBuilder builder = new GenericJdbcDdlBuilder(DATABASE_NAME, platform);
        assertFalse(builder.getDatabaseInfo().isTriggersSupported());
        assertFalse(builder.getDatabaseInfo().isDelimitedIdentifiersSupported());
        assertEquals("", builder.getDatabaseInfo().getDelimiterToken());
    }

    @Test
    void testWrapsConnectionFailureInSqlException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("unavailable"));
        assertThrows(SqlException.class, () -> new GenericJdbcDdlBuilder(DATABASE_NAME, platform));
    }
}
