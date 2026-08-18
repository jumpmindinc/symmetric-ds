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

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;

/**
 * Represents any named object in the database schema.
 */
public abstract class SchemaObject implements Serializable, Comparable<SchemaObject> {
    private static final long serialVersionUID = 1L;
    protected String catalog = null;
    protected String schema = null;
    protected String name = null;
    protected String description = null;
    protected String type = null;
    protected String fullyQualifiedName;
    protected String fullyQualifiedNameLowerCase;
    protected String nameLowerCase;

    protected SchemaObject() {
    }

    protected SchemaObject(String name) {
        this(null, null, name);
    }

    protected SchemaObject(String catalog, String schema, String name) {
        this.catalog = catalog;
        this.schema = schema;
        this.name = name;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
        this.fullyQualifiedName = this.fullyQualifiedNameLowerCase = null;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
        this.fullyQualifiedName = this.fullyQualifiedNameLowerCase = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.fullyQualifiedName = this.fullyQualifiedNameLowerCase = null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFullyQualifiedName() {
        if (fullyQualifiedName == null) {
            fullyQualifiedName = getFullyQualifiedName(catalog, schema, name, null, ".", ".");
        }
        return fullyQualifiedName;
    }

    public String getFullyQualifiedNameLowerCase() {
        if (fullyQualifiedNameLowerCase == null) {
            fullyQualifiedNameLowerCase = getFullyQualifiedName().toLowerCase();
        }
        return fullyQualifiedNameLowerCase;
    }

    public String getFullyQualifiedName(String quote) {
        return getFullyQualifiedName(catalog, schema, name, quote, ".", ".");
    }

    public String getNameLowerCase() {
        if (nameLowerCase == null) {
            nameLowerCase = getName().toLowerCase();
        }
        return nameLowerCase;
    }

    public String getQualifiedName(String quoteString, String catalogSeparator, String schemaSeparator) {
        return getFullyQualifiedName(catalog, schema, name, quoteString, catalogSeparator, schemaSeparator);
    }

    public String getQualifiedName() {
        return getQualifiedName("", ".", ".");
    }

    public String getQualifiedPrefix(String quoteString, String catalogSeparator, String schemaSeparator) {
        return getFullyQualifiedPrefix(new StringBuilder(), catalog, schema, quoteString, catalogSeparator, schemaSeparator);
    }

    public static String getFullyQualifiedName(String catalogName, String schemaName, String objectName) {
        return getFullyQualifiedName(catalogName, schemaName, objectName, null, ".", ".");
    }

    public static String getFullyQualifiedName(String catalogName, String schemaName, String objectName,
            String quoteString, String catalogSeparator, String schemaSeparator) {
        boolean hasCatalog = StringUtils.isNotBlank(catalogName);
        boolean hasSchema = StringUtils.isNotBlank(schemaName);
        if (objectName != null && (quoteString == null || quoteString.isEmpty())) {
            if (hasCatalog && hasSchema) {
                return catalogName + catalogSeparator + schemaName + schemaSeparator + objectName;
            } else if (hasSchema) {
                return schemaName + schemaSeparator + objectName;
            } else if (hasCatalog) {
                return catalogName + catalogSeparator + objectName;
            }
            return objectName;
        }
        String quote = quoteString == null ? "" : quoteString;
        int catalogLength = hasCatalog ? catalogName.length() : 0;
        int schemaLength = hasSchema ? schemaName.length() : 0;
        int objectNameLength = objectName == null ? 4 : objectName.length();
        StringBuilder sb = new StringBuilder(catalogLength + schemaLength + objectNameLength + 6 * quote.length() + 8);
        if (hasCatalog) {
            sb.append(quote).append(catalogName).append(quote).append(catalogSeparator);
        }
        if (hasSchema) {
            sb.append(quote).append(schemaName).append(quote).append(schemaSeparator);
        }
        sb.append(quote).append(objectName).append(quote);
        return sb.toString();
    }

    public static String getFullyQualifiedPrefix(String catalogName, String schemaName) {
        return getFullyQualifiedPrefix(new StringBuilder(), catalogName, schemaName, null, ".", ".");
    }

    public static String getFullyQualifiedPrefix(String catalogName, String schemaName,
            String quoteString, String catalogSeparator, String schemaSeparator) {
        return getFullyQualifiedPrefix(new StringBuilder(), catalogName, schemaName, quoteString, catalogSeparator, schemaSeparator);
    }

    public static String getFullyQualifiedPrefix(StringBuilder sb, String catalogName, String schemaName,
            String quoteString, String catalogSeparator, String schemaSeparator) {
        if (quoteString == null) {
            quoteString = "";
        }
        if (!StringUtils.isBlank(catalogName)) {
            sb.append(quoteString).append(catalogName).append(quoteString).append(catalogSeparator);
        }
        if (!StringUtils.isBlank(schemaName)) {
            sb.append(quoteString).append(schemaName).append(quoteString).append(schemaSeparator);
        }
        return sb.toString();
    }

    @Override
    public int compareTo(SchemaObject o) {
        return this.getFullyQualifiedName().compareTo(o.getFullyQualifiedName());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SchemaObject other) {
            return getFullyQualifiedName().equals(other.getFullyQualifiedName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getFullyQualifiedName().hashCode();
    }
}
