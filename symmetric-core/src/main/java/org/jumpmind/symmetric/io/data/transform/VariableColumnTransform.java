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

import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;

public class VariableColumnTransform extends AbstractColumnTransform implements ISingleNewAndOldValueColumnTransform {
    public static final String NAME = "variable";
    final String SOURCE_NODE_KEY = String.format("%d.SourceNode", hashCode());
    protected static final String TS_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    protected static final String DATE_PATTERN = "yyyy-MM-dd";
    protected static final String OPTION_TIMESTAMP = "system_timestamp";
    protected static final String OPTION_TIMESTAMP_UTC = "system_timestamp_utc";
    protected static final String OPTION_DATE = "system_date";
    protected static final String OPTION_SOURCE_NODE_ID = "source_node_id";
    protected static final String OPTION_TARGET_NODE_ID = "target_node_id";
    protected static final String OPTION_SOURCE_NODE_ID_FROM_DATA = "source_node_id_from_data";
    protected static final String OPTION_NULL = "null";
    protected static final String OPTION_OLD_VALUE = "old_column_value";
    protected static final String OPTION_SOURCE_TABLE_NAME = "source_table_name";
    protected static final String OPTION_SOURCE_CATALOG_NAME = "source_catalog_name";
    protected static final String OPTION_SOURCE_SCHEMA_NAME = "source_schema_name";
    protected static final String OPTION_SOURCE_DML_TYPE = "source_dml_type";
    protected static final String OPTION_BATCH_ID = "batch_id";
    protected static final String OPTION_BATCH_START_TIME = "batch_start_time";
    protected static final String OPTION_DELETE_INDICATOR_FLAG = "delete_indicator_flag";
    private static final String[] OPTIONS = new String[] { OPTION_TIMESTAMP, OPTION_TIMESTAMP_UTC, OPTION_DATE,
            OPTION_SOURCE_NODE_ID, OPTION_TARGET_NODE_ID, OPTION_SOURCE_NODE_ID_FROM_DATA,
            OPTION_NULL, OPTION_OLD_VALUE, OPTION_SOURCE_CATALOG_NAME,
            OPTION_SOURCE_SCHEMA_NAME, OPTION_SOURCE_TABLE_NAME, OPTION_SOURCE_DML_TYPE, OPTION_BATCH_ID, OPTION_BATCH_START_TIME,
            OPTION_DELETE_INDICATOR_FLAG };

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isParameterServiceRequired() {
        return false;
    }

    @Override
    public boolean isExtractColumnTransform() {
        return true;
    }

    @Override
    public boolean isLoadColumnTransform() {
        return true;
    }

    public static String[] getOptions() {
        return OPTIONS;
    }

    @Override
    public NewAndOldValue transform(IDatabasePlatform platform,
            DataContext context,
            TransformColumn column, TransformedData data, Map<String, String> sourceValues,
            String newValue, String oldValue) throws IgnoreColumnException, IgnoreRowException {
        String varName = column.getTransformExpression();
        String value = null;
        if (varName != null) {
            if (varName.equalsIgnoreCase(OPTION_TIMESTAMP)) {
                value = DateFormatUtils.format(System.currentTimeMillis(), TS_PATTERN);
            } else if (varName.equalsIgnoreCase(OPTION_TIMESTAMP_UTC)) {
                value = DateFormatUtils.format(System.currentTimeMillis(), TS_PATTERN, TimeZone.getTimeZone("GMT"));
            } else if (varName.equalsIgnoreCase(OPTION_DATE)) {
                value = DateFormatUtils.format(System.currentTimeMillis(), DATE_PATTERN);
            } else if (varName.equalsIgnoreCase(OPTION_SOURCE_NODE_ID)) {
                value = context.getBatch().getSourceNodeId();
            } else if (varName.equalsIgnoreCase(OPTION_TARGET_NODE_ID)) {
                value = context.getBatch().getTargetNodeId();
            } else if (varName.equalsIgnoreCase(OPTION_SOURCE_NODE_ID_FROM_DATA)) {
                value = context.getData().getAttribute(CsvData.ATTRIBUTE_SOURCE_NODE_ID);
            } else if (varName.equalsIgnoreCase(OPTION_OLD_VALUE)) {
                value = oldValue;
            } else if (varName.equals(OPTION_NULL)) {
                value = null;
            } else if (varName.equals(OPTION_SOURCE_TABLE_NAME)) {
                Data csvData = (Data) context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA);
                if (csvData != null && csvData.getTriggerHistory() != null) {
                    value = csvData.getTriggerHistory().getSourceTableName();
                }
            } else if (varName.equals(OPTION_SOURCE_CATALOG_NAME)) {
                Data csvData = (Data) context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA);
                if (csvData != null && csvData.getTriggerHistory() != null) {
                    value = csvData.getTriggerHistory().getSourceCatalogName();
                }
            } else if (varName.equals(OPTION_SOURCE_SCHEMA_NAME)) {
                Data csvData = (Data) context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA);
                if (csvData != null && csvData.getTriggerHistory() != null) {
                    value = csvData.getTriggerHistory().getSourceSchemaName();
                }
            } else if (varName.equals(OPTION_SOURCE_DML_TYPE)) {
                value = data.getSourceDmlType().toString();
            } else if (varName.equals(OPTION_BATCH_ID)) {
                value = String.valueOf(context.getBatch().getBatchId());
            } else if (varName.equals(OPTION_BATCH_START_TIME)) {
                value = DateFormatUtils.format(context.getBatch().getStartTime(), TS_PATTERN);
            } else if (varName.equals(OPTION_DELETE_INDICATOR_FLAG)) {
                value = data.getSourceDmlType().equals(DataEventType.DELETE) ? "Y" : "N";
            }
        }
        return new NewAndOldValue(column, data, value);
    }
}