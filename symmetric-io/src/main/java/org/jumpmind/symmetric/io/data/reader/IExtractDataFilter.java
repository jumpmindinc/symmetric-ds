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
package org.jumpmind.symmetric.io.data.reader;

import org.jumpmind.db.model.Table;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;

/**
 * @deprecated Use {@link org.jumpmind.symmetric.io.data.reader.IRelationExtractDataFilter} instead, which accepts {@link org.jumpmind.db.model.Relation} to
 *             support both tables and views. Enable legacy behavior via the {@code extension.use.legacy.interface.enabled} parameter.
 */
@Deprecated(forRemoval = true)
public interface IExtractDataFilter extends IExtensionPoint {
    /**
     * Called during extraction of sym_data or source table
     *
     * @return true if the row should be extracted
     */
    public boolean filterData(DataContext context, Batch batch, Table table, CsvData data);
}
