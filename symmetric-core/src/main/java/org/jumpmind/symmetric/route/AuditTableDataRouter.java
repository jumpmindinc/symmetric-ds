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
package org.jumpmind.symmetric.route;

import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IParameterService;

public class AuditTableDataRouter extends AbstractDataRouter implements IBuiltInExtensionPoint {
    private static final String COLUMN_AUDIT_EVENT = "AUDIT_EVENT";
    private static final String COLUMN_AUDIT_TIME = "AUDIT_TIME";
    private static final String COLUMN_AUDIT_ID = "AUDIT_ID";
    private ISymmetricEngine engine;
    private Map<String, Table> auditTables = new HashMap<String, Table>();

    public AuditTableDataRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData,
            Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        if (initialLoad) { // loads are not audited; this is for CDC.
            return null;
        }
        DataEventType eventType = dataMetaData.getData().getDataEventType();
        if (eventType == DataEventType.INSERT || eventType == DataEventType.UPDATE
                || eventType == DataEventType.DELETE) {
            IParameterService parameterService = engine.getParameterService();
            IDatabasePlatform platform = engine.getDatabasePlatform();
            TriggerHistory triggerHistory = dataMetaData.getTriggerHistory();
            Table table = dataMetaData.getTable().copyAndFilterColumns(
                    triggerHistory.getParsedColumnNames(), triggerHistory.getParsedPkColumnNames(),
                    true, false);
            String tableName = table.getFullyQualifiedTableName();
            Table auditTable = auditTables.get(tableName);
            if (auditTable == null) {
                auditTable = toAuditTable(table);
                if (parameterService.is(ParameterConstants.AUTO_CONFIGURE_DATABASE)) {
                    platform.alterTables(true, auditTable);
                }
                auditTable = platform.getTableFromCache(auditTable.getCatalog(), auditTable.getSchema(), auditTable.getName(), false);
                auditTables.put(tableName, auditTable);
            }
            DatabaseInfo dbInfo = platform.getDatabaseInfo();
            String auditTableName = auditTable.getQualifiedTableName(dbInfo.getDelimiterToken(),
                    dbInfo.getCatalogSeparator(), dbInfo.getSchemaSeparator());
            ISqlTemplate template = platform.getSqlTemplate();
            Map<String, Object> values = null;
            if (eventType != DataEventType.DELETE) {
                values = new HashMap<String, Object>(getNewDataAsObject(null,
                        dataMetaData, engine.getSymmetricDialect(), false));
            } else {
                values = new HashMap<String, Object>(getOldDataAsObject(null,
                        dataMetaData, engine.getSymmetricDialect(), false));
            }
            Long sequence = (Long) context.get(auditTableName);
            if (sequence == null) {
                sequence = 1l + template.queryForLong(String.format("select max(%s) from %s",
                        auditTable.getColumnWithName(COLUMN_AUDIT_ID).getName(), auditTableName));
            } else {
                sequence = 1l + sequence;
            }
            context.put(auditTable.getName(), sequence);
            values.put(auditTable.getColumnWithName(COLUMN_AUDIT_ID).getName(), sequence);
            values.put(auditTable.getColumnWithName(COLUMN_AUDIT_TIME).getName(), new Date());
            values.put(auditTable.getColumnWithName(COLUMN_AUDIT_EVENT).getName(), eventType.getCode());
            DmlStatement statement = platform.createDmlStatement(DmlType.INSERT, auditTable, null);
            int[] types = statement.getTypes();
            Object[] args = statement.getValueArray(values);
            String sql = statement.getSql();
            template.update(sql, args, types);
        }
        return null;
    }

    protected Table toAuditTable(Table table) {
        IDatabasePlatform platform = engine.getDatabasePlatform();
        Table auditTable = table.copy();
        auditTable.setName(String.format("%s_%s", auditTable.getName(), platform.alterCaseToMatchDatabaseDefaultCase("AUDIT")));
        Column[] columns = auditTable.getColumns();
        auditTable.removeAllColumns();
        auditTable.addColumn(new Column(COLUMN_AUDIT_ID, true, Types.BIGINT, 0, 0));
        auditTable.addColumn(new Column(COLUMN_AUDIT_TIME, false, Types.TIMESTAMP, 0, 0));
        auditTable.addColumn(new Column(COLUMN_AUDIT_EVENT, false, Types.CHAR, 1, 0));
        for (Column column : columns) {
            column.setRequired(false);
            column.setPrimaryKey(false);
            column.setAutoIncrement(false);
            auditTable.addColumn(column);
        }
        auditTable.removeAllForeignKeys();
        auditTable.removeAllIndices();
        platform.alterCaseToMatchDatabaseDefaultCase(auditTable);
        return auditTable;
    }
}
