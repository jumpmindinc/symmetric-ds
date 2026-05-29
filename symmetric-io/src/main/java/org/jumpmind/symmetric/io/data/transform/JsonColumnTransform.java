package org.jumpmind.symmetric.io.data.transform;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataContext;

import com.google.gson.Gson;

public class JsonColumnTransform implements ISingleNewAndOldValueColumnTransform, IBuiltInExtensionPoint {
    public final static String NAME = "json";
    public final static String ID_HEADER = "idHeader";
    public final static String ID_COLUMN_NAME = "idColumnName";
    public final static String DATA_HEADER = "dataHeader";
    public final static String EXCLUDE_COLUMN_NAMES = "excludeColumnNames";

    public String getName() {
        return NAME;
    }

    @Override
    public boolean isExtractColumnTransform() {
        return true;
    }

    @Override
    public boolean isLoadColumnTransform() {
        return true;
    }

    @Override
    public NewAndOldValue transform(IDatabasePlatform platform, DataContext context, TransformColumn column,
            TransformedData data, Map<String, String> sourceValues, String newValue, String oldValue)
            throws IgnoreColumnException, IgnoreRowException {
        CsvData csvData = context.getData();
        Map<String, String> oldValues = null;
        Map<String, ?> oldValueMap = null;
        if (csvData.contains(CsvData.OLD_DATA)) {
            oldValues = csvData.toColumnNameValuePairs(context.getRelation().getColumnNames(), CsvData.OLD_DATA);
            oldValueMap = new LinkedHashMap<String, String>(oldValues);
        }
        Map<String, String> expressionMap = parseExpression(column.getTransformExpression());
        Map<String, ?> newValueMap = new LinkedHashMap<String, String>(sourceValues);
        if (expressionMap.containsKey(EXCLUDE_COLUMN_NAMES)) {
            for (String excludedColumnName : expressionMap.get(EXCLUDE_COLUMN_NAMES).split(",")) {
                newValueMap.remove(excludedColumnName);
                if (oldValueMap != null) {
                    oldValueMap.remove(excludedColumnName);
                }
            }
        }
        String dataHeader = expressionMap.get(DATA_HEADER);
        if (dataHeader != null) {
            Map<String, Object> newMap = new LinkedHashMap<String, Object>(), oldMap = new LinkedHashMap<String, Object>();
            String idHeader = expressionMap.get(ID_HEADER);
            if (idHeader != null) {
                String idColumnName = expressionMap.get(ID_COLUMN_NAME);
                newMap.put(idHeader, idColumnName != null ? sourceValues.get(idColumnName) : null);
                if (oldValues != null) {
                    oldMap.put(idHeader, idColumnName != null ? oldValues.get(idColumnName) : null);
                }
            }
            newMap.put(dataHeader, newValueMap);
            oldMap.put(dataHeader, oldValueMap);
            newValueMap = newMap;
            oldValueMap = oldMap;
        }
        Gson gson = new Gson();
        return new NewAndOldValue(gson.toJson(newValueMap), gson.toJson(oldValueMap));
    }

    public static Map<String, String> parseExpression(String expression) {
        Map<String, String> expressionMap = new HashMap<String, String>();
        if (StringUtils.isNotBlank(expression)) {
            for (String expressionPairString : CsvUtils.tokenizeCsvData(expression)) {
                String[] expressionPair = expressionPairString.split("==", -1);
                if (expressionPair.length == 2) {
                    expressionMap.put(expressionPair[0], expressionPair[1]);
                }
            }
        }
        return expressionMap;
    }
}
