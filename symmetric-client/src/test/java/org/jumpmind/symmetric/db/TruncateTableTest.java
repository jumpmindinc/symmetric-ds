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
package org.jumpmind.symmetric.db;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.db.db2.Db2SymmetricDialect;
import org.jumpmind.symmetric.db.firebird.FirebirdSymmetricDialect;
import org.jumpmind.symmetric.db.hsqldb2.HsqlDb2SymmetricDialect;
import org.jumpmind.symmetric.db.interbase.InterbaseSymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TruncateTableTest {
    private static final String TABLE_NAME_WITH_STATEMENT = "\"item\";drop table x'";
    private static final String EXPECTED_DELETE = "delete from \"item\"drop table x";
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        DatabaseInfo databaseInfo = new DatabaseInfo();
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(ddlBuilder.getDatabaseInfo()).thenReturn(databaseInfo);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(platform.getDatabaseInfo()).thenReturn(databaseInfo);
    }

    @Test
    void db2_stripsStatementCharactersFromTableName() {
        new Db2SymmetricDialect(parameterService, platform).truncateTable(TABLE_NAME_WITH_STATEMENT);
        verify(sqlTemplate).update(EXPECTED_DELETE);
    }

    @Test
    void hsqlDb2_stripsStatementCharactersFromTableName() {
        new HsqlDb2SymmetricDialect(parameterService, platform).truncateTable(TABLE_NAME_WITH_STATEMENT);
        verify(sqlTemplate).update(EXPECTED_DELETE);
    }

    @Test
    void firebird_stripsStatementCharactersFromTableName() {
        new FirebirdSymmetricDialect(parameterService, platform).truncateTable(TABLE_NAME_WITH_STATEMENT);
        verify(sqlTemplate).update(EXPECTED_DELETE);
    }

    @Test
    void interbase_stripsStatementCharactersFromTableName() {
        new InterbaseSymmetricDialect(parameterService, platform).truncateTable(TABLE_NAME_WITH_STATEMENT);
        verify(sqlTemplate).update(EXPECTED_DELETE);
    }
}
