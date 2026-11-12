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
package org.jumpmind.db.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.db.platform.clickhouse.ClickHouseDdlBuilder;
import org.jumpmind.db.platform.hana.HanaDdlBuilder;
import org.jumpmind.db.platform.mysql.MySqlDdlBuilder;
import org.junit.jupiter.api.Test;

class DdlBuilderFactoryTest {
    private final DdlBuilderFactory factory = new DdlBuilderFactory();

    @Test
    void testCreate_createsClickHouseBuilder() {
        assertTrue(DdlBuilderFactory.getInstance().create(DatabaseNamesConstants.CLICKHOUSE) instanceof ClickHouseDdlBuilder);
    }

    @Test
    void testCreateReturnsHanaDdlBuilderForHdb() {
        assertEquals(HanaDdlBuilder.class, factory.create(DatabaseNamesConstants.HANA).getClass());
    }

    @Test
    void testCreateMatchesHanaNameIgnoringCase() {
        assertEquals(HanaDdlBuilder.class, factory.create("HDB").getClass());
    }

    @Test
    void testCreateReturnsMySqlDdlBuilderForMySql() {
        assertEquals(MySqlDdlBuilder.class, factory.create(DatabaseNamesConstants.MYSQL).getClass());
    }

    @Test
    void testCreateReturnsNullForUnmappedDatabase() {
        assertNull(factory.create("unmapped"));
    }
}
