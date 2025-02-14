package org.jumpmind.db.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jumpmind.db.platform.DatabaseNamesConstants;

public class Function implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;
    private String functionName;
    private String catalogName;
    private String schemaName;
    private String tableName;
    private String triggerName;
    Map<String, PlatformFunction> platformFunctions;

    public Function(String functionName, String catalogName, String schemaName, String tableName) {
        this.functionName = functionName;
        this.catalogName = catalogName;
        this.schemaName = schemaName;
        this.tableName = tableName;
    }

    public Function() {
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public void setCatalogName(String catalogName) {
        this.catalogName = catalogName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTriggerName() {
        return triggerName;
    }

    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    public void removePlatformFunction(String databaseName) {
        if (platformFunctions != null) {
            platformFunctions.remove(databaseName);
        }
    }

    public void addPlatformFunction(PlatformFunction platformFunction) {
        if (platformFunctions == null) {
            platformFunctions = new HashMap<String, PlatformFunction>();
        }
        platformFunctions.put(platformFunction.getName(), platformFunction);
    }

    public Map<String, PlatformFunction> getPlatformFunctions() {
        return platformFunctions;
    }

    public PlatformFunction findPlatformFunction(String name) {
        PlatformFunction platformFunction = null;
        if (platformFunctions != null) {
            platformFunction = platformFunctions.get(name);
            if (platformFunction == null) {
                if (name.contains(DatabaseNamesConstants.MSSQL)) {
                    return findDifferentVersionPlatformFunction(DatabaseNamesConstants.MSSQL);
                } else if (name.contains(DatabaseNamesConstants.ORACLE)) {
                    return findDifferentVersionPlatformFunction(DatabaseNamesConstants.ORACLE);
                } else if (name.contains(DatabaseNamesConstants.POSTGRESQL)) {
                    return findDifferentVersionPlatformFunction(DatabaseNamesConstants.POSTGRESQL);
                } else if (name.contains(DatabaseNamesConstants.SQLANYWHERE)) {
                    return findDifferentVersionPlatformFunction(DatabaseNamesConstants.SQLANYWHERE);
                } else if (name.contains(DatabaseNamesConstants.FIREBIRD)) {
                    return findDifferentVersionPlatformFunction(DatabaseNamesConstants.FIREBIRD);
                } else if (name.contains(DatabaseNamesConstants.HSQLDB)) {
                    return findDifferentVersionPlatformFunction(DatabaseNamesConstants.HSQLDB);
                }
            }
        }
        return platformFunction;
    }

    private PlatformFunction findDifferentVersionPlatformFunction(String name) {
        if (platformFunctions != null) {
            for (Entry<String, PlatformFunction> entry : platformFunctions.entrySet()) {
                if (entry.getKey() != null && entry.getKey().contains(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public boolean anyPlatformFunctionNameContains(String name) {
        if (platformFunctions != null) {
            for (String platformTriggerName : platformFunctions.keySet()) {
                if (platformTriggerName != null && platformTriggerName.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean allPlatformFunctionNamesContain(String name) {
        if (platformFunctions != null) {
            for (String platformTriggerName : platformFunctions.keySet()) {
                if (platformTriggerName != null && !platformTriggerName.contains(name)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Object clone() throws CloneNotSupportedException {
        Function result = (Function) super.clone();
        result.catalogName = catalogName;
        result.schemaName = schemaName;
        result.tableName = tableName;
        result.functionName = functionName;
        result.triggerName = triggerName;
        if (platformFunctions != null) {
            result.platformFunctions = new HashMap<String, PlatformFunction>(platformFunctions.size());
            for (Map.Entry<String, PlatformFunction> platformFunction : platformFunctions.entrySet()) {
                result.platformFunctions.put(platformFunction.getKey(), (PlatformFunction) platformFunction.getValue().clone());
            }
        }
        return result;
    }

    public String getFullyQualifiedName() {
        return getFullyQualifiedName(catalogName, schemaName, tableName, triggerName);
    }

    public static String getFullyQualifiedName(String catalog, String schema, String tableName, String functionName) {
        String fullName = "";
        if (catalog != null) {
            fullName += catalog + ".";
        }
        if (schema != null) {
            fullName += schema + ".";
        }
        fullName += tableName + "." + functionName;
        return fullName;
    }
}
