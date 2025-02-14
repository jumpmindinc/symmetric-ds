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
package org.jumpmind.db.platform;

import java.util.List;

import org.jumpmind.db.alter.IModelChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Table;

public interface IDdlBuilder {
    public String createTables(Database database, boolean dropTables);

    public String getTableName(String tableName);

    public String getIndexName(IIndex index);

    public String getForeignKeyName(Table table, ForeignKey fk);

    public String getConstraintName(String prefix, Table table, String secondPart, String suffix);

    public boolean isAlterDatabase(Database currentModel, Database desiredModel, IAlterDatabaseInterceptor... alterDatabaseInterceptors);

    public String createTable(Table table);

    public String alterDatabase(Database currentModel, Database desiredModel, IAlterDatabaseInterceptor... alterDatabaseInterceptors);

    public String alterTable(Table currentTable, Table desiredTable, IAlterDatabaseInterceptor... alterDatabaseInterceptors);

    public String dropTables(Database database);

    /*
     * Determines whether delimited identifiers are used or normal SQL92 identifiers (which may only contain alpha numerical characters and the underscore, must
     * start with a letter and cannot be a reserved keyword). Per default, delimited identifiers are not used
     * 
     * @return <code>true</code> if delimited identifiers are used
     */
    public boolean isDelimitedIdentifierModeOn();

    /*
     * Specifies whether delimited identifiers are used or normal SQL92 identifiers.
     * 
     * @param delimitedIdentifierModeOn <code>true</code> if delimited identifiers shall be used
     */
    public void setDelimitedIdentifierModeOn(boolean delimitedIdentifierModeOn);

    public void setCaseSensitive(boolean caseSensitive);

    public DatabaseInfo getDatabaseInfo();

    public String getColumnTypeDdl(Table table, Column column);

    public String getSqlType(Column column);

    public String mapDefaultValue(Object defaultValue, Column column);

    public boolean areColumnSizesTheSame(Column sourceColumn, Column targetColumn);

    public boolean areMappedTypesTheSame(Column sourceColumn, Column targetColumn);

    public List<IModelChange> getDetectedChanges(Database currentModel, Database desiredModel, IAlterDatabaseInterceptor... alterDatabaseInterceptors);

    public void initCteExpression();

    public void setTriggerDelimiterReplacementCharacters(String triggerDelimiterReplacementCharacters);

    public String getTriggerDelimiterReplacementCharacters();
}
