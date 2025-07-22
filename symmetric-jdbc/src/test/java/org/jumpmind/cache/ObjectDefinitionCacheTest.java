/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.cache;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ObjectDefinitionCacheTest {
    private AbstractJdbcDdlReader ddlReader;
    private IDatabasePlatform platform;

    @BeforeEach
    public void setup() {
        ddlReader = mock(AbstractJdbcDdlReader.class);
        platform = mock(IDatabasePlatform.class);
        when(ddlReader.getPlatform()).thenReturn(platform);
    }

    @Test
    public void tableNameCacheTest() {
        ObjectDefinitionCache cache = new ObjectDefinitionCache(ddlReader);
        when(ddlReader.getTableNamesFromDatabase("catalog", "schema", null)).thenReturn(Arrays.asList("table0"));
        when(platform.getClearCacheModelTimeoutInMs()).thenReturn(3600000l);
        List<String> tableNames = cache.getTableNames("catalog", "schema", null);
        assertEquals(1, tableNames.size());
        when(ddlReader.getTableNamesFromDatabase("catalog", "schema", null)).thenReturn(Arrays.asList("table0", "table1"));
        tableNames = cache.getTableNames("catalog", "schema", null);
        assertEquals(1, tableNames.size());
        cache.clearTableNameCache();
        tableNames = cache.getTableNames("catalog", "schema", null);
        assertEquals(2, tableNames.size());
        when(platform.getClearCacheModelTimeoutInMs()).thenReturn(5l);
        when(ddlReader.getTableNamesFromDatabase("catalog", "schema", null)).thenReturn(Arrays.asList("table0"));
        try {
            Thread.sleep(10l);
        } catch (InterruptedException e) {
        }
        tableNames = cache.getTableNames("catalog", "schema", null);
        assertEquals(1, tableNames.size());
        assertEquals("table0", tableNames.get(0));
    }
}
