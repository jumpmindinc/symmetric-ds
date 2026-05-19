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
package org.jumpmind.symmetric.observability.interfaces;

import java.util.List;

/** A single named attribute (label) attached to a metric context. */
public record MetricAttribute(String name, String value) {
    public static String[] packNamesIntoArray(List<MetricAttribute> attrs, int maxSize, String defaultValue) {
        String[] names = new String[maxSize];
        int size = attrs != null ? Math.min(attrs.size(), maxSize) : 0;
        for (int i = 0; i < maxSize; i++) {
            names[i] = (i < size && attrs.get(i).name() != null) ? attrs.get(i).name() : defaultValue;
        }
        return names;
    }

    public static String[] packValuesIntoArray(List<MetricAttribute> attrs, int maxSize, String defaultValue) {
        String[] values = new String[maxSize];
        int size = attrs != null ? Math.min(attrs.size(), maxSize) : 0;
        for (int i = 0; i < maxSize; i++) {
            values[i] = (i < size && attrs.get(i).value() != null) ? attrs.get(i).value() : defaultValue;
        }
        return values;
    }
}
