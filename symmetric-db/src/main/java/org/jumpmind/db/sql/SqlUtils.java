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
package org.jumpmind.db.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class SqlUtils {
    private static Logger log = LoggerFactory.getLogger(SqlUtils.class);
    private static final int MAX_IDENTIFIER_LENGTH = 255;
    private static final int MAX_TABLE_PREFIX_LENGTH = 32;
    private static boolean captureOwner = false;
    private static List<ISqlTransaction> sqlTransactions = Collections.synchronizedList(new ArrayList<ISqlTransaction>());
    private static List<ISqlReadCursor<?>> sqlReadCursors = Collections.synchronizedList(new ArrayList<ISqlReadCursor<?>>());
    private static Map<ISqlTransaction, Exception> sqlTransactionsOwnerMap = new ConcurrentHashMap<ISqlTransaction, Exception>();
    private static Map<ISqlReadCursor<?>, Exception> sqlReadCursorsOwnerMap = new ConcurrentHashMap<ISqlReadCursor<?>, Exception>();

    protected static void addSqlTransaction(ISqlTransaction transaction) {
        sqlTransactions.add(transaction);
        if (captureOwner) {
            sqlTransactionsOwnerMap.put(transaction, new Exception());
        }
    }

    protected static void addSqlReadCursor(ISqlReadCursor<?> cursor) {
        sqlReadCursors.add(cursor);
        if (captureOwner) {
            sqlReadCursorsOwnerMap.put(cursor, new Exception());
        }
    }

    protected static void removeSqlReadCursor(ISqlReadCursor<?> cursor) {
        sqlReadCursors.remove(cursor);
        if (captureOwner) {
            sqlReadCursorsOwnerMap.remove(cursor);
        }
    }

    protected static void removeSqlTransaction(ISqlTransaction transaction) {
        sqlTransactions.remove(transaction);
        if (captureOwner) {
            sqlTransactionsOwnerMap.remove(transaction);
        }
    }

    public static List<ISqlTransaction> getOpenTransactions() {
        return new ArrayList<ISqlTransaction>(sqlTransactions);
    }

    public static List<ISqlReadCursor<?>> getOpenSqlReadCursors() {
        return new ArrayList<ISqlReadCursor<?>>(sqlReadCursors);
    }

    public static void logOpenResources() {
        List<ISqlReadCursor<?>> cursors = SqlUtils.getOpenSqlReadCursors();
        for (ISqlReadCursor<?> cursor : cursors) {
            Exception ex = sqlReadCursorsOwnerMap.get(cursor);
            if (ex != null) {
                log.error("The following stack contains the owner of an open read cursor", ex);
            }
        }
        List<ISqlTransaction> transactions = SqlUtils.getOpenTransactions();
        for (ISqlTransaction transaction : transactions) {
            Exception ex = sqlTransactionsOwnerMap.get(transaction);
            if (ex != null) {
                log.error("The following stack contains the owner of an open database transaction", ex);
            }
        }
    }

    public static void setCaptureOwner(boolean captureOwner) {
        SqlUtils.captureOwner = captureOwner;
    }

    public static String sanitizeIdentifier(String name) {
        if (name != null) {
            if (name.length() > MAX_IDENTIFIER_LENGTH) {
                name = name.substring(0, MAX_IDENTIFIER_LENGTH);
            }
            name = name.replaceAll("[\\\"'/;]", "");
        }
        return name;
    }

    public static String sanitizeFunction(String name) {
        if (name != null) {
            if (name.length() > MAX_IDENTIFIER_LENGTH) {
                name = name.substring(0, MAX_IDENTIFIER_LENGTH);
            }
            name = name.replaceAll("[\\\"'/; ]", "");
        }
        return name;
    }

    public static String sanitizeTablePrefix(String name) {
        if (name != null) {
            if (name.length() > MAX_TABLE_PREFIX_LENGTH) {
                name = name.substring(0, MAX_TABLE_PREFIX_LENGTH);
            }
            name = name.replaceAll("[^\\w_]", "_").replaceAll("_+", "_");
        }
        return name;
    }

    public static String escapeString(String s) {
        if (s != null) {
            s = s.replaceAll("'", "''");
        }
        return s;
    }
}
