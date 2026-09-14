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
package org.jumpmind.db.platform;

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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wrapper class for database meta data that stores additional info.
 * 
 * @version $Revision: 329426 $
 */
public class DatabaseMetaDataWrapper {
    /** The database meta data. */
    private DatabaseMetaData _metaData;
    /** The catalog to access in the database. */
    private String _catalog;
    /** The schema(s) to access in the database. */
    private String _schemaPattern;
    /** The relation types to process. */
    private String[] relationTypes;

    /**
     * Returns the database meta data.
     * 
     * @return The meta data
     */
    public DatabaseMetaData getMetaData() {
        return _metaData;
    }

    /**
     * Sets the database meta data.
     * 
     * @param metaData
     *            The meta data
     */
    public void setMetaData(DatabaseMetaData metaData) {
        _metaData = metaData;
    }

    /**
     * Returns the catalog in the database to read.
     * 
     * @return The catalog
     */
    public String getCatalog() {
        return _catalog;
    }

    /**
     * Sets the catalog in the database to read.
     * 
     * @param catalog
     *            The catalog
     */
    public void setCatalog(String catalog) {
        _catalog = catalog;
    }

    /**
     * Returns the schema in the database to read.
     * 
     * @return The schema
     */
    public String getSchemaPattern() {
        return _schemaPattern;
    }

    /**
     * Sets the schema in the database to read.
     * 
     * @param schema
     *            The schema
     */
    public void setSchemaPattern(String schema) {
        _schemaPattern = schema;
    }

    /**
     * Returns the relation types to recognize.
     * 
     * @return The relation types
     */
    public String[] getRelationTypes() {
        return relationTypes;
    }

    /**
     * Sets the relation types to recognize.
     * 
     * @param types
     *            The relation types
     */
    public void setRelationTypes(String[] types) {
        relationTypes = types;
    }

    /**
     * Convenience method to return the table meta data using the configured catalog, schema pattern and relation types.
     * 
     * @param tableNamePattern
     *            The pattern identifying for which tables to return info
     * @return The table meta data
     * @throws SQLException
     *             If an error occurred retrieving the meta data
     * @see DatabaseMetaData#getTables(java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
     */
    public ResultSet getTables(String tableNamePattern) throws SQLException {
        return getMetaData().getTables(getCatalog(), getSchemaPattern(), tableNamePattern,
                getRelationTypes());
    }

    /**
     * Convenience method to return the column meta data using the configured catalog and schema pattern.
     * 
     * @param tableNamePattern
     *            The pattern identifying for which tables to return info
     * @param columnNamePattern
     *            The pattern identifying for which columns to return info
     * @return The column meta data
     * @throws SQLException
     *             If an error occurred retrieving the meta data
     * @see DatabaseMetaData#getColumns(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public ResultSet getColumns(String tableNamePattern, String columnNamePattern)
            throws SQLException {
        return getMetaData().getColumns(getCatalog(), getSchemaPattern(), tableNamePattern,
                columnNamePattern);
    }

    /**
     * Convenience method to return the primary key meta data using the configured catalog and schema pattern.
     * 
     * @param tableNamePattern
     *            The pattern identifying for which tables to return info
     * @return The primary key meta data
     * @throws SQLException
     *             If an error occurred retrieving the meta data
     * @see DatabaseMetaData#getPrimaryKeys(java.lang.String, java.lang.String, java.lang.String)
     */
    public ResultSet getPrimaryKeys(String tableNamePattern) throws SQLException {
        return getMetaData().getPrimaryKeys(getCatalog(), getSchemaPattern(), tableNamePattern);
    }

    /**
     * Convenience method to return the foreign key meta data using the configured catalog and schema pattern.
     * 
     * @param tableNamePattern
     *            The pattern identifying for which tables to return info
     * @return The foreign key meta data
     * @throws SQLException
     *             If an error occurred retrieving the meta data
     * @see DatabaseMetaData#getImportedKeys(java.lang.String, java.lang.String, java.lang.String)
     */
    public ResultSet getForeignKeys(String tableNamePattern) throws SQLException {
        return getMetaData().getImportedKeys(getCatalog(), getSchemaPattern(), tableNamePattern);
    }

    /**
     * Convenience method to return the foreign keys that reference this table using the configured catalog and schema pattern.
     * 
     * @param tableNamePattern
     *            The pattern identifying for which tables to return info
     * @return The foreign key meta data
     * @throws SQLException
     *             If an error occurred retrieving the meta data
     * @see DatabaseMetaData#getImportedKeys(java.lang.String, java.lang.String, java.lang.String)
     */
    public ResultSet getExportedKeys(String tableNamePattern) throws SQLException {
        return getMetaData().getExportedKeys(getCatalog(), getSchemaPattern(), tableNamePattern);
    }

    /**
     * Convenience method to return the index meta data using the configured catalog and schema pattern.
     * 
     * @param tableNamePattern
     *            The pattern identifying for which tables to return info
     * @param unique
     *            Whether to return only indices for unique values
     * @param approximate
     *            Whether the result is allowed to reflect approximate or out of data values
     * @return The index meta data
     * @throws SQLException
     *             If an error occurred retrieving the meta data
     * @see DatabaseMetaData#getIndexInfo(java.lang.String, java.lang.String, java.lang.String, boolean, boolean)
     */
    public ResultSet getIndices(String tableNamePattern, boolean unique, boolean approximate)
            throws SQLException {
        return getMetaData().getIndexInfo(getCatalog(), getSchemaPattern(), tableNamePattern,
                unique, approximate);
    }
}
