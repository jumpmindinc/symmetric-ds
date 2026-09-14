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
package org.jumpmind.symmetric.observability.interfaces;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** An ordered list of {@link MetricAttribute} entries with packing helpers for array-based storage. */
public class MetricAttributeList extends ArrayList<MetricAttribute> {
    private static final long serialVersionUID = 1L;
    static final String ATTR_NAME_NULL = "null";

    public MetricAttributeList(int capacity) {
        super(capacity);
    }

    public MetricAttributeList(Collection<MetricAttribute> attrs) {
        super(attrs != null ? attrs : List.of());
    }

    public String concatenateNames(String separator, int maxSize) {
        StringBuilder names = new StringBuilder();
        int limit = Math.min(size(), maxSize);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                names.append(separator);
            }
            if (get(i).name() != null) {
                names.append(get(i).name());
            } else {
                names.append(ATTR_NAME_NULL);
            }
        }
        return names.toString();
    }

    public String[] packNamesIntoArray(int maxSize) {
        String[] names = new String[maxSize];
        int limit = Math.min(size(), maxSize);
        for (int i = 0; i < limit; i++) {
            names[i] = get(i).name();
        }
        return names;
    }

    public String[] packValuesIntoArray(int maxSize, String defaultValue) {
        String[] values = new String[maxSize];
        int limit = Math.min(size(), maxSize);
        for (int i = 0; i < limit; i++) {
            values[i] = get(i).value() != null ? get(i).value() : defaultValue;
        }
        for (int i = limit; i < maxSize; i++) {
            values[i] = defaultValue;
        }
        return values;
    }

    /** It is recommended to use some non-empty value as a default when hashing */
    public int generateHashOfValues(int maxSize, String defaultValue) {
        String[] values = packValuesIntoArray(maxSize, defaultValue);
        return Arrays.hashCode(values);
    }

    public static MetricAttributeList of(MetricAttribute... attrs) {
        return attrs != null ? new MetricAttributeList(Arrays.asList(attrs)) : new MetricAttributeList(0);
    }

    /**
     * Returns arry [n1, v1, n2, v2, ...] interleaved up to maxSize attribute name=value pairs; name is {@code null} and value is {@code defaultValue} for
     * unused slots.
     */
    public String[] packNamesAndValuesIntoArray(int maxSize, String defaultValue) {
        String[] av = new String[maxSize * 2];
        int limit = Math.min(size(), maxSize);
        for (int i = 0; i < limit; i++) {
            av[i * 2] = get(i).name();
            av[i * 2 + 1] = get(i).value() != null ? get(i).value() : defaultValue;
        }
        for (int i = limit; i < maxSize; i++) {
            av[i * 2 + 1] = defaultValue;
        }
        return av;
    }
}
