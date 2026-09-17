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
package org.jumpmind.symmetric.db.derby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DerbyTriggerTemplateTest {
    private DerbyTriggerTemplate triggerTemplate;

    @BeforeEach
    void setUp() {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        triggerTemplate = new DerbyTriggerTemplate(symmetricDialect);
    }

    @Test
    void testGetPrimaryKeyWhereString_withNumericColumn() {
        Column id = new Column("ID", true, Types.INTEGER, 10, 0);
        assertEquals("'\"ID\"='||rtrim(char(old.\"ID\"))||''",
                triggerTemplate.getPrimaryKeyWhereString("old", new Column[] { id }));
    }

    @Test
    void testGetPrimaryKeyWhereString_withStringColumn() {
        Column name = new Column("NAME", true, Types.VARCHAR, 50, 0);
        assertEquals("'\"NAME\"='''||old.\"NAME\"||''''",
                triggerTemplate.getPrimaryKeyWhereString("old", new Column[] { name }));
    }

    @Test
    void testGetPrimaryKeyWhereString_withTimestampColumn() {
        Column created = new Column("CREATE_TIME", true, Types.TIMESTAMP, 0, 0);
        assertEquals("'\"CREATE_TIME\"={ts '''||rtrim(char(old.\"CREATE_TIME\"))||'''}'",
                triggerTemplate.getPrimaryKeyWhereString("old", new Column[] { created }));
    }

    @Test
    void testGetPrimaryKeyWhereString_withMultipleColumns() {
        Column id = new Column("ID", true, Types.INTEGER, 10, 0);
        Column name = new Column("NAME", true, Types.VARCHAR, 50, 0);
        assertEquals("'\"ID\"='||rtrim(char(old.\"ID\"))||' and \"NAME\"='''||old.\"NAME\"||''''",
                triggerTemplate.getPrimaryKeyWhereString("old", new Column[] { id, name }));
    }

    @Test
    void testGetPrimaryKeyWhereString_skipsBinaryColumns() {
        Column id = new Column("ID", true, Types.INTEGER, 10, 0);
        Column payload = new Column("PAYLOAD", true, Types.BLOB, 0, 0);
        assertEquals("'\"ID\"='||rtrim(char(old.\"ID\"))||''",
                triggerTemplate.getPrimaryKeyWhereString("old", new Column[] { id, payload }));
    }

    @Test
    void testGetPrimaryKeyWhereString_withOnlyBinaryColumns() {
        Column payload = new Column("PAYLOAD", true, Types.BLOB, 0, 0);
        assertEquals("''", triggerTemplate.getPrimaryKeyWhereString("old", new Column[] { payload }));
    }

    @Test
    void testGetPrimaryKeyWhereString_withNoColumns() {
        assertEquals("''", triggerTemplate.getPrimaryKeyWhereString("old", new Column[0]));
    }

    @Test
    void testGetPrimaryKeyWhereString_usesGivenAlias() {
        Column id = new Column("ID", true, Types.INTEGER, 10, 0);
        assertEquals("'\"ID\"='||rtrim(char(new.\"ID\"))||''",
                triggerTemplate.getPrimaryKeyWhereString("new", new Column[] { id }));
    }
}
