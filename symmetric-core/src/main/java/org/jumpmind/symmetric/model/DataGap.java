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
package org.jumpmind.symmetric.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class DataGap implements Serializable, Comparable<DataGap> {
    private static final long serialVersionUID = 1L;
    private long startId;
    private long endId;
    private Date createTime;

    public DataGap(long startId, long endId) {
        this.startId = startId;
        this.endId = endId;
        this.createTime = new Date();
    }

    public DataGap(long startId, long endId, Date createTime) {
        this.startId = startId;
        this.endId = endId;
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "{ startId: " + startId + ", endId: " + endId + ", createTime: \"" + createTime + "\" }";
    }

    public long getEndId() {
        return endId;
    }

    public long getStartId() {
        return startId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public boolean contains(DataGap gap) {
        return startId <= gap.startId && endId >= gap.endId;
    }

    public boolean containsAny(List<Long> dataIds) {
        for (Long dataId : dataIds) {
            if (dataId != null && startId >= dataId && endId <= dataId) {
                return true;
            }
        }
        return false;
    }

    public boolean overlaps(DataGap gap) {
        return (startId >= gap.startId && startId <= gap.endId) || (endId >= gap.startId && endId <= gap.endId) ||
                (startId <= gap.startId && endId >= gap.endId);
    }

    public long gapSize() {
        return endId - startId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (endId ^ (endId >>> 32));
        result = prime * result + (int) (startId ^ (startId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DataGap other = (DataGap) obj;
        if (endId != other.endId)
            return false;
        if (startId != other.startId)
            return false;
        return true;
    }

    @Override
    public int compareTo(DataGap gap) {
        if (startId < gap.getStartId() || (startId == gap.getStartId() && endId < gap.getEndId())) {
            return -1;
        } else if (startId > gap.getStartId() || (startId == gap.getStartId() && endId > gap.getEndId())) {
            return 1;
        }
        return 0;
    }
}