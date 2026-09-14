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
package org.jumpmind.symmetric.io.stage;

import java.util.Objects;

public class PerfResult {
    private String name;
    private long count;
    private long millis;
    private float rating;
    private String outcome;
    public static final String OUTCOME_SUCCESS = "Success";
    public static final String OUTCOME_FAILED = "Failed";
    public static final String OUTCOME_SKIPPED = "Skipped";
    public static final String OUTCOME_TIMED_OUT = "Timed Out";

    public PerfResult(String name, long count, long millis, float rating) {
        this.name = name;
        this.count = count;
        this.millis = millis;
        this.outcome = "";
    }

    public PerfResult(String name) {
        this.name = name;
        this.count = 0;
        this.millis = 0;
        this.outcome = "";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.count, this.millis, this.name, this.outcome);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PerfResult other = (PerfResult) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " { name=" + name + ", count=" + count + ", millis=" + millis + ", ops=" + getOperationsPerSecond() + " }";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCount() {
        return count;
    }

    public void incrementCount(long inc) {
        count += inc;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public long getMillis() {
        return millis;
    }

    public void incrementMillis(long inc) {
        millis += inc;
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }

    public long getOperationsPerSecond() {
        if (millis > 0) {
            return (long) (count / (millis / 1000f));
        }
        return count;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
