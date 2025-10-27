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
package org.jumpmind.symmetric.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Constants that represent SymmetricDS tables
 */
public class TableConstants {
    public static final String SYM_PARAMETER = "parameter";
    public static final String SYM_LOCK = "lock";
    public static final String SYM_OUTGOING_BATCH = "outgoing_batch";
    public static final String SYM_EXTRACT_REQUEST = "extract_request";
    public static final String SYM_INCOMING_BATCH = "incoming_batch";
    public static final String SYM_TRIGGER = "trigger";
    public static final String SYM_ROUTER = "router";
    public static final String SYM_TRIGGER_HIST = "trigger_hist";
    public static final String SYM_NODE_GROUP = "node_group";
    public static final String SYM_NODE = "node";
    public static final String SYM_NODE_HOST = "node_host";
    public static final String SYM_DATA = "data";
    public static final String SYM_DATA_GAP = "data_gap";
    public static final String SYM_DATA_EVENT = "data_event";
    public static final String SYM_TRANSFORM_TABLE = "transform_table";
    public static final String SYM_LOAD_FILTER = "load_filter";
    public static final String SYM_TRANSFORM_COLUMN = "transform_column";
    public static final String SYM_TRIGGER_ROUTER = "trigger_router";
    public static final String SYM_CHANNEL = "channel";
    public static final String SYM_NODE_SECURITY = "node_security";
    public static final String SYM_NODE_IDENTITY = "node_identity";
    public static final String SYM_NODE_COMMUNICATION = "node_communication";
    public static final String SYM_NODE_GROUP_LINK = "node_group_link";
    public static final String SYM_NODE_HOST_STATS = "node_host_stats";
    public static final String SYM_NODE_HOST_JOB_STATS = "node_host_job_stats";
    public static final String SYM_REGISTRATION_REQUEST = "registration_request";
    public static final String SYM_REGISTRATION_REDIRECT = "registration_redirect";
    public static final String SYM_NODE_CHANNEL_CTL = "node_channel_ctl";
    public static final String SYM_CONFLICT = "conflict";
    public static final String SYM_NODE_GROUP_CHANNEL_WND = "node_group_channel_wnd";
    public static final String SYM_NODE_HOST_CHANNEL_STATS = "node_host_channel_stats";
    public static final String SYM_INCOMING_ERROR = "incoming_error";
    public static final String SYM_OUTGOING_ERROR = "outgoing_error";
    public static final String SYM_SEQUENCE = "sequence";
    public static final String SYM_TABLE_RELOAD_REQUEST = "table_reload_request";
    public static final String SYM_GROUPLET = "grouplet";
    public static final String SYM_GROUPLET_LINK = "grouplet_link";
    public static final String SYM_TRIGGER_ROUTER_GROUPLET = "trigger_router_grouplet";
    public static final String SYM_FILE_TRIGGER = "file_trigger";
    public static final String SYM_FILE_TRIGGER_ROUTER = "file_trigger_router";
    public static final String SYM_FILE_SNAPSHOT = "file_snapshot";
    public static final String SYM_FILE_INCOMING = "file_incoming";
    public static final String SYM_CONSOLE_USER = "console_user";
    public static final String SYM_CONSOLE_ROLE = "console_role";
    public static final String SYM_CONSOLE_ROLE_PRIVILEGE = "console_role_privilege";
    public static final String SYM_CONSOLE_USER_HIST = "console_user_hist";
    public static final String SYM_CONSOLE_USER_PREFERENCE = "console_user_preference";
    public static final String SYM_CONSOLE_EVENT = "console_event";
    public static final String SYM_CONSOLE_TABLE_STATS = "console_table_stats";
    public static final String SYM_EXTENSION = "extension";
    public static final String SYM_MONITOR = "monitor";
    public static final String SYM_MONITOR_EVENT = "monitor_event";
    public static final String SYM_NOTIFICATION = "notification";
    public static final String SYM_CONTEXT = "context";
    public static final String SYM_JOB = "job";
    public static final String SYM_TABLE_RELOAD_STATUS = "table_reload_status";
    public static final String SYM_TABLE_GROUP = "table_group";
    public static final String SYM_TABLE_GROUP_HIER = "table_group_hier";
    public static final String SYM_COMPARE_REQUEST = "compare_request";
    public static final String SYM_COMPARE_STATUS = "compare_status";
    public static final String SYM_COMPARE_TABLE_STATUS = "compare_table_status";
    protected static boolean hasConsoleSchema = TableConstants.class.getResourceAsStream("/console-schema.xml") != null;
    /**
     * Historical list of decommissioned SymmetricDS tables. Used to filter configuration files produced by older versions. See getRemovedConfigTables:
     */
    public static final String SYM_DESIGN_DIAGRAM = "design_diagram";
    public static final String SYM_DIAGRAM_GROUP = "diagram_group";

    /**
     * Set of all SymmetricDS configuration and runtime tables.
     */
    public static final Set<String> getTables(String tablePrefix) {
        Set<String> tables = new HashSet<String>();
        addPrefixToTableNames(tables, tablePrefix, SYM_CHANNEL, SYM_CONFLICT, SYM_CONTEXT, SYM_DATA, SYM_DATA_GAP, SYM_DATA_EVENT, SYM_EXTRACT_REQUEST,
                SYM_EXTENSION, SYM_FILE_INCOMING, SYM_FILE_SNAPSHOT, SYM_FILE_TRIGGER, SYM_FILE_TRIGGER_ROUTER, SYM_GROUPLET, SYM_GROUPLET_LINK,
                SYM_INCOMING_BATCH, SYM_INCOMING_ERROR, SYM_JOB, SYM_LOAD_FILTER, SYM_LOCK, SYM_NODE, SYM_NODE_CHANNEL_CTL,
                SYM_NODE_COMMUNICATION, SYM_NODE_GROUP, SYM_NODE_GROUP_CHANNEL_WND, SYM_NODE_GROUP_LINK, SYM_NODE_HOST, SYM_NODE_HOST_CHANNEL_STATS,
                SYM_NODE_HOST_JOB_STATS, SYM_NODE_HOST_STATS, SYM_NODE_IDENTITY, SYM_NODE_SECURITY, SYM_OUTGOING_BATCH, SYM_PARAMETER,
                SYM_REGISTRATION_REDIRECT, SYM_REGISTRATION_REQUEST, SYM_ROUTER, SYM_SEQUENCE, SYM_TABLE_RELOAD_REQUEST, SYM_TABLE_RELOAD_STATUS,
                SYM_TRANSFORM_TABLE, SYM_TRANSFORM_COLUMN, SYM_TRIGGER, SYM_TRIGGER_HIST, SYM_TRIGGER_ROUTER, SYM_TRIGGER_ROUTER_GROUPLET, SYM_OUTGOING_ERROR);
        if (hasConsoleSchema) {
            tables.addAll(getTablesForConsole(tablePrefix));
        }
        return tables;
    }

    /**
     * Set of all SymmetricDS configuration and runtime tables used in professional console.
     */
    public static final Set<String> getTablesForConsole(String tablePrefix) {
        Set<String> tables = new HashSet<String>();
        addPrefixToTableNames(tables, tablePrefix, SYM_CONSOLE_EVENT, SYM_CONSOLE_USER, SYM_CONSOLE_USER_HIST, SYM_CONSOLE_USER_PREFERENCE,
                SYM_CONSOLE_ROLE, SYM_CONSOLE_ROLE_PRIVILEGE, SYM_CONSOLE_TABLE_STATS, SYM_TABLE_GROUP, SYM_TABLE_GROUP_HIER,
                SYM_COMPARE_REQUEST, SYM_COMPARE_STATUS, SYM_COMPARE_TABLE_STATUS, SYM_MONITOR, SYM_MONITOR_EVENT, SYM_NOTIFICATION);
        return tables;
    }

    public static final Set<String> getTablesWithoutPrefix() {
        return getTables("");
    }

    /**
     * Tables sent in the configuration batch for registration, with the order of tables specified by this list. Each table gets a trigger history record, and
     * it also gets a trigger installed for change capture unless it is excluded (see getConfigTablesWithoutCapture() method). The tables are also included in a
     * configuration export unless the table is returned by getConfigTablesExcludedFromExport().
     */
    public static final List<String> getConfigTables(String tablePrefix) {
        List<String> tables = new ArrayList<String>();
        addPrefixToTableNames(tables, tablePrefix, SYM_NODE_GROUP, SYM_NODE_GROUP_LINK, SYM_NODE, SYM_NODE_HOST, SYM_NODE_IDENTITY, SYM_NODE_SECURITY,
                SYM_PARAMETER, SYM_CHANNEL, SYM_NODE_GROUP_CHANNEL_WND, SYM_TRIGGER, SYM_ROUTER, SYM_TRIGGER_ROUTER, SYM_TRANSFORM_TABLE, SYM_LOAD_FILTER,
                SYM_TRANSFORM_COLUMN, SYM_CONFLICT, SYM_GROUPLET, SYM_GROUPLET_LINK, SYM_TRIGGER_ROUTER_GROUPLET, SYM_FILE_TRIGGER, SYM_FILE_TRIGGER_ROUTER,
                SYM_FILE_SNAPSHOT, SYM_EXTENSION, SYM_JOB, SYM_TABLE_RELOAD_REQUEST, SYM_TABLE_RELOAD_STATUS,
                SYM_EXTRACT_REQUEST, SYM_INCOMING_ERROR, SYM_OUTGOING_ERROR);
        if (hasConsoleSchema) {
            addPrefixToTableNames(tables, tablePrefix, SYM_CONSOLE_ROLE, SYM_CONSOLE_USER, SYM_CONSOLE_ROLE_PRIVILEGE, SYM_CONSOLE_USER_HIST,
                    SYM_CONSOLE_USER_PREFERENCE, SYM_TABLE_GROUP, SYM_TABLE_GROUP_HIER, SYM_COMPARE_REQUEST, SYM_COMPARE_STATUS, SYM_COMPARE_TABLE_STATUS,
                    SYM_MONITOR, SYM_MONITOR_EVENT, SYM_NOTIFICATION);
        }
        return tables;
    }

    /**
     * Map with key of each configuration table and value of the SymmetricDS version when they were introduced.
     */
    public static final Map<String, String> getConfigTablesByVersion(String tablePrefix) {
        Map<String, String> map = new HashMap<String, String>();
        addPrefixToTableNames(map, tablePrefix, "3.3.0", SYM_GROUPLET, SYM_GROUPLET_LINK, SYM_TRIGGER_ROUTER_GROUPLET);
        addPrefixToTableNames(map, tablePrefix, "3.5.0", SYM_FILE_TRIGGER, SYM_FILE_TRIGGER_ROUTER, SYM_FILE_SNAPSHOT, SYM_NODE_GROUP_CHANNEL_WND);
        addPrefixToTableNames(map, tablePrefix, "3.7.0", SYM_EXTENSION);
        addPrefixToTableNames(map, tablePrefix, "3.8.0", SYM_NOTIFICATION, SYM_MONITOR, SYM_MONITOR_EVENT, SYM_CONSOLE_EVENT);
        addPrefixToTableNames(map, tablePrefix, "3.8.18", SYM_CONSOLE_USER_HIST);
        addPrefixToTableNames(map, tablePrefix, "3.9.0", SYM_JOB);
        addPrefixToTableNames(map, tablePrefix, "3.12.0", SYM_CONSOLE_ROLE, SYM_CONSOLE_ROLE_PRIVILEGE);
        addPrefixToTableNames(map, tablePrefix, "3.14.0", SYM_TABLE_RELOAD_STATUS, SYM_EXTRACT_REQUEST, SYM_TABLE_GROUP, SYM_TABLE_GROUP_HIER);
        addPrefixToTableNames(map, tablePrefix, "3.15.0", SYM_OUTGOING_ERROR, SYM_INCOMING_ERROR, SYM_COMPARE_REQUEST, SYM_COMPARE_STATUS,
                SYM_COMPARE_TABLE_STATUS);
        addPrefixToTableNames(map, tablePrefix, "3.17.0", SYM_CONSOLE_USER_PREFERENCE);
        return map;
    }

    /**
     * Which tables from getConfigTables() should not be sent during registration. These tables will still have a trigger installed for capturing and sending
     * changes.
     */
    public static final String[] getConfigTablesExcludedFromRegistration() {
        return new String[] { SYM_MONITOR_EVENT, SYM_TABLE_RELOAD_REQUEST, SYM_TABLE_RELOAD_STATUS, SYM_EXTRACT_REQUEST, SYM_OUTGOING_ERROR, SYM_INCOMING_ERROR,
                SYM_COMPARE_REQUEST, SYM_COMPARE_STATUS, SYM_COMPARE_TABLE_STATUS };
    }

    /**
     * Which tables from getConfigTables() should not have a trigger installed for capturing changes. In other words, these are tables that are sent only during
     * registration, and they won't receive changes.
     */
    public static final Set<String> getConfigTablesWithoutCapture(String tablePrefix) {
        Set<String> tables = new HashSet<String>();
        addPrefixToTableNames(tables, tablePrefix, SYM_NODE_IDENTITY);
        return tables;
    }

    /**
     * Which tables from getConfigTables() should be excluded from a configuration export.
     */
    public static final String[] getConfigTablesExcludedFromExport() {
        return new String[] { SYM_NODE, SYM_NODE_SECURITY, SYM_NODE_IDENTITY, SYM_NODE_HOST, SYM_FILE_SNAPSHOT, SYM_CONSOLE_USER, SYM_CONSOLE_ROLE,
                SYM_CONSOLE_ROLE_PRIVILEGE, SYM_CONSOLE_USER_HIST, SYM_CONSOLE_USER_PREFERENCE, SYM_MONITOR_EVENT, SYM_TABLE_RELOAD_REQUEST,
                SYM_TABLE_RELOAD_STATUS,
                SYM_EXTRACT_REQUEST, SYM_OUTGOING_ERROR, SYM_INCOMING_ERROR, SYM_COMPARE_REQUEST, SYM_COMPARE_STATUS, SYM_COMPARE_TABLE_STATUS };
    }

    /**
     * List of configuration tables that should be used during a configuration export.
     */
    public static final List<String> getConfigTablesForExport(String tablePrefix) {
        List<String> tables = getConfigTables(tablePrefix);
        for (String table : getConfigTablesExcludedFromExport()) {
            tables.remove(getTableName(tablePrefix, table));
        }
        return tables;
    }

    /**
     * Set of configuration tables that have been decommissioned and should be removed when importing configuration from older versions.
     */
    public static final Set<String> getRemovedConfigTables(String tablePrefix) {
        Set<String> tables = new HashSet<String>();
        addPrefixToTableNames(tables, tablePrefix, SYM_DESIGN_DIAGRAM, SYM_DIAGRAM_GROUP);
        return tables;
    }

    protected static final void addPrefixToTableNames(Map<String, String> map, String tablePrefix, String version, String... names) {
        for (String name : names) {
            map.put(getTableName(tablePrefix, name), version);
        }
    }

    protected static final void addPrefixToTableNames(Collection<String> collection, String tablePrefix, String... names) {
        for (String name : names) {
            collection.add(getTableName(tablePrefix, name));
        }
    }

    public static String getTableName(String tablePrefix, String tableSuffix) {
        return String.format("%s%s%s", tablePrefix, StringUtils.isNotBlank(tablePrefix) ? "_" : "", tableSuffix);
    }

    public static boolean hasConsoleSchema() {
        return hasConsoleSchema;
    }
}