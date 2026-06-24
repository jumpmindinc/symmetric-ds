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

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsTest {
    private Settings settings;

    @BeforeEach
    void setUp() {
        settings = new Settings();
    }

    private SqlHistory makeHistory(String sql, long epochMillis) {
        SqlHistory h = new SqlHistory();
        h.setSqlStatement(sql);
        h.setLastExecuteTime(new Date(epochMillis));
        return h;
    }

    @Test
    void defaultDelimiter_isSemicolon() {
        assertEquals(";", settings.getProperties().get(Settings.SQL_EXPLORER_DELIMITER));
    }

    @Test
    void defaultAutoCommit_isTrue() {
        assertTrue(settings.getProperties().is(Settings.SQL_EXPLORER_AUTO_COMMIT));
    }

    @Test
    void defaultMaxResults_is1000() {
        assertEquals("1000", settings.getProperties().get(Settings.SQL_EXPLORER_MAX_RESULTS));
    }

    @Test
    void defaultMaxHistory_is100() {
        assertEquals("100", settings.getProperties().get(Settings.SQL_EXPLORER_MAX_HISTORY));
    }

    @Test
    void defaultExcludeTablesRegex_matchesSym() {
        String regex = settings.getProperties().get(Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX);
        assertNotNull(regex);
        assertTrue("SYM_TRIGGER".matches(regex));
    }

    @Test
    void defaultPermissions_allTrue() {
        assertTrue(settings.isAllowQueries());
        assertTrue(settings.isAllowDml());
        assertTrue(settings.isAllowImport());
        assertTrue(settings.isAllowExport());
        assertTrue(settings.isAllowFill());
        assertTrue(settings.isAllowCompare());
        assertTrue(settings.isAllowRepair());
    }

    @Test
    void addSqlHistory_addsEntry() {
        settings.addSqlHistory(makeHistory("SELECT 1", 1000));
        assertEquals(1, settings.getSqlHistory().size());
    }

    @Test
    void addSqlHistory_multipleCalls_addsAll() {
        settings.addSqlHistory(makeHistory("SELECT 1", 1000));
        settings.addSqlHistory(makeHistory("SELECT 2", 2000));
        settings.addSqlHistory(makeHistory("SELECT 3", 3000));
        assertEquals(3, settings.getSqlHistory().size());
    }

    @Test
    void addSqlHistory_belowMaxSize_doesNotEvict() {
        settings.getProperties().put(Settings.SQL_EXPLORER_MAX_HISTORY, "5");
        for (int i = 0; i < 5; i++) {
            settings.addSqlHistory(makeHistory("SELECT " + i, i * 1000L));
        }
        assertEquals(5, settings.getSqlHistory().size());
    }

    @Test
    void addSqlHistory_whenListExceedsMaxSize_evictsMostRecentlyExecutedEntry() {
        settings.getProperties().put(Settings.SQL_EXPLORER_MAX_HISTORY, "1");
        SqlHistory h1 = makeHistory("SELECT 1", 1000);
        SqlHistory h2 = makeHistory("SELECT 2", 2000);
        SqlHistory h3 = makeHistory("SELECT 3", 3000);
        settings.addSqlHistory(h1);
        settings.addSqlHistory(h2);
        settings.addSqlHistory(h3);
        List<SqlHistory> history = settings.getSqlHistory();
        assertTrue(history.contains(h1));
        assertFalse(history.contains(h2));
        assertTrue(history.contains(h3));
    }

    @Test
    void getSqlHistory_withMatchingStatement_returnsEntry() {
        SqlHistory entry = makeHistory("SELECT 1", 1000);
        settings.addSqlHistory(entry);
        assertSame(entry, settings.getSqlHistory("SELECT 1"));
    }

    @Test
    void getSqlHistory_trimsWhitespace_beforeMatching() {
        settings.addSqlHistory(makeHistory("SELECT 1", 1000));
        assertNotNull(settings.getSqlHistory("  SELECT 1  "));
    }

    @Test
    void getSqlHistory_withNoMatch_returnsNull() {
        assertNull(settings.getSqlHistory("SELECT 99"));
    }

    @Test
    void getSqlHistory_withMultipleEntries_returnsCorrectOne() {
        SqlHistory h1 = makeHistory("SELECT 1", 1000);
        SqlHistory h2 = makeHistory("SELECT 2", 2000);
        settings.addSqlHistory(h1);
        settings.addSqlHistory(h2);
        assertSame(h2, settings.getSqlHistory("SELECT 2"));
        assertSame(h1, settings.getSqlHistory("SELECT 1"));
    }

    @Test
    void setAllowImport_false_reflectedInGetter() {
        settings.setAllowImport(false);
        assertFalse(settings.isAllowImport());
    }

    @Test
    void setAllowDml_false_reflectedInGetter() {
        settings.setAllowDml(false);
        assertFalse(settings.isAllowDml());
    }
}
