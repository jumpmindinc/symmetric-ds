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
package org.jumpmind.db.alter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.UniqueIndex;
import org.jumpmind.db.platform.DatabaseInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ModelComparatorTest {
    private ArrayList<IModelChange> changeList = new ArrayList<IModelChange>();
    private Table sourceTable = new Table("test");
    private Table targetTable = new Table("test");
    private static IIndex nonUniqueIndexLowercase;
    private static IIndex uniqueIndexLowercase;
    private static IIndex nonUniqueIndexLowercaseColumnUppercase;
    private static IIndex uniqueIndexLowercaseColumnUppercase;
    private static IIndex nonUniqueIndexUppercase;
    private static IIndex uniqueIndexUppercase;

    @BeforeAll
    public static void setUpAll() {
        nonUniqueIndexLowercase = new NonUniqueIndex("nonunique");
        nonUniqueIndexLowercase.addColumn(new IndexColumn("nonuniquecolumn"));
        uniqueIndexLowercase = new UniqueIndex("unique");
        uniqueIndexLowercase.addColumn(new IndexColumn("uniquecolumn"));
        nonUniqueIndexLowercaseColumnUppercase = new NonUniqueIndex("nonunique");
        nonUniqueIndexLowercaseColumnUppercase.addColumn(new IndexColumn("NONUNIQUECOLUMN"));
        uniqueIndexLowercaseColumnUppercase = new UniqueIndex("unique");
        uniqueIndexLowercaseColumnUppercase.addColumn(new IndexColumn("UNIQUECOLUMN"));
        nonUniqueIndexUppercase = new NonUniqueIndex("NONUNIQUE");
        nonUniqueIndexUppercase.addColumn(new IndexColumn("NONUNIQUECOLUMN"));
        uniqueIndexUppercase = new UniqueIndex("UNIQUE");
        uniqueIndexUppercase.addColumn(new IndexColumn("UNIQUECOLUMN"));
    }

    @BeforeEach
    public void setUpEach() {
        changeList.clear();
        sourceTable.removeAllIndexes();
        targetTable.removeAllIndexes();
    }

    @Test
    public void testDetectIndexChangesCaseSensitive() {
        ModelComparator modelComparator = new ModelComparator(null, new DatabaseInfo(), true);
        targetTable.addIndex(nonUniqueIndexLowercase);
        targetTable.addIndex(uniqueIndexLowercase);
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(2, changeList.size());
        assertTrue(changeList.stream().anyMatch(c -> c instanceof AddIndexChange && ((AddIndexChange) c).getNewIndex().equals(nonUniqueIndexLowercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof AddIndexChange && ((AddIndexChange) c).getNewIndex().equals(uniqueIndexLowercase)));
        changeList.clear();
        sourceTable.addIndex(nonUniqueIndexUppercase);
        sourceTable.addIndex(uniqueIndexUppercase);
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(4, changeList.size());
        assertTrue(changeList.stream().anyMatch(c -> c instanceof AddIndexChange && ((AddIndexChange) c).getNewIndex().equals(nonUniqueIndexLowercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof AddIndexChange && ((AddIndexChange) c).getNewIndex().equals(uniqueIndexLowercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof RemoveIndexChange && ((RemoveIndexChange) c).getIndex().equals(nonUniqueIndexUppercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof RemoveIndexChange && ((RemoveIndexChange) c).getIndex().equals(uniqueIndexUppercase)));
        changeList.clear();
        sourceTable.removeAllIndexes();
        sourceTable.addIndex(nonUniqueIndexLowercaseColumnUppercase);
        sourceTable.addIndex(uniqueIndexLowercaseColumnUppercase);
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(4, changeList.size());
        assertTrue(changeList.stream().anyMatch(c -> c instanceof AddIndexChange && ((AddIndexChange) c).getNewIndex().equals(nonUniqueIndexLowercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof AddIndexChange && ((AddIndexChange) c).getNewIndex().equals(uniqueIndexLowercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof RemoveIndexChange && ((RemoveIndexChange) c).getIndex().equals(
                nonUniqueIndexLowercaseColumnUppercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof RemoveIndexChange && ((RemoveIndexChange) c).getIndex().equals(
                uniqueIndexLowercaseColumnUppercase)));
        changeList.clear();
        sourceTable.removeAllIndexes();
        sourceTable.addIndex(nonUniqueIndexLowercase);
        sourceTable.addIndex(uniqueIndexLowercase);
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(0, changeList.size());
        targetTable.removeAllIndexes();
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(2, changeList.size());
        assertTrue(changeList.stream().anyMatch(c -> c instanceof RemoveIndexChange && ((RemoveIndexChange) c).getIndex().equals(nonUniqueIndexLowercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof RemoveIndexChange && ((RemoveIndexChange) c).getIndex().equals(uniqueIndexLowercase)));
    }

    @Test
    public void testDetectIndexChangesCaseInsensitive() {
        ModelComparator modelComparator = new ModelComparator(null, new DatabaseInfo(), false);
        targetTable.addIndex(nonUniqueIndexLowercase);
        targetTable.addIndex(uniqueIndexLowercase);
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(2, changeList.size());
        assertTrue(changeList.stream().anyMatch(c -> c instanceof AddIndexChange && ((AddIndexChange) c).getNewIndex().equals(nonUniqueIndexLowercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof AddIndexChange && ((AddIndexChange) c).getNewIndex().equals(uniqueIndexLowercase)));
        changeList.clear();
        sourceTable.addIndex(nonUniqueIndexUppercase);
        sourceTable.addIndex(uniqueIndexUppercase);
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(0, changeList.size());
        sourceTable.removeAllIndexes();
        sourceTable.addIndex(nonUniqueIndexLowercaseColumnUppercase);
        sourceTable.addIndex(uniqueIndexLowercaseColumnUppercase);
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(0, changeList.size());
        sourceTable.removeAllIndexes();
        sourceTable.addIndex(nonUniqueIndexLowercase);
        sourceTable.addIndex(uniqueIndexLowercase);
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(0, changeList.size());
        targetTable.removeAllIndexes();
        modelComparator.detectIndexChanges(null, sourceTable, null, targetTable, changeList);
        assertEquals(2, changeList.size());
        assertTrue(changeList.stream().anyMatch(c -> c instanceof RemoveIndexChange && ((RemoveIndexChange) c).getIndex().equals(nonUniqueIndexLowercase)));
        assertTrue(changeList.stream().anyMatch(c -> c instanceof RemoveIndexChange && ((RemoveIndexChange) c).getIndex().equals(uniqueIndexLowercase)));
    }
}
