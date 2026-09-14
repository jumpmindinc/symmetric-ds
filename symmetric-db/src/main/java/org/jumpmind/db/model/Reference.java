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
 * Represents a reference between a column in the local table and a column in another table.
 * 
 * @version $Revision: 463305 $
 */
public class Reference implements Cloneable, Serializable {
    /** Unique ID for serialization purposes. */
    private static final long serialVersionUID = 1L;
    /** The sequence value within the key. */
    private int _sequenceValue;
    /** The local column. */
    private Column _localColumn;
    /** The foreign column. */
    private Column _foreignColumn;
    /** The name of the local column. */
    private String _localColumnName;
    /** The name of the foreign column. */
    private String _foreignColumnName;

    /**
     * Creates a new, empty reference.
     */
    public Reference() {
    }

    /**
     * Creates a new reference between the two given columns.
     * 
     * @param localColumn
     *            The local column
     * @param foreignColumn
     *            The remote column
     */
    public Reference(Column localColumn, Column foreignColumn) {
        setLocalColumn(localColumn);
        setForeignColumn(foreignColumn);
    }

    /**
     * Returns the sequence value within the owning key.
     *
     * @return The sequence value
     */
    public int getSequenceValue() {
        return _sequenceValue;
    }

    /**
     * Sets the sequence value within the owning key. Please note that you should not change the value once the reference has been added to a key.
     *
     * @param sequenceValue
     *            The sequence value
     */
    public void setSequenceValue(int sequenceValue) {
        _sequenceValue = sequenceValue;
    }

    /**
     * Returns the local column.
     *
     * @return The local column
     */
    public Column getLocalColumn() {
        return _localColumn;
    }

    /**
     * Sets the local column.
     *
     * @param localColumn
     *            The local column
     */
    public final void setLocalColumn(Column localColumn) {
        _localColumn = localColumn;
        _localColumnName = (localColumn == null ? null : localColumn.getName());
    }

    /**
     * Returns the foreign column.
     *
     * @return The foreign column
     */
    public Column getForeignColumn() {
        return _foreignColumn;
    }

    /**
     * Sets the foreign column.
     *
     * @param foreignColumn
     *            The foreign column
     */
    public final void setForeignColumn(Column foreignColumn) {
        _foreignColumn = foreignColumn;
        _foreignColumnName = (foreignColumn == null ? null : foreignColumn.getName());
    }

    /**
     * Returns the name of the local column.
     * 
     * @return The column name
     */
    public String getLocalColumnName() {
        return _localColumnName;
    }

    /**
     * Sets the name of the local column. Note that you should not use this method when manipulating the model manually. Rather use the
     * {@link #setLocalColumn(Column)} method.
     * 
     * @param localColumnName
     *            The column name
     */
    public void setLocalColumnName(String localColumnName) {
        if ((_localColumn != null) && !_localColumn.getName().equals(localColumnName)) {
            _localColumn = null;
        }
        _localColumnName = localColumnName;
    }

    /**
     * Returns the name of the foreign column.
     * 
     * @return The column name
     */
    public String getForeignColumnName() {
        return _foreignColumnName;
    }

    /**
     * Sets the name of the remote column. Note that you should not use this method when manipulating the model manually. Rather use the
     * {@link #setForeignColumn(Column)} method.
     * 
     * @param foreignColumnName
     *            The column name
     */
    public void setForeignColumnName(String foreignColumnName) {
        if ((_foreignColumn != null) && !_foreignColumn.getName().equals(foreignColumnName)) {
            _foreignColumn = null;
        }
        _foreignColumnName = foreignColumnName;
    }

    /**
     * {@inheritDoc}
     */
    public Object clone() throws CloneNotSupportedException {
        Reference result = (Reference) super.clone();
        result._localColumnName = _localColumnName;
        result._foreignColumnName = _foreignColumnName;
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (obj instanceof Reference) {
            Reference other = (Reference) obj;
            return new EqualsBuilder().append(_localColumnName, other._localColumnName)
                    .append(_foreignColumnName, other._foreignColumnName)
                    .isEquals();
        } else {
            return false;
        }
    }

    /**
     * Compares this reference to the given one while ignoring the case of identifiers.
     * 
     * @param otherRef
     *            The other reference
     * @return <code>true</code> if this reference is equal (ignoring case) to the given one
     */
    public boolean equalsIgnoreCase(Reference otherRef) {
        return (otherRef != null) &&
                _localColumnName.equalsIgnoreCase(otherRef._localColumnName) &&
                _foreignColumnName.equalsIgnoreCase(otherRef._foreignColumnName);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(_localColumnName)
                .append(_foreignColumnName)
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(getLocalColumnName());
        result.append(" -> ");
        result.append(getForeignColumnName());
        return result.toString();
    }
}
