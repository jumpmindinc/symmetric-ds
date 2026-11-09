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

import org.jumpmind.db.platform.clickhouse.ClickHouseDatabasePlatform;
import org.junit.jupiter.api.Test;

class JdbcDatabasePlatformFactoryClickHouseTest {
    private final JdbcDatabasePlatformFactory factory = new JdbcDatabasePlatformFactory();

    @Test
    void testFindPlatformClass_byProductName() {
        DatabaseVersion version = new DatabaseVersion();
        version.setName("ClickHouse");
        version.setVersion(26);
        version.setProtocol("unknown");
        assertEquals(ClickHouseDatabasePlatform.class, factory.findPlatformClass(version));
    }

    @Test
    void testFindPlatformClass_byClickHouseSubProtocol() {
        assertEquals(ClickHouseDatabasePlatform.class, factory.findPlatformClass(versionWithProtocol("clickhouse")));
    }

    @Test
    void testFindPlatformClass_byShortChSubProtocol() {
        assertEquals(ClickHouseDatabasePlatform.class, factory.findPlatformClass(versionWithProtocol("ch")));
    }

    private DatabaseVersion versionWithProtocol(String protocol) {
        DatabaseVersion version = new DatabaseVersion();
        version.setName("Some Other Name");
        version.setVersion(1);
        version.setProtocol(protocol);
        return version;
    }
}
