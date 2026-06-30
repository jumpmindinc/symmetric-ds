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
package org.jumpmind.symmetric.io.data.transform;

import java.util.TimeZone;

import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;

public class TransformVariableUtils {
    public static final String OPTION_TIMESTAMP = "system_timestamp";
    public static final String OPTION_TIMESTAMP_UTC = "system_timestamp_utc";
    public static final String OPTION_DATE = "system_date";
    public static final String OPTION_SOURCE_NODE_ID = "source_node_id";
    public static final String OPTION_TARGET_NODE_ID = "target_node_id";
    public static final String OPTION_SOURCE_NODE_ID_FROM_DATA = "source_node_id_from_data";
    public static final String OPTION_NULL = "null";
    public static final String OPTION_OLD_VALUE = "old_column_value";
    public static final String OPTION_SOURCE_TABLE_NAME = "source_table_name";
    public static final String OPTION_SOURCE_CATALOG_NAME = "source_catalog_name";
    public static final String OPTION_SOURCE_SCHEMA_NAME = "source_schema_name";
    public static final String OPTION_SOURCE_DML_TYPE = "source_dml_type";
    public static final String OPTION_BATCH_ID = "batch_id";
    public static final String OPTION_BATCH_START_TIME = "batch_start_time";
    public static final String OPTION_DELETE_INDICATOR_FLAG = "delete_indicator_flag";
    static final String TS_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String VARIABLE_PREFIX = "$(";
    private static final String VARIABLE_SUFFIX = ")";
    private static final String[] OPTIONS = new String[] { OPTION_TIMESTAMP, OPTION_TIMESTAMP_UTC, OPTION_DATE,
            OPTION_SOURCE_NODE_ID, OPTION_TARGET_NODE_ID, OPTION_SOURCE_NODE_ID_FROM_DATA,
            OPTION_NULL, OPTION_OLD_VALUE, OPTION_SOURCE_CATALOG_NAME,
            OPTION_SOURCE_SCHEMA_NAME, OPTION_SOURCE_TABLE_NAME, OPTION_SOURCE_DML_TYPE, OPTION_BATCH_ID,
            OPTION_BATCH_START_TIME, OPTION_DELETE_INDICATOR_FLAG };

    private TransformVariableUtils() {
    }

    public static String[] getOptions() {
        return OPTIONS;
    }

    public static String resolveVariable(String varName, DataContext context, TransformedData data, String oldValue) {
        if (OPTION_TIMESTAMP.equalsIgnoreCase(varName)) {
            return DateFormatUtils.format(System.currentTimeMillis(), TS_PATTERN);
        } else if (OPTION_TIMESTAMP_UTC.equalsIgnoreCase(varName)) {
            return DateFormatUtils.format(System.currentTimeMillis(), TS_PATTERN, TimeZone.getTimeZone("GMT"));
        } else if (OPTION_DATE.equalsIgnoreCase(varName)) {
            return DateFormatUtils.format(System.currentTimeMillis(), DATE_PATTERN);
        } else if (OPTION_SOURCE_NODE_ID.equalsIgnoreCase(varName)) {
            return context.getBatch().getSourceNodeId();
        } else if (OPTION_TARGET_NODE_ID.equalsIgnoreCase(varName)) {
            return context.getBatch().getTargetNodeId();
        } else if (OPTION_SOURCE_NODE_ID_FROM_DATA.equalsIgnoreCase(varName)) {
            return context.getData().getAttribute(CsvData.ATTRIBUTE_SOURCE_NODE_ID);
        } else if (OPTION_OLD_VALUE.equalsIgnoreCase(varName)) {
            return oldValue;
        } else if (OPTION_NULL.equalsIgnoreCase(varName)) {
            return null;
        } else if (Strings.CI.equalsAny(varName, OPTION_SOURCE_TABLE_NAME, OPTION_SOURCE_CATALOG_NAME, OPTION_SOURCE_SCHEMA_NAME)) {
            return resolveSourceNameVariable(varName, context);
        } else if (OPTION_SOURCE_DML_TYPE.equalsIgnoreCase(varName)) {
            return data.getSourceDmlType().toString();
        } else if (OPTION_BATCH_ID.equalsIgnoreCase(varName)) {
            return String.valueOf(context.getBatch().getBatchId());
        } else if (OPTION_BATCH_START_TIME.equalsIgnoreCase(varName)) {
            return DateFormatUtils.format(context.getBatch().getStartTime(), TS_PATTERN);
        } else if (OPTION_DELETE_INDICATOR_FLAG.equalsIgnoreCase(varName)) {
            return data.getSourceDmlType().equals(DataEventType.DELETE) ? "Y" : "N";
        }
        return null;
    }

    public static String resolveExpression(String expression, DataContext context, TransformedData data, String oldValue) {
        if (isVariableExpression(expression)) {
            return resolveVariable(extractVariableName(expression), context, data, oldValue);
        }
        return expression;
    }

    public static boolean isVariableExpression(String expression) {
        return expression != null && expression.startsWith(VARIABLE_PREFIX) && expression.endsWith(VARIABLE_SUFFIX)
                && expression.length() > VARIABLE_PREFIX.length() + VARIABLE_SUFFIX.length();
    }

    public static String extractVariableName(String expression) {
        return expression.substring(VARIABLE_PREFIX.length(), expression.length() - VARIABLE_SUFFIX.length());
    }

    private static String resolveSourceNameVariable(String varName, DataContext context) {
        Data csvData = (Data) context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA);
        if (csvData == null || csvData.getTriggerHistory() == null) {
            return null;
        }
        if (OPTION_SOURCE_TABLE_NAME.equals(varName)) {
            return csvData.getTriggerHistory().getSourceTableName();
        }
        if (OPTION_SOURCE_CATALOG_NAME.equals(varName)) {
            return csvData.getTriggerHistory().getSourceCatalogName();
        }
        return csvData.getTriggerHistory().getSourceSchemaName();
    }
}
