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
package org.jumpmind.db.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jumpmind.db.platform.DatabaseNamesConstants;

public class Trigger implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    public enum TriggerType {
        INSERT, UPDATE, DELETE
    };

    String triggerName;
    String catalogName;
    String schemaName;
    String tableName;
    String source;
    TriggerType triggerType;
    boolean enabled;
    Map<String, Object> metaData = new HashMap<String, Object>();
    Map<String, PlatformTrigger> platformTriggers;

    public Trigger(String name, String catalogName, String schemaName, String tableName, TriggerType triggerType) {
        this(name, catalogName, schemaName, tableName, triggerType, true);
    }

    public Trigger(String name, String catalogName, String schemaName, String tableName, TriggerType triggerType, boolean enabled) {
        this.triggerName = name;
        this.catalogName = catalogName;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.triggerType = triggerType;
        this.enabled = enabled;
    }

    public Trigger() {
    }

    public String getName() {
        return triggerName;
    }

    public void setName(String name) {
        this.triggerName = name;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getMetaData() {
        return metaData;
    }

    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public String getFullyQualifiedName() {
        return getFullyQualifiedName(catalogName, schemaName, tableName, triggerName);
    }

    public void removePlatformTrigger(String databaseName) {
        if (platformTriggers != null) {
            platformTriggers.remove(databaseName);
        }
    }

    public void addPlatformTrigger(PlatformTrigger platformTrigger) {
        if (platformTriggers == null) {
            platformTriggers = new HashMap<String, PlatformTrigger>();
        }
        platformTriggers.put(platformTrigger.getName(), platformTrigger);
    }

    public Map<String, PlatformTrigger> getPlatformTriggers() {
        return platformTriggers;
    }

    public PlatformTrigger findPlatformTrigger(String name) {
        PlatformTrigger platformTrigger = null;
        if (platformTriggers != null) {
            platformTrigger = platformTriggers.get(name);
            if (platformTrigger == null) {
                if (name.contains(DatabaseNamesConstants.MSSQL)) {
                    return findDifferentVersionPlatformTrigger(DatabaseNamesConstants.MSSQL);
                } else if (name.contains(DatabaseNamesConstants.ORACLE)) {
                    return findDifferentVersionPlatformTrigger(DatabaseNamesConstants.ORACLE);
                } else if (name.contains(DatabaseNamesConstants.POSTGRESQL)) {
                    return findDifferentVersionPlatformTrigger(DatabaseNamesConstants.POSTGRESQL);
                } else if (name.contains(DatabaseNamesConstants.SQLANYWHERE)) {
                    return findDifferentVersionPlatformTrigger(DatabaseNamesConstants.SQLANYWHERE);
                } else if (name.contains(DatabaseNamesConstants.FIREBIRD)) {
                    return findDifferentVersionPlatformTrigger(DatabaseNamesConstants.FIREBIRD);
                } else if (name.contains(DatabaseNamesConstants.HSQLDB)) {
                    return findDifferentVersionPlatformTrigger(DatabaseNamesConstants.HSQLDB);
                }
            }
        }
        return platformTrigger;
    }

    private PlatformTrigger findDifferentVersionPlatformTrigger(String name) {
        if (platformTriggers != null) {
            for (Entry<String, PlatformTrigger> entry : platformTriggers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().contains(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public boolean anyPlatformTriggerNameContains(String name) {
        if (platformTriggers != null) {
            for (String platformTriggerName : platformTriggers.keySet()) {
                if (platformTriggerName != null && platformTriggerName.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean allPlatformTriggerNamesContain(String name) {
        if (platformTriggers != null) {
            for (String platformTriggerName : platformTriggers.keySet()) {
                if (platformTriggerName != null && !platformTriggerName.contains(name)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Object clone() throws CloneNotSupportedException {
        Trigger result = (Trigger) super.clone();
        result.triggerName = triggerName;
        result.catalogName = catalogName;
        result.enabled = enabled;
        result.schemaName = schemaName;
        result.source = source;
        result.tableName = tableName;
        result.triggerType = triggerType;
        if (platformTriggers != null) {
            result.platformTriggers = new HashMap<String, PlatformTrigger>(platformTriggers.size());
            for (Map.Entry<String, PlatformTrigger> platformTrigger : platformTriggers.entrySet()) {
                result.platformTriggers.put(platformTrigger.getKey(), (PlatformTrigger) platformTrigger.getValue().clone());
            }
        }
        return result;
    }

    public static String getFullyQualifiedName(String catalog, String schema, String tableName, String triggerName) {
        String fullName = "";
        if (catalog != null)
            fullName += catalog + ".";
        if (schema != null)
            fullName += schema + ".";
        fullName += tableName + "." + triggerName;
        return fullName;
    }
}
