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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlAnywhereJdbcSqlTemplateTest {
    private SqlAnywhereJdbcSqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() throws SQLException {
        sqlTemplate = new SqlAnywhereJdbcSqlTemplate(newDataSourceMock(), new SqlTemplateSettings(), null,
                new SqlAnywhereDdlBuilder().getDatabaseInfo());
    }

    @Test
    void testSupportsGetGeneratedKeys() {
        assertFalse(sqlTemplate.supportsGetGeneratedKeys());
    }

    @Test
    void testGetSelectLastInsertIdSql() {
        assertEquals("select @@identity", sqlTemplate.getSelectLastInsertIdSql("sym_data_seq"));
    }

    @Test
    void testGetSelectLastInsertIdSql_ignoresSequenceName() {
        assertEquals(sqlTemplate.getSelectLastInsertIdSql("a"), sqlTemplate.getSelectLastInsertIdSql("b"));
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
