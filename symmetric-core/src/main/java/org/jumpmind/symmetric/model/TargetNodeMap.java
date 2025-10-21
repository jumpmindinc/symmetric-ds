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
package org.jumpmind.symmetric.model;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class TargetNodeMap {
    private Map<String, Set<String>> map = new TreeMap<String, Set<String>>();

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(Object channelId) {
        return map.containsKey(channelId);
    }

    public boolean containsValue(Object targetNodeIdSet) {
        return map.containsValue(targetNodeIdSet);
    }

    public Set<String> get(Object channelId) {
        return map.get(channelId);
    }

    public Set<String> put(String channelId, Set<String> targetNodeIdSet) {
        return map.put(channelId, targetNodeIdSet);
    }

    public Set<String> remove(Object channelId) {
        return map.remove(channelId);
    }

    public void putAll(TargetNodeMap targetNodeMap) {
        map.putAll(targetNodeMap.map);
    }

    public void clear() {
        map.clear();
    }

    public Set<String> keySet() {
        return map.keySet();
    }

    public Collection<Set<String>> values() {
        return map.values();
    }

    public Set<Entry<String, Set<String>>> entrySet() {
        return map.entrySet();
    }
}
