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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.DataContext;

public class ColumnsToRowsKeyColumnTransform implements IMultipleValueColumnTransform, IBuiltInExtensionPoint {
    public final static String NAME = "columnsToRowsKey";
    public final static String CONTEXT_MAP = "Map";
    public final static String CONTEXT_PK_COLUMN = "PKColumn";

    public String getName() {
        return NAME;
    }

    public static String getContextBase(String transformId) {
        return NAME + ":" + transformId + ":";
    }

    public boolean isExtractColumnTransform() {
        return true;
    }

    public boolean isLoadColumnTransform() {
        return true;
    }

    public List<String> transform(IDatabasePlatform platform, DataContext context, TransformColumn column, TransformedData data,
            Map<String, String> sourceValues, String newValue, String oldValue) throws IgnoreRowException {
        if (StringUtils.trimToNull(column.getTransformExpression()) == null) {
            throw new RuntimeException(
                    "Transform configured incorrectly.  A map representing PK and column names must be defined as part of the transform expression");
        }
        String mapAsString = StringUtils.trimToEmpty(column.getTransformExpression());
        // Build reverse map, while also building up array to return
        List<String> result = new ArrayList<String>();
        Map<String, String> reverseMap = new HashMap<String, String>();
        StringTokenizer tokens = new StringTokenizer(mapAsString);
        while (tokens.hasMoreElements()) {
            String keyValue = (String) tokens.nextElement();
            int equalIndex = keyValue.indexOf("=");
            if (equalIndex != -1) {
                reverseMap.put(keyValue.substring(equalIndex + 1), keyValue.substring(0, equalIndex));
                result.add(keyValue.substring(equalIndex + 1));
            } else {
                throw new RuntimeException("Map string for " + column.getTransformExpression() + " is invalid format: " + mapAsString);
            }
        }
        context.put(getContextBase(column.getTransformId()) + CONTEXT_MAP, reverseMap);
        context.put(getContextBase(column.getTransformId()) + CONTEXT_PK_COLUMN, column.getTargetColumnName());
        return result;
    }
}
