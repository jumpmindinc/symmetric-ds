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
package org.jumpmind.symmetric.integrate;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.load.IReloadListener;
import org.jumpmind.symmetric.model.Node;

public class RunSqlReloadListener implements IReloadListener, ISymmetricEngineAware {
    private ISymmetricEngine engine;
    private String sqlToRunAtTargetBeforeReload;
    private String sqlToRunAtTargetAfterReload;

    public void afterReload(ISqlTransaction transaction, Node node, long loadId) {
        if (StringUtils.isNotBlank(sqlToRunAtTargetAfterReload)) {
            engine.getDataService().insertSqlEvent(transaction, node, sqlToRunAtTargetAfterReload, true, loadId, null);
        }
    }

    public void beforeReload(ISqlTransaction transaction, Node node, long loadId) {
        if (StringUtils.isNotBlank(sqlToRunAtTargetBeforeReload)) {
            engine.getDataService().insertSqlEvent(transaction, node, sqlToRunAtTargetBeforeReload, true, loadId, null);
        }
    }

    public void setSqlToRunAtTargetAfterReload(String sqlToRunAfterReload) {
        this.sqlToRunAtTargetAfterReload = sqlToRunAfterReload;
    }

    public void setSqlToRunAtTargetBeforeReload(String sqlToRunBeforeReload) {
        this.sqlToRunAtTargetBeforeReload = sqlToRunBeforeReload;
    }

    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }
}
