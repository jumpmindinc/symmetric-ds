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

public class GroupletServiceSqlMap extends AbstractSqlMap {
    public GroupletServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        putSql("selectGroupletSql", "select grouplet_id, grouplet_link_policy, description, create_time, last_update_by, last_update_time from $(grouplet)");
        putSql("selectGroupletLinkSql", "select grouplet_id, external_id, create_time, last_update_by, last_update_time from $(grouplet_link)");
        putSql("selectTriggerRouterGroupletSql",
                "select grouplet_id, trigger_id, router_id, applies_when, create_time, last_update_by, last_update_time from $(trigger_router_grouplet)");
        putSql("updateGroupletSql",
                "update $(grouplet) set grouplet_link_policy=?, description=?, create_time=?, last_update_by=?, last_update_time=? where grouplet_id=?");
        putSql("insertGroupletSql",
                "insert into $(grouplet) (grouplet_link_policy, description, create_time, last_update_by, last_update_time, grouplet_id) values(?,?,?,?,?,?)");
        putSql("deleteGroupletSql", "delete from $(grouplet) where grouplet_id=?");
        putSql("deleteAllGroupletsSql", "delete from $(grouplet)");
        putSql("updateGroupletLinkSql",
                "update $(grouplet_link) set create_time=?, last_update_by=?, last_update_time=? where grouplet_id=? and external_id=?");
        putSql("insertGroupletLinkSql",
                "insert into $(grouplet_link) (create_time, last_update_by, last_update_time, grouplet_id, external_id) values(?,?,?,?,?)");
        putSql("deleteGroupletLinkSql", "delete from $(grouplet_link) where grouplet_id=? and external_id=?");
        putSql("deleteAllGroupletLinksSql", "delete from $(grouplet_link)");
        putSql("updateTriggerRouterGroupletSql",
                "update $(trigger_router_grouplet) set create_time=?, last_update_by=?, last_update_time=? where grouplet_id=? and applies_when=? and trigger_id=? and router_id=?");
        putSql("insertTriggerRouterGroupletSql",
                "insert into $(trigger_router_grouplet) (create_time, last_update_by, last_update_time, grouplet_id, applies_when, trigger_id, router_id) values(?,?,?,?,?,?,?)");
        putSql("deleteTriggerRouterGroupletSql",
                "delete from $(trigger_router_grouplet) where grouplet_id=? and applies_when=? and trigger_id=? and router_id=?");
        putSql("deleteAllTriggerRouterGroupletsSql", "delete from $(trigger_router_grouplet)");
        putSql("deleteTriggerRouterGroupletForSql", "delete from $(trigger_router_grouplet) where trigger_id=? and router_id=?");
        putSql("deleteTriggerRouterGroupletsForRouterSql", "delete from $(trigger_router_grouplet) where router_id=?");
        putSql("selectMaxGroupletLastUpdateTime", "select max(last_update_time) from $(grouplet) where last_update_time is not null");
        putSql("selectMaxGroupletLinkLastUpdateTime", "select max(last_update_time) from $(grouplet_link) where last_update_time is not null");
        putSql("selectMaxTriggerRouterGroupletLastUpdateTime",
                "select max(last_update_time) from $(trigger_router_grouplet) where last_update_time is not null");
        putSql("whereGroupletIdLike", "where grouplet_id like ?");
    }
}
