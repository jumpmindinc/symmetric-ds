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

/**
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
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jumpmind.db.model.ColumnTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains information about the database platform such as supported features and native type mappings.
 */
public class DatabaseInfo {
    /** The Log to which logging calls will be made. */
    private final Logger log = LoggerFactory.getLogger(DatabaseInfo.class);
    /**
     * Whether the database requires the explicit stating of NULL as the default value.
     */
    private boolean nullAsDefaultValueRequired = false;
    /**
     * Whether default values can be defined for LONGVARCHAR/LONGVARBINARY columns.
     */
    private boolean defaultValuesForLongTypesSupported = true;
    // properties influencing the specification of table constraints
    /**
     * Whether primary key constraints are embedded inside the create table statement.
     */
    private boolean primaryKeyEmbedded = true;
    /**
     * Whether foreign key constraints are embedded inside the create table statement.
     */
    private boolean foreignKeysEmbedded = false;
    /** Whether embedded foreign key constraints are explicitly named. */
    private boolean embeddedForeignKeysNamed = false;
    /** Whether non-unique indices are supported. */
    private boolean indicesSupported = true;
    /** Whether database has foreign key support */
    private boolean foreignKeysSupported = true;
    /** Whether indices are embedded inside the create table statement. */
    private boolean indicesEmbedded = false;
    /**
     * Whether unique constraints are embedded inside the column definition statement.
     */
    private boolean uniqueEmbedded = true;
    private boolean triggersSupported = true;
    /** Whether table-level logging manipulation (to reduce overhead of data loads) is supported. */
    private boolean tableLevelLoggingSupported = false;
    private boolean triggersCreateOrReplaceSupported = false;
    /** Whether identity specification is supported for non-primary key columns. */
    private boolean nonPKIdentityColumnsSupported = true;
    /** Whether generated/computed/virtual columns are supported. */
    private boolean generatedColumnsSupported = false;
    /** Whether generated columns that physically store their value (e.g. SQL Server PERSISTED) are supported. */
    private boolean persistedGeneratedColumnsSupported = false;
    /** Whether an index can be created on a generated column that isn't persisted (e.g. a virtual/non-materialized computed column). */
    private boolean nonPersistedGeneratedColumnsIndexSupported = false;
    /** Whether expressions can be used as default values */
    private boolean expressionsAsDefaultValuesSupported = false;
    /** Whether functional indices are supported */
    private boolean functionalIndicesSupported = false;
    /**
     * Whether the auto-increment definition is done via the DEFAULT part of the column definition.
     */
    private boolean defaultValueUsedForIdentitySpec = false;
    // properties influencing the reading of models from live databases
    /**
     * Whether system indices (database-generated indices for primary and foreign keys) are returned when reading a model from a database.
     */
    private boolean systemIndicesReturned = true;
    /**
     * Whether system indices for foreign keys are always non-unique or can be unique (i.e. if a primary key column is used to establish the foreign key).
     */
    private boolean systemForeignKeyIndicesAlwaysNonUnique = false;
    /**
     * Whether the database returns a synthetic default value for non-identity required columns.
     */
    private boolean syntheticDefaultValueForRequiredReturned = false;
    /**
     * Whether the platform is able to determine auto increment status from an existing database.
     */
    private boolean identityStatusReadingSupported = true;
    // other DDL/DML properties
    /** Whether comments are supported. */
    private boolean sqlCommentsSupported = true;
    /** Whether delimited identifiers are supported or not. */
    private boolean delimitedIdentifiersSupported = true;
    /** Whether an ALTER TABLE is needed to drop indexes. */
    private boolean alterTableForDropUsed = false;
    /**
     * Whether the platform allows for the explicit specification of values for identity columns in INSERT and UPDATE statements.
     */
    private boolean identityOverrideAllowed = true;
    /**
     * Whether the values of identity columns can be read back from the database after insertion.
     */
    private boolean lastIdentityValueReadable = true;
    /**
     * Whether auto-commit mode for the reading of the values of identity columns after insertion shall be used.
     */
    private boolean autoCommitModeForLastIdentityValueReading = true;
    /**
     * Specifies the maximum length that a table name can have for this database (-1 if there is no limit).
     */
    private int maxTableNameLength = -1;
    /**
     * Specifies the maximum length that a column name can have for this database (-1 if there is no limit).
     */
    private int maxColumnNameLength = -1;
    /**
     * Specifies the maximum length that a constraint name can have for this database (-1 if there is no limit).
     */
    private int maxConstraintNameLength = -1;
    /**
     * Specifies the maximum length that a foreign key name can have for this database (-1 if there is no limit).
     */
    private int maxForeignKeyNameLength = -1;
    /**
     * The string used for delimiting SQL identifiers, eg. table names, column names etc.
     */
    private String delimiterToken = "\"";
    /**
     * The string used for escaping values when generating textual SQL statements.
     */
    private String valueQuoteToken = "'";
    private String binaryQuoteStart = "'";
    private String binaryQuoteEnd = "'";
    /** The string that starts a comment. */
    private String commentPrefix = "--";
    /** The string that ends a comment. */
    private String commentSuffix = "";
    /** The text separating individual sql commands. */
    private String sqlCommandDelimiter = ";";
    private boolean dateOverridesToTimestamp;
    private boolean emptyStringNulled = false;
    private boolean autoIncrementUpdateAllowed = true;
    /**
     * True if blank characters are padded out
     */
    private boolean blankCharColumnSpacePadded;
    /**
     * True if non-blank characters are padded out
     */
    private boolean nonBlankCharColumnSpacePadded;
    private boolean charColumnSpaceTrimmed;
    /***
     * Indicates whether each ddl statement needs to be committed.
     */
    private boolean requiresAutoCommitForDdl = false;
    private boolean requiresSavePointsInTransaction = false;
    private String catalogSeparator = ".";
    private String schemaSeparator = ".";
    /** Contains non-default mappings from jdbc to native types. */
    private Map<Integer, String> nativeTypes = new HashMap<Integer, String>();
    /**
     * Contains the jdbc types corresponding to the native types for non-default mappings.
     */
    private Map<Integer, Integer> targetJdbcTypes = new HashMap<Integer, Integer>();
    /**
     * Contains those JDBC types whose corresponding native types have a null value as the default value.
     */
    private Set<Integer> typesWithNullDefault = new HashSet<Integer>();
    /**
     * Contains those JDBC types whose corresponding native types are types that have a size on this platform.
     */
    private Set<Integer> typesWithSize = new HashSet<Integer>();
    /**
     * Contains the default sizes for those JDBC types whose corresponding native types require a size.
     */
    private Map<Integer, Integer> typesDefaultSizes = new HashMap<Integer, Integer>();
    /**
     * Contains the maximum sizes for those JDBC types whose corresponding native types require a size.
     */
    private Map<String, Integer> nativeTypeMaxSizes = new HashMap<String, Integer>();
    /**
     * Contains those JDBC types whose corresponding native types are types that have precision and scale on this platform.
     */
    private HashSet<Integer> typesWithPrecisionAndScale = new HashSet<Integer>();
    private Supplier<Set<String>> defaultValuesToLeaveUnquotedSupplier;
    private Supplier<Map<String, String>> defaultValuesToTranslateSupplier;
    /**
     * The minimum transaction isolation level for the given database that will prevent phantom reads from occurring. Default is TRANSACTION_READ_COMMITTED
     * which covers most dbs
     */
    private int minIsolationLevelToPreventPhantomReads = Connection.TRANSACTION_READ_COMMITTED;
    /**
     * Specifies if an empty string entered into a required char column will be read out as null (Sql Anywhere).
     */
    private boolean requiredCharColumnEmptyStringSameAsNull;
    private boolean notNullColumnsSupported = true;
    private boolean zeroDateAllowed;
    private boolean infinityDateAllowed;
    private String cteExpression;
    private boolean logBased;
    private boolean triggersContainJava = false;
    private boolean canDeleteUsingExists = true;
    private boolean canTriggerExistWithoutTable = false;
    private boolean jdbcTimestampAllowed = true;

    /**
     * Creates a new platform info object.
     */
    public DatabaseInfo() {
        this.typesWithNullDefault.add(Types.CHAR);
        this.typesWithNullDefault.add(Types.VARCHAR);
        this.typesWithNullDefault.add(Types.LONGVARCHAR);
        this.typesWithNullDefault.add(Types.CLOB);
        this.typesWithNullDefault.add(Types.BINARY);
        this.typesWithNullDefault.add(Types.VARBINARY);
        this.typesWithNullDefault.add(Types.LONGVARBINARY);
        this.typesWithNullDefault.add(Types.BLOB);
        this.typesWithNullDefault.add(Types.BIT);
        this.typesWithNullDefault.add(Types.BOOLEAN);
        this.typesWithNullDefault.add(Types.TINYINT);
        this.typesWithNullDefault.add(Types.SMALLINT);
        this.typesWithNullDefault.add(Types.INTEGER);
        this.typesWithNullDefault.add(Types.BIGINT);
        this.typesWithNullDefault.add(Types.FLOAT);
        this.typesWithNullDefault.add(Types.REAL);
        this.typesWithNullDefault.add(Types.DOUBLE);
        this.typesWithNullDefault.add(Types.NUMERIC);
        this.typesWithNullDefault.add(Types.DECIMAL);
        this.typesWithNullDefault.add(Types.DATE);
        this.typesWithNullDefault.add(Types.TIME);
        this.typesWithNullDefault.add(Types.TIMESTAMP);
        this.typesWithNullDefault.add(Types.NCHAR);
        this.typesWithNullDefault.add(Types.NVARCHAR);
        this.typesWithNullDefault.add(Types.LONGNVARCHAR);
        this.typesWithNullDefault.add(Types.NCLOB);
        this.typesWithNullDefault.add(Types.TIMESTAMP_WITH_TIMEZONE);
        this.typesWithNullDefault.add(Types.TIME_WITH_TIMEZONE);
        this.typesWithSize.add(Integer.valueOf(Types.CHAR));
        this.typesWithSize.add(Integer.valueOf(Types.VARCHAR));
        this.typesWithSize.add(Integer.valueOf(Types.BINARY));
        this.typesWithSize.add(Integer.valueOf(Types.VARBINARY));
        this.typesWithSize.add(Integer.valueOf(ColumnTypes.NCHAR));
        this.typesWithSize.add(Integer.valueOf(ColumnTypes.NVARCHAR));
        this.typesWithPrecisionAndScale.add(Integer.valueOf(Types.DECIMAL));
        this.typesWithPrecisionAndScale.add(Integer.valueOf(Types.NUMERIC));
        this.nativeTypes.put(Integer.valueOf(ColumnTypes.TIMESTAMPTZ), "TIMESTAMP");
        this.nativeTypes.put(Integer.valueOf(ColumnTypes.TIMESTAMPLTZ), "TIMESTAMP");
        this.nativeTypes.put(Integer.valueOf(ColumnTypes.TIMETZ), "TIME");
        this.targetJdbcTypes.put(Integer.valueOf(ColumnTypes.TIMESTAMPTZ), Types.TIMESTAMP);
        this.targetJdbcTypes.put(Integer.valueOf(ColumnTypes.TIMESTAMPLTZ), Types.TIMESTAMP);
        this.targetJdbcTypes.put(Integer.valueOf(ColumnTypes.TIMETZ), Types.TIME);
    }
    // properties influencing the definition of columns

    /**
     * Determines whether a NULL needs to be explicitly stated when the column has no specified default value. Default is false.
     * 
     * @return <code>true</code> if NULL must be written for empty default values
     */
    public boolean isNullAsDefaultValueRequired() {
        return nullAsDefaultValueRequired;
    }

    /**
     * Specifies whether a NULL needs to be explicitly stated when the column has no specified default value. Default is false.
     * 
     * @param requiresNullAsDefaultValue
     *            Whether NULL must be written for empty default values
     */
    public void setNullAsDefaultValueRequired(boolean requiresNullAsDefaultValue) {
        this.nullAsDefaultValueRequired = requiresNullAsDefaultValue;
    }

    /**
     * Determines whether default values can be specified for LONGVARCHAR/LONGVARBINARY columns.
     * 
     * @return <code>true</code> if default values are allowed
     */
    public boolean isDefaultValuesForLongTypesSupported() {
        return defaultValuesForLongTypesSupported;
    }

    /**
     * Specifies whether default values can be specified for LONGVARCHAR/LONGVARBINARY columns.
     * 
     * @param isSupported
     *            <code>true</code> if default values are supported
     */
    public void setDefaultValuesForLongTypesSupported(boolean isSupported) {
        this.defaultValuesForLongTypesSupported = isSupported;
    }
    // properties influencing the specification of table constraints

    /**
     * Determines whether primary key constraints are embedded in the create table clause or as seperate alter table statements. The default is embedded pks.
     * 
     * @return <code>true</code> if pk constraints are embedded
     */
    public boolean isPrimaryKeyEmbedded() {
        return primaryKeyEmbedded;
    }

    /**
     * Specifies whether the primary key constraints are embedded in the create table clause or as seperate alter table statements.
     * 
     * @param primaryKeyEmbedded
     *            Whether pk constraints are embedded
     */
    public void setPrimaryKeyEmbedded(boolean primaryKeyEmbedded) {
        this.primaryKeyEmbedded = primaryKeyEmbedded;
    }

    /**
     * Determines whether foreign key constraints are embedded in the create table clause or as seperate alter table statements. Per default, foreign keys are
     * external.
     * 
     * @return <code>true</code> if fk constraints are embedded
     */
    public boolean isForeignKeysEmbedded() {
        return foreignKeysEmbedded;
    }

    /**
     * Specifies whether foreign key constraints are embedded in the create table clause or as seperate alter table statements.
     * 
     * @param foreignKeysEmbedded
     *            Whether fk constraints are embedded
     */
    public void setForeignKeysEmbedded(boolean foreignKeysEmbedded) {
        this.foreignKeysEmbedded = foreignKeysEmbedded;
    }

    /**
     * Returns whether embedded foreign key constraints should have a name.
     * 
     * @return <code>true</code> if embedded fks have name
     */
    public boolean isEmbeddedForeignKeysNamed() {
        return embeddedForeignKeysNamed;
    }

    /**
     * Specifies whether embedded foreign key constraints should be named.
     * 
     * @param embeddedForeignKeysNamed
     *            Whether embedded fks shall have a name
     */
    public void setEmbeddedForeignKeysNamed(boolean embeddedForeignKeysNamed) {
        this.embeddedForeignKeysNamed = embeddedForeignKeysNamed;
    }

    /**
     * Determines whether indices are supported.
     * 
     * @return <code>true</code> if indices are supported
     */
    public boolean isIndicesSupported() {
        return indicesSupported;
    }

    /**
     * Specifies whether indices are supported.
     * 
     * @param supportingIndices
     *            <code>true</code> if indices are supported
     */
    public void setIndicesSupported(boolean supportingIndices) {
        this.indicesSupported = supportingIndices;
    }

    /**
     * Determines whether table-level logging is supported.
     */
    public boolean isTableLevelLoggingSupported() {
        return this.tableLevelLoggingSupported;
    }

    /**
     * Specifies whether table-level logging is supported.
     */
    public void setTableLevelLoggingSupported(boolean value) {
        this.tableLevelLoggingSupported = value;
    }

    /**
     * Determines whether indices are supported.
     * 
     * @return <code>true</code> if indices are supported
     */
    public boolean isForeignKeysSupported() {
        return foreignKeysSupported;
    }

    /**
     * Specifies whether indices are supported.
     * 
     * @param supportingIndices
     *            <code>true</code> if indices are supported
     */
    public void setForeignKeysSupported(boolean foreignKeysSupported) {
        this.foreignKeysSupported = foreignKeysSupported;
    }

    /**
     * Determines whether the indices are embedded in the create table clause or as seperate statements. Per default, indices are external.
     * 
     * @return <code>true</code> if indices are embedded
     */
    public boolean isIndicesEmbedded() {
        return indicesEmbedded;
    }

    /**
     * Specifies whether indices are embedded in the create table clause or as seperate alter table statements.
     * 
     * @param indicesEmbedded
     *            Whether indices are embedded
     */
    public void setIndicesEmbedded(boolean indicesEmbedded) {
        this.indicesEmbedded = indicesEmbedded;
    }

    /**
     * Determines whether unique constraints are embedded in the column definition or as separate constraint statements. The default is non-embedded unique.
     * 
     * @return <code>true</code> if unique constraints are embedded
     */
    public boolean isUniqueEmbedded() {
        return uniqueEmbedded;
    }

    /**
     * Specifies whether the unique constraints are embedded in the column definition or as separate constraint statements.
     * 
     * @param primaryKeyEmbedded
     *            Whether unique constraints are embedded
     */
    public void setUniqueEmbedded(boolean unique) {
        this.uniqueEmbedded = unique;
    }

    /**
     * Determines whether non-primary key columns can be auto-incrementing (IDENTITY columns).
     * 
     * @return <code>true</code> if normal non-PK columns can be auto-incrementing
     */
    public boolean isNonPKIdentityColumnsSupported() {
        return nonPKIdentityColumnsSupported;
    }

    /**
     * Specifies whether non-primary key columns can be auto-incrementing (IDENTITY columns).
     * 
     * @param supportingNonPKIdentityColumns
     *            <code>true</code> if normal non-PK columns can be auto-incrementing
     */
    public void setNonPKIdentityColumnsSupported(boolean supportingNonPKIdentityColumns) {
        this.nonPKIdentityColumnsSupported = supportingNonPKIdentityColumns;
    }

    /**
     * Determines whether generated/computed/virtual columns are supported.
     * 
     * @return <code>true</code> if generated/computed/virtual columns are supported
     */
    public boolean isGeneratedColumnsSupported() {
        return generatedColumnsSupported;
    }

    /**
     * Specifies whether generated/computed/virtual columns are supported.
     * 
     * @param generatedColumnsSupported
     *            <code>true</code> if generated/computed/virtual columns are supported
     */
    public void setGeneratedColumnsSupported(boolean generatedColumnsSupported) {
        this.generatedColumnsSupported = generatedColumnsSupported;
    }

    /**
     * Determines whether the platform supports persisted/retained generated columns whose value is physically stored on disk.
     *
     * @return <code>true</code> if persisted generated columns are supported
     */
    public boolean isPersistedGeneratedColumnsSupported() {
        return persistedGeneratedColumnsSupported;
    }

    /**
     * Specifies whether the platform supports persisted/retained generated columns.
     *
     * @param persistedGeneratedColumnsSupported
     *            <code>true</code> if persisted generated columns are supported
     */
    public void setPersistedGeneratedColumnsSupported(boolean persistedGeneratedColumnsSupported) {
        this.persistedGeneratedColumnsSupported = persistedGeneratedColumnsSupported;
    }

    /**
     * Determines whether the platform supports creating an index on a generated column that isn't persisted (e.g. a virtual/non-materialized computed column).
     *
     * @return <code>true</code> if an index can be created on a non-persisted generated column
     */
    public boolean isNonPersistedGeneratedColumnsIndexSupported() {
        return nonPersistedGeneratedColumnsIndexSupported;
    }

    /**
     * Specifies whether the platform supports creating an index on a generated column that isn't persisted.
     *
     * @param nonPersistedGeneratedColumnsIndexSupported
     *            <code>true</code> if an index can be created on a non-persisted generated column
     */
    public void setNonPersistedGeneratedColumnsIndexSupported(boolean nonPersistedGeneratedColumnsIndexSupported) {
        this.nonPersistedGeneratedColumnsIndexSupported = nonPersistedGeneratedColumnsIndexSupported;
    }

    /**
     * Determines whether expressions can be used as default values.
     * 
     * @return <code>true</code> if expressions can be used as default values
     */
    public boolean isExpressionsAsDefaultValuesSupported() {
        return expressionsAsDefaultValuesSupported;
    }

    /**
     * Specifies whether expressions can be used as default values.
     * 
     * @param expressionsAsDefaultValuesSupported
     *            <code>true</code> if expressions can be used as default values
     */
    public void setExpressionsAsDefaultValuesSupported(boolean expressionsAsDefaultValuesSupported) {
        this.expressionsAsDefaultValuesSupported = expressionsAsDefaultValuesSupported;
    }

    /**
     * Determines whether the auto-increment specification uses the DEFAULT value of the column definition.
     * 
     * @return <code>true</code> if the auto-increment spec is done via the DEFAULT value
     */
    public boolean isDefaultValueUsedForIdentitySpec() {
        return defaultValueUsedForIdentitySpec;
    }

    /**
     * Specifies whether the auto-increment specification uses the DEFAULT value of the column definition.
     * 
     * @param identitySpecUsesDefaultValue
     *            <code>true</code> if the auto-increment spec is done via the DEFAULT value
     */
    public void setDefaultValueUsedForIdentitySpec(boolean identitySpecUsesDefaultValue) {
        this.defaultValueUsedForIdentitySpec = identitySpecUsesDefaultValue;
    }
    // properties influencing the reading of models from live databases

    /**
     * Determines whether database-generated indices for primary and foreign keys are returned when reading a model from a database.
     * 
     * @return <code>true</code> if system indices are read from a live database
     */
    public boolean isSystemIndicesReturned() {
        return systemIndicesReturned;
    }

    /**
     * Specifies whether database-generated indices for primary and foreign keys are returned when reading a model from a database.
     * 
     * @param returningSystemIndices
     *            <code>true</code> if system indices are read from a live database
     */
    public void setSystemIndicesReturned(boolean returningSystemIndices) {
        this.systemIndicesReturned = returningSystemIndices;
    }

    /**
     * Determines whether system indices for foreign keys are always non-unique or can be unique (i.e. if a primary key column is used to establish the foreign
     * key).
     * 
     * @return <code>true</code> if system foreign key indices are always non-unique; default is <code>false</code>
     */
    public boolean isSystemForeignKeyIndicesAlwaysNonUnique() {
        return systemForeignKeyIndicesAlwaysNonUnique;
    }

    /**
     * Specifies whether system indices for foreign keys are always non-unique or can be unique (i.e. if a primary key column is used to establish the foreign
     * key).
     * 
     * @param alwaysNonUnique
     *            <code>true</code> if system foreign key indices are always non-unique
     */
    public void setSystemForeignKeyIndicesAlwaysNonUnique(boolean alwaysNonUnique) {
        this.systemForeignKeyIndicesAlwaysNonUnique = alwaysNonUnique;
    }

    /**
     * Determines whether the platform returns synthetic default values (e.g. 0 for numeric columns etc.) for non-identity required columns when reading a model
     * from a database.
     * 
     * @return <code>true</code> if synthetic default values are returned for non-identity required columns
     */
    public boolean isSyntheticDefaultValueForRequiredReturned() {
        return syntheticDefaultValueForRequiredReturned;
    }

    /**
     * Specifies whether the platform returns synthetic default values (e.g. 0 for numeric columns etc.) for non-identity required columns when reading a model
     * from a database.
     * 
     * @param returningDefaultValue
     *            <code>true</code> if synthetic default values are returned for non-identity required columns
     */
    public void setSyntheticDefaultValueForRequiredReturned(boolean returningDefaultValue) {
        this.syntheticDefaultValueForRequiredReturned = returningDefaultValue;
    }

    /**
     * Determines whether the platform is able to read the auto-increment status for columns from an existing database.
     * 
     * @return <code>true</code> if the auto-increment status can be determined from an existing database
     */
    public boolean getIdentityStatusReadingSupported() {
        return identityStatusReadingSupported;
    }

    /**
     * Specifies whether the platform is able to read the auto-increment status for columns from an existing database.
     * 
     * @param canReadAutoIncrementStatus
     *            <code>true</code> if the auto-increment status can be determined from an existing database
     */
    public void setIdentityStatusReadingSupported(boolean canReadAutoIncrementStatus) {
        this.identityStatusReadingSupported = canReadAutoIncrementStatus;
    }
    // other ddl properties

    /**
     * Determines whether the database supports SQL comments.
     * 
     * @return <code>true</code> if comments are supported
     */
    public boolean isSqlCommentsSupported() {
        return sqlCommentsSupported;
    }

    /**
     * Specifies whether SQL comments are supported by the database.
     * 
     * @param commentsSupported
     *            <code>true</code> if comments are supported
     */
    public void setSqlCommentsSupported(boolean commentsSupported) {
        this.sqlCommentsSupported = commentsSupported;
    }

    /**
     * Determines whether delimited identifiers are supported.
     * 
     * @return <code>true</code> if delimited identifiers are supported
     */
    public boolean isDelimitedIdentifiersSupported() {
        return delimitedIdentifiersSupported;
    }

    /**
     * Specifies whether delimited identifiers are supported.
     * 
     * @param areSupported
     *            <code>true</code> if delimited identifiers are supported
     */
    public void setDelimitedIdentifiersSupported(boolean areSupported) {
        this.delimitedIdentifiersSupported = areSupported;
    }

    /**
     * Determines whether an ALTER TABLE statement shall be used for dropping indices or constraints. The default is false.
     * 
     * @return <code>true</code> if ALTER TABLE is required
     */
    public boolean isAlterTableForDropUsed() {
        return alterTableForDropUsed;
    }

    /**
     * Specifies whether an ALTER TABLE statement shall be used for dropping indices or constraints.
     * 
     * @param useAlterTableForDrop
     *            Whether ALTER TABLE will be used
     */
    public void setAlterTableForDropUsed(boolean useAlterTableForDrop) {
        this.alterTableForDropUsed = useAlterTableForDrop;
    }

    /**
     * Determines whether the platform is allows the explicit specification of values for identity columns in INSERT/UPDATE statements.
     * 
     * @return <code>true</code> if values for identity columns can be specified
     */
    public boolean isIdentityOverrideAllowed() {
        return identityOverrideAllowed;
    }

    /**
     * Specifies whether the platform is allows the explicit specification of values for identity columns in INSERT/UPDATE statements.
     * 
     * @param identityOverrideAllowed
     *            <code>true</code> if values for identity columns can be specified
     */
    public void setIdentityOverrideAllowed(boolean identityOverrideAllowed) {
        this.identityOverrideAllowed = identityOverrideAllowed;
    }

    /**
     * Determines whether the values of identity columns can be read back from the database after insertion of a row.
     * 
     * @return <code>true</code> if the identity column(s) can be read back
     */
    public boolean isLastIdentityValueReadable() {
        return lastIdentityValueReadable;
    }

    /**
     * Specifies whether the values of identity columns can be read back from the database after insertion of a row.
     * 
     * @param lastIdentityValueReadable
     *            <code>true</code> if the identity column(s) can be read back
     */
    public void setLastIdentityValueReadable(boolean lastIdentityValueReadable) {
        this.lastIdentityValueReadable = lastIdentityValueReadable;
    }

    /**
     * Determines whether auto-commit mode for the reading of the values of identity columns after insertion shall be used, i.e. whether between the insertion
     * of the row and the reading of the database-generated identity value a commit is issued.
     * 
     * @return <code>true</code> if auto-commit mode is used
     */
    public boolean isAutoCommitModeForLastIdentityValueReading() {
        return autoCommitModeForLastIdentityValueReading;
    }

    /**
     * Determines whether auto-commit mode for the reading of the values of identity columns after insertion shall be used, i.e. whether between the insertion
     * of the row and the reading of the database-generated identity value a commit is issued.
     * 
     * @param autoCommitModeForLastIdentityValueReading
     *            <code>true</code> if auto-commit mode shall be used
     */
    public void setAutoCommitModeForLastIdentityValueReading(
            boolean autoCommitModeForLastIdentityValueReading) {
        this.autoCommitModeForLastIdentityValueReading = autoCommitModeForLastIdentityValueReading;
    }

    /**
     * Returns the maximum number of characters that a table name can have.
     * 
     * @return The number of characters, or -1 if not limited
     */
    public int getMaxTableNameLength() {
        return maxTableNameLength;
    }

    /**
     * Returns the maximum number of characters that a column name can have.
     * 
     * @return The number of characters, or -1 if not limited
     */
    public int getMaxColumnNameLength() {
        return maxColumnNameLength;
    }

    /**
     * Returns the maximum number of characters that a constraint name can have.
     * 
     * @return The number of characters, or -1 if not limited
     */
    public int getMaxConstraintNameLength() {
        return maxConstraintNameLength;
    }

    /**
     * Returns the maximum number of characters that a foreign key name can have.
     * 
     * @return The number of characters, or -1 if not limited
     */
    public int getMaxForeignKeyNameLength() {
        return maxForeignKeyNameLength;
    }

    /**
     * Sets the maximum length of all identifiers that this database allows. Use this method if the length limit is the same for all kinds of identifiers.
     * 
     * @param maxIdentifierLength
     *            The maximum identifier length, -1 if unlimited
     */
    public void setMaxIdentifierLength(int maxIdentifierLength) {
        this.maxTableNameLength = maxIdentifierLength;
        this.maxColumnNameLength = maxIdentifierLength;
        this.maxConstraintNameLength = maxIdentifierLength;
        this.maxForeignKeyNameLength = maxIdentifierLength;
    }

    /**
     * Sets the maximum length of table names that this database allows.
     * 
     * @param maxTableNameLength
     *            The maximum length, -1 if unlimited
     */
    public void setMaxTableNameLength(int maxTableNameLength) {
        this.maxTableNameLength = maxTableNameLength;
    }

    /**
     * Sets the maximum length of column names that this database allows.
     * 
     * @param maxColumnNameLength
     *            The maximum length, -1 if unlimited
     */
    public void setMaxColumnNameLength(int maxColumnNameLength) {
        this.maxColumnNameLength = maxColumnNameLength;
    }

    /**
     * Sets the maximum length of constraint names that this database allows.
     * 
     * @param maxConstraintNameLength
     *            The maximum length, -1 if unlimited
     */
    public void setMaxConstraintNameLength(int maxConstraintNameLength) {
        this.maxConstraintNameLength = maxConstraintNameLength;
    }

    /**
     * Sets the maximum length of foreign key names that this database allows.
     * 
     * @param maxForeignKeyNameLength
     *            The maximum length, -1 if unlimited
     */
    public void setMaxForeignKeyNameLength(int maxForeignKeyNameLength) {
        this.maxForeignKeyNameLength = maxForeignKeyNameLength;
    }

    /**
     * Returns the text that is used to delimit identifiers (eg. table names). Per default, this is a double quotation character (").
     * 
     * @return The delimiter text
     */
    public String getDelimiterToken() {
        return delimiterToken;
    }

    /**
     * Sets the text that is used to delimit identifiers (eg. table names).
     * 
     * @param delimiterToken
     *            The delimiter text
     */
    public void setDelimiterToken(String delimiterToken) {
        this.delimiterToken = delimiterToken;
    }

    /**
     * Returns the text that is used for for quoting values (e.g. text) when printing default values and in generates insert/update/delete statements. Per
     * default, this is a single quotation character (').
     * 
     * @return The quote text
     */
    public String getValueQuoteToken() {
        return valueQuoteToken;
    }

    /**
     * Sets the text that is used for for quoting values (e.g. text) when printing default values and in generates insert/update/delete statements.
     * 
     * @param valueQuoteChar
     *            The new quote text
     */
    public void setValueQuoteToken(String valueQuoteChar) {
        this.valueQuoteToken = valueQuoteChar;
    }

    /**
     * Returns the string that denotes the beginning of a comment.
     * 
     * @return The comment prefix
     */
    public String getCommentPrefix() {
        return commentPrefix;
    }

    /**
     * Sets the text that starts a comment.
     * 
     * @param commentPrefix
     *            The new comment prefix
     */
    public void setCommentPrefix(String commentPrefix) {
        this.commentPrefix = (commentPrefix == null ? "" : commentPrefix);
    }

    /**
     * Returns the string that denotes the end of a comment. Note that comments will be always on their own line.
     * 
     * @return The comment suffix
     */
    public String getCommentSuffix() {
        return commentSuffix;
    }

    /**
     * Sets the text that ends a comment.
     * 
     * @param commentSuffix
     *            The new comment suffix
     */
    public void setCommentSuffix(String commentSuffix) {
        this.commentSuffix = (commentSuffix == null ? "" : commentSuffix);
    }

    /**
     * Returns the text separating individual sql commands.
     * 
     * @return The delimiter text
     */
    public String getSqlCommandDelimiter() {
        return sqlCommandDelimiter;
    }

    /**
     * Sets the text separating individual sql commands.
     * 
     * @param sqlCommandDelimiter
     *            The delimiter text
     */
    public void setSqlCommandDelimiter(String sqlCommandDelimiter) {
        this.sqlCommandDelimiter = sqlCommandDelimiter;
    }

    /**
     * Returns the database-native type for the given type code.
     * 
     * @param typeCode
     *            The {@link java.sql.Types} type code
     * 
     * @return The native type or <code>null</code> if there isn't one defined
     */
    public String getNativeType(int typeCode) {
        return this.nativeTypes.get(Integer.valueOf(typeCode));
    }

    /**
     * Returns the jdbc type corresponding to the native type that is used for the given jdbc type. This is most often the same jdbc type, but can also be a
     * different one. For instance, if a database has no native boolean type, then the source jdbc type would be <code>BIT</code> or <code>BOOLEAN</code>, and
     * the target jdbc type might be <code>TINYINT</code> or <code>SMALLINT</code>.
     * 
     * @param typeCode
     *            The {@link java.sql.Types} type code
     * 
     * @return The target jdbc type
     */
    public int getTargetJdbcType(int typeCode) {
        Integer targetJdbcType = targetJdbcTypes.get(Integer.valueOf(typeCode));
        return targetJdbcType == null ? typeCode : targetJdbcType.intValue();
    }

    /**
     * Adds a mapping from jdbc type to database-native type.
     * 
     * @param jdbcTypeCode
     *            The jdbc type code as defined by {@link java.sql.Types}
     * 
     * @param nativeType
     *            The native type
     */
    public void addNativeTypeMapping(int jdbcTypeCode, String nativeType) {
        this.nativeTypes.put(Integer.valueOf(jdbcTypeCode), nativeType);
    }

    /**
     * Adds a mapping from jdbc type to database-native type.
     * 
     * @param jdbcTypeCode
     *            The jdbc type code as defined by {@link java.sql.Types}
     * 
     * @param nativeType
     *            The native type
     * 
     * @param targetJdbcTypeCode
     *            The jdbc type code corresponding to the native type (e.g. when reading the model from the database)
     */
    public void addNativeTypeMapping(int jdbcTypeCode, String nativeType, int targetJdbcTypeCode) {
        addNativeTypeMapping(jdbcTypeCode, nativeType);
        this.targetJdbcTypes.put(Integer.valueOf(jdbcTypeCode), Integer.valueOf(targetJdbcTypeCode));
    }

    /**
     * Adds a mapping from jdbc type to database-native type. Note that this method accesses the named constant in {@link java.sql.Types} via reflection and is
     * thus safe to use under JDK 1.2/1.3 even with constants defined only in later Java versions - for these, the method simply will not add a mapping.
     * 
     * @param jdbcTypeName
     *            The jdbc type name, one of the constants defined in {@link java.sql.Types}
     * 
     * @param nativeType
     *            The native type
     */
    public void addNativeTypeMapping(String jdbcTypeName, String nativeType) {
        try {
            Field constant = Types.class.getField(jdbcTypeName);
            if (constant != null) {
                addNativeTypeMapping(constant.getInt(null), nativeType);
            }
        } catch (Exception ex) {
            // ignore -> won't be defined
            this.log.warn("Cannot add native type mapping for undefined jdbc type " + jdbcTypeName,
                    ex);
        }
    }

    /**
     * Adds a mapping from jdbc type to database-native type. Note that this method accesses the named constant in {@link java.sql.Types} via reflection and is
     * thus safe to use under JDK 1.2/1.3 even with constants defined only in later Java versions - for these, the method simply will not add a mapping.
     * 
     * @param jdbcTypeName
     *            The jdbc type name, one of the constants defined in {@link java.sql.Types}
     * 
     * @param nativeType
     *            The native type
     * 
     * @param targetJdbcTypeName
     *            The jdbc type corresponding to the native type (e.g. when reading the model from the database)
     */
    public void addNativeTypeMapping(String jdbcTypeName, String nativeType,
            String targetJdbcTypeName) {
        try {
            Field sourceType = Types.class.getField(jdbcTypeName);
            Field targetType = Types.class.getField(targetJdbcTypeName);
            if ((sourceType != null) && (targetType != null)) {
                addNativeTypeMapping(sourceType.getInt(null), nativeType, targetType.getInt(null));
            }
        } catch (Exception ex) {
            // ignore -> won't be defined
            this.log.warn("Cannot add native type mapping for undefined jdbc type " + jdbcTypeName
                    + ", target jdbc type " + targetJdbcTypeName, ex);
        }
    }

    /**
     * Determines whether the native type for the given sql type code (one of the {@link java.sql.Types} constants) has a null default value on this platform.
     * 
     * @param sqlTypeCode
     *            The sql type code
     * 
     * @return <code>true</code> if the native type has a null default value
     */
    public boolean hasNullDefault(int sqlTypeCode) {
        return typesWithNullDefault.contains(Integer.valueOf(sqlTypeCode));
    }

    /**
     * Specifies whether the native type for the given sql type code (one of the {@link java.sql.Types} constants) has a null default value on this platform.
     * 
     * @param sqlTypeCode
     *            The sql type code
     * 
     * @param hasNullDefault
     *            <code>true</code> if the native type has a null default value
     */
    public void setHasNullDefault(int sqlTypeCode, boolean hasNullDefault) {
        if (hasNullDefault) {
            this.typesWithNullDefault.add(Integer.valueOf(sqlTypeCode));
        } else {
            this.typesWithNullDefault.remove(Integer.valueOf(sqlTypeCode));
        }
    }

    /**
     * Determines whether the native type for the given sql type code (one of the {@link java.sql.Types} constants) has a size specification on this platform.
     * 
     * @param sqlTypeCode
     *            The sql type code
     * 
     * @return <code>true</code> if the native type has a size specification
     */
    public boolean hasSize(int sqlTypeCode) {
        return typesWithSize.contains(Integer.valueOf(sqlTypeCode));
    }

    /**
     * Specifies whether the native type for the given sql type code (one of the {@link java.sql.Types} constants) has a size specification on this platform.
     * 
     * @param sqlTypeCode
     *            The sql type code
     * 
     * @param hasSize
     *            <code>true</code> if the native type has a size specification
     */
    public void setHasSize(int sqlTypeCode, boolean hasSize) {
        if (hasSize) {
            this.typesWithSize.add(Integer.valueOf(sqlTypeCode));
        } else {
            this.typesWithSize.remove(Integer.valueOf(sqlTypeCode));
        }
    }

    /**
     * Returns the default size value for the given type, if any.
     * 
     * @param jdbcTypeCode
     *            The jdbc type code
     * 
     * @return The default size or <code>null</code> if none is defined
     */
    public Integer getDefaultSize(int jdbcTypeCode) {
        return typesDefaultSizes.get(Integer.valueOf(jdbcTypeCode));
    }

    /**
     * Adds a default size for the given jdbc type.
     * 
     * @param jdbcTypeCode
     *            The jdbc type code
     * 
     * @param defaultSize
     *            The default size
     */
    public void setDefaultSize(int jdbcTypeCode, int defaultSize) {
        this.typesDefaultSizes.put(Integer.valueOf(jdbcTypeCode), Integer.valueOf(defaultSize));
    }

    /**
     * Adds a default size for the given jdbc type.
     * 
     * @param jdbcTypeName
     *            The name of the jdbc type, one of the {@link Types} constants
     * 
     * @param defaultSize
     *            The default size
     */
    public void setDefaultSize(String jdbcTypeName, int defaultSize) {
        try {
            Field constant = Types.class.getField(jdbcTypeName);
            if (constant != null) {
                setDefaultSize(constant.getInt(null), defaultSize);
            }
        } catch (Exception ex) {
            // ignore -> won't be defined
            this.log.warn("Cannot add default size for undefined jdbc type " + jdbcTypeName, ex);
        }
    }

    public int getMaxSize(String nativeType) {
        Integer maxSize = nativeTypeMaxSizes.get(nativeType);
        return maxSize == null ? 0 : maxSize;
    }

    public void setMaxSize(String nativeType, int defaultSize) {
        nativeTypeMaxSizes.put(nativeType, defaultSize);
    }

    /**
     * Determines whether the native type for the given sql type code (one of the {@link java.sql.Types} constants) has precision and scale specifications on
     * this platform.
     * 
     * @param sqlTypeCode
     *            The sql type code
     * 
     * @return <code>true</code> if the native type has precision and scale specifications
     */
    public boolean hasPrecisionAndScale(int sqlTypeCode) {
        return typesWithPrecisionAndScale.contains(Integer.valueOf(sqlTypeCode));
    }

    /**
     * Specifies whether the native type for the given sql type code (one of the {@link java.sql.Types} constants) has precision and scale specifications on
     * this platform.
     * 
     * @param sqlTypeCode
     *            The sql type code
     * 
     * @param hasPrecisionAndScale
     *            <code>true</code> if the native type has precision and scale specifications
     */
    public void setHasPrecisionAndScale(int sqlTypeCode, boolean hasPrecisionAndScale) {
        if (hasPrecisionAndScale) {
            this.typesWithPrecisionAndScale.add(Integer.valueOf(sqlTypeCode));
        } else {
            this.typesWithPrecisionAndScale.remove(Integer.valueOf(sqlTypeCode));
        }
    }

    public Set<String> getDefaultValuesToLeaveUnquoted() {
        if (defaultValuesToLeaveUnquotedSupplier != null) {
            return defaultValuesToLeaveUnquotedSupplier.get();
        }
        return new HashSet<String>();
    }

    public void setDefaultValuesToLeaveUnquotedSupplier(Supplier<Set<String>> defaultValuesToLeaveUnquotedSupplier) {
        this.defaultValuesToLeaveUnquotedSupplier = defaultValuesToLeaveUnquotedSupplier;
    }

    public Map<String, String> getDefaultValuesToTranslate() {
        if (defaultValuesToTranslateSupplier != null) {
            return defaultValuesToTranslateSupplier.get();
        }
        return new HashMap<String, String>();
    }

    public void setDefaultValuesToTranslateSupplier(Supplier<Map<String, String>> defaultValuesToTranslateSupplier) {
        this.defaultValuesToTranslateSupplier = defaultValuesToTranslateSupplier;
    }

    public void setTriggersSupported(boolean triggersSupported) {
        this.triggersSupported = triggersSupported;
    }

    public boolean isTriggersSupported() {
        return triggersSupported;
    }

    public boolean isDateOverridesToTimestamp() {
        return dateOverridesToTimestamp;
    }

    public void setDateOverridesToTimestamp(boolean dateOverridesToTimestamp) {
        this.dateOverridesToTimestamp = dateOverridesToTimestamp;
    }

    public boolean isEmptyStringNulled() {
        return emptyStringNulled;
    }

    public void setEmptyStringNulled(boolean emptyStringNulled) {
        this.emptyStringNulled = emptyStringNulled;
    }

    public void setBlankCharColumnSpacePadded(boolean blankCharColumnSpacePadded) {
        this.blankCharColumnSpacePadded = blankCharColumnSpacePadded;
    }

    public boolean isBlankCharColumnSpacePadded() {
        return blankCharColumnSpacePadded;
    }

    public void setCharColumnSpaceTrimmed(boolean charColumnSpaceTrimmed) {
        this.charColumnSpaceTrimmed = charColumnSpaceTrimmed;
    }

    public boolean isCharColumnSpaceTrimmed() {
        return charColumnSpaceTrimmed;
    }

    public void setNonBlankCharColumnSpacePadded(boolean nonBlankCharColumnSpacePadded) {
        this.nonBlankCharColumnSpacePadded = nonBlankCharColumnSpacePadded;
    }

    public boolean isNonBlankCharColumnSpacePadded() {
        return nonBlankCharColumnSpacePadded;
    }

    public boolean isAutoIncrementUpdateAllowed() {
        return autoIncrementUpdateAllowed;
    }

    public void setAutoIncrementUpdateAllowed(boolean autoIncrementUpdateAllowed) {
        this.autoIncrementUpdateAllowed = autoIncrementUpdateAllowed;
    }

    public void setRequiresAutoCommitForDdl(boolean requireAutoCommitForDdl) {
        this.requiresAutoCommitForDdl = requireAutoCommitForDdl;
    }

    public boolean isRequiresAutoCommitForDdl() {
        return requiresAutoCommitForDdl;
    }

    public void setRequiresSavePointsInTransaction(boolean requiresSavePointsInTransaction) {
        this.requiresSavePointsInTransaction = requiresSavePointsInTransaction;
    }

    public boolean isRequiresSavePointsInTransaction() {
        return requiresSavePointsInTransaction;
    }

    public int getMinIsolationLevelToPreventPhantomReads() {
        return minIsolationLevelToPreventPhantomReads;
    }

    public void setMinIsolationLevelToPreventPhantomReads(
            int minIsolationLevelToPreventPhantomReads) {
        this.minIsolationLevelToPreventPhantomReads = minIsolationLevelToPreventPhantomReads;
    }

    public boolean isRequiredCharColumnEmptyStringSameAsNull() {
        return requiredCharColumnEmptyStringSameAsNull;
    }

    public void setRequiredCharColumnEmptyStringSameAsNull(
            boolean requiredCharColumnEmptyStringSameAsNull) {
        this.requiredCharColumnEmptyStringSameAsNull = requiredCharColumnEmptyStringSameAsNull;
    }

    public String getBinaryQuoteStart() {
        return binaryQuoteStart;
    }

    public void setBinaryQuoteStart(String binaryQuoteStart) {
        this.binaryQuoteStart = binaryQuoteStart;
    }

    public String getBinaryQuoteEnd() {
        return binaryQuoteEnd;
    }

    public void setBinaryQuoteEnd(String binaryQuoteEnd) {
        this.binaryQuoteEnd = binaryQuoteEnd;
    }

    public void setCatalogSeparator(String catalogSeparator) {
        this.catalogSeparator = catalogSeparator;
    }

    public String getCatalogSeparator() {
        return catalogSeparator;
    }

    public void setSchemaSeparator(String schemaSeparator) {
        this.schemaSeparator = schemaSeparator;
    }

    public String getSchemaSeparator() {
        return schemaSeparator;
    }

    public boolean isNotNullColumnsSupported() {
        return notNullColumnsSupported;
    }

    public void setNotNullColumnsSupported(boolean notNullColumnsSupported) {
        this.notNullColumnsSupported = notNullColumnsSupported;
    }

    public boolean isZeroDateAllowed() {
        return zeroDateAllowed;
    }

    public void setZeroDateAllowed(boolean zeroDateAllowed) {
        this.zeroDateAllowed = zeroDateAllowed;
    }

    public boolean isInfinityDateAllowed() {
        return infinityDateAllowed;
    }

    public void setInfinityDateAllowed(boolean infinityDateAllowed) {
        this.infinityDateAllowed = infinityDateAllowed;
    }

    public boolean isTriggersCreateOrReplaceSupported() {
        return triggersCreateOrReplaceSupported;
    }

    public void setTriggersCreateOrReplaceSupported(boolean triggersCreateOrReplaceSupported) {
        this.triggersCreateOrReplaceSupported = triggersCreateOrReplaceSupported;
    }

    public String getCteExpression() {
        return cteExpression;
    }

    public void setCteExpression(String cteExpression) {
        this.cteExpression = cteExpression;
    }

    public boolean isLogBased() {
        return logBased;
    }

    public void setLogBased(boolean logBased) {
        this.logBased = logBased;
    }

    public boolean isTriggersContainJava() {
        return triggersContainJava;
    }

    public void setTriggersContainJava(boolean triggersContainJava) {
        this.triggersContainJava = triggersContainJava;
    }

    public boolean isFunctionalIndicesSupported() {
        return functionalIndicesSupported;
    }

    public void setFunctionalIndicesSupported(boolean functionalIndicesSupported) {
        this.functionalIndicesSupported = functionalIndicesSupported;
    }

    public boolean canDeleteUsingExists() {
        return canDeleteUsingExists;
    }

    public void setCanDeleteUsingExists(boolean canDeleteUsingExists) {
        this.canDeleteUsingExists = canDeleteUsingExists;
    }

    public boolean canTriggerExistWithoutTable() {
        return canTriggerExistWithoutTable;
    }

    public void setCanTriggerExistWithoutTable(boolean canTriggerExistWithoutTable) {
        this.canTriggerExistWithoutTable = canTriggerExistWithoutTable;
    }

    public boolean isJdbcTimestampAllowed() {
        return jdbcTimestampAllowed;
    }

    public void setJdbcTimestampAllowed(boolean jdbcTimestampAllowed) {
        this.jdbcTimestampAllowed = jdbcTimestampAllowed;
    }
}