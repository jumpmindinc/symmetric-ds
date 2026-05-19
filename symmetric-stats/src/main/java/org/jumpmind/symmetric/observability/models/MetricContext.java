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
package org.jumpmind.symmetric.observability.models;

import java.util.List;
import java.util.Objects;

import org.jumpmind.symmetric.observability.interfaces.ISymMetricContext;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;

/**
 * Immutable identity record for one metric series. The {@code contextId} is a surrogate key in the format {@code NNNNNNNYYYY} (sequential part × 10000 +
 * current year) assigned by the repository on first persistence. Holds up to three {@link MetricAttribute} pairs.
 */
public record MetricContext(long contextId, MetricAttributeList attributes) implements ISymMetricContext {
    public static final long UNDEFINED = -1L;
    public static final String NA = "N/A";
    /** Upper bound (inclusive) of the pre-assigned seed context ID range. IDs at or below this value are never evicted from cache. */
    public static final long SEED_IDS_END = 20_000_000_000L;

    @Override
    public long getContextId() {
        return contextId;
    }

    @Override
    public MetricAttributeList getAttributes() {
        return attributes;
    }

    /**
     * Computes the {@code attributes_hash} column value: {@link Objects#hash} across all six attribute fields, with {@code null} substituted by {@link #NA}.
     * Attributes beyond position 2 (0-indexed) are treated as absent.
     */
    public static int computeHash(List<MetricAttribute> attrs) {
        String a1n = NA;
        String a1v = NA;
        String a2n = NA;
        String a2v = NA;
        String a3n = NA;
        String a3v = NA;
        if (attrs != null) {
            if (!attrs.isEmpty()) {
                MetricAttribute a = attrs.get(0);
                a1n = orNA(a.name());
                a1v = orNA(a.value());
            }
            if (attrs.size() > 1) {
                MetricAttribute a = attrs.get(1);
                a2n = orNA(a.name());
                a2v = orNA(a.value());
            }
            if (attrs.size() > 2) {
                MetricAttribute a = attrs.get(2);
                a3n = orNA(a.name());
                a3v = orNA(a.value());
            }
        }
        return Objects.hash(a1n, a1v, a2n, a2v, a3n, a3v);
    }

    private static String orNA(String s) {
        return s != null ? s : NA;
    }
}
