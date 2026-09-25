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
package org.jumpmind.db.platform.sqlanywhere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlAnywhere12DatabasePlatformTest {
    private SqlAnywhere12DatabasePlatform platform;

    @BeforeEach
    void setUp() throws SQLException {
        platform = new SqlAnywhere12DatabasePlatform(newDataSourceMock(), new SqlTemplateSettings());
    }

    @Test
    void testGetClassName() {
        SqlAnywhere12DatabasePlatform mockedPlatform = mock(SqlAnywhere12DatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(SqlAnywhere12DatabasePlatform.class.getName(), mockedPlatform.getClassName());
    }

    @Test
    void testGetClassName_differsFromSuperclass() {
        assertEquals(SqlAnywhere12DatabasePlatform.class.getName(), platform.getClassName());
    }

    @Test
    void testExtendsSqlAnywhereDatabasePlatform() {
        assertInstanceOf(SqlAnywhereDatabasePlatform.class, platform);
    }

    @Test
    void testGetName_inheritsSqlAnywhere() {
        assertEquals(DatabaseNamesConstants.SQLANYWHERE, platform.getName());
    }

    @Test
    void testCreateDdlBuilder_inheritsSqlAnywhereBuilder() {
        assertInstanceOf(SqlAnywhereDdlBuilder.class, platform.createDdlBuilder());
    }

    private DataSource newDataSourceMock() throws SQLException {
        DatabaseMetaData metaDataMock = mock(DatabaseMetaData.class);
        when(metaDataMock.getJDBCMajorVersion()).thenReturn(4);
        when(metaDataMock.getURL()).thenReturn("jdbc:sybase:Tds:localhost:2638");
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.getMetaData()).thenReturn(metaDataMock);
        DataSource dataSourceMock = mock(DataSource.class);
        when(dataSourceMock.getConnection()).thenReturn(connectionMock);
        return dataSourceMock;
    }
}
