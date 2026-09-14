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
package org.jumpmind.symmetric.ext;

import org.jumpmind.db.model.Table;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterFilterAdapter;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;
import org.junit.Ignore;

@Ignore
public class TestDataWriterFilter extends DatabaseWriterFilterAdapter implements IDatabaseWriterFilter, IBuiltInExtensionPoint {
    private int numberOfTimesCalled = 0;
    private static int numberOfTimesCreated;

    public TestDataWriterFilter() {
        numberOfTimesCreated++;
    }

    @Override
    public boolean beforeWrite(
            DataContext context, Table table, CsvData data) {
        numberOfTimesCalled++;
        return true;
    }

    public static int getNumberOfTimesCreated() {
        return numberOfTimesCreated;
    }

    public int getNumberOfTimesCalled() {
        return numberOfTimesCalled;
    }
}