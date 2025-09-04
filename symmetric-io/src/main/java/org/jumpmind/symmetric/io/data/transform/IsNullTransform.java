/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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
import org.jumpmind.symmetric.io.data.DataContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IsNullTransform extends ConflictAwareColumnTransformExtension {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static final String NAME = "isNull";

    @Override
    public String getName() {
        return NAME;
    }


    @Override
    public String transformNewValue(IDatabasePlatform platform, DataContext context, TransformColumn column,
            TransformedData data, Map<String, String> sourceValues, String newValue, String oldValue)
            throws IgnoreColumnException, IgnoreRowException {
        if (newValue != null) {
            return newValue;
        }
        String expression = column.getTransformExpression();
        if (StringUtils.isNotEmpty(expression)) {
            return expression;
        } else {
            return "";
        }
    }

    @Override
    public String transformOldValue(IDatabasePlatform platform, DataContext context, TransformColumn column,
            TransformedData data, Map<String, String> sourceValues, String newValue, String oldValue)
            throws IgnoreColumnException, IgnoreRowException {
        if (oldValue != null) {
            return oldValue;
        }
        String expression = column.getTransformExpression();
        if (StringUtils.isNotEmpty(expression)) {
            return expression;
        } else {
            return "";
        }
    }
}
