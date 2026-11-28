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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jumpmind.db.DbTestUtils;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.common.TableConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExtractRequestByLoadDbTest {
    private static final String EXTRACT_REQUEST = TableConstants.getTableName("sym", TableConstants.SYM_EXTRACT_REQUEST);
    private static final long LOAD_ID = 2;
    private static IDatabasePlatform platform;
    private static Table extractRequestTable;
    private static DataExtractorServiceSqlMap sqlMap;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        platform = DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT);
        Database database = platform.readDatabaseFromXml("/symmetric-schema.xml", true);
        extractRequestTable = database.findTable(TableConstants.SYM_EXTRACT_REQUEST);
        extractRequestTable.setName(platform.alterCaseToMatchDatabaseDefaultCase(EXTRACT_REQUEST));
        Database extractRequestDatabase = new Database();
        extractRequestDatabase.addTable(extractRequestTable);
        platform.createDatabase(extractRequestDatabase, true, false);
        sqlMap = new DataExtractorServiceSqlMap(platform, Collections.singletonMap(TableConstants.SYM_EXTRACT_REQUEST, EXTRACT_REQUEST));
        ISqlTemplate sqlTemplate = platform.getSqlTemplate();
        String insert = "insert into " + EXTRACT_REQUEST
                + " (request_id, source_node_id, node_id, queue, status, start_batch_id, end_batch_id, trigger_id, router_id, load_id, table_name, last_update_time, create_time)"
                + " values (?, ?, ?, 'reload', 'OK', 0, 0, ?, 'store to hub', ?, ?, current_timestamp, current_timestamp)";
        sqlTemplate.update(insert, 1, "store1", "hub", "product", LOAD_ID, "product");
        sqlTemplate.update(insert, 2, "store1", "hub", "sale", LOAD_ID, "sale");
        sqlTemplate.update(insert, 3, "store2", "hub", "sale", LOAD_ID, "sale");
        sqlTemplate.update(insert, 4, "store2", "hub", "customer", LOAD_ID, "customer");
        sqlTemplate.update(insert, 5, "store2", "hub", "customer", LOAD_ID + 1, "customer");
    }

    @AfterAll
    static void tearDownAfterClass() {
        platform.dropTables(true, extractRequestTable);
    }

    @Test
    void testSelectByLoadIdAndSourceNodeIdAndNodeId_returnsOnlyThatSourceNodesTables() {
        assertEquals(List.of(3L, 4L), queryRequestIds("selectTablesForExtractByLoadIdAndSourceNodeIdAndNodeId", LOAD_ID, "store2", "hub"));
        assertEquals(List.of(1L, 2L), queryRequestIds("selectTablesForExtractByLoadIdAndSourceNodeIdAndNodeId", LOAD_ID, "store1", "hub"));
    }

    @Test
    void testSelectByLoadIdAndNodeId_mixesSourceNodesThatShareTheLoadId() {
        assertEquals(List.of(1L, 2L, 3L, 4L), queryRequestIds("selectTablesForExtractByLoadIdAndNodeId", LOAD_ID, "hub"));
    }

    private static List<Long> queryRequestIds(String sqlName, Object... args) {
        return platform.getSqlTemplate().query(sqlMap.getSql(sqlName), args).stream().map(row -> row.getLong("request_id"))
                .collect(Collectors.toList());
    }
}
