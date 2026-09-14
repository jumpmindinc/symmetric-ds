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

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.transform.IColumnTransform;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformTable;

public class TransformDatabaseWriter extends TransformWriter {
    public TransformDatabaseWriter(IDatabasePlatform symmetricPlatform,
            IDatabasePlatform targetPlatform, String tablePrefix,
            DatabaseWriterSettings defaultSettings, Map<String, IColumnTransform<?>> columnTransforms,
            TransformTable[] transforms) {
        super(targetPlatform, TransformPoint.LOAD,
                new DynamicDefaultDatabaseWriter(symmetricPlatform, targetPlatform, tablePrefix, defaultSettings), columnTransforms, transforms);
        getDatabaseWriter().setConflictResolver(new DefaultTransformWriterConflictResolver(this));
    }

    public final DefaultDatabaseWriter getDatabaseWriter() {
        return getNestedWriterOfType(DefaultDatabaseWriter.class);
    }
}
