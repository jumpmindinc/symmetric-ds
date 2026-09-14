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
package org.jumpmind.db.platform.greenplum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GreenplumPlatformTest {
    private GreenplumPlatform platform;

    @BeforeEach
    void setUp() {
        DataSource dataSourceMock = mock(DataSource.class);
        platform = new GreenplumPlatform(dataSourceMock, new SqlTemplateSettings());
    }

    @Test
    void testGetClassName() {
        GreenplumPlatform mockedPlatform = mock(GreenplumPlatform.class, CALLS_REAL_METHODS);
        assertEquals(GreenplumPlatform.class.getName(), mockedPlatform.getClassName());
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.GREENPLUM, platform.getName());
    }

    @Test
    void testCreateDdlBuilder() {
        assertInstanceOf(GreenplumDdlBuilder.class, platform.createDdlBuilder());
    }

    @Test
    void testCreateDdlReader() {
        assertInstanceOf(GreenplumDdlReader.class, platform.createDdlReader());
    }

    @Test
    void testCreateSqlTemplate() {
        assertInstanceOf(GreenplumJdbcSqlTemplate.class, platform.createSqlTemplate());
    }

    @Test
    void testDetectionQueries() {
        assertEquals("select count(*) from information_schema.tables where table_name = 'gp_id'", GreenplumPlatform.SQL_GET_GREENPLUM_COUNT);
        assertEquals("select productversion from gp_version_at_initdb", GreenplumPlatform.SQL_GET_GREENPLUM_VERSION);
    }
}
