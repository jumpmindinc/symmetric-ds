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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DbExport.Compatible;
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
}
