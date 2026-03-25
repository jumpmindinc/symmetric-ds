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
package org.jumpmind.symmetric.observability.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;

/**
 * Wraps a {@link LongCounter} with a fixed set of engine attributes. Only positive deltas are accepted; negative values are rejected with an
 * {@link IllegalArgumentException}.
 */
public class IncreasingCounter extends AbstractCounter {
    private final LongCounter counter;

    IncreasingCounter(String metricId, LongCounter counter, Attributes attributes) {
        super(metricId, attributes);
        this.counter = counter;
    }

    public void add(long delta) {
        if (delta == 0) {
            return;
        }
        if (delta < 0) {
            throw new IllegalArgumentException("IncreasingCounter does not accept negative deltas: " + delta);
        }
        value.addAndGet(delta);
        lastModified = System.currentTimeMillis();
        if (counter != null) {
            counter.add(delta, attributes);
        }
    }

    public void increment() {
        add(1);
    }

    public void increment(long delta) {
        add(delta);
    }
}
