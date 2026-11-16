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
package org.jumpmind.symmetric.io.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DbExport.Compatible;
import org.jumpmind.symmetric.io.data.DbExport.Format;
import org.junit.jupiter.api.Test;

class DbExportTest {
    @Test
    void testHanaPlatformMapsToHdbCompatible() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.HANA);
        assertEquals(Compatible.HDB, new DbExport(platform).getCompatible());
    }

    @Test
    void testH2PlatformMapsToH2Compatible() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.H2);
        assertEquals(Compatible.H2, new DbExport(platform).getCompatible());
    }

    @Test
    void testSqlExportFromHanaPlatformWritesHanaDdl() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.HANA);
        String output = exportTableDefinition(new DbExport(platform)).toLowerCase();
        assertTrue(output.contains("create table"), output);
        assertTrue(output.contains("nvarchar(50)"), output);
    }

    @Test
    void testSqlExportWithHdbCompatibleWritesHanaDdl() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.H2);
        DbExport export = new DbExport(platform);
        export.setCompatible(Compatible.HDB);
        String output = exportTableDefinition(export).toLowerCase();
        assertTrue(output.contains("nvarchar(50)"), output);
    }

    @Test
    void testSybaseCompatibleUsesAseDatabaseName() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.H2);
        DbExport export = new DbExport(platform);
        export.setCompatible(Compatible.SYBASE);
        assertEquals(DatabaseNamesConstants.ASE, export.getDatabaseName());
    }

    @Test
    void testSqlExportWithSybaseCompatibleWritesDdl() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.H2);
        DbExport export = new DbExport(platform);
        export.setCompatible(Compatible.SYBASE);
        String output = exportTableDefinition(export).toLowerCase();
        assertTrue(output.contains("create table"), output);
    }

    @Test
    void testSqlExportWithMariaDbCompatibleWritesDdl() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.H2);
        DbExport export = new DbExport(platform);
        export.setCompatible(Compatible.MARIADB);
        String output = exportTableDefinition(export).toLowerCase();
        assertTrue(output.contains("create table"), output);
    }

    private String exportTableDefinition(DbExport export) throws Exception {
        export.setFormat(Format.SQL);
        export.setNoCreateInfo(false);
        export.setNoData(true);
        return export.exportTables(new Table[] { newTable() });
    }

    private Table newTable() {
        Column id = new Column("id", true, Types.INTEGER, 10, 0);
        id.setJdbcTypeCode(Types.INTEGER);
        id.setJdbcTypeName("INTEGER");
        Column name = new Column("name", false, Types.VARCHAR, 50, 0);
        name.setJdbcTypeCode(Types.VARCHAR);
        name.setJdbcTypeName("VARCHAR");
        return new Table("export_test", id, name);
    }
}
