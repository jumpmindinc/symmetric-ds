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
package org.jumpmind.symmetric.config;

import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.impl.TriggerRouterContext;

/**
 * @deprecated Use {@link org.jumpmind.symmetric.config.IRelationResolver} instead, which accepts {@link org.jumpmind.db.model.Relation} to support both tables
 *             and views. Enable legacy behavior via the {@code extension.use.legacy.interface.enabled} parameter.
 */
@Deprecated(forRemoval = true)
public interface ITableResolver extends IExtensionPoint {
    public void resolve(String catalog, String schema, Set<Table> tables, IDatabasePlatform platform,
            INodeService nodeService, Trigger trigger, boolean useTableCache, TriggerRouterContext triggerRouterContext);
}
