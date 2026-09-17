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
package org.jumpmind.db.platform.redshift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedshiftJdbcSqlTemplateTest {
    private RedshiftJdbcSqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSourceMock = mock(DataSource.class);
        sqlTemplate = new RedshiftJdbcSqlTemplate(dataSourceMock, new SqlTemplateSettings(), new SymmetricLobHandler(), new DatabaseInfo());
    }

    @Test
    void testConstructor_requiresAutoCommitFalseToSetFetchSize() {
        assertTrue(sqlTemplate.isRequiresAutoCommitFalseToSetFetchSize());
    }

    @Test
    void testAllowsNullForIdentityColumn_returnsFalse() {
        assertFalse(sqlTemplate.allowsNullForIdentityColumn());
    }

    @Test
    void testGetSelectLastInsertIdSql_forSymDataSequence_returnsMaxDataIdQuery() {
        assertEquals("select max(data_id) from sym_data", sqlTemplate.getSelectLastInsertIdSql("sym_data_data_id"));
    }

    @Test
    void testGetSelectLastInsertIdSql_forOtherSequence_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> sqlTemplate.getSelectLastInsertIdSql("some_other_seq"));
    }
}
