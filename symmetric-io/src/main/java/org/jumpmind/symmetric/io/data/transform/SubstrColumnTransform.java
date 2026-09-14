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

public class SubstrColumnTransform implements ISingleNewAndOldValueColumnTransform, IBuiltInExtensionPoint {
    public static final String NAME = "substr";

    public String getName() {
        return NAME;
    }

    public boolean isExtractColumnTransform() {
        return true;
    }

    public boolean isLoadColumnTransform() {
        return true;
    }

    public NewAndOldValue transform(IDatabasePlatform platform,
            DataContext context,
            TransformColumn column, TransformedData data, Map<String, String> sourceValues,
            String newValue, String oldValue) throws IgnoreColumnException, IgnoreRowException {
        if (StringUtils.isNotEmpty(newValue)) {
            String expression = column.getTransformExpression();
            if (StringUtils.isNotBlank(expression)) {
                String[] tokens = expression.split(",");
                if (tokens.length == 1) {
                    int index = Integer.parseInt(tokens[0]);
                    if (newValue.length() > index) {
                        newValue = newValue.substring(index);
                    } else {
                        newValue = "";
                    }
                } else if (tokens.length > 1) {
                    int beginIndex = Integer.parseInt(tokens[0]);
                    int endIndex = Integer.parseInt(tokens[1]);
                    if (newValue.length() > endIndex && endIndex > beginIndex) {
                        newValue = newValue.substring(beginIndex, endIndex);
                    } else if (newValue.length() > beginIndex) {
                        newValue = newValue.substring(beginIndex);
                    } else {
                        newValue = "";
                    }
                }
            }
        }
        return new NewAndOldValue(column, data, newValue);
    }

    protected String[] prepend(String v, String[] array) {
        String[] dest = new String[array.length + 1];
        dest[0] = v;
        for (int i = 0; i < array.length; i++) {
            dest[i + 1] = array[i];
        }
        return dest;
    }
}
