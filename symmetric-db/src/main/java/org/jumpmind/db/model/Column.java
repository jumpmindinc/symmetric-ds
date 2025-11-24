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

import static org.jumpmind.db.model.ColumnTypes.ORACLE_TIMESTAMPLTZ;
import static org.jumpmind.db.model.ColumnTypes.ORACLE_TIMESTAMPTZ;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PlatformUtils;
import org.jumpmind.util.FormatUtils;

/**
 * Represents a column in the database model.
 */
public class Column implements Cloneable, Serializable {
    /** Unique ID for serialization purposes. */
    private static final long serialVersionUID = 1L;
    /** The name of the column. */
    private String name;
    /**
     * The java name of the column (optional and unused by DdlUtils, for Torque compatibility).
     */
    private String javaName;
    /** The column's description. */
    private String description;
    /** Whether the column is a primary key column. */
    private boolean primaryKey;
    /**
     * Whether the column is required, ie. it must not contain <code>NULL</code>
     */
    private boolean required;
    /** Whether the column's value is incremented automatically. */
    private boolean autoIncrement;
    /** Whether the column's value is unique using the unique constraint keyword (e.g. SQLite). */
    private boolean unique;
    /** Whether the column is a generated/computed/virtual column. */
    private boolean generated;
    /** Whether the column has an expression for a default value. */
    private boolean expressionAsDefaultValue;
    /**
     * The mapped JDBC type code
     */
    private int mappedTypeCode;
    /**
     * The mapped JDBC type
     */
    private String mappedType;
    /** The size of the column for JDBC types that require/support this. */
    private String size;
    /** The size of the column for JDBC types that require/support this. */
    private Integer sizeAsInt;
    private int charOctetLength;
    /** The scale of the column for JDBC types that require/support this. */
    private int scale;
    /** The default value. */
    private String defaultValue;
    /**
     * The actual JDBC type code.
     */
    private int jdbcTypeCode = Integer.MIN_VALUE;
    /**
     * The actual JDBC type name.
     */
    private String jdbcTypeName;
    private boolean distributionKey;
    private int precisionRadix;
    private Map<String, PlatformColumn> platformColumns;
    private int primaryKeySequence;
    // private String[] enumValues;
    /** Flag for timestamp column that is updated to current timestamp automatically when a row is updated */
    private boolean autoUpdate;

    public Column() {
    }

    public Column(String name) {
        this(name, false);
    }

    public Column(String name, boolean primaryKey) {
        this.name = name;
        this.primaryKey = primaryKey;
        this.required = primaryKey;
    }

    public Column(String name, boolean primaryKey, int typeCode, int size, int scale) {
        this.name = name;
        this.primaryKey = primaryKey;
        this.required = primaryKey;
        setTypeCode(typeCode);
        setSizeAndScale(size, scale);
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
     * Returns the java name of the column. This property is unused by DdlUtils and only for Torque compatibility.
     * 
     * @return The java name
     */
    public String getJavaName() {
        return javaName;
    }

    /**
     * Sets the java name of the column. This property is unused by DdlUtils and only for Torque compatibility.
     * 
     * @param javaName
     *            The java name
     */
    public void setJavaName(String javaName) {
        this.javaName = javaName;
    }

    /**
     * Returns the description of the column.
     * 
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the column.
     * 
     * @param description
     *            The description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Determines whether this column is a primary key column.
     * 
     * @return <code>true</code> if this column is a primary key column
     */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * Specifies whether this column is a primary key column.
     * 
     * @param primaryKey
     *            <code>true</code> if this column is a primary key column
     */
    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
        if (primaryKey) {
            this.required = true;
        }
    }

    /**
     * Determines whether this column is a required column, ie. that it is not allowed to contain <code>NULL</code> values.
     * 
     * @return <code>true</code> if this column is a required column
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Specifies whether this column is a required column, ie. that it is not allowed to contain <code>NULL</code> values.
     * 
     * @param required
     *            <code>true</code> if this column is a required column
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Determines whether this column is an auto-increment column.
     * 
     * @return <code>true</code> if this column is an auto-increment column
     */
    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    /**
     * Specifies whether this column is an auto-increment column.
     * 
     * @param autoIncrement
     *            <code>true</code> if this column is an auto-increment column
     */
    public void setAutoIncrement(boolean autoIncrement) {
        this.autoIncrement = autoIncrement;
    }

    /**
     * Determines whether this column is an unique column.
     * 
     * @return <code>true</code> if this column is a unique column
     */
    public boolean isUnique() {
        return unique;
    }

    /**
     * Specifies whether this column is an unique column.
     * 
     * @param unique
     *            <code>true</code> if this column is a unique column
     */
    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    /**
     * Determines whether this column is a generated/computed/virtual column.
     * 
     * @return <code>true</code> if this column is a generated/computed/virtual column
     */
    public boolean isGenerated() {
        return generated;
    }

    /**
     * Specifies whether this column is a generated/computed/virtual column.
     * 
     * @param generated
     *            <code>true</code> if this column is a generated/computed/virtual column
     */
    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    /**
     * Determines whether this column has an expression for a default value.
     * 
     * @return <code>true</code> if this column has an expression for a default value
     */
    public boolean isExpressionAsDefaultValue() {
        return expressionAsDefaultValue;
    }

    /**
     * Specifies whether this column has an expression for a default value.
     * 
     * @param expressionAsDefaultValue
     *            <code>true</code> if this column has an expression for a default value
     */
    public void setExpressionAsDefaultValue(boolean expressionAsDefaultValue) {
        this.expressionAsDefaultValue = expressionAsDefaultValue;
    }

    /**
     * Returns the code (one of the constants in {@link java.sql.Types}) of the JDBC type of the column.
     * 
     * @return The type code
     */
    public int getMappedTypeCode() {
        return mappedTypeCode;
    }

    /**
     * Sets the code (one of the constants in {@link java.sql.Types}) of the JDBC type of the column.
     * 
     * @param typeCode
     *            The type code
     */
    public final void setMappedTypeCode(int typeCode) {
        this.mappedType = TypeMap.getJdbcTypeName(typeCode);
        if (this.mappedType == null) {
            throw new ModelException("Unknown JDBC type code " + typeCode + " for : " + toString());
        }
        this.mappedTypeCode = typeCode;
    }

    /**
     * Returns the JDBC type of the column.
     * 
     * @return The type
     */
    public String getMappedType() {
        return mappedType;
    }

    /**
     * Sets the JDBC type of the column.
     * 
     * @param type
     *            The type
     */
    public void setMappedType(String type) {
        Integer typeCode = TypeMap.getJdbcTypeCode(type);
        if (typeCode == null) {
            throw new ModelException("Unknown JDBC type " + type + " for : " + toString());
        } else {
            this.mappedTypeCode = typeCode.intValue();
            // we get the corresponding string value from the TypeMap in order
            // to detect extension types which we don't want in the model
            this.mappedType = TypeMap.getJdbcTypeName(typeCode);
        }
    }

    /**
     * Determines whether this column is of a numeric type.
     * 
     * @return <code>true</code> if this column is of a numeric type
     */
    public boolean isOfNumericType() {
        return TypeMap.isNumericType(getMappedTypeCode());
    }

    /**
     * Determines whether this column is of a text type.
     * 
     * @return <code>true</code> if this column is of a text type
     */
    public boolean isOfTextType() {
        return TypeMap.isTextType(getMappedTypeCode());
    }

    /**
     * Determines whether this column is of a binary type.
     * 
     * @return <code>true</code> if this column is of a binary type
     */
    public boolean isOfBinaryType() {
        return TypeMap.isBinaryType(getMappedTypeCode());
    }

    /**
     * Determines whether this column is of a special type.
     * 
     * @return <code>true</code> if this column is of a special type
     */
    public boolean isOfSpecialType() {
        return TypeMap.isSpecialType(getMappedTypeCode());
    }

    /**
     * Returns the size of the column.
     * 
     * @return The size
     */
    public String getSize() {
        return size;
    }

    /**
     * Returns the size of the column as an integer.
     * 
     * @return The size as an integer
     */
    public int getSizeAsInt() {
        return sizeAsInt == null ? 0 : sizeAsInt.intValue();
    }

    /**
     * Sets the size of the column. This is either a simple integer value or a comma-separated pair of integer values specifying the size and scale.
     * 
     * @param size
     *            The size
     */
    public void setSize(String size) {
        if (size != null) {
            int pos = size.indexOf(",");
            this.size = size;
            if (pos < 0) {
                scale = 0;
                sizeAsInt = Integer.valueOf(size);
            } else {
                sizeAsInt = Integer.valueOf(size.substring(0, pos));
                scale = Integer.parseInt(size.substring(pos + 1));
            }
        } else {
            this.size = null;
            sizeAsInt = null;
            scale = 0;
        }
        if (platformColumns != null) {
            for (PlatformColumn platformColumn : platformColumns.values()) {
                platformColumn.setSize(sizeAsInt == null ? -1 : sizeAsInt);
                platformColumn.setDecimalDigits(size == null || size.indexOf(",") < 0 ? -1 : scale);
            }
        }
    }

    /**
     * Returns the scale of the column.
     * 
     * @return The scale
     */
    public int getScale() {
        return scale;
    }

    /**
     * Sets the scale of the column.
     * 
     * @param scale
     *            The scale
     */
    public void setScale(int scale) {
        setSizeAndScale(getSizeAsInt(), scale);
    }

    /**
     * Sets both the size and scale.
     * 
     * @param size
     *            The size
     * @param scale
     *            The scale
     */
    public final void setSizeAndScale(int size, int scale) {
        sizeAsInt = Integer.valueOf(size);
        this.scale = scale;
        this.size = String.valueOf(size);
        if (scale > 0) {
            this.size += "," + scale;
        }
        if (platformColumns != null) {
            for (PlatformColumn platformColumn : platformColumns.values()) {
                platformColumn.setSize(size);
                platformColumn.setDecimalDigits(scale);
            }
        }
    }

    /**
     * Returns the precision radix of the column.
     * 
     * @return The precision radix
     */
    public int getPrecisionRadix() {
        return this.precisionRadix;
    }

    /**
     * Sets the precision radix of the column.
     * 
     * @param precisionRadix
     *            The precision radix
     */
    public void setPrecisionRadix(int precisionRadix) {
        this.precisionRadix = precisionRadix;
    }

    /**
     * Returns the default value of the column.
     * 
     * @return The default value
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Tries to parse the default value of the column and returns it as an object of the corresponding java type. If the value could not be parsed, then the
     * original definition is returned.
     * 
     * @return The parsed default value
     */
    public Object getParsedDefaultValue() {
        if ((defaultValue != null) && (defaultValue.length() > 0)) {
            try {
                switch (mappedTypeCode) {
                    case Types.TINYINT:
                    case Types.SMALLINT:
                        return Short.valueOf(getCleanDefaultValue());
                    case Types.INTEGER:
                        try {
                            return Integer.valueOf(getCleanDefaultValue());
                        } catch (NumberFormatException e) {
                            return Long.valueOf(getCleanDefaultValue());
                        }
                    case Types.BIGINT:
                        return Long.valueOf(getCleanDefaultValue());
                    case Types.DECIMAL:
                    case Types.NUMERIC:
                        return new BigDecimal(getCleanDefaultValue());
                    case Types.REAL:
                        return Float.valueOf(getCleanDefaultValue());
                    case Types.DOUBLE:
                    case Types.FLOAT:
                        return Double.valueOf(getCleanDefaultValue());
                    case Types.DATE:
                        return Date.valueOf(defaultValue);
                    case Types.TIME:
                        return Time.valueOf(defaultValue);
                    case Types.TIMESTAMP:
                        return Timestamp.valueOf(defaultValue);
                    case Types.BIT:
                        return FormatUtils.toBoolean(defaultValue);
                    default:
                        if (PlatformUtils.supportsJava14JdbcTypes()
                                && (mappedTypeCode == PlatformUtils.determineBooleanTypeCode())) {
                            return FormatUtils.toBoolean(defaultValue);
                        } else if (isTimestampWithTimezone()) {
                            return removeOuterParentheses(defaultValue);
                        }
                        break;
                }
            } catch (NumberFormatException ex) {
            } catch (IllegalArgumentException ex) {
            }
        }
        return defaultValue;
    }

    private String getCleanDefaultValue() {
        return defaultValue.replace("'", "");
    }

    private String removeOuterParentheses(String value) {
        String newValue = new String(value);
        if (newValue != null) {
            while (newValue.startsWith("(") && newValue.endsWith(")")) {
                newValue = newValue.substring(1, newValue.length() - 1);
            }
        }
        return newValue;
    }

    public void removePlatformColumn(String databaseName) {
        if (platformColumns != null) {
            platformColumns.remove(databaseName);
        }
    }

    public void addPlatformColumn(PlatformColumn platformColumn) {
        if (platformColumns == null) {
            platformColumns = new HashMap<String, PlatformColumn>();
        }
        platformColumns.put(platformColumn.getName(), platformColumn);
    }

    public Map<String, PlatformColumn> getPlatformColumns() {
        return platformColumns;
    }

    public PlatformColumn findPlatformColumn(String name) {
        PlatformColumn platformColumn = null;
        if (platformColumns != null) {
            platformColumn = platformColumns.get(name);
            if (platformColumn == null) {
                if (name.contains(DatabaseNamesConstants.MSSQL)) {
                    return findDifferentVersionPlatformColumn(DatabaseNamesConstants.MSSQL);
                } else if (name.contains(DatabaseNamesConstants.ORACLE)) {
                    return findDifferentVersionPlatformColumn(DatabaseNamesConstants.ORACLE);
                } else if (name.contains(DatabaseNamesConstants.POSTGRESQL)) {
                    return findDifferentVersionPlatformColumn(DatabaseNamesConstants.POSTGRESQL);
                } else if (name.contains(DatabaseNamesConstants.SQLANYWHERE)) {
                    return findDifferentVersionPlatformColumn(DatabaseNamesConstants.SQLANYWHERE);
                } else if (name.contains(DatabaseNamesConstants.FIREBIRD)) {
                    return findDifferentVersionPlatformColumn(DatabaseNamesConstants.FIREBIRD);
                } else if (name.contains(DatabaseNamesConstants.HSQLDB)) {
                    return findDifferentVersionPlatformColumn(DatabaseNamesConstants.HSQLDB);
                }
            }
        }
        return platformColumn;
    }

    private PlatformColumn findDifferentVersionPlatformColumn(String name) {
        if (platformColumns != null) {
            for (Entry<String, PlatformColumn> entry : platformColumns.entrySet()) {
                if (entry.getKey() != null && entry.getKey().contains(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public boolean anyPlatformColumnNameContains(String name) {
        if (platformColumns != null) {
            for (String platformColumnName : platformColumns.keySet()) {
                if (platformColumnName != null && platformColumnName.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean anyPlatformColumnTypeContains(String type) {
        if (platformColumns != null) {
            for (PlatformColumn platformColumn : platformColumns.values()) {
                if (platformColumn.getType() != null && platformColumn.getType().contains(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean allPlatformColumnNamesContain(String name) {
        if (platformColumns != null) {
            for (String platformColumnName : platformColumns.keySet()) {
                if (platformColumnName != null && !platformColumnName.contains(name)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Sets the default value of the column. Note that this expression will be used within quotation marks when generating the column, and thus is subject to
     * the conversion rules of the target database.
     * 
     * @param defaultValue
     *            The default value
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        if (platformColumns != null) {
            Collection<PlatformColumn> cols = platformColumns.values();
            for (PlatformColumn platformColumn : cols) {
                platformColumn.setDefaultValue(defaultValue);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object clone() throws CloneNotSupportedException {
        Column result = (Column) super.clone();
        result.name = name;
        result.javaName = javaName;
        result.primaryKey = primaryKey;
        result.required = required;
        result.autoIncrement = autoIncrement;
        result.mappedTypeCode = mappedTypeCode;
        result.mappedType = mappedType;
        result.size = size;
        result.defaultValue = defaultValue;
        result.scale = scale;
        result.size = size;
        result.sizeAsInt = sizeAsInt;
        if (platformColumns != null) {
            result.platformColumns = new HashMap<String, PlatformColumn>(platformColumns.size());
            for (Map.Entry<String, PlatformColumn> entry : platformColumns.entrySet()) {
                result.platformColumns.put(entry.getKey(), (PlatformColumn) entry.getValue().clone());
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (obj instanceof Column) {
            Column other = (Column) obj;
            EqualsBuilder comparator = new EqualsBuilder();
            // Note that this compares case sensitive
            comparator.append(name, other.name);
            comparator.append(primaryKey, other.primaryKey);
            comparator.append(required, other.required);
            comparator.append(autoIncrement, other.autoIncrement);
            comparator.append(mappedTypeCode, other.mappedTypeCode);
            comparator.append(getParsedDefaultValue(), other.getParsedDefaultValue());
            // comparing the size makes only sense for types where it is
            // relevant
            if ((mappedTypeCode == Types.NUMERIC) || (mappedTypeCode == Types.DECIMAL)) {
                comparator.append(size, other.size);
                comparator.append(scale, other.scale);
            } else if ((mappedTypeCode == Types.CHAR) || (mappedTypeCode == Types.VARCHAR)
                    || (mappedTypeCode == Types.BINARY) || (mappedTypeCode == Types.VARBINARY)) {
                comparator.append(size, other.size);
            }
            return comparator.isEquals();
        } else {
            return false;
        }
    }

    public boolean equalsByName(Column other) {
        return Strings.CI.equals(name, other.name) && primaryKey == other.primaryKey;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37);
        builder.append(name);
        builder.append(primaryKey);
        builder.append(required);
        builder.append(autoIncrement);
        builder.append(mappedTypeCode);
        builder.append(mappedType);
        builder.append(scale);
        builder.append(getParsedDefaultValue());
        if (!TypeMap.isNumericType(mappedTypeCode)) {
            builder.append(size);
        }
        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Column [name=");
        result.append(getName());
        result.append("; jdbcType=");
        result.append(getJdbcTypeName());
        result.append("; mappedType=");
        result.append(getMappedType());
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a verbose string representation of this column.
     * 
     * @return The string representation
     */
    public String toVerboseString() {
        StringBuilder result = new StringBuilder();
        result.append("Column [name=");
        result.append(getName());
        result.append("; javaName=");
        result.append(getJavaName());
        result.append("; type=");
        result.append(getMappedType());
        result.append("; typeCode=");
        result.append(getMappedTypeCode());
        result.append("; size=");
        result.append(getSize());
        result.append("; required=");
        result.append(isRequired());
        result.append("; primaryKey=");
        result.append(isPrimaryKey());
        result.append("; autoIncrement=");
        result.append(isAutoIncrement());
        result.append("; defaultValue=");
        result.append(getDefaultValue());
        result.append("; precisionRadix=");
        result.append(getPrecisionRadix());
        result.append("; scale=");
        result.append(getScale());
        result.append("]");
        return result.toString();
    }

    public void setJdbcTypeName(String jdbcTypeName) {
        this.jdbcTypeName = jdbcTypeName;
    }

    public String getJdbcTypeName() {
        return jdbcTypeName;
    }

    public boolean isDistributionKey() {
        return distributionKey;
    }

    public void setDistributionKey(boolean distributionKey) {
        this.distributionKey = distributionKey;
    }

    public final void setTypeCode(int typeCode) {
        this.setMappedTypeCode(typeCode);
        this.setJdbcTypeCode(typeCode);
    }

    public final void setJdbcTypeCode(int jdbcTypeCode) {
        this.jdbcTypeCode = jdbcTypeCode;
    }

    public int getJdbcTypeCode() {
        return jdbcTypeCode;
    }

    public boolean isTimestampWithTimezone() {
        return mappedTypeCode == ORACLE_TIMESTAMPLTZ || mappedTypeCode == ORACLE_TIMESTAMPTZ ||
                jdbcTypeCode == ORACLE_TIMESTAMPLTZ || jdbcTypeCode == ORACLE_TIMESTAMPTZ ||
                (jdbcTypeName != null && jdbcTypeName.equals("timestamptz"));
    }

    public boolean containsJdbcTypes() {
        return jdbcTypeCode != Integer.MIN_VALUE && jdbcTypeName != null;
    }
    //
    // public void setEnumValues(String[] enumValues) {
    // this.enumValues = enumValues;
    // }
    //
    // public String[] getEnumValues() {
    // return enumValues;
    // }
    //
    // public boolean isEnum() {
    // return enumValues != null && enumValues.length > 0;
    // }

    public int getCharOctetLength() {
        return charOctetLength;
    }

    public void setCharOctetLength(int charOctetLength) {
        this.charOctetLength = charOctetLength;
    }

    public int getPrimaryKeySequence() {
        return primaryKeySequence;
    }

    public void setPrimaryKeySequence(int primaryKeySequence) {
        this.primaryKeySequence = primaryKeySequence;
    }

    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }
}
