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
package org.jumpmind.symmetric.statistic;

import java.util.List;
import java.util.Set;

import org.jumpmind.symmetric.model.DataGap;

public class RouterStats {
    private long startDataId;
    private long endDataId;
    private long dataReadCount;
    private long peekAheadFillCount;
    private List<DataGap> dataGaps;

    public RouterStats() {
    }

    public RouterStats(long startDataId, long endDataId, long dataReadCount, long peekAheadFillCount,
            List<DataGap> dataGaps, Set<String> transactions) {
        this.startDataId = startDataId;
        this.endDataId = endDataId;
        this.dataReadCount = dataReadCount;
        this.peekAheadFillCount = peekAheadFillCount;
        this.dataGaps = dataGaps;
    }

    @Override
    public String toString() {
        return "{ startDataId: " + startDataId + ", endDataId: " + endDataId + ", dataReadCount: " + dataReadCount +
                ", peekAheadFillCount: " + peekAheadFillCount + ", dataGaps: " + dataGaps + " }";
    }

    public long getStartDataId() {
        return startDataId;
    }

    public void setStartDataId(long startDataId) {
        this.startDataId = startDataId;
    }

    public long getEndDataId() {
        return endDataId;
    }

    public void setEndDataId(long endDataId) {
        this.endDataId = endDataId;
    }

    public long getDataReadCount() {
        return dataReadCount;
    }

    public void setDataReadCount(long dataReadCount) {
        this.dataReadCount = dataReadCount;
    }

    public long getPeekAheadFillCount() {
        return peekAheadFillCount;
    }

    public void setPeekAheadFillCount(long peekAheadFillCount) {
        this.peekAheadFillCount = peekAheadFillCount;
    }

    public List<DataGap> getDataGaps() {
        return dataGaps;
    }

    public void setDataGaps(List<DataGap> dataGaps) {
        this.dataGaps = dataGaps;
    }
}
