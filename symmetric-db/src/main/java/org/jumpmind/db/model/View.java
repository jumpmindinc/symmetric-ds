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
        View copy = copy();
        copy.orderColumns(orderedColumnNames, addMissingColumns);
        if (setPrimaryKeys && copy.columns != null) {
            clearPrimaryKeys(copy);
            if (pkColumnNames != null) {
                applyPrimaryKeys(copy, pkColumnNames);
            }
        }
        return copy;
    }

    private static void clearPrimaryKeys(View view) {
        for (Column column : view.columns) {
            if (column != null) {
                column.setPrimaryKey(false);
            }
        }
    }

    private static void applyPrimaryKeys(View view, String[] pkColumnNames) {
        for (Column column : view.columns) {
            if (column == null) {
                continue;
            }
            for (String pkColumnName : pkColumnNames) {
                if (column.getName().equalsIgnoreCase(pkColumnName)) {
                    boolean required = column.isRequired();
                    column.setPrimaryKey(true);
                    column.setRequired(required);
                }
            }
        }
    }

    @Override
    public View copy() {
        View result = new View(catalog, schema, name);
        result.description = description;
        result.type = type;
        result.madeAllColumnsPrimaryKey = madeAllColumnsPrimaryKey;
        for (Column column : columns) {
            if (column != null) {
                try {
                    result.columns.add((Column) column.clone());
                } catch (CloneNotSupportedException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
        return result;
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
