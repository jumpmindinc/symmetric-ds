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

/**
 * Represents the change of the primary key of a table.
 * 
 * @version $Revision: $
 */
public class PrimaryKeyChange extends TableChangeImplBase {
    /** The columns making up the original primary key. */
    private Column[] _oldPrimaryKeyColumns;
    /** The columns making up the new primary key. */
    private Column[] _newPrimaryKeyColumns;

    /**
     * Creates a new change object.
     * 
     * @param table
     *            The table whose primary key is to be changed
     * @param oldPrimaryKeyColumns
     *            The columns making up the original primary key
     * @param newPrimaryKeyColumns
     *            The columns making up the new primary key
     */
    public PrimaryKeyChange(Table table, Column[] oldPrimaryKeyColumns, Column[] newPrimaryKeyColumns) {
        super(table);
        _oldPrimaryKeyColumns = oldPrimaryKeyColumns;
        _newPrimaryKeyColumns = newPrimaryKeyColumns;
    }

    /**
     * Returns the columns making up the original primary key.
     *
     * @return The columns
     */
    public Column[] getOldPrimaryKeyColumns() {
        return _oldPrimaryKeyColumns;
    }

    /**
     * Returns the columns making up the new primary key.
     *
     * @return The columns
     */
    public Column[] getNewPrimaryKeyColumns() {
        return _newPrimaryKeyColumns;
    }

    /**
     * {@inheritDoc}
     */
    public void apply(Database database, boolean caseSensitive) {
        Table table = database.findTable(getChangedTable().getName(), caseSensitive);
        for (int idx = 0; idx < _oldPrimaryKeyColumns.length; idx++) {
            Column column = table.findColumn(_oldPrimaryKeyColumns[idx].getName(), caseSensitive);
            if (column != null) {
                column.setPrimaryKey(false);
            }
        }
        for (int idx = 0; idx < _newPrimaryKeyColumns.length; idx++) {
            Column column = table.findColumn(_newPrimaryKeyColumns[idx].getName(), caseSensitive);
            column.setPrimaryKey(true);
        }
    }
}
