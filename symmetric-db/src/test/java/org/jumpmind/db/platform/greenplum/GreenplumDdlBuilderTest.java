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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.postgresql.PostgreSqlDdlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GreenplumDdlBuilderTest {
    private GreenplumDdlBuilder ddlBuilder;

    @BeforeEach
    void setUp() {
        ddlBuilder = new GreenplumDdlBuilder();
    }

    @Test
    void testConstructor_disablesTriggerSupport() {
        assertFalse(ddlBuilder.getDatabaseInfo().isTriggersSupported());
        assertTrue(new PostgreSqlDdlBuilder().getDatabaseInfo().isTriggersSupported());
    }

    @Test
    void testWriteCascadeAttributesForForeignKey_withCascade_writesNothing() {
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKey(cascadingForeignKey(), ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testCreateTables_differsFromPostgreSql() {
        String sql = new PostgreSqlDdlBuilder().createTables(databaseWithCascadingForeignKey(), false);
        assertTrue(sql.contains("ON DELETE CASCADE"));
        assertTrue(sql.contains("ON UPDATE SET NULL"));
    }

    @Test
    void testCreateTables_omitsCascadeActions() {
        String sql = ddlBuilder.createTables(databaseWithCascadingForeignKey(), false);
        assertTrue(sql.contains("FOREIGN KEY"));
        assertFalse(sql.contains("ON DELETE"));
        assertFalse(sql.contains("ON UPDATE"));
    }

    private Database databaseWithCascadingForeignKey() {
        Column parentId = new Column("id", true, Types.INTEGER, 0, 0);
        Table parent = new Table("parent", parentId);
        Column childId = new Column("id", true, Types.INTEGER, 0, 0);
        Column parentRef = new Column("parent_id", false, Types.INTEGER, 0, 0);
        Table child = new Table("child", childId, parentRef);
        ForeignKey foreignKey = cascadingForeignKey();
        foreignKey.setForeignTable(parent);
        foreignKey.addReference(new Reference(parentRef, parentId));
        child.addForeignKey(foreignKey);
        Database database = new Database();
        database.addTable(parent);
        database.addTable(child);
        return database;
    }

    private ForeignKey cascadingForeignKey() {
        ForeignKey foreignKey = new ForeignKey("fk_child_parent");
        foreignKey.setOnDeleteAction(ForeignKeyAction.CASCADE);
        foreignKey.setOnUpdateAction(ForeignKeyAction.SETNULL);
        return foreignKey;
    }
}
