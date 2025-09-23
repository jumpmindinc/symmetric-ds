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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.Serializable;
import java.sql.DatabaseMetaData;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.commons.collections4.set.ListOrderedSet;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Represents a database foreign key.
 */
public class ForeignKey implements Cloneable, Serializable {
    public enum ForeignKeyAction {
        CASCADE("CASCADE"), NOACTION("NO ACTION"), SETNULL("SET NULL"), SETDEFAULT("SET DEFAULT"), RESTRICT("RESTRICT");

        private String foreignKeyActionName;

        private ForeignKeyAction(String foreignKeyActionName) {
            this.foreignKeyActionName = foreignKeyActionName;
        }

        public String getForeignKeyActionName() {
            return foreignKeyActionName;
        }
    }

    private static final long serialVersionUID = 1L;
    /** The name of the foreign key, may be <code>null</code>. */
    private String name;
    /** The target table. */
    private Table foreignTable;
    /** The name of the foreign table. */
    private String foreignTableName;
    /** The references between local and remote columns. */
    private ListOrderedSet<Reference> references = new ListOrderedSet<Reference>();
    /** Whether this foreign key has an associated auto-generated index. */
    private boolean autoIndexPresent;
    private String foreignTableCatalog;
    private String foreignTableSchema;
    private ForeignKeyAction onDeleteAction = ForeignKeyAction.NOACTION;
    private ForeignKeyAction onUpdateAction = ForeignKeyAction.NOACTION;

    /**
     * Creates a new foreign key object that has no name.
     */
    public ForeignKey() {
        this(null);
    }

    /**
     * Creates a new foreign key object.
     * 
     * @param name
     *            The name of the foreign key
     */
    public ForeignKey(String name) {
        this(name, null);
    }

    /**
     * Creates a new foreign key object.
     * 
     * @param name
     *            The name of the foreign key
     * @param foreignKeyTableName
     *            The name of the foreign key table
     */
    public ForeignKey(String name, String foreignKeyTableName) {
        this.name = name;
        this.foreignTableName = foreignKeyTableName;
    }

    /**
     * Returns the name of this foreign key.
     * 
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this foreign key.
     * 
     * @param name
     *            The name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the foreign table.
     * 
     * @return The foreign table
     */
    public Table getForeignTable() {
        return foreignTable;
    }

    /**
     * Sets the foreign table.
     * 
     * @param foreignTable
     *            The foreign table
     */
    public void setForeignTable(Table foreignTable) {
        this.foreignTable = foreignTable;
        this.foreignTableName = (foreignTable == null ? null : foreignTable.getName());
        this.foreignTableCatalog = (foreignTable == null ? null : foreignTable.getCatalog());
        this.foreignTableSchema = (foreignTable == null ? null : foreignTable.getSchema());
    }

    /**
     * Returns the name of the foreign table.
     * 
     * @return The table name
     */
    public String getForeignTableName() {
        return foreignTableName;
    }

    /**
     * Sets the name of the foreign table. Please note that you should not use this method when manually constructing or manipulating the database model. Rather
     * utilize the {@link #setForeignTable(Table)} method.
     * 
     * @param foreignTableName
     *            The table name
     */
    public void setForeignTableName(String foreignTableName) {
        if ((foreignTable != null) && !foreignTable.getName().equals(foreignTableName)) {
            foreignTable = null;
        }
        this.foreignTableName = foreignTableName;
    }

    /**
     * Returns the number of references.
     * 
     * @return The number of references
     */
    public int getReferenceCount() {
        return references.size();
    }

    /**
     * Returns the indicated reference.
     * 
     * @param idx
     *            The index
     * @return The reference
     */
    public Reference getReference(int idx) {
        return (Reference) references.get(idx);
    }

    /**
     * Returns the references.
     * 
     * @return The references
     */
    public Reference[] getReferences() {
        return (Reference[]) references.toArray(new Reference[references.size()]);
    }

    /**
     * Returns the first reference if it exists.
     * 
     * @return The first reference
     */
    public Reference getFirstReference() {
        return (Reference) (references.isEmpty() ? null : references.get(0));
    }

    /**
     * Adds a reference, ie. a mapping between a local column (in the table that owns this foreign key) and a remote column.
     * 
     * @param reference
     *            The reference to add
     */
    public void addReference(Reference reference) {
        if (reference != null) {
            for (int idx = 0; idx < references.size(); idx++) {
                Reference curRef = getReference(idx);
                if (curRef.getSequenceValue() > reference.getSequenceValue()) {
                    references.add(idx, reference);
                    return;
                }
            }
            references.add(reference);
        }
    }

    /**
     * Removes the given reference.
     * 
     * @param reference
     *            The reference to remove
     */
    public void removeReference(Reference reference) {
        if (reference != null) {
            references.remove(reference);
        }
    }

    /**
     * Removes the indicated reference.
     * 
     * @param idx
     *            The index of the reference to remove
     */
    public void removeReference(int idx) {
        references.remove(idx);
    }

    /**
     * Determines whether this foreign key uses the given column as a local column in a reference.
     * 
     * @param column
     *            The column to check
     * @return <code>true</code> if a reference uses the column as a local column
     */
    public boolean hasLocalColumn(Column column) {
        for (int idx = 0; idx < getReferenceCount(); idx++) {
            if (column.equals(getReference(idx).getLocalColumn())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether this foreign key uses the given column as a foreign column in a reference.
     * 
     * @param column
     *            The column to check
     * @return <code>true</code> if a reference uses the column as a foreign column
     */
    public boolean hasForeignColumn(Column column) {
        for (int idx = 0; idx < getReferenceCount(); idx++) {
            if (column.equals(getReference(idx).getForeignColumn())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether this foreign key has an auto-generated associated index.
     * 
     * @return <code>true</code> if an auto-generated index exists
     */
    public boolean isAutoIndexPresent() {
        return autoIndexPresent;
    }

    /**
     * Specifies whether this foreign key has an auto-generated associated index.
     * 
     * @param autoIndexPresent
     *            <code>true</code> if an auto-generated index exists
     */
    public void setAutoIndexPresent(boolean autoIndexPresent) {
        this.autoIndexPresent = autoIndexPresent;
    }

    /**
     * {@inheritDoc}
     */
    public Object clone() throws CloneNotSupportedException {
        ForeignKey result = (ForeignKey) super.clone();
        result.name = name;
        result.foreignTableName = foreignTableName;
        result.references = new ListOrderedSet<Reference>();
        result.foreignTableCatalog = foreignTableCatalog;
        result.foreignTableSchema = foreignTableSchema;
        result.onDeleteAction = getOnDeleteAction();
        result.onUpdateAction = getOnUpdateAction();
        for (Iterator<Reference> it = references.iterator(); it.hasNext();) {
            result.references.add((Reference) it.next().clone());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (obj instanceof ForeignKey) {
            ForeignKey otherFk = (ForeignKey) obj;
            // Note that this compares case sensitive
            // Note also that we can simply compare the references regardless of
            // their order
            // (which is irrelevant for fks) because they are contained in a set
            EqualsBuilder builder = new EqualsBuilder();
            if (isCheckName(otherFk)) {
                builder.append(name, otherFk.name);
            }
            builder.append(foreignTableName, otherFk.foreignTableName);
            // RESTRICT and NOACTION mean the same functionally, so change RESTRICT to NOACTION
            ForeignKeyAction otherForeignKeyDeleteAction = otherFk.getOnDeleteAction();
            if (otherForeignKeyDeleteAction == ForeignKeyAction.RESTRICT) {
                otherForeignKeyDeleteAction = ForeignKeyAction.NOACTION;
            }
            ForeignKeyAction myForeignKeyDeleteAction = getOnDeleteAction();
            if (myForeignKeyDeleteAction == ForeignKeyAction.RESTRICT) {
                myForeignKeyDeleteAction = ForeignKeyAction.NOACTION;
            }
            ForeignKeyAction otherForeignKeyUpdateAction = otherFk.getOnUpdateAction();
            if (otherForeignKeyUpdateAction == ForeignKeyAction.RESTRICT) {
                otherForeignKeyUpdateAction = ForeignKeyAction.NOACTION;
            }
            ForeignKeyAction myForeignKeyUpdateAction = getOnUpdateAction();
            if (myForeignKeyUpdateAction == ForeignKeyAction.RESTRICT) {
                myForeignKeyUpdateAction = ForeignKeyAction.NOACTION;
            }
            builder.append(myForeignKeyDeleteAction, otherForeignKeyDeleteAction);
            builder.append(myForeignKeyUpdateAction, otherForeignKeyUpdateAction);
            builder.append(references.size(), otherFk.references.size());
            for (int i = 0; i < references.size() && i < otherFk.references.size(); i++) {
                builder.append(references.get(i), otherFk.references.get(i));
            }
            return builder.isEquals();
        } else {
            return false;
        }
    }

    /**
     * Compares this foreign key to the given one while ignoring the case of identifiers.
     * 
     * @param otherFk
     *            The other foreign key
     * @return <code>true</code> if this foreign key is equal (ignoring case) to the given one
     */
    public boolean equalsIgnoreCase(ForeignKey otherFk) {
        boolean checkName = isCheckName(otherFk);
        if ((!checkName || name.equalsIgnoreCase(otherFk.name))
                && foreignTableName.equalsIgnoreCase(otherFk.foreignTableName)) {
            // RESTRICT and NOACTION mean the same functionally, so change RESTRICT to NOACTION
            ForeignKeyAction otherForeignKeyDeleteAction = otherFk.getOnDeleteAction();
            if (otherForeignKeyDeleteAction == ForeignKeyAction.RESTRICT) {
                otherForeignKeyDeleteAction = ForeignKeyAction.NOACTION;
            }
            ForeignKeyAction myForeignKeyDeleteAction = getOnDeleteAction();
            if (myForeignKeyDeleteAction == ForeignKeyAction.RESTRICT) {
                myForeignKeyDeleteAction = ForeignKeyAction.NOACTION;
            }
            if (otherForeignKeyDeleteAction != myForeignKeyDeleteAction) {
                return false;
            }
            ForeignKeyAction otherForeignKeyUpdateAction = otherFk.getOnUpdateAction();
            if (otherForeignKeyUpdateAction == ForeignKeyAction.RESTRICT) {
                otherForeignKeyUpdateAction = ForeignKeyAction.NOACTION;
            }
            ForeignKeyAction myForeignKeyUpdateAction = getOnUpdateAction();
            if (myForeignKeyUpdateAction == ForeignKeyAction.RESTRICT) {
                myForeignKeyUpdateAction = ForeignKeyAction.NOACTION;
            }
            if (otherForeignKeyUpdateAction != myForeignKeyUpdateAction) {
                return false;
            }
            HashSet<Reference> otherRefs = new HashSet<Reference>();
            otherRefs.addAll(otherFk.references);
            for (Iterator<?> it = references.iterator(); it.hasNext();) {
                Reference curLocalRef = (Reference) it.next();
                boolean found = false;
                for (Iterator<?> otherIt = otherRefs.iterator(); otherIt.hasNext();) {
                    Reference curOtherRef = (Reference) otherIt.next();
                    if (curLocalRef.equalsIgnoreCase(curOtherRef)) {
                        otherIt.remove();
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return otherRefs.isEmpty();
        } else {
            return false;
        }
    }

    private boolean isCheckName(ForeignKey otherFk) {
        return name != null && name.length() > 0 && otherFk.name != null
                && otherFk.name.length() > 0;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37).append(foreignTableName).append(
                references);
        if (isNotBlank(name)) {
            builder.append(name);
        }
        // RESTRICT and NOACTION mean the same functionally, so change RESTRICT to NOACTION
        ForeignKeyAction myForeignKeyDeleteAction = getOnDeleteAction();
        if (myForeignKeyDeleteAction == ForeignKeyAction.RESTRICT) {
            myForeignKeyDeleteAction = ForeignKeyAction.NOACTION;
        }
        ForeignKeyAction myForeignKeyUpdateAction = getOnUpdateAction();
        if (myForeignKeyUpdateAction == ForeignKeyAction.RESTRICT) {
            myForeignKeyUpdateAction = ForeignKeyAction.NOACTION;
        }
        builder.append(myForeignKeyDeleteAction);
        builder.append(myForeignKeyUpdateAction);
        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Foreign key [");
        if ((getName() != null) && (getName().length() > 0)) {
            result.append("name=");
            result.append(getName());
            result.append("; ");
        }
        result.append("foreign table=");
        result.append(getForeignTableName());
        result.append("; ");
        if (getOnDeleteAction() != ForeignKeyAction.RESTRICT && getOnDeleteAction() != ForeignKeyAction.NOACTION) {
            result.append("ON DELETE " + getOnDeleteAction().getForeignKeyActionName()).append("; ");
        }
        if (getOnUpdateAction() != ForeignKeyAction.RESTRICT && getOnUpdateAction() != ForeignKeyAction.NOACTION) {
            result.append("ON UPDATE " + getOnUpdateAction().getForeignKeyActionName()).append(";");
        }
        result.append(getReferenceCount());
        result.append(" references]");
        return result.toString();
    }

    /**
     * Returns a verbose string representation of this foreign key.
     * 
     * @return The string representation
     */
    public String toVerboseString() {
        StringBuilder result = new StringBuilder();
        result.append("Foreign key [");
        if ((getName() != null) && (getName().length() > 0)) {
            result.append("name=");
            result.append(getName());
            result.append("; ");
        }
        result.append("foreign table=");
        result.append(getForeignTableName());
        result.append(";");
        if (getOnDeleteAction() != ForeignKeyAction.RESTRICT && getOnDeleteAction() != ForeignKeyAction.NOACTION) {
            result.append(" ON DELETE " + getOnDeleteAction().getForeignKeyActionName());
        }
        if (getOnUpdateAction() != ForeignKeyAction.RESTRICT && getOnUpdateAction() != ForeignKeyAction.NOACTION) {
            result.append(" ON UPDATE " + getOnUpdateAction().getForeignKeyActionName());
        }
        result.append("] references:");
        for (int idx = 0; idx < getReferenceCount(); idx++) {
            result.append(" ");
            result.append(getReference(idx));
        }
        return result.toString();
    }

    public String getForeignTableCatalog() {
        return foreignTableCatalog;
    }

    public void setForeignTableCatalog(String foreignTableCatalog) {
        this.foreignTableCatalog = foreignTableCatalog;
    }

    public String getForeignTableSchema() {
        return foreignTableSchema;
    }

    public void setForeignTableSchema(String foreignTableSchema) {
        this.foreignTableSchema = foreignTableSchema;
    }

    public ForeignKeyAction getOnDeleteAction() {
        return onDeleteAction;
    }

    public void setOnDeleteAction(ForeignKeyAction onDeleteAction) {
        this.onDeleteAction = onDeleteAction;
    }

    public ForeignKeyAction getOnUpdateAction() {
        return onUpdateAction;
    }

    public void setOnUpdateAction(ForeignKeyAction onUpdateAction) {
        this.onUpdateAction = onUpdateAction;
    }

    public static ForeignKeyAction getForeignKeyAction(short importedKeyAction) {
        switch (importedKeyAction) {
            case DatabaseMetaData.importedKeyCascade:
                return ForeignKeyAction.CASCADE;
            case DatabaseMetaData.importedKeyNoAction:
                return ForeignKeyAction.NOACTION;
            case DatabaseMetaData.importedKeyRestrict:
                return ForeignKeyAction.RESTRICT;
            case DatabaseMetaData.importedKeySetDefault:
                return ForeignKeyAction.SETDEFAULT;
            case DatabaseMetaData.importedKeySetNull:
                return ForeignKeyAction.SETNULL;
            default:
                return ForeignKeyAction.NOACTION;
        }
    }

    public static ForeignKeyAction getForeignKeyActionByForeignKeyActionName(String foreignKeyActionName) throws IllegalArgumentException {
        for (ForeignKeyAction action : ForeignKeyAction.values()) {
            if (Strings.CS.equals(foreignKeyActionName, action.getForeignKeyActionName())) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown ForeignKeyAction: " + foreignKeyActionName);
    }
}
