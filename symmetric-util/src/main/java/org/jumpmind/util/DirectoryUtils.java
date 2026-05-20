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
package org.jumpmind.util;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

@IgnoreJRERequirement
public class DirectoryUtils {

    public static String reportDirectoryContents(File dir, PrintDirConfig config) throws IOException {
        StringBuilder output = new StringBuilder();
        reportDirectoryContentsInternal(dir, output, config);
        return output.toString();
    }

    private static void reportDirectoryContentsInternal(File dir, StringBuilder output, PrintDirConfig config) throws IOException {
        if (config.getFileCount() >= config.getMaxCount()) {
            return;
        }
        output.append("\n");
        output.append(dir.getCanonicalPath());
        output.append("\n");
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.parallelSort(files, config.getFileComparator());
            for (File file : files) {
                output.append("  ");
                output.append(file.isDirectory() ? "d" : "-");
                output.append(file.canRead() ? "r" : "-");
                output.append(file.canWrite() ? "w" : "-");
                output.append(file.canExecute() ? "x" : "-");
                output.append(StringUtils.leftPad(file.length() + "", 11));
                output.append(" ");
                output.append(config.getDateFormat().format(new Date(file.lastModified())));
                output.append(" ");
                output.append(file.getName());
                output.append("\n");
                if (config.incrementFileCount() >= config.getMaxCount()) {
                    output.append("\n*** MAX LIMIT OF " + config.getMaxCount() + " FILES ***\n");
                    return;
                }
            }
            for (File file : files) {
                if (file.isDirectory() && (config.getExcludeDir() == null || (!config.getExcludeDir().equals(dir)
                        && !file.getName().equalsIgnoreCase("tmp")))) {
                    reportDirectoryContentsInternal(file, output, config);
                }
            }
        }
    }

    static class FileComparator implements Comparator<File> {
        @Override
        public int compare(File o1, File o2) {
            return o1.getPath().compareToIgnoreCase(o2.getPath());
        }
    }

    public static class PrintDirConfig {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Comparator<File> fileComparator = new FileComparator();
        int fileCount;
        int maxCount;
        File excludeDir;

        public PrintDirConfig(int maxCount) {
            this.maxCount = maxCount;
        }

        public PrintDirConfig(int maxCount, File excludeDir) {
            this.maxCount = maxCount;
            this.excludeDir = excludeDir;
        }

        public Comparator<File> getFileComparator() {
            return fileComparator;
        }

        public SimpleDateFormat getDateFormat() {
            return df;
        }

        public int incrementFileCount() {
            return fileCount++;
        }

        public int getFileCount() {
            return fileCount;
        }

        public int getMaxCount() {
            return maxCount;
        }

        public File getExcludeDir() {
            return excludeDir;
        }
    }
}
