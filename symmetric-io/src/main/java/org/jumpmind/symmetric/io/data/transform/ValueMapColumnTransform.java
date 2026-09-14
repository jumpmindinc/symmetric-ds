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

import org.apache.commons.text.StringTokenizer;
import org.apache.commons.text.matcher.StringMatcherFactory;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.DataContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValueMapColumnTransform implements ISingleNewAndOldValueColumnTransform, IBuiltInExtensionPoint {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static final String NAME = "valueMap";

    public String getName() {
        return NAME;
    }

    public boolean isExtractColumnTransform() {
        return true;
    }

    public boolean isLoadColumnTransform() {
        return true;
    }

    private static String getValue(String value, String expression) {
        if (expression == null) {
            return null;
        }
        StringTokenizer tokens = new StringTokenizer(expression);
        tokens.setQuoteChar('"');
        tokens.setDelimiterMatcher(StringMatcherFactory.INSTANCE.charSetMatcher(' ', '='));
        String defaultValue = null;
        String[] tokenArray = tokens.getTokenArray();
        for (int i = 0; i < tokenArray.length - 1; i += 2) {
            if (tokenArray[i].equals(value)) {
                return tokenArray[i + 1];
            } else if (tokenArray[i].equals("*")) {
                String targetValue = tokenArray[i + 1];
                if (targetValue.equals("*")) {
                    defaultValue = value;
                } else {
                    defaultValue = targetValue;
                }
            }
        }
        return defaultValue;
    }

    public NewAndOldValue transform(IDatabasePlatform platform,
            DataContext context,
            TransformColumn column, TransformedData data, Map<String, String> sourceValues,
            String newValue, String oldValue) throws IgnoreColumnException, IgnoreRowException {
        String value = getValue(newValue, column.getTransformExpression());
        return new NewAndOldValue(column, data, value);
    }
}
