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
package org.jumpmind.db.platform.greenplum;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlDdlReader;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;

public class GreenplumDdlReader extends PostgreSqlDdlReader {
    public GreenplumDdlReader(IDatabasePlatform platform) {
        super(platform);
    }

    protected void setDistributionKeys(Connection connection, Relation relation, String schema)
            throws SQLException {
        // get the distribution keys for segments
        StringBuilder query = new StringBuilder();
        query.append("select                                        ");
        query.append("   t.relname,                                 ");
        query.append("   a.attname                                  ");
        query.append("from                                          ");
        query.append("   pg_class t,                                ");
        query.append("   pg_namespace n,                            ");
        query.append("   pg_attribute a,                            ");
        query.append("   gp_distribution_policy p                   ");
        query.append("where                                         ");
        query.append("   n.oid = t.relnamespace and                 ");
        query.append("   p.localoid = t.oid and                     ");
        query.append("   a.attrelid = t.oid and                     ");
        query.append("   a.attnum = any(p.attrnums) and             ");
        query.append("   n.nspname = ? and                          ");
        query.append("   t.relname = ?                              ");
        PreparedStatement prepStmt = connection.prepareStatement(query.toString());
        try {
            // set the schema parm in the query
            prepStmt.setString(1, schema);
            prepStmt.setString(2, relation.getName());
            ResultSet rs = prepStmt.executeQuery();
            // for every row, set the distributionKey for the corresponding
            // columns
            while (rs.next()) {
                Column column = relation.findColumn(rs.getString(2).trim(), getPlatform().getDdlBuilder()
                        .isDelimitedIdentifierModeOn());
                if (column != null) {
                    column.setDistributionKey(true);
                }
            }
            rs.close();
        } finally {
            if (prepStmt != null) {
                prepStmt.close();
            }
        }
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        Relation relation = super.readRelation(connection, metaData, values);
        setDistributionKeys(connection, relation, metaData.getSchemaPattern());
        return relation;
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        return table.doesIndexContainOnlyPrimaryKeyColumns(index);
    }

    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "SELECT "
                + "trigger_name, "
                + "trigger_schema, "
                + "trigger_catalog, "
                + "event_manipulation AS trigger_type, "
                + "event_object_table AS table_name,"
                + "trig.*, "
                + "pgproc.prosrc "
                + "FROM INFORMATION_SCHEMA.TRIGGERS AS trig "
                + "INNER JOIN pg_catalog.pg_trigger AS pgtrig "
                + "ON pgtrig.tgname=trig.trigger_name "
                + "INNER JOIN pg_catalog.pg_proc AS pgproc "
                + "ON pgproc.oid=pgtrig.tgfoid "
                + "WHERE event_object_table=? AND event_object_schema=?;";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("trigger_name"));
                trigger.setCatalogName(row.getString("trigger_catalog"));
                trigger.setSchemaName(row.getString("trigger_schema"));
                trigger.setTableName(row.getString("table_name"));
                trigger.setEnabled(true);
                trigger.setSource(row.getString("prosrc"));
                row.remove("prosrc");
                String triggerType = row.getString("trigger_type");
                if (triggerType.equals("DELETE")
                        || triggerType.equals("INSERT")
                        || triggerType.equals("UPDATE")) {
                    trigger.setTriggerType(TriggerType.valueOf(triggerType));
                }
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName, schema);
        return triggers;
    }

    @Override
    protected void readForeignKeyUpdateRule(Map<String, Object> values, ForeignKey fk) {
        // Greenplum does not support cascading
        fk.setOnUpdateAction(ForeignKeyAction.NOACTION);
    }

    @Override
    protected void readForeignKeyDeleteRule(Map<String, Object> values, ForeignKey fk) {
        // Greenplum does not support cascading
        fk.setOnDeleteAction(ForeignKeyAction.NOACTION);
    }
}
