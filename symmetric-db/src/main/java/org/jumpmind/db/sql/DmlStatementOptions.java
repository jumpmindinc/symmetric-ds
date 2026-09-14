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
package org.jumpmind.db.sql;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.DmlStatement.DmlType;

public class DmlStatementOptions {
    protected DmlType dmlType;
    protected String tableName;
    protected Column[] columns;
    protected DatabaseInfo databaseInfo = new DatabaseInfo();
    protected String catalogName;
    protected String schemaName;
    protected Column[] keys;
    protected boolean[] nullKeyValues;
    protected boolean useQuotedIdentifiers;
    protected boolean namedParameters;
    protected String textColumnExpression;

    public DmlStatementOptions(DmlType dmlType, String tableName) {
        this.dmlType = dmlType;
        this.tableName = tableName;
    }

    public DmlStatementOptions(DmlType dmlType, Table table) {
        this.dmlType = dmlType;
        this.tableName = table.getName();
        this.catalogName = table.getCatalog();
        this.schemaName = table.getSchema();
        this.columns = table.getColumns();
        this.keys = table.getPrimaryKeyColumns();
    }

    public DmlStatementOptions databaseInfo(DatabaseInfo databaseInfo) {
        this.databaseInfo = databaseInfo;
        return this;
    }

    public DmlStatementOptions catalogName(String catalogName) {
        this.catalogName = catalogName;
        return this;
    }

    public DmlStatementOptions schemaName(String schemaName) {
        this.schemaName = schemaName;
        return this;
    }

    public DmlStatementOptions columns(Column[] columns) {
        this.columns = columns;
        return this;
    }

    public DmlStatementOptions keys(Column[] keys) {
        this.keys = keys;
        return this;
    }

    public DmlStatementOptions nullKeyValues(boolean[] nullKeyValues) {
        this.nullKeyValues = nullKeyValues;
        return this;
    }

    public DmlStatementOptions quotedIdentifiers(boolean useQuotedIdentifiers) {
        this.useQuotedIdentifiers = useQuotedIdentifiers;
        return this;
    }

    public DmlStatementOptions namedParameters(boolean namedParameters) {
        this.namedParameters = namedParameters;
        return this;
    }

    public DmlStatementOptions textColumnExpression(String textColumnExpression) {
        this.textColumnExpression = textColumnExpression;
        return this;
    }

    public DmlStatementOptions dmlType(DmlType dmlType) {
        this.dmlType = dmlType;
        return this;
    }

    public DmlType getDmlType() {
        return dmlType;
    }

    public String getTableName() {
        return tableName;
    }

    public Column[] getColumns() {
        return columns;
    }

    public DatabaseInfo getDatabaseInfo() {
        return databaseInfo;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public Column[] getKeys() {
        return keys;
    }

    public boolean[] getNullKeyValues() {
        return nullKeyValues;
    }

    public boolean useQuotedIdentifiers() {
        return useQuotedIdentifiers;
    }

    public boolean isNamedParameters() {
        return namedParameters;
    }

    public String getTextColumnExpression() {
        return textColumnExpression;
    }
}
