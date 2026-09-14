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
package org.jumpmind.symmetric.tool;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;

import org.apache.commons.io.FileUtils;

public class RefactorTool {
    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("user.dir")).getParentFile();
        Collection<File> files = FileUtils.listFiles(dir, new String[] { "java" }, true);
        for (File file : files) {
            if (file.getAbsolutePath().contains("/symmetric-")) {
                System.out.println("Refactoring file: " + file.getName());
                StringBuilder contents = new StringBuilder(FileUtils.readFileToString(file, Charset.defaultCharset()));
                if (refactor(contents)) {
                    FileUtils.write(file, contents.toString(), Charset.defaultCharset(), false);
                }
            }
        }
    }

    protected static boolean refactor(StringBuilder contents) {
        boolean refactored = false;
        String[] lines = contents.toString().split("\n");
        contents.setLength(0);
        int logmode = 0;
        for (String line : lines) {
            if (line.contains("log.") || line.contains("logger.")) {
                logmode = 4;
            }
            if (!line.contains("String.format") && logmode > 0) {
                refactored = true;
                line = line.replace("%s", "{}");
                line = line.replace("%d", "{}");
                logmode--;
            } else {
                logmode = 0;
            }
            contents.append(line);
            contents.append("\n");
        }
        contents.substring(0, contents.length() - 1);
        return refactored;
    }
}
