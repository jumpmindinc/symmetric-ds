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
package org.jumpmind.db.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a database view. A view has a name, catalog, schema, and columns but no indices or foreign keys.
 */
public class View extends Relation {
    private static final long serialVersionUID = 1L;

    public View() {
    }

    public View(String name) {
        this(null, null, name);
    }

    public View(String catalog, String schema, String name) {
        super(catalog, schema, name);
    }

    @Override
    public View copyAndFilterColumns(String[] orderedColumnNames, String[] pkColumnNames,
            boolean setPrimaryKeys, boolean addMissingColumns) {
        try {
            View copy = (View) this.clone();
            copy.orderColumns(orderedColumnNames, addMissingColumns);
            Set<String> columnNameSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            columnNameSet.addAll(Arrays.asList(orderedColumnNames));
            if (setPrimaryKeys && copy.columns != null) {
                for (Column column : copy.columns) {
                    if (column != null) {
                        column.setPrimaryKey(false);
                    }
                }
                if (pkColumnNames != null) {
                    for (Column column : copy.columns) {
                        if (column != null) {
                            for (String pkColumnName : pkColumnNames) {
                                if (column.getName().equalsIgnoreCase(pkColumnName)) {
                                    boolean required = column.isRequired();
                                    column.setPrimaryKey(true);
                                    column.setRequired(required);
                                }
                            }
                        }
                    }
                }
            }
            return copy;
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        View result = (View) super.clone();
        result.columns = new ArrayList<>(columns.size());
        for (Column column : columns) {
            if (column != null) {
                result.columns.add((Column) column.clone());
            }
        }
        return result;
    }

    @Override
    public View copy() {
        try {
            return (View) this.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof View other) {
            return getFullyQualifiedName().equals(other.getFullyQualifiedName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getFullyQualifiedName().hashCode();
    }

    @Override
    public String toString() {
        return "View [name=" + getName() + "; " + getColumnCount() + " columns]";
    }
}
