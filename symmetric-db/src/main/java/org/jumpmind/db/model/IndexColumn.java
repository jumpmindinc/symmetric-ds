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

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Represents a column of an index in the database model.
 */
public class IndexColumn implements Cloneable, Serializable {
    /** Unique ID for serialization purposes. */
    private static final long serialVersionUID = 1L;
    /** The position within the owning index. */
    private int ordinalPosition;
    /** The indexed column. */
    private Column column;
    /** The name of the column. */
    protected String name;
    /** The size of the column in the index. */
    protected String size;
    protected boolean primaryKey;

    /**
     * Creates a new index column object.
     */
    public IndexColumn() {
    }

    /**
     * Creates a new index column object.
     * 
     * @param column
     *            The indexed column
     */
    public IndexColumn(Column column) {
        this.column = column;
        this.name = column.getName();
        this.primaryKey = column.isPrimaryKey();
    }

    /**
     * Creates a new index column object.
     * 
     * @param columnName
     *            The name of the corresponding table column
     */
    public IndexColumn(String columnName) {
        name = columnName;
    }

    /**
     * Returns the position within the owning index.
     * 
     * @return The position
     */
    public int getOrdinalPosition() {
        return ordinalPosition;
    }

    /**
     * Sets the position within the owning index. Please note that you should not change the value once the column has been added to a index.
     * 
     * @param position
     *            The position
     */
    public void setOrdinalPosition(int position) {
        this.ordinalPosition = position;
    }

    /**
     * Returns the name of the column.
     * 
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the column.
     * 
     * @param name
     *            The name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the indexed column.
     * 
     * @return The column
     */
    public Column getColumn() {
        return column;
    }

    /**
     * Sets the indexed column.
     * 
     * @param column
     *            The column
     */
    public void setColumn(Column column) {
        this.column = column;
        this.name = (column == null ? name : column.getName());
        this.primaryKey = (column == null ? primaryKey : column.isPrimaryKey());
    }

    /**
     * Returns the size of the column in the index.
     * 
     * @return The size
     */
    public String getSize() {
        return size;
    }

    /**
     * Sets the size of the column in the index.
     * 
     * @param size
     *            The size
     */
    public void setSize(String size) {
        this.size = size;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public Object clone() throws CloneNotSupportedException {
        IndexColumn result = (IndexColumn) super.clone();
        result.name = name;
        result.size = size;
        return result;
    }

    public boolean equals(Object obj) {
        if (obj instanceof IndexColumn) {
            IndexColumn other = (IndexColumn) obj;
            return new EqualsBuilder().append(name, other.name).append(size, other.size)
                    .isEquals();
        } else {
            return false;
        }
    }

    /**
     * Compares this index column to the given one while ignoring the case of identifiers.
     * 
     * @param other
     *            The other index column
     * @return <code>true</code> if this index column is equal (ignoring case) to the given one
     */
    public boolean equalsIgnoreCase(IndexColumn other) {
        return new EqualsBuilder().append(name.toUpperCase(), other.name.toUpperCase())
                .append(size, other.size).isEquals();
    }

    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(name).append(size).toHashCode();
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Index column [name=");
        result.append(getName());
        result.append("; size=");
        result.append(getSize());
        result.append("]");
        return result.toString();
    }
}
