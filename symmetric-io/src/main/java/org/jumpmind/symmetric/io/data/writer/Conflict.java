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
package org.jumpmind.symmetric.io.data.writer;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.SchemaObject;

public class Conflict implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum DetectConflict {
        USE_CHANGED_DATA, USE_OLD_DATA, USE_PK_DATA, USE_TIMESTAMP, USE_VERSION
    };

    public enum ResolveConflict {
        NEWER_WINS, FALLBACK, MANUAL, IGNORE
    };

    public enum PingBack {
        OFF, SINGLE_ROW, REMAINING_ROWS
    }

    public enum DetectExpressionKey {
        EXCLUDED_COLUMN_NAMES
    }

    private String conflictId;
    private String targetChannelId;
    private String targetCatalogName;
    private String targetSchemaName;
    private String targetTableName;
    private DetectConflict detectType = DetectConflict.USE_CHANGED_DATA;
    private String detectExpression;
    private ResolveConflict resolveType = ResolveConflict.NEWER_WINS;
    private boolean resolveChangesOnly = false;
    private boolean resolveRowOnly = true;
    private Date createTime = new Date();
    private String lastUpdateBy = "symmetricds";
    private Date lastUpdateTime = new Date();
    private PingBack pingBack = PingBack.OFF;

    public String toQualifiedTableName() {
        if (StringUtils.isNotBlank(targetTableName)) {
            return SchemaObject.getFullyQualifiedName(targetCatalogName, targetSchemaName,
                    targetTableName).toLowerCase();
        } else {
            return null;
        }
    }

    public String getConflictId() {
        return conflictId;
    }

    public void setConflictId(String conflictId) {
        this.conflictId = conflictId;
    }

    public String getTargetChannelId() {
        return targetChannelId;
    }

    public void setTargetChannelId(String targetChannelId) {
        this.targetChannelId = targetChannelId;
    }

    public String getTargetCatalogName() {
        return targetCatalogName;
    }

    public void setTargetCatalogName(String targetCatalogName) {
        this.targetCatalogName = targetCatalogName;
    }

    public String getTargetSchemaName() {
        return targetSchemaName;
    }

    public void setTargetSchemaName(String targetSchemaName) {
        this.targetSchemaName = targetSchemaName;
    }

    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
    }

    public DetectConflict getDetectType() {
        return detectType;
    }

    public void setDetectType(DetectConflict detectUpdateType) {
        this.detectType = detectUpdateType;
    }

    public ResolveConflict getResolveType() {
        return resolveType;
    }

    public void setResolveType(ResolveConflict resolveUpdateType) {
        this.resolveType = resolveUpdateType;
    }

    public boolean isResolveChangesOnly() {
        return resolveChangesOnly;
    }

    public void setResolveChangesOnly(boolean resolveChangesOnly) {
        this.resolveChangesOnly = resolveChangesOnly;
    }

    public boolean isResolveRowOnly() {
        return resolveRowOnly;
    }

    public void setResolveRowOnly(boolean resolveRowOnly) {
        this.resolveRowOnly = resolveRowOnly;
    }

    public String getDetectExpression() {
        return detectExpression;
    }

    public String getDetectExpressionValue(DetectExpressionKey key) {
        String value = null;
        if (key != null && detectExpression != null) {
            String[] parms = detectExpression.split(";");
            for (String parm : parms) {
                String[] args = parm.split("=");
                if (args.length == 2 && args[0].trim().equalsIgnoreCase(key.name())) {
                    value = args[1].trim();
                    break;
                }
            }
        }
        return value;
    }

    public void setDetectExpression(String conflictColumnName) {
        this.detectExpression = conflictColumnName;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setPingBack(PingBack pingBack) {
        this.pingBack = pingBack;
    }

    public PingBack getPingBack() {
        return pingBack;
    }
}
