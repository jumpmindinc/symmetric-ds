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

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;

public class ExtensionServiceSqlMap extends AbstractSqlMap {
    public ExtensionServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        putSql("selectEnabled", "select extension_id, extension_type, interface_name, node_group_id, enabled, extension_order, " +
                "extension_text, create_time, last_update_by, last_update_time from $(extension) " +
                "where enabled = 1 and (node_group_id = ? or node_group_id='ALL') order by extension_order");
        putSql("selectAll", "select extension_id, extension_type, interface_name, node_group_id, enabled, extension_order, " +
                "extension_text, create_time, last_update_by, last_update_time from $(extension)");
        putSql("insertExtensionSql", "insert into $(extension) (extension_type, interface_name, node_group_id, enabled, " +
                "extension_order, extension_text, create_time, last_update_by, last_update_time, extension_id) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        putSql("updateExtensionSql", "update $(extension) set extension_type = ?, interface_name = ?, " +
                "node_group_id = ?, enabled = ?, extension_order = ?, extension_text = ?, last_update_by = ?, " +
                "last_update_time = ? where extension_id = ?");
        putSql("deleteExtensionSql", "delete from $(extension) where extension_id = ?");
        putSql("deleteAllExtensionsSql", "delete from $(extension)");
        putSql("whereExtensionIdLike", "where extension_id like ?");
    }
}
