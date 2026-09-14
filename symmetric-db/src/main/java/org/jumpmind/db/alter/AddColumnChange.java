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
package org.jumpmind.db.alter;

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

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DdlException;

/**
 * Represents the addition of a column to a table.
 */
public class AddColumnChange extends TableChangeImplBase {
    /** The new column. */
    private Column _newColumn;
    /** The column after which the new column should be added. */
    private Column _previousColumn;
    /** The column before which the new column should be added. */
    private Column _nextColumn;
    /** Whether the column is added at the end. */
    private boolean _atEnd;

    /**
     * Creates a new change object.
     * 
     * @param table
     *            The table to add the column to
     * @param newColumn
     *            The new column
     * @param previousColumn
     *            The column after which the new column should be added
     * @param nextColumn
     *            The column before which the new column should be added
     */
    public AddColumnChange(Table table, Column newColumn, Column previousColumn, Column nextColumn) {
        super(table);
        _newColumn = newColumn;
        _previousColumn = previousColumn;
        _nextColumn = nextColumn;
    }

    /**
     * Returns the new column.
     *
     * @return The new column
     */
    public Column getNewColumn() {
        return _newColumn;
    }

    /**
     * Returns the column after which the new column should be added.
     *
     * @return The previous column
     */
    public Column getPreviousColumn() {
        return _previousColumn;
    }

    /**
     * Returns the column before which the new column should be added.
     *
     * @return The next column
     */
    public Column getNextColumn() {
        return _nextColumn;
    }

    /**
     * Determines whether the column is added at the end (when applied in the order of creation of the changes).
     * 
     * @return <code>true</code> if the column is added at the end
     */
    public boolean isAtEnd() {
        return _atEnd;
    }

    /**
     * Specifies whether the column is added at the end (when applied in the order of creation of the changes).
     * 
     * @param atEnd
     *            <code>true</code> if the column is added at the end
     */
    public void setAtEnd(boolean atEnd) {
        _atEnd = atEnd;
    }

    /**
     * {@inheritDoc}
     */
    public void apply(Database database, boolean caseSensitive) {
        Column newColumn = null;
        try {
            newColumn = (Column) _newColumn.clone();
        } catch (CloneNotSupportedException ex) {
            throw new DdlException(ex);
        }
        Table table = database.findTable(getChangedTable().getName(), caseSensitive);
        if ((_previousColumn != null) && (_nextColumn != null)) {
            int idx = table.getColumnIndex(_previousColumn) + 1;
            table.addColumn(idx, newColumn);
        } else {
            table.addColumn(newColumn);
        }
    }
}
