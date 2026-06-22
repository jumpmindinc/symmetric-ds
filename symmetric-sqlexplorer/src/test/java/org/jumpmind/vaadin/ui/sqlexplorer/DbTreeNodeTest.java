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
package org.jumpmind.vaadin.ui.sqlexplorer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class DbTreeNodeTest {
    private DbTreeNode node(String name, String type, DbTreeNode parent) {
        DbTreeNode n = new DbTreeNode();
        n.setName(name);
        n.setType(type);
        n.setParent(parent);
        return n;
    }

    private DbTreeNode node(String name, String type) {
        return node(name, type, null);
    }

    @Test
    void hasChildren_withNoChildren_returnsFalse() {
        assertFalse(node("root", "database").hasChildren());
    }

    @Test
    void hasChildren_withChildren_returnsTrue() {
        DbTreeNode root = node("root", "database");
        root.getChildren().add(node("child", "table", root));
        assertTrue(root.hasChildren());
    }

    @Test
    void find_self_returnsSelf() {
        DbTreeNode root = node("root", "database");
        DbTreeNode searchFor = node("root", "database");
        assertSame(root, root.find(searchFor));
    }

    @Test
    void find_directChild_returnsChild() {
        DbTreeNode root = node("root", "database");
        DbTreeNode child = node("orders", "table", root);
        root.getChildren().add(child);
        DbTreeNode searchFor = node("orders", "table", root);
        assertSame(child, root.find(searchFor));
    }

    @Test
    void find_grandchild_returnsGrandchild() {
        DbTreeNode root = node("root", "database");
        DbTreeNode child = node("schema", "schema", root);
        DbTreeNode grandchild = node("orders", "table", child);
        root.getChildren().add(child);
        child.getChildren().add(grandchild);
        DbTreeNode searchFor = node("orders", "table", child);
        assertSame(grandchild, root.find(searchFor));
    }

    @Test
    void find_nonExistentNode_returnsNull() {
        DbTreeNode root = node("root", "database");
        root.getChildren().add(node("orders", "table", root));
        assertNull(root.find(node("nonexistent", "table", root)));
    }

    @Test
    void find_emptyTree_returnsNullForNonSelf() {
        DbTreeNode root = node("root", "database");
        assertNull(root.find(node("other", "table")));
    }

    @Test
    void delete_directChild_removesAndReturnsTrue() {
        DbTreeNode root = node("root", "database");
        DbTreeNode child = node("orders", "table", root);
        root.getChildren().add(child);
        DbTreeNode toDelete = node("orders", "table", root);
        assertTrue(root.delete(toDelete));
        assertFalse(root.hasChildren());
    }

    @Test
    void delete_grandchild_removesAndReturnsTrue() {
        DbTreeNode root = node("root", "database");
        DbTreeNode child = node("schema", "schema", root);
        DbTreeNode grandchild = node("orders", "table", child);
        root.getChildren().add(child);
        child.getChildren().add(grandchild);
        DbTreeNode toDelete = node("orders", "table", child);
        assertTrue(root.delete(toDelete));
        assertFalse(child.hasChildren());
    }

    @Test
    void delete_nonExistentNode_returnsFalse() {
        DbTreeNode root = node("root", "database");
        root.getChildren().add(node("orders", "table", root));
        assertFalse(root.delete(node("nonexistent", "table", root)));
    }

    @Test
    void delete_onLeafNode_returnsFalse() {
        DbTreeNode root = node("root", "database");
        assertFalse(root.delete(node("anything", "table")));
    }

    @Test
    void findTreeNodeNamesOfType_findsMatchingNodesInSubtree() {
        DbTreeNode root = node("root", "database");
        DbTreeNode t1 = node("orders", "table", root);
        DbTreeNode t2 = node("customers", "table", root);
        DbTreeNode schema = node("public", "schema", root);
        root.getChildren().add(t1);
        root.getChildren().add(t2);
        root.getChildren().add(schema);
        List<String> tableNames = root.findTreeNodeNamesOfType("table");
        assertEquals(2, tableNames.size());
        assertTrue(tableNames.contains("orders"));
        assertTrue(tableNames.contains("customers"));
    }

    @Test
    void findTreeNodeNamesOfType_withNoMatches_returnsEmptyList() {
        DbTreeNode root = node("root", "database");
        root.getChildren().add(node("orders", "table", root));
        assertTrue(root.findTreeNodeNamesOfType("trigger").isEmpty());
    }

    @Test
    void findTreeNodeNamesOfType_includesSelfIfMatchingType() {
        DbTreeNode root = node("root", "database");
        List<String> names = root.findTreeNodeNamesOfType("database");
        assertEquals(1, names.size());
        assertEquals("root", names.get(0));
    }

    @Test
    void equals_sameNameTypeAndParent_areEqual() {
        DbTreeNode parent = node("parent", "schema");
        DbTreeNode a = node("orders", "table", parent);
        DbTreeNode b = node("orders", "table", parent);
        assertEquals(a, b);
    }

    @Test
    void equals_differentName_notEqual() {
        assertNotEquals(node("orders", "table"), node("customers", "table"));
    }

    @Test
    void equals_differentType_notEqual() {
        assertNotEquals(node("orders", "table"), node("orders", "trigger"));
    }

    @Test
    void hashCode_equalObjects_sameHashCode() {
        DbTreeNode parent = node("parent", "schema");
        DbTreeNode a = node("orders", "table", parent);
        DbTreeNode b = node("orders", "table", parent);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
