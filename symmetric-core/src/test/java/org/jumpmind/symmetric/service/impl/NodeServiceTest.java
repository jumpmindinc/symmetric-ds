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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NodeServiceTest {
    @Test
    void toColumnName_convertsCamelCasePropertyId() {
        assertEquals("node_group_id", NodeService.toColumnName("nodeGroupId"));
    }

    @Test
    void toColumnName_keepsSingleWordPropertyId() {
        assertEquals("status", NodeService.toColumnName("status"));
    }

    @Test
    void toColumnName_rejectsPropertyIdWithSqlCharacters() {
        assertThrows(IllegalArgumentException.class, () -> NodeService.toColumnName("nodeId = nodeId or 1=1 --"));
    }

    @Test
    void toColumnName_rejectsPropertyIdWithQuote() {
        assertThrows(IllegalArgumentException.class, () -> NodeService.toColumnName("node'Id"));
    }
}
