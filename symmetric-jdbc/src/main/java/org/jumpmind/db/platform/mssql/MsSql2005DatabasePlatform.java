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
package org.jumpmind.db.platform.mssql;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlTemplateSettings;

/*
 * The platform implementation for the Microsoft SQL Server 2005 database.
 * Adds NVARCHAR(MAX), VARCHAR(MAX) columns, which are treated as a regular string type, not as "large objects".
 */
public class MsSql2005DatabasePlatform extends MsSql2000DatabasePlatform {
    /*
     * Creates a new platform instance.
     */
    public MsSql2005DatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        supportsTruncate = false;
    }

    @Override
    protected IDdlBuilder createDdlBuilder() {
        return new MsSql2005DdlBuilder();
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.MSSQL2005;
    }

    @Override
    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = getSqlTemplate().queryForObject("select SCHEMA_NAME()",
                    String.class);
        }
        return defaultSchema;
    }

    @Override
    public List<Transaction> getTransactions() {
        String sql = "select" +
                "  r.session_id," +
                "  s.login_name," +
                "  c.client_net_address," +
                "  s.host_name," +
                "  r.status," +
                "  r.reads," +
                "  r.writes," +
                "  r.blocking_session_id," +
                "  r.start_time," +
                "  sql.text " +
                "from sys.dm_exec_requests as r " +
                "left join sys.dm_exec_connections as c" +
                "  on r.connection_id = c.connection_id " +
                "join sys.dm_exec_sessions as s" +
                "  on r.session_id = s.session_id " +
                "cross apply sys.dm_exec_sql_text(r.sql_handle) as sql;";
        List<Transaction> transactions = new ArrayList<Transaction>();
        int count = getSqlTemplate().queryForInt("select count(*) from fn_my_permissions(null, 'SERVER') where permission_name = 'VIEW SERVER STATE'");
        if (count > 0) {
            for (Row row : getSqlTemplate().query(sql)) {
                Transaction transaction = new Transaction(row.getString("session_id"), row.getString("login_name"),
                        row.getString("blocking_session_id"), row.getDateTime("start_time"), row.getString("text"));
                transaction.setRemoteIp(row.getString("client_net_address"));
                transaction.setRemoteHost(row.getString("host_name"));
                transaction.setStatus(row.getString("status"));
                transaction.setReads(row.getInt("reads"));
                transaction.setWrites(row.getInt("writes"));
                transactions.add(transaction);
            }
        } else {
            throw new RuntimeException("Missing permission VIEW SERVER STATE to view blocked transactions");
        }
        return transactions;
    }

    @Override
    public boolean supportsLimitOffset() {
        return true;
    }

    @Override
    public String massageForLimitOffset(String sql, int limit, int offset) {
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        if (sqlTemplate.getDatabaseMajorVersion() >= 11) {
            return sql + " offset " + offset + " rows fetch next " + limit + " rows only;";
        }
        int orderIndex = Strings.CI.lastIndexOf(sql, "order by");
        String order = sql.substring(orderIndex);
        String innerSql = sql.substring(0, orderIndex - 1);
        innerSql = Strings.CI.replace(innerSql, " from", ", ROW_NUMBER() over (" + order + ") as RowNum from");
        return "select * from (" + innerSql + ") A " +
                "where RowNum between " + (offset + 1) + " and " + (offset + limit);
    }

    @Override
    public boolean supportsSliceTables() {
        return true;
    }

    @Override
    public String getSliceTableSql(String columnName, int sliceNum, int totalSlices) {
        return "ascii(substring(" + columnName + ", 1, 1)) % " + totalSlices + " = " + sliceNum;
    }

    @Override
    public String getCharSetName() {
        return getSqlTemplate().queryForObject("select collation_name from sys.databases where name =\r\n"
                + "db_name()", String.class);
    }
}
