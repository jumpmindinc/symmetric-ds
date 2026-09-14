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
package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.DataContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaPerNodeDataLoaderFilterTest {
    private SchemaPerNodeDataLoaderFilter filter;
    private DataContext context;
    private Table table;

    @BeforeEach
    void setUp() {
        filter = new SchemaPerNodeDataLoaderFilter();
        filter.setTablePrefix("sym_");
        Batch batch = new Batch();
        batch.setSourceNodeId("node1");
        context = new DataContext();
        context.setBatch(batch);
        table = new Table("TEST_TABLE");
    }

    @Test
    void testBeforeWrite_setsSchemaToSourceNodeId() {
        boolean result = filter.beforeWrite(context, table, null);
        assertTrue(result);
        assertEquals("node1", table.getSchema());
    }

    @Test
    void testBeforeWrite_withSchemaPrefixPrependsPrefix() {
        filter.setSchemaPrefix("tenant_");
        boolean result = filter.beforeWrite(context, table, null);
        assertTrue(result);
        assertEquals("tenant_node1", table.getSchema());
    }

    @Test
    void testBeforeWrite_skipsWhenTableNameStartsWithPrefix() {
        Table symTable = new Table("sym_node");
        boolean result = filter.beforeWrite(context, symTable, null);
        assertTrue(result);
        assertNull(symTable.getSchema());
    }
}
