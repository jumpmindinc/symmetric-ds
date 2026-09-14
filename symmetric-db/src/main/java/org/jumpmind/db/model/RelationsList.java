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
package org.jumpmind.db.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.util.TableRow;

public class RelationsList extends ArrayList<Relation> {
    private static final long serialVersionUID = 1L;

    public RelationsList() {
        super();
    }

    public RelationsList(int initialCapacity) {
        super(initialCapacity);
    }

    public RelationsList(Collection<? extends Relation> collection) {
        super(collection);
    }

    public static RelationsList of(Relation... relations) {
        RelationsList list = new RelationsList(relations.length);
        for (Relation relation : relations) {
            list.add(relation);
        }
        return list;
    }

    public static RelationsList of(List<TableRow> tableRows) {
        RelationsList list = new RelationsList(tableRows.size());
        for (TableRow tableRow : tableRows) {
            list.add(tableRow.getTable());
        }
        return list;
    }

    public Map<Relation, Integer> getPositionMap() {
        Map<Relation, Integer> indexMap = new HashMap<>();
        int index = 0;
        for (Relation relation : this) {
            indexMap.put(relation, index++);
        }
        return indexMap;
    }
}
