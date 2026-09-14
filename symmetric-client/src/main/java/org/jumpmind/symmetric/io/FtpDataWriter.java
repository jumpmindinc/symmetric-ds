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
package org.jumpmind.symmetric.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.jumpmind.db.model.Table;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.DataWriterStatisticConstants;
import org.jumpmind.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpDataWriter implements IDataWriter {
    public enum Format {
        CSV
    }

    public enum Protocol {
        FTP, SFTP
    }

    private static final Logger logger = LoggerFactory.getLogger(FtpDataWriter.class);
    protected String server;
    protected String username;
    protected String password;
    protected FtpDataWriter.Protocol protocol = Protocol.FTP;
    protected FtpDataWriter.Format format = Format.CSV;
    protected String stagingDir;
    protected String remoteDir;
    protected Batch batch;
    protected Table table;
    protected Map<String, FileInfo> fileInfoByTable = new HashMap<String, FtpDataWriter.FileInfo>();
    protected FileSystemManager manager;
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();

    public void setRemoteDir(String remoteDir) {
        this.remoteDir = remoteDir;
    }

    public void setFormat(FtpDataWriter.Format format) {
        this.format = format;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public void setProtocol(FtpDataWriter.Protocol protocol) {
        this.protocol = protocol;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setStagingDir(String stagingDir) {
        this.stagingDir = stagingDir;
    }

    public void open(DataContext context) {
        try {
            manager = VFS.getManager();
        } catch (FileSystemException e) {
            throw new IoException(e);
        }
    }

    public void close() {
    }

    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }

    public void start(Batch batch) {
        this.batch = batch;
        this.statistics.put(batch, new Statistics());
    }

    public boolean start(Table table) {
        this.table = table;
        createFile(table, batch);
        return true;
    }

    public void write(CsvData data) {
        if (format == Format.CSV
                && (data.getDataEventType() == DataEventType.UPDATE || data.getDataEventType() == DataEventType.INSERT)) {
            printCsvData(data);
        }
    }

    public void end(Table table) {
    }

    public void end(Batch batch, boolean inError) {
        if (!inError) {
            try {
                closeFiles();
                sendFiles();
            } finally {
                deleteFiles();
            }
        } else {
            try {
                closeFiles();
            } finally {
                deleteFiles();
            }
        }
    }

    protected void createFile(Table table, Batch batch) {
        FileInfo fileInfo = fileInfoByTable.get(table.getFullyQualifiedName());
        if (fileInfo == null) {
            try {
                fileInfo = new FileInfo();
                fileInfo.outputFile = new File(stagingDir, batch.getBatchId() + "-"
                        + table.getFullyQualifiedName() + "." + format.name().toLowerCase());
                fileInfo.outputFile.getParentFile().mkdirs();
                fileInfo.outputFileWriter = new BufferedWriter(new FileWriter(fileInfo.outputFile));
                fileInfoByTable.put(table.getFullyQualifiedName(), fileInfo);
                if (format == Format.CSV) {
                    printCsvTableHeader();
                }
            } catch (IOException e) {
                throw new IoException(e);
            }
        }
    }

    protected void closeFiles() {
        Collection<FileInfo> fileInfos = fileInfoByTable.values();
        for (FileInfo fileInfo : fileInfos) {
            try {
                fileInfo.outputFileWriter.flush();
                fileInfo.outputFileWriter.close();
            } catch (IOException e) {
                logger.warn("", e);
            }
        }
    }

    protected void deleteFiles() {
        Collection<FileInfo> fileInfos = fileInfoByTable.values();
        for (FileInfo fileInfo : fileInfos) {
            try {
                fileInfo.outputFileWriter.close();
            } catch (IOException e) {
                logger.warn("", e);
            } finally {
                FileUtils.deleteQuietly(fileInfo.outputFile);
            }
        }
        fileInfoByTable.clear();
    }

    protected void sendFiles() {
        if (fileInfoByTable.size() > 0) {
            try {
                String sftpUri = buildUri();
                FileSystemOptions opts = new FileSystemOptions();
                FtpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, true);
                SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(opts, "no");
                SftpFileSystemConfigBuilder.getInstance().setSessionTimeout(opts, Duration.ofMillis(60000));
                Collection<FileInfo> fileInfos = fileInfoByTable.values();
                for (FileInfo fileInfo : fileInfos) {
                    FileObject fileObject = manager.resolveFile(
                            sftpUri + "/" + fileInfo.outputFile.getName(), opts);
                    FileObject localFileObject = manager.resolveFile(fileInfo.outputFile
                            .getAbsolutePath());
                    fileObject.copyFrom(localFileObject, Selectors.SELECT_SELF);
                    fileObject.close();
                }
            } catch (FileSystemException e) {
                logger.warn("If you have not configured your ftp connection it should be configured in conf/ftp-extensions.xml");
                throw new IoException(e);
            } catch (Exception e) {
                throw new IoException(e);
            } finally {
                manager.close();
            }
        }
    }

    protected String buildUri() {
        String credentials = "";
        if (StringUtils.isNotBlank(username)) {
            credentials = username;
            if (StringUtils.isNotBlank(password)) {
                credentials = credentials + ":" + password;
            }
            credentials = credentials + "@";
        }
        String path = "";
        if (StringUtils.isNotBlank(this.remoteDir)) {
            path = "/" + this.remoteDir;
        }
        return protocol.toString().toLowerCase() + "://" + credentials + server + path;
    }

    protected void printCsvTableHeader() {
        println(table.getColumnNames());
    }

    protected void printCsvData(CsvData data) {
        println(data.getParsedData(CsvData.ROW_DATA));
    }

    protected void println(String... data) {
        FileInfo fileInfo = fileInfoByTable.get(table.getFullyQualifiedName());
        if (fileInfo != null) {
            try {
                StringBuilder buffer = new StringBuilder();
                for (int i = 0; i < data.length; i++) {
                    if (i != 0) {
                        buffer.append(",");
                    }
                    buffer.append(data[i]);
                }
                buffer.append("\n");
                fileInfo.outputFileWriter.write(buffer.toString());
                long byteCount = buffer.length();
                statistics.get(batch).increment(DataWriterStatisticConstants.BYTECOUNT, byteCount);
            } catch (IOException e) {
                throw new IoException(e);
            }
        }
    }

    static class FileInfo {
        File outputFile;
        BufferedWriter outputFileWriter;
    }
}
