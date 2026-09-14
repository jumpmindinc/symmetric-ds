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
package org.jumpmind.vaadin.ui.common;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;

public class GridDataProvider<T> implements IDataProvider<T> {
    private Grid<T> grid;
    private Map<Column<T>, ValueProvider<T, Object>> valueProviderMap;

    public GridDataProvider(Grid<T> grid, Map<Column<T>, ValueProvider<T, Object>> valueProviderMap) {
        this.grid = grid;
        this.valueProviderMap = valueProviderMap;
    }

    @Override
    public Collection<T> getRowItems() {
        return (Collection<T>) grid.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
    }

    @Override
    public List<?> getColumns() {
        return grid.getColumns();
    }

    @Override
    public Object getCellValue(T item, Object column) {
        if (column instanceof Column && valueProviderMap.get(column) != null) {
            return valueProviderMap.get(column).apply(item);
        }
        return null;
    }

    @Override
    public String getKeyValue(Object column) {
        return ((Column<?>) column).getKey();
    }
}