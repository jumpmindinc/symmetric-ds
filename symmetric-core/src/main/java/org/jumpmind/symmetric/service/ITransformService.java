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
package org.jumpmind.symmetric.service;

import java.util.List;
import java.util.Map;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.io.data.transform.IColumnTransform;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;

public interface ITransformService {
    public boolean refreshFromDatabase();

    public List<TransformTableNodeGroupLink> findTransformsFor(NodeGroupLink link,
            TransformPoint transformPoint);

    public List<TransformTableNodeGroupLink> findTransformsFor(String sourceNodeGroupId, String targetNodeGroupId, String table);

    public List<TransformTableNodeGroupLink> getTransformTables(boolean includeColumns);

    public List<TransformTableNodeGroupLink> getTransformTables(boolean includeColumns, boolean replaceTokens);

    public List<TransformColumn> getTransformColumns();

    public List<TransformColumn> getTransformColumnsForTable(String transformId);

    public void deleteAllTransformColumns();

    public void saveTransformTable(TransformTableNodeGroupLink transformTable, boolean saveTransformColumns);

    public void saveTransformTable(ISqlTransaction transaction, TransformTableNodeGroupLink transformTable, boolean saveTransformColumns);

    public void saveTransformTableAsCopy(String originalId, TransformTableNodeGroupLink transformTable);

    public void renameTransformTable(String oldId, TransformTableNodeGroupLink transformTable);

    public void deleteTransformTable(String transformTableId);

    public void deleteTransformTable(ISqlTransaction transaction, String transformTableId);

    public void deleteAllTransformTables();

    public Map<String, IColumnTransform<?>> getColumnTransforms();

    public void clearCache();

    public List<TransformTableNodeGroupLink> getConfigExtractTransforms(NodeGroupLink nodeGroupLink);

    public List<TransformTableNodeGroupLink> getConfigLoadTransforms(NodeGroupLink nodeGroupLink);

    public Map<NodeGroupLink, Map<TransformPoint, List<TransformTableNodeGroupLink>>> readInCacheIfExpiredFromDb();
}
