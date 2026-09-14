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
package org.jumpmind.symmetric.io.data.reader;

import org.jumpmind.db.model.Relation;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;

public interface IExtractDataReaderSource {
    public Batch getBatch();

    /**
     * Return the relation with the catalog, schema, and relation name of the target relation for the last {@link CsvData} retrieved by {@link #next()}
     */
    public Relation getTargetRelation();

    /**
     * Return the relation with the catalog, schema, and relation name of the source relation for the last {@link CsvData} retrieved by {@link #next()}
     */
    public Relation getSourceRelation();

    public CsvData next();

    public boolean requiresLobsSelectedFromSource(CsvData data);

    public void close();
}
