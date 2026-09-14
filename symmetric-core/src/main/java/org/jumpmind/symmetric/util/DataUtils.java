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
package org.jumpmind.symmetric.util;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Relation;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.util.LinkedCaseInsensitiveMap;

final public class DataUtils {
    private static final String OLD_ = "OLD_";

    public static Map<String, String> getDataMap(DataMetaData dataMetaData) {
        Map<String, String> data = null;
        DataEventType dml = dataMetaData.getData().getDataEventType();
        if (DataEventType.UPDATE.equals(dml) || DataEventType.INSERT.equals(dml) || DataEventType.DELETE.equals(dml)) {
            Relation relation = dataMetaData.getRelation();
            if (relation != null) {
                data = new LinkedCaseInsensitiveMap<String>(relation.getColumnCount() * 4);
            } else {
                data = new LinkedCaseInsensitiveMap<String>();
            }
        } else {
            data = new LinkedCaseInsensitiveMap<String>(1);
        }
        switch (dml) {
            case UPDATE:
                data.putAll(getNewDataAsString(null, dataMetaData));
                data.putAll(getOldDataAsString(OLD_, dataMetaData));
                break;
            case INSERT:
                data.putAll(getNewDataAsString(null, dataMetaData));
                Map<String, String> map = getNullData(OLD_, dataMetaData);
                data.putAll(map);
                break;
            case DELETE:
                data.putAll(getOldDataAsString(null, dataMetaData));
                data.putAll(getOldDataAsString(OLD_, dataMetaData));
                break;
            default:
        }
        if (data != null) {
            if (data.size() == 0) {
                data.putAll(getPkDataAsString(dataMetaData));
            }
            data.put("EXTERNAL_DATA", dataMetaData.getData().getExternalData());
        }
        return data;
    }

    public static Map<String, String> getNewDataAsString(String prefix, DataMetaData dataMetaData) {
        String[] rowData = dataMetaData.getData().toParsedRowData();
        return getDataAsString(prefix, dataMetaData, rowData);
    }

    public static Map<String, String> getOldDataAsString(String prefix, DataMetaData dataMetaData) {
        String[] rowData = dataMetaData.getData().toParsedOldData();
        return getDataAsString(prefix, dataMetaData, rowData);
    }

    public static Map<String, String> getDataAsString(String prefix, DataMetaData dataMetaData, String[] rowData) {
        String[] columns = dataMetaData.getTriggerHistory().getParsedColumnNames();
        Map<String, String> map = new LinkedCaseInsensitiveMap<String>(columns.length * 2);
        if (rowData != null) {
            testColumnNamesMatchValues(dataMetaData, columns, rowData);
            for (int i = 0; i < columns.length; i++) {
                String columnName = columns[i];
                columnName = prefix != null ? prefix + columnName : columnName;
                map.put(columnName, rowData[i]);
                map.put(columnName.toUpperCase(), rowData[i]);
            }
        }
        return map;
    }

    public static Map<String, String> getPkDataAsString(DataMetaData dataMetaData) {
        String[] columns = dataMetaData.getTriggerHistory().getParsedPkColumnNames();
        String[] rowData = dataMetaData.getData().toParsedPkData();
        Map<String, String> map = new LinkedCaseInsensitiveMap<String>(columns.length);
        if (rowData != null) {
            testColumnNamesMatchValues(dataMetaData, columns, rowData);
            for (int i = 0; i < columns.length; i++) {
                String columnName = columns[i].toUpperCase();
                map.put(columnName, rowData[i]);
            }
        }
        return map;
    }

    public static Map<String, Object> getDataObjectMap(DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        Map<String, Object> data = null;
        DataEventType dml = dataMetaData.getData().getDataEventType();
        switch (dml) {
            case UPDATE:
                data = new LinkedCaseInsensitiveMap<Object>(dataMetaData.getRelation().getColumnCount() * 2);
                data.putAll(getNewDataAsObject(null, dataMetaData, symmetricDialect, upperCase));
                data.putAll(getOldDataAsObject(OLD_, dataMetaData, symmetricDialect, upperCase));
                break;
            case INSERT:
                data = new LinkedCaseInsensitiveMap<Object>(dataMetaData.getRelation().getColumnCount() * 2);
                data.putAll(getNewDataAsObject(null, dataMetaData, symmetricDialect, upperCase));
                Map<String, Object> map = getNullData(OLD_, dataMetaData);
                data.putAll(map);
                break;
            case DELETE:
                data = new LinkedCaseInsensitiveMap<Object>(dataMetaData.getRelation().getColumnCount() * 2);
                data.putAll(getOldDataAsObject(null, dataMetaData, symmetricDialect, upperCase));
                data.putAll(getOldDataAsObject(OLD_, dataMetaData, symmetricDialect, upperCase));
                if (data.size() == 0) {
                    data.putAll(getPkDataAsObject(dataMetaData, symmetricDialect));
                }
                break;
            default:
                data = new LinkedCaseInsensitiveMap<Object>(1);
                break;
        }
        if (data.size() == 0) {
            data.putAll(getPkDataAsString(dataMetaData));
        }
        if (StringUtils.isNotBlank(dataMetaData.getData().getExternalData())) {
            data.put("EXTERNAL_DATA", dataMetaData.getData().getExternalData());
        } else {
            data.put("EXTERNAL_DATA", null);
        }
        return data;
    }

    public static Map<String, Object> getNewDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        return getDataAsObject(prefix, dataMetaData, symmetricDialect, dataMetaData.getData()
                .toParsedRowData(), upperCase);
    }

    public static Map<String, Object> getOldDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        return getDataAsObject(prefix, dataMetaData, symmetricDialect, dataMetaData.getData()
                .toParsedOldData(), upperCase);
    }

    public static <T> Map<String, T> getNullData(String prefix, DataMetaData dataMetaData) {
        String[] columnNames = dataMetaData.getTriggerHistory().getParsedColumnNames();
        Map<String, T> data = new LinkedCaseInsensitiveMap<T>(columnNames.length * 2);
        for (String columnName : columnNames) {
            columnName = prefix != null ? prefix + columnName : columnName;
            data.put(columnName, null);
            data.put(columnName.toUpperCase(), null);
        }
        return data;
    }

    public static Map<String, Object> getDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, String[] rowData, boolean upperCase) {
        if (rowData != null) {
            Map<String, Object> data = new LinkedCaseInsensitiveMap<Object>(rowData.length);
            String[] columnNames = dataMetaData.getTriggerHistory().getParsedColumnNames();
            Object[] objects = symmetricDialect.getPlatform().getObjectValues(
                    symmetricDialect.getBinaryEncoding(), dataMetaData.getRelation(), columnNames,
                    rowData);
            testColumnNamesMatchValues(dataMetaData, columnNames, objects);
            for (int i = 0; i < columnNames.length; i++) {
                String colName = upperCase ? columnNames[i].toUpperCase() : columnNames[i];
                data.put(prefix != null ? (prefix + colName) : colName, objects[i]);
            }
            return data;
        } else {
            return Collections.emptyMap();
        }
    }

    public static void testColumnNamesMatchValues(DataMetaData dataMetaData, String[] columnNames, Object[] values) {
        if (columnNames.length != values.length) {
            String message = String.format(
                    "The router row for table %s had %d columns but expected %d.",
                    dataMetaData.getData().getTableName(), values.length, columnNames.length);
            throw new ProtocolException(message);
        }
    }

    public static Map<String, Object> getPkDataAsObject(DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect) {
        String[] rowData = dataMetaData.getData().toParsedPkData();
        if (rowData != null) {
            Map<String, Object> data = new LinkedCaseInsensitiveMap<Object>(rowData.length);
            String[] columnNames = dataMetaData.getTriggerHistory().getParsedPkColumnNames();
            Object[] objects = symmetricDialect.getPlatform().getObjectValues(
                    symmetricDialect.getBinaryEncoding(), dataMetaData.getRelation(), columnNames,
                    rowData);
            testColumnNamesMatchValues(dataMetaData, columnNames, objects);
            for (int i = 0; i < columnNames.length; i++) {
                data.put(columnNames[i].toUpperCase(), objects[i]);
            }
            return data;
        } else {
            return Collections.emptyMap();
        }
    }
}
