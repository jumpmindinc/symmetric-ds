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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class DatabaseTest {
    @Test
    public void testOrderingOfFourTables() {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        t1.addForeignKey(new ForeignKey("2", "2"));
        t2.addForeignKey(new ForeignKey("3", "3"));
        t3.addForeignKey(new ForeignKey("4", "4"));
        list.add(t2);
        list.add(t1);
        list.add(t4);
        list.add(t3);
        list = Database.sortByForeignKeys(list, null, null, null);
        assertTrue(list.toString(), list.indexOf(t4) < list.indexOf(t1));
        assertTrue(list.toString(), list.indexOf(t2) < list.indexOf(t1));
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t2));
        assertTrue(list.toString(), list.indexOf(t4) < list.indexOf(t3));
    }

    @Test
    public void testOrderingOfTenTables() {
        RelationsList list = new RelationsList(10);
        Table t0 = new Table("0");
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        Table t5 = new Table("5");
        Table t6 = new Table("6");
        Table t7 = new Table("7");
        Table t8 = new Table("8");
        Table t9 = new Table("9");
        t5.addForeignKey(new ForeignKey("4", "4"));
        t4.addForeignKey(new ForeignKey("3", "3"));
        t3.addForeignKey(new ForeignKey("2", "2"));
        list.add(t5);
        list.add(t7);
        list.add(t1);
        list.add(t4);
        list.add(t3);
        list.add(t6);
        list.add(t9);
        list.add(t0);
        list.add(t2);
        list.add(t8);
        list = Database.sortByForeignKeys(list, null, null, null);
        assertTrue(list.toString(), list.indexOf(t4) < list.indexOf(t5));
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t4));
        assertTrue(list.toString(), list.indexOf(t2) < list.indexOf(t3));
        assertTrue(list.toString(), list.indexOf(t2) < list.indexOf(t5));
    }

    @Test
    public void testCyclicalReferences() {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        t1.addForeignKey(new ForeignKey("2", "2"));
        t2.addForeignKey(new ForeignKey("3", "3"));
        t3.addForeignKey(new ForeignKey("1", "1"));
        list.add(t3);
        list.add(t2);
        list.add(t1);
        list = Database.sortByForeignKeys(list, null, null, null);
        // for now just make sure it doesn't blow up
    }

    @Test
    public void testMultipleParentsTables() {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        t1.addForeignKey(new ForeignKey("2", "2"));
        t2.addForeignKey(new ForeignKey("3", "3"));
        t2.addForeignKey(new ForeignKey("4", "4"));
        t1.addForeignKey(new ForeignKey("4", "4"));
        list.add(t2);
        list.add(t1);
        list.add(t4);
        list.add(t3);
        list = Database.sortByForeignKeys(list, null, null, null);
        assertTrue(list.toString(), list.indexOf(t2) < list.indexOf(t1));
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t2));
        assertTrue(list.toString(), list.indexOf(t4) < list.indexOf(t2));
    }

    @Test
    public void testSplitTreeTables() {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        t1.addForeignKey(new ForeignKey("2", "2"));
        t2.addForeignKey(new ForeignKey("3", "3"));
        t1.addForeignKey(new ForeignKey("3", "3"));
        list.add(t2);
        list.add(t1);
        list.add(t4);
        list.add(t3);
        list = Database.sortByForeignKeys(list, null, null, null);
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t4));
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t2));
        assertTrue(list.toString(), list.indexOf(t2) < list.indexOf(t1));
    }

    @Test
    public void testIndependentTreesSameTables() {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        t2.addForeignKey(new ForeignKey("3", "3"));
        t1.addForeignKey(new ForeignKey("2", "2"));
        t1.addForeignKey(new ForeignKey("4", "4"));
        list.add(t1);
        list.add(t2);
        list.add(t3);
        list.add(t4);
        list = Database.sortByForeignKeys(list, null, null, null);
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t4));
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t2));
        assertTrue(list.toString(), list.indexOf(t2) < list.indexOf(t1));
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t1));
    }

    @Test
    public void testSelfReferenceTables() {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        t1.addForeignKey(new ForeignKey("1", "1"));
        t1.addForeignKey(new ForeignKey("2", "2"));
        t2.addForeignKey(new ForeignKey("3", "3"));
        t3.addForeignKey(new ForeignKey("4", "4"));
        list.add(t1);
        list.add(t2);
        list.add(t3);
        list.add(t4);
        list = Database.sortByForeignKeys(list, null, null, null);
        assertTrue(list.toString(), list.indexOf(t4) < list.indexOf(t3));
        assertTrue(list.toString(), list.indexOf(t3) < list.indexOf(t2));
        assertTrue(list.toString(), list.indexOf(t2) < list.indexOf(t1));
    }

    @Test
    public void testMissingDepdendentTables() {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t4 = new Table("4");
        t1.addForeignKey(new ForeignKey("2", "2"));
        t1.addForeignKey(new ForeignKey("3", "3"));
        list.add(t1);
        list.add(t4);
        Map<Relation, Set<String>> missingDependencyMap = new HashMap<Relation, Set<String>>();
        list = Database.sortByForeignKeys(list, null, null, missingDependencyMap);
        assertTrue(list.toString(), list.indexOf(t1) < list.indexOf(t4));
        assertEquals(missingDependencyMap.size(), 1);
        assertTrue(missingDependencyMap.containsKey(t1));
        Iterator<String> i = missingDependencyMap.get(t1).iterator();
        assertEquals(i.next(), "2");
        assertEquals(i.next(), "3");
    }

    @Test
    public void testRemoveAllTablesExcept() throws Exception {
        Database database = new Database();
        database.addTable(new Table("SYM_DATA"));
        database.addTable(new Table("SYM_DOO_DADS"));
        assertEquals(2, database.getTableCount());
        database.removeAllTablesExcept("SYM_DATA");
        assertEquals(1, database.getTableCount());
        assertNotNull(database.findTable("SYM_DATA"));
    }

    @Test
    public void testDependentMapIndependent() throws Exception {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        list.add(t1);
        list.add(t2);
        list.add(t3);
        Map<Integer, Set<Relation>> dependencyMap = new HashMap<Integer, Set<Relation>>();
        list = Database.sortByForeignKeys(list, null, dependencyMap, null);
        assertEquals(dependencyMap.size(), 3);
    }

    @Test
    public void testDependentMapParentChild() throws Exception {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        t1.addForeignKey(new ForeignKey("2", "2"));
        list.add(t1);
        list.add(t2);
        list.add(t3);
        Map<Integer, Set<Relation>> dependencyMap = new HashMap<Integer, Set<Relation>>();
        list = Database.sortByForeignKeys(list, null, dependencyMap, null);
        assertTrue(dependencyMap.get(1).contains(t1));
        assertTrue(dependencyMap.get(1).contains(t2));
        assertTrue(dependencyMap.get(2).contains(t3));
    }

    @Test
    public void testDependentMapParentChildReverseOrder() throws Exception {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        t1.addForeignKey(new ForeignKey("2", "2"));
        list.add(t2);
        list.add(t1);
        list.add(t3);
        Map<Integer, Set<Relation>> dependencyMap = new HashMap<Integer, Set<Relation>>();
        list = Database.sortByForeignKeys(list, null, dependencyMap, null);
        assertTrue(dependencyMap.get(1).contains(t1));
        assertTrue(dependencyMap.get(1).contains(t2));
        assertTrue(dependencyMap.get(2).contains(t3));
    }

    @Test
    public void testDependentMapTwoGroups() throws Exception {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        Table t5 = new Table("5");
        Table t6 = new Table("6");
        t2.addForeignKey(new ForeignKey("1", "1"));
        t3.addForeignKey(new ForeignKey("1", "1"));
        t5.addForeignKey(new ForeignKey("4", "4"));
        t6.addForeignKey(new ForeignKey("4", "4"));
        list.add(t1);
        list.add(t2);
        list.add(t3);
        list.add(t4);
        list.add(t5);
        list.add(t6);
        Map<Integer, Set<Relation>> dependencyMap = new HashMap<Integer, Set<Relation>>();
        list = Database.sortByForeignKeys(list, null, dependencyMap, null);
        assertTrue(dependencyMap.get(1).contains(t1));
        assertTrue(dependencyMap.get(1).contains(t2));
        assertTrue(dependencyMap.get(1).contains(t3));
        assertTrue(dependencyMap.get(2).contains(t4));
        assertTrue(dependencyMap.get(2).contains(t5));
        assertTrue(dependencyMap.get(2).contains(t6));
    }

    @Test
    public void testDependentMapCircular() throws Exception {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        Table t5 = new Table("5");
        t4.addForeignKey(new ForeignKey("3", "3"));
        t3.addForeignKey(new ForeignKey("2", "2"));
        t4.addForeignKey(new ForeignKey("2", "2"));
        list.add(t1);
        list.add(t2);
        list.add(t3);
        list.add(t4);
        list.add(t5);
        Map<Integer, Set<Relation>> dependencyMap = new HashMap<Integer, Set<Relation>>();
        list = Database.sortByForeignKeys(list, null, dependencyMap, null);
        assertTrue(dependencyMap.get(1).contains(t1));
        assertTrue(dependencyMap.get(2).contains(t2));
        assertTrue(dependencyMap.get(2).contains(t3));
        assertTrue(dependencyMap.get(2).contains(t4));
        assertTrue(dependencyMap.get(3).contains(t5));
    }

    @Test
    public void testDependentMapMultipleParents() throws Exception {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        Table t5 = new Table("5");
        t2.addForeignKey(new ForeignKey("1", "1"));
        t3.addForeignKey(new ForeignKey("1", "1"));
        t3.addForeignKey(new ForeignKey("4", "4"));
        t5.addForeignKey(new ForeignKey("4", "4"));
        list.add(t1);
        list.add(t2);
        list.add(t3);
        list.add(t4);
        list.add(t5);
        Map<Integer, Set<Relation>> dependencyMap = new HashMap<Integer, Set<Relation>>();
        list = Database.sortByForeignKeys(list, null, dependencyMap, null);
        assertTrue(dependencyMap.get(1).contains(t1));
        assertTrue(dependencyMap.get(1).contains(t2));
        assertTrue(dependencyMap.get(1).contains(t3));
        assertTrue(dependencyMap.get(1).contains(t4));
        assertTrue(dependencyMap.get(1).contains(t5));
    }

    @Test
    public void testDependentMapMergeGroups() throws Exception {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        Table t5 = new Table("5");
        t2.addForeignKey(new ForeignKey("1", "1"));
        t3.addForeignKey(new ForeignKey("1", "1"));
        t3.addForeignKey(new ForeignKey("4", "4"));
        t3.addForeignKey(new ForeignKey("5", "5"));
        list.add(t1);
        list.add(t2);
        list.add(t4);
        list.add(t5);
        list.add(t3);
        Map<Integer, Set<Relation>> dependencyMap = new HashMap<Integer, Set<Relation>>();
        list = Database.sortByForeignKeys(list, null, dependencyMap, null);
        assertTrue(dependencyMap.get(1).contains(t1));
        assertTrue(dependencyMap.get(1).contains(t2));
        assertTrue(dependencyMap.get(1).contains(t3));
        assertTrue(dependencyMap.get(1).contains(t4));
        assertTrue(dependencyMap.get(1).contains(t5));
    }

    @Test
    public void testDependentMapOutOfOrder() throws Exception {
        RelationsList list = new RelationsList(4);
        Table t1 = new Table("1");
        Table t2 = new Table("2");
        Table t3 = new Table("3");
        Table t4 = new Table("4");
        Table t5 = new Table("5");
        t5.addForeignKey(new ForeignKey("1", "1"));
        t4.addForeignKey(new ForeignKey("1", "1"));
        list.add(t1);
        list.add(t3);
        list.add(t4);
        list.add(t2);
        list.add(t5);
        Map<Integer, Set<Relation>> dependencyMap = new HashMap<Integer, Set<Relation>>();
        list = Database.sortByForeignKeys(list, null, dependencyMap, null);
        assertTrue(dependencyMap.get(1).contains(t1));
        assertTrue(dependencyMap.get(1).contains(t4));
        assertTrue(dependencyMap.get(1).contains(t5));
        assertTrue(dependencyMap.get(2).contains(t3));
        assertTrue(dependencyMap.get(3).contains(t2));
    }
}
