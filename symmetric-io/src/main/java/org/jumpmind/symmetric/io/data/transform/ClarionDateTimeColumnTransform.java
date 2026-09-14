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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.DataContext;

public class ClarionDateTimeColumnTransform implements ISingleNewAndOldValueColumnTransform, IBuiltInExtensionPoint {
    public final static String NAME = "clarionDateTime";
    public final static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.S";

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
        String clarionTimeStr = null;
        String columnName = column.getTransformExpression();
        if (columnName != null && !StringUtils.isEmpty(columnName)) {
            clarionTimeStr = sourceValues.get(columnName);
        }
        String value = convertClarionDate(newValue, clarionTimeStr);
        return new NewAndOldValue(column, data, value);
    }

    public String convertClarionDate(String clarionDate, String clarionTime) {
        if (clarionDate != null && !StringUtils.isEmpty(clarionDate)) {
            Integer date = Integer.parseInt(clarionDate);
            Calendar cal = Calendar.getInstance();
            cal.set(1800, Calendar.DECEMBER, 28, 0, 0, 0);
            cal.add(Calendar.DATE, date);
            cal.set(Calendar.MILLISECOND, 0);
            if (clarionTime != null && !StringUtils.isEmpty(clarionTime)) {
                Integer time = Integer.parseInt(clarionTime);
                if (time > 0) {
                    cal.add(Calendar.MILLISECOND, (time - 1) * 10);
                }
            }
            return new SimpleDateFormat(DATE_FORMAT).format(cal.getTime());
        }
        return null;
    }
}