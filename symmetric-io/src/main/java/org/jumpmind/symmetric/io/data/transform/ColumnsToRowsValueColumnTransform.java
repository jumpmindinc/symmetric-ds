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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;

public class ColumnsToRowsValueColumnTransform implements ISingleValueColumnTransform, IBuiltInExtensionPoint {
    public final static String NAME = "columnsToRowsValue";
    protected final static String OPTION_CHANGES_ONLY = "changesOnly";
    protected final static String OPTION_IGNORE_NULLS = "ignoreNulls";

    public String getName() {
        return NAME;
    }

    public boolean isExtractColumnTransform() {
        return true;
    }

    public boolean isLoadColumnTransform() {
        return true;
    }

    public String transform(IDatabasePlatform platform, DataContext context, TransformColumn column, TransformedData data,
            Map<String, String> sourceValues, String newValue, String oldValue) throws IgnoreRowException, IgnoreColumnException {
        String contextBase = ColumnsToRowsKeyColumnTransform.getContextBase(column.getTransformId());
        @SuppressWarnings("unchecked")
        Map<String, String> reverseMap = (Map<String, String>) context.get(contextBase + ColumnsToRowsKeyColumnTransform.CONTEXT_MAP);
        String pkColumnName = (String) context.get(contextBase + ColumnsToRowsKeyColumnTransform.CONTEXT_PK_COLUMN);
        if (reverseMap == null) {
            throw new RuntimeException("Reverse map not found in context as key " + contextBase
                    + ColumnsToRowsKeyColumnTransform.CONTEXT_MAP + "  Unable to transform.");
        }
        if (pkColumnName == null) {
            throw new RuntimeException("Primary key column name not found in context as key " + contextBase
                    + ColumnsToRowsKeyColumnTransform.CONTEXT_PK_COLUMN + "  Unable to transform.");
        }
        String expr = column.getTransformExpression();
        boolean isChangesOnly = false;
        boolean isIgnoreNulls = false;
        if (expr != null) {
            isChangesOnly = expr.indexOf(OPTION_CHANGES_ONLY + "=true") != -1;
            isIgnoreNulls = expr.indexOf(OPTION_IGNORE_NULLS + "=true") != -1;
        }
        String pkValue = data.getTargetKeyValues().get(pkColumnName);
        String value = null;
        if (pkValue != null) {
            value = reverseMap.get(pkValue);
            if (value != null) {
                String srcNewValue = data.getSourceValues().get(value);
                String srcOldValue = data.getOldSourceValues() != null ? data.getOldSourceValues().get(value) : null;
                if (isIgnoreNulls && DataEventType.INSERT == data.getSourceDmlType() && (StringUtils.trimToNull(srcNewValue) == null)) {
                    throw new IgnoreRowException();
                } else if (DataEventType.UPDATE == data.getSourceDmlType()) {
                    if (isChangesOnly && StringUtils.trimToEmpty(srcNewValue).equals(StringUtils.trimToEmpty(srcOldValue))) {
                        throw new IgnoreRowException();
                    } else if (isIgnoreNulls && StringUtils.trimToNull(srcNewValue) == null) {
                        data.setTargetDmlType(DataEventType.DELETE);
                    }
                }
                return srcNewValue;
            } else {
                throw new RuntimeException("Unable to locate column name for pk value " + pkValue);
            }
        } else {
            throw new RuntimeException("Unable to locate column with pk name " + pkColumnName + " in target values.  Did you mark it as PK?");
        }
    }
}
