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
package org.jumpmind.symmetric.service.impl;

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.model.RegistrationRequest;

public class RegistrationServiceSqlMap extends AbstractSqlMap {
    public RegistrationServiceSqlMap(IDatabasePlatform platform,
            Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        StringBuilder incompleteStatusList = new StringBuilder();
        for (RegistrationRequest.RegistrationStatus statusName : RegistrationRequest.incompleteStatuses) {
            if (!incompleteStatusList.isEmpty()) {
                incompleteStatusList.append(",");
            }
            incompleteStatusList.append("'").append(statusName.name()).append("'");
        }
        putSql("findNodeToRegisterSql",
                ""
                        + "select min(c.node_id) from $(node) c inner join                                "
                        + "  $(node_security) s on c.node_id = s.node_id where s.registration_enabled =   "
                        + "  1 and c.node_group_id = ? and c.external_id = ?                                    ");
        putSql("registerNodeSecuritySql",
                ""
                        + "update $(node_security) set registration_enabled = 0, registration_time =   "
                        + "  ? where node_id = ?                                             ");
        putSql("registrationPendingSql", "update $(node_security) set registration_time = ? where node_id = ?");
        putSql("reopenRegistrationSql", ""
                + "update $(node_security) set node_password = ?, registration_enabled = 1, "
                + "registration_time = null, registration_not_before = ?, registration_not_after = ?, failed_logins = 0 "
                + "where node_id = ? and registration_enabled = 0  ");
        putSql("openRegistrationNodeSecuritySql", ""
                + "insert into $(node_security) (node_id, node_password, registration_enabled, registration_not_before, "
                + "registration_not_after, created_at_node_id) values (?, ?, 1, ?, ?, ?)");
        putSql("getRegistrationRedirectUrlSql", ""
                + "select sync_url from $(node) n inner join $(registration_redirect) r "
                + "on n.node_id=r.registration_node_id where r.registrant_external_id=?   ");
        putSql("insertRegistrationRedirectUrlSql", "" + "insert into $(registration_redirect) "
                + "(registration_node_id, registrant_external_id) values (?, ?)   ");
        putSql("updateRegistrationRedirectUrlSql", "" + "update $(registration_redirect) "
                + "set registration_node_id=? where registrant_external_id=?   ");
        putSql("deleteRegistrationRedirectByNodeIdSql", "delete from $(registration_redirect) where registration_node_id=?");
        putSql("insertRegistrationRequestSql",
                ""
                        + "insert into $(registration_request)                                                 "
                        + "  (last_update_by, last_update_time, attempt_count, registered_node_id, status,     "
                        + "  node_group_id, external_id, ip_address, host_name, error_message, create_time)    "
                        + "  values (?,?,1,?,?,?,?,?,?,?,?)                                    ");
        putSql("updateRegistrationRequestSql",
                ""
                        + "update $(registration_request)                                                                    "
                        + "  set attempt_count=?,                                                                                      "
                        + "  last_update_by=?, last_update_time=?, registered_node_id=?, status=?, error_message=?   ");
        putSql("selectRegistrationRequestSql",
                ""
                        + "select node_group_id, external_id, status, host_name, ip_address, error_message,     "
                        + "  attempt_count, registered_node_id, create_time, last_update_by, last_update_time   "
                        + "  from $(registration_request)                                                       ");
        putSql("deleteRegistrationRequestSql",
                "delete from $(registration_request) where node_group_id=? and external_id=? and host_name=? and status=?");
        putSql("whereStatusIsRequestOrErrorSql", "where status in ('RQ','ER')");
        putSql("whereNodeGroupIdAndExternalIdSql", "where node_group_id=? and external_id=?");
        putSql("wherePkSql", "where node_group_id=? and external_id=? and create_time=?");
        putSql("whereNodeGroupIdExternalIdHostNameStatusSql",
                "where node_group_id=? and external_id=? and host_name=? and status in (" + incompleteStatusList + ")");
    }
}