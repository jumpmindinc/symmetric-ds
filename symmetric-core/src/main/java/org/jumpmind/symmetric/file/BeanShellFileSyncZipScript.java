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
package org.jumpmind.symmetric.file;

import java.io.File;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.model.FileConflictStrategy;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;

public class BeanShellFileSyncZipScript extends FileSyncZipScript {
    protected IExtensionService extensionService;
    protected IParameterService parameterService;
    protected ISymmetricEngine engine;

    public BeanShellFileSyncZipScript(ISymmetricEngine engine) {
        this.engine = engine;
        this.extensionService = engine.getExtensionService();
        this.parameterService = engine.getParameterService();
    }

    @Override
    public String getScriptFileName(Batch batch) {
        return "sync.bsh";
    }

    @Override
    public void buildScriptStart(Batch batch) {
        getScript().append("fileList = new HashMap();\n");
    }

    @Override
    public void buildScriptFileSnapshot(Batch batch, FileSnapshot snapshot, FileTriggerRouter triggerRouter,
            FileTrigger fileTrigger, File file, String targetBaseDir, String targetFile) {
        LastEventType eventType = snapshot.getLastEventType();
        long oldCrc32Checksum = snapshot.getOldCrc32Checksum();
        long crc32Checksum = snapshot.getCrc32Checksum();
        StringBuilder command = new StringBuilder();
        command.append("targetBaseDir = \"").append(targetBaseDir).append("\";\n");
        command.append("if (targetBaseDir.startsWith(\"${androidBaseDir}\")) {                      \n");
        command.append("    targetBaseDir = targetBaseDir.replace(\"${androidBaseDir}\", \"\");     \n");
        command.append("    targetBaseDir = androidBaseDir + targetBaseDir;                         \n");
        command.append("} else if (targetBaseDir.startsWith(\"${androidAppFilesDir}\")) {           \n");
        command.append("    targetBaseDir = targetBaseDir.replace(\"${androidAppFilesDir}\", \"\"); \n");
        command.append("    targetBaseDir = androidAppFilesDir + targetBaseDir;                     \n");
        command.append("}                                                                           \n");
        command.append("processFile = true;\n");
        command.append("sourceFileName = \"").append(snapshot.getFileName())
                .append("\";\n");
        command.append("targetRelativeDir = \"");
        if (!snapshot.getRelativeDir().equals(".")) {
            command.append(StringEscapeUtils.escapeJava(snapshot
                    .getRelativeDir()));
            command.append("\";\n");
        } else {
            command.append("\";\n");
        }
        command.append("targetFileName = sourceFileName;\n");
        command.append("sourceFilePath = \"");
        command.append(StringEscapeUtils.escapeJava(snapshot.getRelativeDir())).append("\";\n");
        if (StringUtils.isNotBlank(fileTrigger.getBeforeCopyScript())) {
            command.append(fileTrigger.getBeforeCopyScript()).append("\n");
        }
        command.append("if (processFile) {\n");
        switch (eventType) {
            case CREATE:
            case MODIFY:
                if (file.exists()) {
                    command.append("  File targetBaseDirFile = new File(targetBaseDir);\n");
                    command.append("  if (!targetBaseDirFile.exists()) {\n");
                    command.append("    targetBaseDirFile.mkdirs();\n");
                    command.append("  }\n");
                    command.append("  java.io.File sourceFile = new java.io.File(batchDir + \"/\"");
                    if (!snapshot.getRelativeDir().equals(".")) {
                        command.append(" + sourceFilePath + \"/\"");
                    }
                    command.append(" + sourceFileName");
                    command.append(");\n");
                    command.append("  java.io.File targetFile = new java.io.File(");
                    command.append(targetFile);
                    command.append(");\n");
                    // no need to copy directory if it already exists
                    command.append("  if (targetFile.exists() && targetFile.isDirectory()) {\n");
                    command.append("      processFile = false;\n");
                    command.append("  }\n");
                    // conflict resolution
                    FileConflictStrategy conflictStrategy = triggerRouter.getConflictStrategy();
                    if (conflictStrategy == FileConflictStrategy.TARGET_WINS ||
                            conflictStrategy == FileConflictStrategy.MANUAL) {
                        command.append("  if (targetFile.exists() && !targetFile.isDirectory()) {\n");
                        command.append("    long targetChecksum = org.apache.commons.io.FileUtils.checksumCRC32(targetFile);\n");
                        command.append("    if (targetChecksum != " + oldCrc32Checksum + "L) {\n");
                        if (conflictStrategy == FileConflictStrategy.MANUAL) {
                            command.append("      throw new org.jumpmind.symmetric.file.FileConflictException(targetFileName + \" was in conflict \");\n");
                        } else {
                            command.append("      processFile = false;\n");
                        }
                        command.append("    }\n");
                        command.append("  }\n");
                    } else {
                        if (triggerRouter.getConflictStrategyString() != null) {
                            for (IFileConflictResolver resolver : extensionService.getExtensionPointList(IFileConflictResolver.class)) {
                                if (triggerRouter.getConflictStrategyString().equals(resolver.getName())) {
                                    command.append(resolver.getResolveCommand(snapshot));
                                }
                            }
                        }
                    }
                    command.append("  if (processFile) {\n");
                    command.append("    try {\n");
                    command.append("      if (sourceFile.isDirectory()) {\n");
                    command.append("        org.apache.commons.io.FileUtils.copyDirectory(sourceFile, targetFile, true);\n");
                    command.append("      } else {\n");
                    command.append("        org.apache.commons.io.FileUtils.copyFile(sourceFile, targetFile, true);\n");
                    command.append("      }\n");
                    command.append("    } catch (java.lang.Throwable e) {\n");
                    command.append("      String errorDir = batchDir.toString();\n");
                    command.append("      errorDir = errorDir.substring(0, errorDir.lastIndexOf('/')) + \"-error/\";\n");
                    if (!snapshot.getRelativeDir().equals(".")) {
                        command.append("      errorDir += sourceFilePath + \"/\";\n");
                    }
                    command.append(
                            "      log.error(\"File '\" + targetFile + \"' is not writable. It will be placed in the '\" + errorDir + \"' directory.\");\n");
                    command.append("      java.io.File errorFile = new java.io.File(errorDir + sourceFileName);\n");
                    command.append("      if (sourceFile.isDirectory()) {\n");
                    command.append("        org.apache.commons.io.FileUtils.copyDirectory(sourceFile, errorFile, true);\n");
                    command.append("      } else {\n");
                    command.append("        org.apache.commons.io.FileUtils.copyFile(sourceFile, errorFile, true);\n");
                    command.append("      }\n");
                    command.append("      processFile = false;");
                    command.append("    }\n");
                    command.append("  }\n");
                    if (parameterService.is(ParameterConstants.FILE_SYNC_VERIFY_TARGET)) {
                        command.append("  if (processFile && !targetFile.isDirectory()) {\n");
                        command.append("    long sourceVerifyValue;\n");
                        command.append("    long targetVerifyValue;\n");
                        boolean fileSyncVerifyUseCrc = parameterService.is(ParameterConstants.FILE_SYNC_VERIFY_USE_CRC);
                        if (crc32Checksum < 0) {
                            fileSyncVerifyUseCrc = false;
                        }
                        if (fileSyncVerifyUseCrc) {
                            command.append("    sourceVerifyValue = " + crc32Checksum + "L;\n");
                            command.append("    targetVerifyValue = org.apache.commons.io.FileUtils.checksumCRC32(targetFile);\n");
                        } else {
                            command.append("    sourceVerifyValue = " + snapshot.getFileSize() + ";\n");
                            command.append("    targetVerifyValue = targetFile.length();\n");
                        }
                        command.append("    if (sourceVerifyValue != targetVerifyValue) {\n");
                        String msg = "File \" + targetFile.getAbsolutePath() + \": ";
                        if (fileSyncVerifyUseCrc) {
                            msg += "CRCs do not match. Source CRC=\" + sourceVerifyValue + \", Target CRC=\" + targetVerifyValue + \"";
                        } else {
                            msg += "Sizes do not match. Source size=\" + sourceVerifyValue + \", Target size=\" + targetVerifyValue + \"";
                        }
                        command.append("      String msg = \"" + msg + "\";\n");
                        command.append("      throw new java.io.IOException(msg);\n");
                        command.append("    }\n");
                        command.append("  }\n");
                    }
                    command.append("  fileList.put(").append(targetFile)
                            .append(",\"");
                    command.append(eventType.getCode());
                    command.append("\");\n");
                }
                break;
            case DELETE:
                command.append("  org.apache.commons.io.FileUtils.deleteQuietly(new java.io.File(");
                command.append(targetFile);
                command.append("));\n");
                command.append("  fileList.put(").append(targetFile).append(",\"");
                command.append(eventType.getCode());
                command.append("\");\n");
                break;
            default:
                break;
        }
        if (StringUtils.isNotBlank(fileTrigger.getAfterCopyScript())) {
            command.append(fileTrigger.getAfterCopyScript()).append("\n");
        }
        command.append("}\n\n");
        getScript().append(command);
    }

    @Override
    public void buildScriptEnd(Batch batch) {
        getScript().append("return fileList;\n");
    }
}
