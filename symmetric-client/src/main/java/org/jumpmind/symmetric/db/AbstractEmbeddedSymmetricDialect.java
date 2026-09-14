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
package org.jumpmind.symmetric.db;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IParameterService;

abstract public class AbstractEmbeddedSymmetricDialect extends AbstractSymmetricDialect implements ISymmetricDialect {
    public AbstractEmbeddedSymmetricDialect(IParameterService parameterService,
            IDatabasePlatform platform) {
        super(parameterService, platform);
    }

    /**
     * All the templates have ' escaped because the SQL is inserted into a view. When returning the raw SQL for use as SQL it needs to be un-escaped.
     */
    @Override
    public String createInitialLoadSqlFor(Node node, TriggerRouter trigger, Relation relation, TriggerHistory triggerHistory, Channel channel,
            String overrideSelectSql) {
        return unescapeTicks(super.createInitialLoadSqlFor(node, trigger, relation, triggerHistory, channel, overrideSelectSql));
    }

    @Override
    public String createCsvDataSql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause) {
        return unescapeTicks(super.createCsvDataSql(trigger, triggerHistory, channel, whereClause));
    }

    @Override
    public String createCsvDataSql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause, Table table) {
        return unescapeTicks(super.createCsvDataSql(trigger, triggerHistory, channel, whereClause, table));
    }

    @Override
    public String createCsvPrimaryKeySql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause) {
        return unescapeTicks(super.createCsvPrimaryKeySql(trigger, triggerHistory, channel, whereClause));
    }

    @Override
    public String createCsvPrimaryKeySql(Trigger trigger, TriggerHistory triggerHistory, Channel channel, String whereClause, Table table) {
        return unescapeTicks(super.createCsvPrimaryKeySql(trigger, triggerHistory, channel, whereClause, table));
    }

    protected String unescapeTicks(String sql) {
        return sql.replace("''", "'");
    }

    public void cleanDatabase() {
    }

    public String getDefaultCatalog() {
        return null;
    }

    @Override
    public String getInitialLoadTableAlias() {
        return "t.";
    }

    @Override
    public String preProcessTriggerSqlClause(String sqlClause) {
        if (StringUtils.isNotBlank(sqlClause)) {
            sqlClause = sqlClause.replace("$(newTriggerValue).", "$(newTriggerValue)");
            sqlClause = sqlClause.replace("$(oldTriggerValue).", "$(oldTriggerValue)");
            sqlClause = sqlClause.replace("$(curTriggerValue).", "$(curTriggerValue)");
            return sqlClause.replace("'", "''");
        } else {
            return sqlClause;
        }
    }

    @Override
    public boolean escapesTemplatesForDatabaseInserts() {
        return true;
    }
}