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
package org.jumpmind.db.io;

public class ReleaseNotesConstants {
    public static final String PRO_INDICATOR = "(Pro)";
    public static final String PRO_TOKEN_START = "ifdef::pro[]";
    public static final String PRO_TOKEN_END = "endif::pro[]";
    public static final String OS_TOKEN_START = "ifndef::pro[]";
    public static final String OS_TOKEN_END = "endif::pro[]";
    public static final String PROPERTIES_DIR = "../symmetric-core/src/main/resources/symmetric-default.properties";
    public static final String PRO_PROPERTIES_DIR = "../../symmetric-pro/symmetric-pro/src/main/resources/symmetric-console-default.properties";
    public static final String SCHEMA_DIR = "../symmetric-core/src/main/resources/symmetric-schema.xml";
    public static final String PRO_SCHEMA_DIR = "../../symmetric-pro/symmetric-pro/src/main/resources/console-schema.xml";
    public static final String ISSUE_URL = "https://jumpmind.atlassian.net/browse/%s[%s]";
    public static final String PROPERTIES_OLD_LOCATION = "build/";
    public static final String NOTES_HEADER = "= Release Notes";
    public static final String OVERVIEW_HEADER = "== Overview";
    public static final String OVERVIEW_DESC = "SymmetricDS @appMajorVersion@ release includes %d features, %d improvements, and %d bug fixes.";
    public static final String OVERVIEW_PRO_DESC = "SymmetricDS Pro @appMajorVersion@ release includes %d features, %d improvements, and %d bug fixes.";
    public static final String INCLUDE_FORMAT_GENERATED = "include::%s/%s[]";
    public static final String INCLUDE_FORMAT_WRITTEN = "include::%s/release-notes/%s[]";
    public static final String PARAMETER_SEC_HEADER = "== Parameters";
    public static final String PARAMETER_SEC_DESC = "The following changes were made to add new parameters, modify their default value, modify their "
            + "description, or remove them from use.";
    public static final String PARAMETER_NEW_HEADER = "=== New Parameters";
    public static final String PARAMETER_MOD_HEADER = "=== Modified Parameters";
    public static final String PARAMETER_FORMAT = ".*%s*";
    public static final String PARAMETER_PRO_FORMAT = ".*%s* (Pro)";
    public static final String PARAMETER_DESC_FORMAT = "%s (Default: %s)";
    public static final String PARAMETER_DESC_MOD_FORMAT = "%s (Old Default: %s) (New Default: %s)";
    public static final String PARAMETER_DESC_REMOVED = "{REMOVED}";
    public static final String TABLES_SEC_HEADER = "== Tables";
    public static final String TABLES_SEC_DESC = "The following changes were made to the definition of configuration and runtime tables. Table changes "
            + "are applied to the database automatically using data definition language (DDL) during startup.";
    public static final String TABLES_NEW_HEADER = "=== New Tables";
    public static final String TABLES_TABLE_HEADER = "[cols=\"4,6\"]\n|===\n|Table Name |Description";
    public static final String TABLES_TABLE_FOOTER = "|===";
    public static final String TABLES_TABLE_ELEMENT = "|`sym_%s`\n|%s";
    public static final String TABLES_TABLE_ELEMENT_PRO = "|`sym_%s` _(Pro)_\n|%s";
    public static final String TABLES_COLUMN_ELEMENT = "|`%s`\n|%s";
    public static final String TABLES_NEW_COL_HEADER = "=== New Columns";
    public static final String TABLES_COL_HEADER = ".*SYM_%s*\n[caption=\"\",cols=\"4,6\"]\n|===\n|Column Name |Description";
    public static final String TABLES_COL_HEADER_PRO = ".*SYM_%s* (Pro)\n[caption=\"\",cols=\"4,6\"]\n|===\n|Column Name |Description";
    public static final String TABLES_MODIFIED_HEADER = "=== Modified Tables";
    public static final String TABLES_MODIFIED_CAPTION = ".*SYM_%s*";
    public static final String TABLES_MODIFIED_CAPTION_PRO = ".*SYM_%s* (Pro)";
    public static final String TABLES_MODIFIED_ELEMENT = "* %s";
    public static final String TABLES_MOD_COLUMN_FORMAT = "`%s` %s changed from `%s` to `%s`";
    public static final String TABLES_ADD_FKEY_FORMAT = "Added foreign key `%s` (`%s`)";
    public static final String TABLES_DEL_FKEY_FORMAT = "Removed foreign key `%s` (`%s`)";
    public static final String TABLES_MOD_FKEY_FORMAT = "Modified references on foreign key `%s`:\n** Old: (`%s`)\n** New: (`%s`)";
    public static final String TABLES_ADD_PKEY_FORMAT = "Added primary keys: `%s`";
    public static final String TABLES_DEL_PKEY_FORMAT = "Removed primary keys: `%s`";
    public static final String TABLES_ADD_INDEX_FORMAT = "Added index `%s` (`%s`)";
    public static final String TABLES_DEL_INDEX_FORMAT = "Removed index `%s` (`%s`)";
    public static final String TABLES_MOD_INDEX_FORMAT = "Modified columns on index `%s`:\n** Old: (`%s`)\n** New: (`%s`)";
    public static final String MODIFIED_CONSTANT_DEF_VAL = "default value";
    public static final String MODIFIED_CONSTANT_IS_REQ = "isRequired";
    public static final String MODIFIED_CONSTANT_IS_UNQ = "isUnique";
    public static final String MODIFIED_CONSTANT_IS_DIST = "isDistributionKey";
    public static final String MODIFIED_CONSTANT_IS_AUTOINC = "isAutoIncrement";
    public static final String MODIFIED_CONSTANT_TYPE = "type";
    public static final String MODIFIED_CONSTANT_SIZE = "size";
    public static final String MODIFIED_CONSTANT_RADIX = "precision radix";
    public static final String MODIFIED_CONSTANT_INDEX = "Index";
    public static final String MODIFIED_CONSTANT_FKEY = "Foreign key";
    public static final String MODIFIED_CONSTANT_PKEY = "Primary key(s)";
    public static final String MODIFIED_CONSTANT_IS_AUTOIDX = "auto index";
    public static final String MODIFIED_CONSTANT_IS_TIMEZONE = "timestamp with timezone";
    public static final String NULL = "null";
    public static final String VALUE_SEPARATOR = "`, `";
    public static final String FIXES_SECURITY_HEADER = "=== Security Fixes";
    public static final String FIXES_PERFORMANCE_HEADER = "=== Performance Fixes";
    public static final String FIXES_TABLE_HEADER = "[cols=\"1,7,2\"]\n|===\n|Issue |Summary |Severity";
    public static final String FIXES_TABLE_FOOTER = "|===";
    public static final String FIXES_TABLE_ELEMENT = "|%s\n|%s\n|%s";
    public static final String FIXES_TABLE_ELEMENT_PRO = "|%s\n|%s (Pro)\n|%s";
    public static final String ISSUES_SEC_HEADER = "== Issues";
    public static final String ISSUES_FEATURES_HEADER = "=== New Features";
    public static final String ISSUES_IMPROVEMENTS_HEADER = "=== Improvements";
    public static final String ISSUES_BUG_FIXES_HEADER = "=== Bug Fixes";
    public static final String ISSUES_SEC_SEPARATOR = "--";
    public static final String ISSUES_HARDBREAKS = "[%hardbreaks]";
    public static final String ISSUES_VERSION_HEADER_PRO = "*%s (Pro)*";
    public static final String ISSUES_VERSION_HEADER = "*%s*";
    public static final String ISSUES_FORMAT = "%s - %s";
    public static final String SYMMETRIC_DS_PROJECT = "symmetric-ds";
    public static final String SYMMETRIC_PRO_PROJECT = "symmetric-pro";
}
