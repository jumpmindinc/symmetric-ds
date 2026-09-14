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
package org.jumpmind.symmetric.io.data.transform;

import java.util.Date;

public class TransformColumn implements Comparable<TransformColumn>, Cloneable {
    public enum IncludeOnType {
        INSERT, UPDATE, DELETE, ALL;

        public static IncludeOnType decode(String v) {
            if (v.equals("I")) {
                return INSERT;
            } else if (v.equals("U")) {
                return UPDATE;
            } else if (v.equals("D")) {
                return DELETE;
            } else {
                return ALL;
            }
        };

        public String toDbValue() {
            if (this == INSERT) {
                return "I";
            } else if (this == DELETE) {
                return "D";
            } else if (this == UPDATE) {
                return "U";
            } else {
                return "*";
            }
        }
    }

    protected String transformId;
    protected String sourceColumnName;
    protected String targetColumnName;
    protected boolean pk;
    protected String transformType = CopyColumnTransform.NAME;
    protected String transformExpression;
    protected int transformOrder;
    protected IncludeOnType includeOn = IncludeOnType.ALL;
    protected Date createTime;
    protected Date lastUpdateTime;
    protected String lastUpdateBy;
    protected String sourceColumnNameLowerCase;
    protected String targetColumnNameLowerCase;

    public TransformColumn(String transformId) {
        this.transformId = transformId;
    }

    public TransformColumn() {
    }

    public TransformColumn(String sourceColumnName, String targetColumnName, boolean pk) {
        setSourceColumnName(sourceColumnName);
        setTargetColumnName(targetColumnName);
        this.pk = pk;
    }

    public TransformColumn(String sourceColumnName, String targetColumnName, boolean pk,
            String transformType, String transformExpression) {
        this(sourceColumnName, targetColumnName, pk);
        this.transformType = transformType;
        this.transformExpression = transformExpression;
    }

    public String getSourceColumnName() {
        return sourceColumnName;
    }

    public String getSourceColumnNameLowerCase() {
        return sourceColumnNameLowerCase;
    }

    public final void setSourceColumnName(String sourceColumnName) {
        this.sourceColumnName = sourceColumnName;
        this.sourceColumnNameLowerCase = sourceColumnName == null ? null : sourceColumnName.toLowerCase();
    }

    public String getTargetColumnName() {
        return targetColumnName;
    }

    public String getTargetColumnNameLowerCase() {
        return targetColumnNameLowerCase;
    }

    public final void setTargetColumnName(String targetColumnName) {
        this.targetColumnName = targetColumnName;
        this.targetColumnNameLowerCase = targetColumnName == null ? null : targetColumnName.toLowerCase();
    }

    public boolean isPk() {
        return pk;
    }

    public void setPk(boolean pk) {
        this.pk = pk;
    }

    public String getTransformType() {
        return transformType;
    }

    public void setTransformType(String transformType) {
        this.transformType = transformType;
    }

    public String getTransformExpression() {
        return transformExpression;
    }

    public void setTransformExpression(String transformExpression) {
        this.transformExpression = transformExpression;
    }

    public void setIncludeOn(IncludeOnType includeOn) {
        this.includeOn = includeOn;
    }

    public IncludeOnType getIncludeOn() {
        return includeOn;
    }

    public void setTransformOrder(int transformOrder) {
        this.transformOrder = transformOrder;
    }

    public int getTransformOrder() {
        return transformOrder;
    }

    public void setTransformId(String transformId) {
        this.transformId = transformId;
    }

    public String getTransformId() {
        return transformId;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((includeOn == null) ? 0 : includeOn.hashCode());
        result = prime * result + ((targetColumnName == null) ? 0 : targetColumnName.hashCode());
        result = prime * result + ((transformId == null) ? 0 : transformId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TransformColumn)) {
            return false;
        }
        TransformColumn other = (TransformColumn) obj;
        if (includeOn != other.includeOn) {
            return false;
        }
        if (targetColumnName == null) {
            if (other.targetColumnName != null) {
                return false;
            }
        } else if (!targetColumnName.equals(other.targetColumnName)) {
            return false;
        }
        if (transformId == null) {
            if (other.transformId != null) {
                return false;
            }
        } else if (!transformId.equals(other.transformId)) {
            return false;
        }
        return true;
    }

    @Override
    public TransformColumn clone() {
        TransformColumn clone = new TransformColumn();
        clone.setTransformId(transformId);
        clone.setSourceColumnName(sourceColumnName);
        clone.setTargetColumnName(targetColumnName);
        clone.setPk(pk);
        clone.setTransformType(transformType);
        clone.setTransformExpression(transformExpression);
        clone.setTransformOrder(transformOrder);
        clone.setIncludeOn(includeOn);
        clone.setCreateTime(createTime == null ? null : new Date(createTime.getTime()));
        clone.setLastUpdateTime(lastUpdateTime == null ? null : new Date(lastUpdateTime.getTime()));
        clone.setLastUpdateBy(lastUpdateBy);
        return clone;
    }

    public int compareTo(TransformColumn o) {
        return Integer.valueOf(transformOrder).compareTo(Integer.valueOf(o.transformOrder));
    }
}
