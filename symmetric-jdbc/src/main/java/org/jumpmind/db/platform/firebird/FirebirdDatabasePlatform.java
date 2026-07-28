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
package org.jumpmind.db.platform.firebird;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;

/*
 * The platform implementation for the Firebird database.
 * It is assumed that the database is configured with sql dialect 3!
 */
public class FirebirdDatabasePlatform extends AbstractJdbcDatabasePlatform {
    /* The standard Firebird jdbc driver. */
    public static final String JDBC_DRIVER = "org.firebirdsql.jdbc.FBDriver";
    /* The subprotocol used by the standard Firebird driver. */
    public static final String JDBC_SUBPROTOCOL = "firebirdsql";

    /*
     * Creates a new Firebird platform instance.
     */
    public FirebirdDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        supportsTruncate = false;
    }

    @Override
    protected FirebirdDdlBuilder createDdlBuilder() {
        return new FirebirdDdlBuilder();
    }

    @Override
    protected FirebirdDdlReader createDdlReader() {
        return new FirebirdDdlReader(this);
    }

    @Override
    protected FirebirdJdbcSqlTemplate createSqlTemplate() {
        return new FirebirdJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    public String getName() {
        return DatabaseNamesConstants.FIREBIRD;
    }

    @Override
    public String getClassName() {
        return FirebirdDatabasePlatform.class.getName();
    }

    public String getDefaultCatalog() {
        return null;
    }

    public String getDefaultSchema() {
        return null;
    }

    @Override
    public PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        String triggerSql = "CREATE TRIGGER TEST_TRIGGER FOR " + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter + " AFTER UPDATE AS BEGIN END";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TRIGGER, triggerSql);
        try {
            getSqlTemplate().update(triggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE TRIGGER permission or TRIGGER permission");
        }
        return result;
    }

    @Override
    public boolean supportsLimitOffset() {
        return true;
    }

    @Override
    public String massageForLimitOffset(String sql, int limit, int offset) {
        return Strings.CI.replace(sql, "select", "select first " + limit + " skip " + offset);
    }

    @Override
    public List<Transaction> getTransactions() {
        // Uncommitted transactions that are older than 5 minutes and have either an active IUD statement or no activity
        int minutesOld = -5;
        String tranSql = "select t.mon$transaction_id, a.mon$user, a.mon$remote_address, t.mon$state, t.mon$timestamp, s.mon$sql_text " +
                "from mon$attachments a " +
                "inner join mon$transactions t on t.mon$attachment_id = a.mon$attachment_id " +
                "left join mon$statements s on s.mon$attachment_id = t.mon$attachment_id and (s.mon$transaction_id = t.mon$transaction_id " +
                "or s.mon$transaction_id is null)" +
                "where t.mon$transaction_id is not null " +
                "and ((s.mon$state = 0 and t.mon$timestamp < dateadd(? minute to current_timestamp)) or " +
                "(s.mon$state = 1 and (upper(s.mon$sql_text) like 'INSERT %' or upper(s.mon$sql_text) like 'UPDATE %'or upper(s.mon$sql_text) like 'DELETE %') "
                +
                "and s.mon$timestamp < dateadd(? minute to current_timestamp)))";
        List<Transaction> transactions = new ArrayList<Transaction>();
        List<Transaction> blockedTransactions = new ArrayList<Transaction>();
        List<Row> rows = getSqlTemplate().query(tranSql, new Object[] { minutesOld, minutesOld }, new int[] { Types.INTEGER, Types.INTEGER });
        for (Row row : rows) {
            Transaction tran = new Transaction(row.getString("mon$transaction_id"), StringUtils.trimToEmpty(row.getString("mon$user")),
                    null, row.getDateTime("mon$timestamp"), row.getString("mon$sql_text"));
            tran.setRemoteIp(row.getString("mon$remote_address"));
            tran.setStatus(row.getString("mon$state"));
            transactions.add(tran);
            String text = StringUtils.trimToEmpty(StringUtils.upperCase(tran.getText()));
            if ("1".equals(tran.getStatus()) && text.startsWith("INSERT") || text.startsWith("UPDATE") || text.startsWith("DELETE")) {
                blockedTransactions.add(tran);
            }
        }
        for (Transaction blockedTran : blockedTransactions) {
            for (Transaction tran : transactions) {
                if (tran.getStartTime() != null && tran.getStartTime().before(blockedTran.getStartTime())) {
                    blockedTran.setBlockingId(tran.getId());
                    break;
                }
            }
        }
        return transactions;
    }
}
