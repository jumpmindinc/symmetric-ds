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
package org.jumpmind.symmetric.route;

/**
 * Counters for one pass of gap detection.
 */
public class DataGapPassStats {
    private int idsFound;
    private long rangeChecked;
    private int gapsAdded;
    private int gapsDeleted;
    private int gapsExpireChecked;

    public void addIdsFound(int count) {
        idsFound += count;
    }

    public void addRangeChecked(long size) {
        rangeChecked += size;
    }

    public void incrementGapsAdded() {
        gapsAdded++;
    }

    public void incrementGapsDeleted() {
        gapsDeleted++;
    }

    public void incrementGapsExpireChecked() {
        gapsExpireChecked++;
    }

    public int getIdsFound() {
        return idsFound;
    }

    public long getRangeChecked() {
        return rangeChecked;
    }

    public int getGapsAdded() {
        return gapsAdded;
    }

    public int getGapsDeleted() {
        return gapsDeleted;
    }

    public int getGapsExpireChecked() {
        return gapsExpireChecked;
    }
}
