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
package org.jumpmind.symmetric.file;

import java.io.File;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;

public class BashFileSyncZipScript extends FileSyncZipScript {
    @Override
    public String getScriptFileName(Batch batch) {
        return "sync.sh";
    }

    @Override
    public void buildScriptStart(Batch batch) {
        appendln("#!/bin/bash");
        appendln();
        appendln("batchDir=$1");
        appendln("sourceNodeId=$2");
        appendln("outputFileName=$3");
        appendln();
    }

    @Override
    public void buildScriptFileSnapshot(Batch batch, FileSnapshot snapshot, FileTriggerRouter triggerRouter,
            FileTrigger fileTrigger, File file, String targetBaseDir, String targetFile) {
        LastEventType eventType = snapshot.getLastEventType();
        appendln("processFile=true");
        appendln(String.format("sourceFileName=\"%s\"", snapshot.getFileName()));
        append("targetRelativeDir=\"");
        if (!snapshot.getRelativeDir().equals(".")) {
            append(StringEscapeUtils.escapeJava(snapshot.getRelativeDir()));
        }
        appendln("\"");
        appendln("targetFileName=$sourceFileName");
        appendln(String.format("sourceFilePath=\"%s\"", StringEscapeUtils.escapeJava(snapshot.getRelativeDir())));
        appendln(String.format("targetBaseDir=\"%s\"", targetBaseDir));
        if (StringUtils.isNotBlank(fileTrigger.getBeforeCopyScript())) {
            appendln(fileTrigger.getBeforeCopyScript());
        }
        appendln("if [ \"$processFile\" = true ] ; then ");
        // This line guards against shell script syntax error caused by empty "if...fi" when the source
        // file was removed before extraction.
        appendln("  echo \"#Processing " + snapshot.getFileName() + "\" >> \"$outputFileName\"");
        switch (eventType) {
            case CREATE:
            case MODIFY:
                if (file.exists()) {
                    appendln("  mkdir -p \"$targetBaseDir\"");
                    append("  sourceFile=\"$batchDir/");
                    if (!snapshot.getRelativeDir().equals(".")) {
                        append("$sourceFilePath/");
                    }
                    append("$sourceFileName");
                    appendln("\"");
                    appendln("targetFile=\"$targetBaseDir/$targetRelativeDir/$targetFileName\"");
                    appendln("targetDir=\"$targetBaseDir/$targetRelativeDir\"");
                    // no need to copy directory if it already exists
                    appendln("  if [ -d \"$targetFile\" ]; then");
                    appendln("    processFile=false");
                    appendln("  fi");
                    // conflict resolution
                    // FileConflictStrategy conflictStrategy = triggerRouter.getConflictStrategy();
                    // if (conflictStrategy == FileConflictStrategy.TARGET_WINS ||
                    // conflictStrategy == FileConflictStrategy.MANUAL) {
                    // command.appendln(" if (targetFile.exists() && !targetFile.isDirectory()) {\n");
                    // command.appendln(" long targetChecksum = org.apache.commons.io.FileUtils.checksumCRC32(targetFile);\n");
                    // command.appendln(" if (targetChecksum != " + snapshot.getOldCrc32Checksum() + "L) {\n");
                    // if (conflictStrategy == FileConflictStrategy.MANUAL) {
                    // command.appendln(" throw new org.jumpmind.symmetric.file.FileConflictException(targetFileName + \" was in conflict \");\n");
                    // } else {
                    // command.appendln(" processFile = false;\n");
                    // }
                    // command.appendln(" }\n");
                    // command.appendln(" }\n");
                    // }
                    appendln("  if [ \"$processFile\" = true ] ; then");
                    appendln("      cp -a \"$sourceFile\" \"$targetDir\"");
                    appendln("      echo \"$targetBaseDir/$targetRelativeDir/$targetFileName=" + eventType.getCode() + "\" >> $outputFileName");
                    appendln("  fi ");
                }
                break;
            case DELETE:
                appendln("  if [ \"$processFile\" = true ] ; then");
                appendln("      rm -rf \"$targetBaseDir/$targetRelativeDir/$targetFileName\"");
                appendln("      echo \"$targetBaseDir/$targetRelativeDir/$targetFileName=D\" >> $outputFileName");
                appendln("  fi ");
                break;
            default:
                break;
        }
        if (StringUtils.isNotBlank(fileTrigger.getAfterCopyScript())) {
            appendln(fileTrigger.getAfterCopyScript());
        }
        appendln("fi\n");
    }

    @Override
    public void buildScriptEnd(Batch batch) {
        // no-op
    }
}
