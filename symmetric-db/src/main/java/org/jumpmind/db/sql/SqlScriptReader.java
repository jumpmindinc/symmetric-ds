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
package org.jumpmind.db.sql;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.exception.IoException;
import org.jumpmind.util.FormatUtils;

/**
 * Provides an interface to read each SQL statement in a SQL script.
 */
public class SqlScriptReader extends LineNumberReader implements ISqlStatementSource {
    public final static String QUERY_ENDS = ";";
    private String delimiter = QUERY_ENDS;
    private Map<String, String> replacementTokens;
    private boolean usePrefixSuffixForReplacementTokens = false;
    private boolean stripOutComments = true;
    private boolean triggersContainJava = false;

    public SqlScriptReader(Reader in) {
        this(in, false);
    }

    public SqlScriptReader(Reader in, boolean triggersContainJava) {
        super(in);
        this.triggersContainJava = triggersContainJava;
    }

    public void setUsePrefixSuffixForReplacementTokens(boolean usePrefixSuffixForReplacementTokens) {
        this.usePrefixSuffixForReplacementTokens = usePrefixSuffixForReplacementTokens;
    }

    public void setStripOutComments(boolean stripOutComments) {
        this.stripOutComments = stripOutComments;
    }

    public boolean isStripOutComments() {
        return stripOutComments;
    }

    public boolean isUsePrefixSuffixForReplacementTokens() {
        return usePrefixSuffixForReplacementTokens;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setReplacementTokens(Map<String, String> replacementTokens) {
        this.replacementTokens = replacementTokens;
    }

    public Map<String, String> getReplacementTokens() {
        return replacementTokens;
    }

    @Override
    public String readSqlStatement() {
        try {
            String line = readLine();
            StringBuilder sql = null;
            int checkStatementEndsIndex = 0;
            CheckEndStatus endStatus = new CheckEndStatus();
            if (line != null) {
                boolean creatingJavaTrigger = false;
                do {
                    if (sql == null) {
                        sql = new StringBuilder();
                    }
                    sql.append("\n");
                    sql.append(line);
                    if (triggersContainJava) {
                        String trimmedLine = line.trim();
                        if (!creatingJavaTrigger && Strings.CI.startsWith(trimmedLine, "CREATE TRIGGER")) {
                            creatingJavaTrigger = true;
                        } else if (creatingJavaTrigger && Strings.CI.startsWith(trimmedLine, "END")) {
                            creatingJavaTrigger = false;
                        }
                    }
                    if (!creatingJavaTrigger && checkStatementEnds(endStatus, checkStatementEndsIndex, sql.toString())) {
                        String toExecute = sql.substring(0, sql.lastIndexOf(delimiter));
                        toExecute = prepareForExecute(toExecute);
                        if (StringUtils.isNotBlank(toExecute)) {
                            return toExecute;
                        } else {
                            sql.setLength(0);
                        }
                    } else {
                        checkStatementEndsIndex = sql.length();
                    }
                    line = readLine();
                } while (line != null);
                String toExecute = sql.toString();
                if (StringUtils.isNotBlank(toExecute)) {
                    return prepareForExecute(toExecute);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    protected String prepareForExecute(String toExecute) {
        if (stripOutComments) {
            toExecute = removeComments(toExecute);
        }
        toExecute = FormatUtils.replaceTokens(toExecute, replacementTokens, usePrefixSuffixForReplacementTokens);
        if (StringUtils.isNotBlank(toExecute)) {
            return toExecute.trim();
        } else {
            return null;
        }
    }

    private final String removeComments(String s) {
        char[] characters = s.toCharArray();
        LiteralInfo literalInfo = null;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int skipNextCount = 0;
        StringBuilder commentsRemoved = new StringBuilder();
        char prev = '\0';
        int index = 0;
        for (char cur : characters) {
            if (!inLineComment) {
                literalInfo = switchLiteral(literalInfo, index, characters);
            }
            if (literalInfo == null && !inLineComment && !inBlockComment) {
                inBlockComment = isBlockCommentStart(prev, cur);
                inLineComment = isLineCommentStart(prev, cur);
            }
            if (inLineComment && isLineCommentEnd(prev, cur)) {
                inLineComment = false;
            }
            if (inBlockComment && isBlockCommentEnd(prev, cur)) {
                inBlockComment = false;
                skipNextCount = 2;
            }
            if (!inBlockComment && !inLineComment && skipNextCount == 0) {
                commentsRemoved.append(prev);
            } else if (skipNextCount > 0) {
                skipNextCount--;
            }
            prev = cur;
            index++;
        }
        if (!inBlockComment && !inLineComment && skipNextCount == 0) {
            commentsRemoved.append(prev);
        }
        return commentsRemoved.toString();
    }

    private final boolean isLineCommentStart(char prev, char cur) {
        return (prev == '#' && cur == '#') || (prev == '-' && cur == '-') || (prev == '/' && cur == '/');
    }

    private final boolean isLineCommentEnd(char prev, char cur) {
        return prev == '\n';
    }

    private final boolean isBlockCommentStart(char prev, char cur) {
        return stripOutComments && (prev == '/' && cur == '*');
    }

    private final boolean isBlockCommentEnd(char prev, char cur) {
        return (prev == '*' && cur == '/');
    }

    private final LiteralInfo switchLiteral(LiteralInfo literalInfo, int currentIndex, char[] statement) {
        if (literalInfo == null && currentIndex > 0) {
            char prev = statement[currentIndex - 1];
            if (prev == '\'' || prev == '"' || prev == '`') {
                literalInfo = new LiteralInfo(prev, currentIndex);
            }
        } else if (literalInfo != null) {
            char cur = statement[currentIndex];
            char prev = statement[currentIndex - 1];
            if (prev == literalInfo.type && cur != literalInfo.type) {
                int count = 0;
                for (int i = currentIndex - 2; i >= literalInfo.startIndex; i--) {
                    char check = statement[i];
                    if (check == literalInfo.type) {
                        count++;
                    } else {
                        break;
                    }
                }
                if (count % 2 == 0) {
                    literalInfo = null;
                }
            }
        }
        return literalInfo;
    }

    private final boolean checkStatementEnds(CheckEndStatus status, int start, String s) {
        char[] characters = s.toCharArray();
        char prev = '\0';
        for (int i = start; i < characters.length; i++) {
            char cur = characters[i];
            if (!status.inLineComment) {
                status.literalInfo = switchLiteral(status.literalInfo, i, characters);
            }
            if (status.literalInfo == null && !status.inLineComment && !status.inBlockComment) {
                status.inBlockComment = isBlockCommentStart(prev, cur);
                status.inLineComment = isLineCommentStart(prev, cur);
            }
            if (status.inLineComment && isLineCommentEnd(prev, cur)) {
                status.inLineComment = false;
            }
            if (status.inBlockComment && isBlockCommentEnd(prev, cur)) {
                status.inBlockComment = false;
            }
            if (status.literalInfo == null && !status.inBlockComment &&
                    !status.inLineComment && s.substring(i, i + delimiter.length()).equals(delimiter)) {
                return true;
            }
            prev = cur;
        }
        return false;
    }

    static class LiteralInfo {
        public LiteralInfo(char type, int startIndex) {
            this.type = type;
            this.startIndex = startIndex;
        }

        char type;
        int startIndex;
    }

    static class CheckEndStatus {
        LiteralInfo literalInfo = null;
        boolean inLineComment = false;
        boolean inBlockComment = false;
    }
}
