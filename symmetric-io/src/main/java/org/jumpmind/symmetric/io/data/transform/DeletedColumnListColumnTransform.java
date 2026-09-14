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

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;

public class DeletedColumnListColumnTransform implements ISingleNewAndOldValueColumnTransform, IBuiltInExtensionPoint {
    public final static String NAME = "deletedColumns";

    public String getName() {
        return NAME;
    }

    public boolean isExtractColumnTransform() {
        return true;
    }

    public boolean isLoadColumnTransform() {
        return true;
    }

    public NewAndOldValue transform(IDatabasePlatform platform, DataContext context,
            TransformColumn column, TransformedData data, Map<String, String> sourceValues, String newValue, String oldValue)
            throws IgnoreColumnException, IgnoreRowException {
        StringBuilder deleteList = new StringBuilder();
        if (data.getSourceDmlType() == DataEventType.UPDATE) {
            Map<String, String> oldValues = data.getOldSourceValues();
            for (String name : sourceValues.keySet()) {
                if (sourceValues.get(name) == null && oldValues.get(name) != null) {
                    if (deleteList.length() > 0) {
                        deleteList.append(",");
                    }
                    deleteList.append(name.toLowerCase());
                }
            }
        }
        return new NewAndOldValue(deleteList.toString(), null);
    }
}
