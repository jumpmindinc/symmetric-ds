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

import java.sql.Types;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.CsvData;

public class DuckDbDatabaseWriter extends DynamicDefaultDatabaseWriter {
    public DuckDbDatabaseWriter(IDatabasePlatform symmetricPlatform, IDatabasePlatform targetPlatform, String prefix,
            IDatabaseWriterConflictResolver conflictResolver, DatabaseWriterSettings settings) {
        super(symmetricPlatform, targetPlatform, prefix, conflictResolver, settings);
    }

    @Override
    protected boolean doesColumnNeedUpdated(int targetColumnIndex, Column column, CsvData data, String[] rowData, String[] oldData,
            boolean applyChangesOnly) {
        boolean needsUpdated = super.doesColumnNeedUpdated(targetColumnIndex, column, data, rowData, oldData, applyChangesOnly);
        if (!needsUpdated && oldData != null && applyChangesOnly && column.getMappedTypeCode() == Types.VARCHAR
                && StringUtils.isBlank(oldData[targetColumnIndex]) && data.isNoBinaryOldData()) {
            // indicate that we are considering the column to be changed
            updateChangedDataIndicator(data, column, true);
            needsUpdated = true;
        }
        return needsUpdated;
    }
}
