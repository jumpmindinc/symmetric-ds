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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDdlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;

import de.f0rce.ace.AceEditor;

public class SqlSuggester {
    private static final Logger logger = LoggerFactory.getLogger(SqlSuggester.class);
    public final static String[] TABLE_TYPES = new String[] { "TABLE",
            "SYSTEM TABLE", "SYSTEM VIEW" };
    public final static String[] QUERY_INITIALIZERS = { "alter ", "create ", "delete ", "drop ", "insert ",
            "select ", "truncate ", "update " };
    private Map<String, List<String>> tableNameCache;
    private Map<String, List<String>> columnNameCache;
    private Map<String, List<String>> schemaNameCache;
    private List<String> catalogNameCache;
    private IDdlReader reader;
    private AceEditor editor;
    private String text;
    private int cursor;
    private String currentWord;
    private boolean addPeriod;
    private boolean setCursorPosition;
    private boolean enabled;
    private List<String> referencedTableNames;
    private Map<String, String> aliases;

    public SqlSuggester(IDb db, AceEditor editor) {
        super();
        this.reader = db.getPlatform().getDdlReader();
        this.editor = editor;
        this.enabled = true;
        this.tableNameCache = new HashMap<String, List<String>>();
        this.columnNameCache = new HashMap<String, List<String>>();
        this.schemaNameCache = new HashMap<String, List<String>>();
        this.catalogNameCache = new ArrayList<String>();
        this.editor.setAutoComplete(true);
        this.editor.addSyncCompletedListener(event -> {
            if (addPeriod) {
                this.editor.addTextAtCurrentPosition(".");
                addPeriod = false;
                this.editor.sync();
                setCursorPosition = true;
            }
        });
        this.editor.addValueChangeListener(event -> updateSuggestions(event.getValue(), getCursorPosition()));
        this.editor.addSelectionChangeListener(event -> {
            if (setCursorPosition && event.getSelection().getEndIndex() != cursor) {
                this.editor.setCursorPosition(cursor);
            }
            setCursorPosition = false;
        });
        Shortcuts.addShortcutListener(this.editor, () -> {
            addPeriod = true;
            this.editor.sync();
        }, Key.PERIOD).listenOn(editor);
    }

    public void updateSuggestions(String text, int cursor) {
        this.editor.addStaticWordCompleter(new ArrayList<String>());
        if (enabled) {
            try {
                this.text = text;
                this.cursor = cursor;
                this.currentWord = getCurrentWord();
                this.referencedTableNames = getReferencedTableNames();
                this.aliases = getAliases();
                List<String> suggestions = new ArrayList<String>();
                int lastDeliminatorIndex = getLastDeliminatorIndex();
                if (lastDeliminatorIndex > 0
                        && text.charAt(lastDeliminatorIndex) == '.'
                        && isSqlIdentifier(text.charAt(lastDeliminatorIndex - 1))) {
                    suggestions.addAll(getHierarchySuggestions());
                } else if (lastDeliminatorIndex > 0 && text.charAt(lastDeliminatorIndex - 1) != '.'
                        || (lastDeliminatorIndex <= 0)) {
                    suggestions.addAll(getAliasSuggestions());
                    for (String fullTableName : referencedTableNames) {
                        String[] fullNameParts = parseFullName(fullTableName);
                        suggestions.addAll(getColumnNameSuggestions(fullNameParts[2],
                                fullNameParts[1], fullNameParts[0]));
                    }
                    suggestions.addAll(getCatalogNameSuggestions());
                    suggestions.addAll(getSchemaNameSuggestions(null));
                    suggestions.addAll(getTableNameSuggestions(null, null));
                }
                removeRepeats(suggestions);
                this.editor.addStaticWordCompleter(suggestions);
            } catch (Exception ex) {
                logger.debug("Failed to generate suggestions. cursor=" + cursor + " text=" + text, ex);
            }
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private int getCursorPosition() {
        String value = editor.getValue();
        int cursorRow = editor.getCursorPosition().getRow();
        int cursorColumn = editor.getCursorPosition().getColumn();
        int row = 0;
        int column = 0;
        int index = 0;
        for (char c : value.toCharArray()) {
            if (row == cursorRow && column == cursorColumn) {
                return index;
            }
            if (c == '\n') {
                row++;
                column = 0;
            } else {
                column++;
            }
            index++;
        }
        if (row == cursorRow && column == cursorColumn) {
            return index;
        }
        return -1;
    }

    private boolean isSqlIdentifier(char c) {
        return Character.isLetterOrDigit(c)
                || c == '-' || c == '@' || c == '_' || c == '#' || c == '$' || c == '*';
    }

    private int getLastDeliminatorIndex() {
        int lookBack = 0;
        while (cursor > lookBack
                && isSqlIdentifier(text.charAt(cursor - 1 - lookBack))) {
            lookBack++;
        }
        return cursor - lookBack - 1;
    }

    private String getCurrentWord() {
        return text.substring(getLastDeliminatorIndex() + 1, cursor);
    }

    private String getPrevWord(int index) {
        int lookBack = 0;
        while (index - 1 >= lookBack
                && isSqlIdentifier(text.charAt(index - 1 - lookBack))) {
            lookBack++;
        }
        while (index - 1 >= lookBack
                && !isSqlIdentifier(text.charAt(index - 1 - lookBack))) {
            if (text.charAt(index - 1 - lookBack) == ';') {
                return "";
            }
            lookBack++;
        }
        int endIndex = index - lookBack;
        while (index - 1 >= lookBack
                && isSqlIdentifier(text.charAt(index - 1 - lookBack))) {
            lookBack++;
        }
        return text.substring(index - lookBack, endIndex);
    }

    private List<String> getAliasSuggestions() {
        List<String> suggestions = new ArrayList<String>();
        for (String alias : aliases.keySet()) {
            if (alias.toLowerCase().startsWith(currentWord.toLowerCase())) {
                suggestions.add(alias);
            }
        }
        Collections.sort(suggestions);
        return suggestions;
    }

    private Map<String, String> getAliases() {
        Map<String, String> aliases = new HashMap<String, String>();
        int lastAliasIndex = 0;
        String as = " as ";
        while (lastAliasIndex + as.length() < text.length()) {
            int asIndex = text.toLowerCase().indexOf(as, lastAliasIndex);
            if (asIndex == -1)
                break;
            int i = asIndex + as.length();
            while (i < text.length() && isSqlIdentifier(text.charAt(i)))
                i++;
            String alias = text.substring(asIndex + as.length(), i);
            i = asIndex;
            while (i > 0 && (text.charAt(--i) == '.' || isSqlIdentifier(text.charAt(i))))
                ;
            aliases.put(alias, text.substring(i + 1, asIndex));
            lastAliasIndex = asIndex + as.length();
        }
        for (String tableName : referencedTableNames) {
            if (!aliases.containsValue(tableName)) {
                int tableNameIndex = text.toLowerCase().indexOf(tableName.toLowerCase());
                if (tableNameIndex == -1)
                    continue;
                int start = tableNameIndex + tableName.length() + 1;
                if (start < text.length()) {
                    while (start < text.length() && Character.isWhitespace(text.charAt(start)))
                        start++;
                    int end = start;
                    while (end < text.length() && isSqlIdentifier(text.charAt(end)))
                        end++;
                    String alias = text.substring(start, end);
                    int i = tableNameIndex;
                    while (i < text.length() && (text.charAt(i) == '.' || isSqlIdentifier(text.charAt(i))))
                        i++;
                    aliases.put(alias, text.substring(tableNameIndex, i));
                }
            }
        }
        return aliases;
    }

    /* Returns the text of the current query with subqueries removed */
    private String getCurrentQuery() {
        List<int[]> queryBlocks = new ArrayList<int[]>();
        int min = text.lastIndexOf(';', cursor + 1);
        int max = text.indexOf(';', cursor) < 0 ? text.length() : text.indexOf(';', cursor);
        queryBlocks.add(new int[] { min, max });
        for (int i = min + 1; i < max; i++) {
            if ((text.charAt(i) == '(' ||
                    (i + 5 < max && text.regionMatches(true, i, "begin", 0, 5)))) {
                queryBlocks.add(new int[] { i, -1 });
            } else if (text.charAt(i) == ')' ||
                    (i > 3 && text.regionMatches(true, i - 3, "end", 0, 3))) {
                int j = queryBlocks.size() - 1;
                while (j > 0 && queryBlocks.get(j)[1] != -1)
                    j--;
                if (j > 0)
                    queryBlocks.get(j)[1] = i;
            }
        }
        int[] currentBlock = queryBlocks.get(0);
        int blockIndex = queryBlocks.size() - 1;
        while (blockIndex > 0 && queryBlocks.get(blockIndex)[1] == -1
                && queryBlocks.get(blockIndex)[0] > cursor) {
            blockIndex--;
        }
        if (queryBlocks.get(blockIndex)[1] == -1) {
            currentBlock[0] = queryBlocks.get(blockIndex)[0];
            currentBlock[1] = max;
        } else {
            for (int[] block : queryBlocks) {
                if (block[0] > currentBlock[0] && block[0] < cursor &&
                        block[1] < currentBlock[1] && block[1] > cursor) {
                    currentBlock = block;
                }
            }
        }
        if (currentBlock[1] == -1 || text.length() - 1 < currentBlock[1] && min >= 0) {
            return text.substring(min, max);
        }
        try {
            String tempText = "";
            if (currentBlock.length > 1) {
                tempText = text.substring(currentBlock[0] + 1, currentBlock[1]);
                int shiftLeft = currentBlock[0] + 1, lastRemoval = currentBlock[0] + 1;
                for (int[] block : queryBlocks) {
                    if (block[1] > 0 && block[0] >= currentBlock[0] && block[1] <= currentBlock[1] && block != currentBlock
                            && block[0] > lastRemoval) {
                        for (String word : QUERY_INITIALIZERS) {
                            if (text.substring(block[0] + 1, block[1]).trim().startsWith(word)) {
                                tempText = tempText.substring(min + 1, block[0] - shiftLeft)
                                        + (tempText.length() > block[1] - shiftLeft + 1 ? tempText.substring(block[1] - shiftLeft + 1) : "");
                                shiftLeft += block[1] - block[0];
                                lastRemoval = block[1];
                                break;
                            }
                        }
                    }
                }
            }
            return tempText;
        } catch (StringIndexOutOfBoundsException e) {
            return "";
        } catch (Exception e) {
            logger.warn("", e);
            return "";
        }
    }

    private List<String> getReferencedTableNames() {
        if (text.isEmpty())
            return new ArrayList<String>();
        List<String> tableNames = new ArrayList<String>();
        String currentQuery = getCurrentQuery();
        String tempText = currentQuery.toLowerCase();
        String[] keywords = { "from ", "update ", "join ", "into " };
        for (String keyword : keywords) {
            int lastIndexOfKeyword = 0;
            while (lastIndexOfKeyword < tempText.length() && lastIndexOfKeyword >= 0) {
                int indexOfKeyword = tempText.indexOf(keyword, lastIndexOfKeyword + 1);
                if (indexOfKeyword == 0 || (indexOfKeyword > 0 &&
                        !isSqlIdentifier(tempText.charAt(indexOfKeyword - 1)))) {
                    int start = indexOfKeyword + keyword.length();
                    boolean newWord = true, seenComma = true, ignore = false;
                    for (int i = start; i < tempText.length() && (isSqlIdentifier(tempText.charAt(i))
                            || tempText.charAt(i) == ',' || Character.isWhitespace(tempText.charAt(i))
                            || tempText.charAt(i) == '.'); i++) {
                        char character = tempText.charAt(i);
                        if (newWord && ignore && isSqlIdentifier(character)) {
                            continue;
                        } else if (newWord && ignore) {
                            ignore = false;
                        } else if (newWord && (character == '.' || character == '$' || Character.isDigit(character))) {
                            break;
                        } else if (newWord && seenComma && isSqlIdentifier(character)) {
                            start = i;
                            newWord = false;
                        } else if (newWord && character == ',') {
                            seenComma = true;
                        } else if (newWord && !seenComma && !Character.isWhitespace(character)) {
                            // if (i < tempText.length()-3 && tempText.regionMatches(i-1, " as ", 0, 4)) {
                            ignore = true;
                            // } else {
                            // break;
                            // }
                        } else if (!newWord && !isSqlIdentifier(character) && character != '.') {
                            tableNames.add(currentQuery.substring(start, i));
                            newWord = true;
                            seenComma = character == ',';
                        } else if (!newWord && i + 1 >= tempText.length()) {
                            tableNames.add(currentQuery.substring(start));
                            break;
                        }
                    }
                }
                lastIndexOfKeyword = indexOfKeyword;
            }
        }
        return tableNames;
    }

    /* Returns list of suggestions for the catalog.schema.table.column format */
    private List<String> getHierarchySuggestions() {
        List<String> suggestions = new ArrayList<String>();
        String prevWord = getPrevWord(cursor), prevWord2 = null, prevWord3 = null;
        int lookBack = prevWord.length();
        if (cursor - 2 > lookBack && text.charAt(cursor - 2 - lookBack) == '.'
                && (isSqlIdentifier(text.charAt(cursor - 3 - lookBack)))) {
            prevWord2 = getPrevWord(cursor - lookBack);
        }
        lookBack += (prevWord2 == null ? 0 : prevWord2.length());
        if (cursor - 2 > lookBack && text.charAt(cursor - 2 - lookBack) == '.'
                && (isSqlIdentifier(text.charAt(cursor - 3 - lookBack)))) {
            prevWord3 = getPrevWord(cursor - lookBack);
        }
        if (prevWord2 == null && prevWord3 == null) {
            suggestions.addAll(getSchemaNameSuggestions(prevWord.isEmpty() ? null : prevWord));
        }
        if (prevWord3 == null) {
            suggestions.addAll(getTableNameSuggestions(prevWord2, prevWord));
        }
        suggestions.addAll(getColumnNameSuggestions(prevWord3, prevWord2, prevWord));
        return suggestions;
    }

    private List<String> getCatalogNameSuggestions() {
        List<String> suggestions = new ArrayList<String>();
        List<String> catalogs = getCatalogNamesFromCache();
        for (String catalog : catalogs) {
            if (catalog.toLowerCase().startsWith(currentWord.toLowerCase())) {
                suggestions.add(catalog);
            }
        }
        Collections.sort(suggestions);
        return suggestions;
    }

    private List<String> getCatalogNamesFromCache() {
        List<String> catalogs = catalogNameCache;
        if (catalogs.isEmpty()) {
            catalogs = reader.getCatalogNames();
        }
        return catalogs;
    }

    public void clearCatalogNameCache() {
        catalogNameCache = new ArrayList<String>();
    }

    private List<String> getSchemaNameSuggestions(String catalog) {
        List<String> suggestions = new ArrayList<String>();
        List<String> schemaNames = getSchemaNamesFromCache(catalog);
        for (String schemaName : schemaNames) {
            if (schemaName.toLowerCase().startsWith(currentWord.toLowerCase())) {
                suggestions.add(schemaName);
            }
        }
        Collections.sort(suggestions);
        return suggestions;
    }

    private List<String> getSchemaNamesFromCache(String catalog) {
        List<String> schemaNames = schemaNameCache.get(catalog);
        if (schemaNames == null) {
            schemaNames = reader.getSchemaNames(catalog);
            schemaNameCache.put(catalog, schemaNames);
        }
        return schemaNames;
    }

    public void clearSchemaNamesCache() {
        schemaNameCache = new HashMap<String, List<String>>();
    }

    private List<String> getTableNameSuggestions(String catalog, String schema) {
        List<String> suggestions = new ArrayList<String>();
        List<String> tableNames = getTableNamesFromCache(catalog, schema);
        for (String tableName : tableNames) {
            if (tableName.toLowerCase().startsWith(currentWord.toLowerCase())) {
                suggestions.add(tableName);
            }
        }
        Collections.sort(suggestions);
        return suggestions;
    }

    private List<String> getTableNamesFromCache(String catalog, String schema) {
        String key = getFullName(catalog, schema, null);
        List<String> tableNames = tableNameCache.get(key);
        if (tableNames == null) {
            tableNames = reader.getRelationNames(catalog, schema, TABLE_TYPES);
            tableNameCache.put(key, tableNames);
        }
        return tableNames;
    }

    public void clearTableNamesCache() {
        tableNameCache = new HashMap<String, List<String>>();
    }

    private List<String> getColumnNameSuggestions(String catalog, String schema, String tableName) {
        List<String> suggestions = new ArrayList<String>();
        if (aliases.get(tableName) != null) {
            String[] parsedName = parseFullName(aliases.get(tableName));
            tableName = parsedName[0];
            schema = parsedName[1];
            catalog = parsedName[2];
        }
        List<String> columnNames = getColumnNamesFromCache(catalog, schema, tableName);
        for (String columnName : columnNames) {
            if (columnName.toLowerCase().startsWith(currentWord.toLowerCase())) {
                suggestions.add(columnName);
            }
        }
        Collections.sort(suggestions);
        return suggestions;
    }

    private List<String> getColumnNamesFromCache(String catalog, String schema, String tableName) {
        String key = getFullName(catalog, schema, tableName);
        List<String> columnNames = columnNameCache.get(key);
        if (columnNames == null) {
            columnNames = reader.getColumnNames(catalog, schema, tableName);
            columnNameCache.put(key, columnNames);
        }
        return columnNames;
    }

    public void clearColumnNamesCache() {
        columnNameCache = new HashMap<String, List<String>>();
    }

    public void clearCaches() {
        clearSchemaNamesCache();
        clearTableNamesCache();
        clearColumnNamesCache();
        clearCatalogNameCache();
    }

    /* Returns list of table name, schema name, and catalog name, respectively */
    private String[] parseFullName(String fullName) {
        List<String> parsedName = new ArrayList<String>();
        while (fullName.lastIndexOf('.') >= 0) {
            parsedName.add(fullName.substring(fullName.lastIndexOf('.') + 1));
            fullName = fullName.substring(0, fullName.lastIndexOf('.'));
        }
        parsedName.add(fullName);
        return parsedName.toArray(new String[3]);
    }

    private String getFullName(String catalogName, String schemaName, String tableName) {
        return (catalogName == null ? "" : catalogName + ".") + (schemaName == null ? "" : schemaName + ".")
                + (tableName == null ? "" : tableName);
    }

    private List<String> removeRepeats(List<String> longList) {
        List<String> shortList = new ArrayList<String>();
        for (String suggestion : longList) {
            if (!shortList.contains(suggestion)) {
                shortList.add(suggestion);
            }
        }
        return shortList;
    }
}
