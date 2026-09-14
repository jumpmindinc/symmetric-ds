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
package org.jumpmind.vaadin.ui.sqlexplorer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.properties.TypedProperties;

import com.vaadin.flow.component.icon.VaadinIcon;

public class DbTreeNode implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String name;
    protected String description;
    protected String type;
    protected VaadinIcon icon;
    protected TypedProperties properties = new TypedProperties();
    protected DbTreeNode parent;
    protected List<DbTreeNode> children = new ArrayList<DbTreeNode>();
    protected DbTree dbTree;

    public DbTreeNode(DbTree dbTree, String name, String type, VaadinIcon icon,
            DbTreeNode parent) {
        this.name = name;
        this.type = type;
        this.parent = parent;
        this.icon = icon;
        this.dbTree = dbTree;
    }

    public DbTreeNode(DbTree dbTree, String type, DbTreeNode parent) {
        this.name = "<empty>";
        this.type = type;
        this.parent = parent;
        this.dbTree = dbTree;
    }

    public DbTreeNode() {
    }

    public void setParent(DbTreeNode parent) {
        this.parent = parent;
    }

    public DbTreeNode getParent() {
        return parent;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean hasChildren() {
        return children.size() > 0;
    }

    public List<DbTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<DbTreeNode> children) {
        this.children = children;
    }

    public DbTreeNode find(DbTreeNode node) {
        if (this.equals(node)) {
            return this;
        } else if (children != null && children.size() > 0) {
            Iterator<DbTreeNode> it = children.iterator();
            while (it.hasNext()) {
                DbTreeNode child = (DbTreeNode) it.next();
                if (child.equals(node)) {
                    return child;
                }
            }
            for (DbTreeNode child : children) {
                DbTreeNode target = child.find(node);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    protected Table getTableFor() {
        IDb db = dbTree.getDbForNode(this);
        IDatabasePlatform platform = db.getPlatform();
        TypedProperties nodeProperties = getProperties();
        return (Table) platform.getRelationFromCache(
                nodeProperties.get(DbTree.PROPERTY_CATALOG_NAME),
                nodeProperties.get(DbTree.PROPERTY_SCHEMA_NAME), name, false);
    }

    public boolean delete(DbTreeNode node) {
        if (children != null && children.size() > 0) {
            Iterator<DbTreeNode> it = children.iterator();
            while (it.hasNext()) {
                DbTreeNode child = (DbTreeNode) it.next();
                if (child.equals(node)) {
                    it.remove();
                    return true;
                }
            }
            for (DbTreeNode child : children) {
                if (child.delete(node)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setIcon(VaadinIcon icon) {
        this.icon = icon;
    }

    public VaadinIcon getIcon() {
        return icon;
    }

    public List<String> findTreeNodeNamesOfType(String type) {
        List<String> names = new ArrayList<String>();
        if (this.getType().equals(type)) {
            names.add(getName());
        }
        findTreeNodeNamesOfType(type, getChildren(), names);
        return names;
    }

    public void findTreeNodeNamesOfType(String type,
            List<DbTreeNode> treeNodes, List<String> names) {
        for (DbTreeNode treeNode : treeNodes) {
            if (treeNode.getType().equals(type)) {
                names.add(treeNode.getName());
            }
            findTreeNodeNamesOfType(type, treeNode.getChildren(), names);
        }
    }

    public void setProperties(TypedProperties properties) {
        this.properties = properties;
    }

    public TypedProperties getProperties() {
        return properties;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((parent == null) ? 0 : parent.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DbTreeNode other = (DbTreeNode) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        if (parent == null) {
            if (other.parent != null) {
                return false;
            }
        } else if (!parent.equals(other.parent)) {
            return false;
        }
        return true;
    }
}
