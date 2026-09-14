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
package org.jumpmind.db.platform.informix;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;

public class InformixDdlReader extends AbstractJdbcDdlReader {
    public InformixDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        String catalog = metaData.getCatalog();
        Relation relation = super.readRelation(connection, metaData, values);
        if (relation != null && (isBlank(catalog) || catalog.equals(getDefaultCatalogPattern()))) {
            /*
             * The default catalog is null so by default if no catalog is provided it must be null as well
             */
            relation.setCatalog(null);
        }
        return relation;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Collection<IIndex> readIndices(Connection connection, DatabaseMetaDataWrapper metaData,
            String tableName) throws SQLException {
        String sql = "select rtrim(dbinfo('dbname')) as TABLE_CAT, st.owner as TABLE_SCHEM, st.tabname as TABLE_NAME, "
                + "case when idxtype = 'U' then 0 else 1 end NON_UNIQUE, si.owner as INDEX_QUALIFIER, si.idxname as INDEX_NAME,  "
                + "3 as TYPE,  "
                + "case when sc.colno = si.part1 then 1 "
                + "when sc.colno = si.part1 then 1 "
                + "when sc.colno = si.part2 then 2 "
                + "when sc.colno = si.part3 then 3 "
                + "when sc.colno = si.part4 then 4 "
                + "when sc.colno = si.part5 then 5 "
                + "when sc.colno = si.part6 then 6 "
                + "when sc.colno = si.part7 then 7 "
                + "when sc.colno = si.part8 then 8 "
                + "else 0 end as ORDINAL_POSITION,  "
                + "sc.colname as COLUMN_NAME, "
                + "null::varchar as ASC_OR_DESC, 0 as CARDINALITY, 0 as PAGES, null::varchar as FILTER_CONDITION "
                + "from sysindexes si "
                + "inner join systables st on si.tabid = st.tabid "
                + "inner join syscolumns sc on si.tabid = sc.tabid "
                + "where st.tabname like ? "
                + "and (sc.colno = si.part1 or sc.colno = si.part2 or sc.colno = si.part3 or  "
                + "sc.colno = si.part4 or sc.colno = si.part5 or sc.colno = si.part6 or  "
                + "sc.colno = si.part7 or sc.colno = si.part8) and "
                + "si.idxname not in (select idxname from sysconstraints where constrtype in ('R'))";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, tableName);
        ResultSet rs = ps.executeQuery();
        Map indices = new ListOrderedMap();
        while (rs.next()) {
            Map values = readMetaData(rs, getColumnsForIndex());
            readIndex(metaData, values, indices);
        }
        rs.close();
        ps.close();
        return indices.values();
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Column column = super.readColumn(metaData, values);
        PlatformColumn platformColumn = column.findPlatformColumn(platform.getName());
        if ("SERIAL".equalsIgnoreCase(column.getJdbcTypeName()) || "BIGSERIAL".equalsIgnoreCase(column.getJdbcTypeName())) {
            column.setAutoIncrement(true);
        } else if (column.getMappedTypeCode() == Types.TIMESTAMP) {
            adjustColumnSize(column, -20);
            if (platformColumn != null) {
                if (column.getSizeAsInt() == 0) {
                    platformColumn.setType("DATETIME YEAR TO SECOND");
                } else {
                    platformColumn.setType("DATETIME YEAR TO FRACTION");
                }
            }
        } else if (column.getMappedTypeCode() == Types.DATE) {
            removeColumnSize(column);
        }
        return column;
    }

    @Override
    public void removeSystemIndices(Connection connection, DatabaseMetaDataWrapper metaData,
            Table table) throws SQLException {
        super.removeSystemIndices(connection, metaData, table);
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) throws SQLException {
        return index.getName().startsWith(" ");
    }

    @Override
    protected boolean isInternalForeignKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, ForeignKey fk, IIndex index1)
            throws SQLException {
        return fk.getName().startsWith(" ");
    }

    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) throws SqlException {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "SELECT "
                + "trigname AS trigger_name, "
                + "tab.owner AS schema_name, "
                + "tab.tabname AS table_name, "
                + "event AS trigger_type, "
                + "trigid, "
                + "tab.tabid, "
                + "old, "
                + "new, "
                + "collation "
                + "FROM systriggers AS trig "
                + "INNER JOIN systables AS tab "
                + "ON tab.tabid = trig.tabid "
                + "WHERE tab.tabname=? AND tab.owner=? ;";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("trigger_name"));
                trigger.setSchemaName(row.getString("schema_name"));
                trigger.setTableName(row.getString("table_name"));
                trigger.setEnabled(true);
                trigger.setSource("");
                String trigEvent = row.getString("trigger_type");
                switch (trigEvent.toUpperCase().charAt(0)) {
                    case ('I'):
                        trigEvent = "INSERT";
                        break;
                    case ('U'):
                        trigEvent = "UPDATE";
                        break;
                    case ('D'):
                        trigEvent = "DELETE";
                }
                trigger.setTriggerType(TriggerType.valueOf(trigEvent));
                row.put("trigger_type", trigEvent);
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName, schema);
        for (final Trigger trigger : triggers) {
            String id = trigger.getMetaData().get("trigid").toString();
            String sourceSql = "SELECT "
                    + "data "
                    + "FROM systrigbody "
                    + "WHERE trigid=? AND (datakey='A' OR datakey='D') "
                    + "ORDER BY datakey DESC, seqno ASC ;";
            sqlTemplate.query(sourceSql, new ISqlRowMapper<Trigger>() {
                public Trigger mapRow(Row row) {
                    trigger.setSource(trigger.getSource() + "\n" + row.getString("data"));
                    return trigger;
                }
            }, id);
        }
        return triggers;
    }

    @Override
    protected void readForeignKeyUpdateRule(Map<String, Object> values, ForeignKey fk) {
        // Informix does not support cascade update
        fk.setOnUpdateAction(ForeignKeyAction.NOACTION);
    }
}
