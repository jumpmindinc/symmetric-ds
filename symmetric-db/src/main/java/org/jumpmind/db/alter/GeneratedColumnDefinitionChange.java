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
 * Represents the change of the definition of a generated column.
 * 
 * @version $Revision: $
 */
public class GeneratedColumnDefinitionChange extends TableChangeImplBase implements ColumnChange {
    /** The column. */
    private Column _column;
    /** The new definition. */
    private String _newDefinition;

    /**
     * Creates a new change object.
     * 
     * @param table
     *            The table of the column
     * @param column
     *            The column
     * @param newDefinition
     *            The new definition
     */
    public GeneratedColumnDefinitionChange(Table table, Column column, String newDefinition) {
        super(table);
        _column = column;
        _newDefinition = newDefinition;
    }

    /**
     * Returns the column.
     *
     * @return The column
     */
    public Column getChangedColumn() {
        return _column;
    }

    /**
     * Returns the new definition.
     *
     * @return The new definition
     */
    public String getNewDefinition() {
        return _newDefinition;
    }

    /**
     * {@inheritDoc}
     */
    public void apply(Database database, boolean caseSensitive) {
        Table table = database.findTable(getChangedTable().getName(), caseSensitive);
        Column column = table.findColumn(_column.getName(), caseSensitive);
        column.setDefaultValue(_newDefinition);
    }
}
