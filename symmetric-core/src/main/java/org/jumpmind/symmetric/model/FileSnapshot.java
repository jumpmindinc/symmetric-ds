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
package org.jumpmind.symmetric.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.exception.IoException;

public class FileSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum LastEventType {
        CREATE("C"), MODIFY("M"), DELETE("D");

        private LastEventType(String code) {
            this.code = code;
        }

        private String code;

        public String getCode() {
            return this.code;
        }

        public static LastEventType fromCode(String code) {
            if ("C".equals(code)) {
                return CREATE;
            } else if ("M".equals(code)) {
                return MODIFY;
            } else if ("D".equals(code)) {
                return DELETE;
            } else {
                return null;
            }
        }
    };

    private String triggerId;
    private String routerId;
    private String relativeDir;
    private String fileName;
    private LastEventType lastEventType;
    private String channelId;
    private String reloadChannelId;
    private long crc32Checksum;
    private long oldCrc32Checksum;
    private long fileSize;
    private long fileModifiedTime;
    private Date createTime = new Date();
    private String lastUpdateBy;
    private Date lastUpdateTime;

    public FileSnapshot() {
    }

    public FileSnapshot(FileSnapshot copy) {
        this.triggerId = copy.triggerId;
        this.routerId = copy.routerId;
        this.relativeDir = copy.relativeDir;
        this.fileName = copy.fileName;
        this.lastEventType = copy.lastEventType;
        this.crc32Checksum = copy.crc32Checksum;
        this.fileSize = copy.fileSize;
        this.fileModifiedTime = copy.fileModifiedTime;
        this.createTime = copy.createTime;
        this.lastUpdateBy = copy.lastUpdateBy;
        this.lastUpdateTime = copy.lastUpdateTime;
        this.channelId = copy.channelId;
        this.reloadChannelId = copy.getReloadChannelId();
    }

    public FileSnapshot(FileTriggerRouter fileTriggerRouter, File file, LastEventType lastEventType) {
        this(fileTriggerRouter, file, lastEventType, false);
    }

    public FileSnapshot(FileTriggerRouter fileTriggerRouter, File file, LastEventType lastEventType, boolean useCrc) {
        boolean isDelete = lastEventType == LastEventType.DELETE;
        this.triggerId = fileTriggerRouter.getFileTrigger().getTriggerId();
        this.channelId = fileTriggerRouter.getFileTrigger().getChannelId();
        this.reloadChannelId = fileTriggerRouter.getFileTrigger().getReloadChannelId();
        this.routerId = fileTriggerRouter.getRouter().getRouterId();
        this.lastEventType = lastEventType;
        this.lastUpdateTime = new Date();
        this.fileName = file.getName();
        this.relativeDir = file.getPath().replace('\\', '/');
        String baseDir = fileTriggerRouter.getFileTrigger().getBaseDir().replace('\\', '/');
        if (this.relativeDir.startsWith(baseDir)) {
            this.relativeDir = this.relativeDir.substring(baseDir.length());
        }
        if (this.relativeDir.endsWith(fileName)) {
            this.relativeDir = this.relativeDir.substring(0, this.relativeDir.lastIndexOf(fileName));
        }
        if (this.relativeDir.startsWith("/")) {
            this.relativeDir = this.relativeDir.substring(1);
        }
        if (this.relativeDir.endsWith("/")) {
            this.relativeDir = this.relativeDir.substring(0, this.relativeDir.length() - 1);
        }
        if (StringUtils.isBlank(relativeDir)) {
            this.relativeDir = ".";
        }
        this.fileSize = isDelete ? 0 : file.length();
        this.fileModifiedTime = isDelete ? 0 : file.lastModified();
        if (useCrc && file.isFile() && !isDelete) {
            try {
                this.crc32Checksum = FileUtils.checksumCRC32(file);
            } catch (FileNotFoundException ex) {
                this.lastEventType = LastEventType.DELETE;
                this.fileSize = 0;
                this.fileModifiedTime = 0;
            } catch (IOException ex) {
                throw new IoException(ex);
            }
        } else {
            this.crc32Checksum = -1;
        }
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public String getRouterId() {
        return routerId;
    }

    public void setRouterId(String routerId) {
        this.routerId = routerId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setReloadChannelId(String reloadChannelId) {
        this.reloadChannelId = reloadChannelId;
    }

    public String getReloadChannelId() {
        return reloadChannelId;
    }

    public String getRelativeDir() {
        return relativeDir;
    }

    public void setRelativeDir(String relativeDir) {
        this.relativeDir = relativeDir;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LastEventType getLastEventType() {
        return lastEventType;
    }

    public void setLastEventType(LastEventType lastEventType) {
        this.lastEventType = lastEventType;
        if (lastEventType == LastEventType.DELETE) {
            this.fileModifiedTime = 0;
            this.fileSize = 0;
        }
    }

    public long getCrc32Checksum() {
        return crc32Checksum;
    }

    public void setCrc32Checksum(long crc32Checksum) {
        this.crc32Checksum = crc32Checksum;
    }

    public long getOldCrc32Checksum() {
        return oldCrc32Checksum;
    }

    public void setOldCrc32Checksum(long oldCrc32Checksum) {
        this.oldCrc32Checksum = oldCrc32Checksum;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getFileModifiedTime() {
        return fileModifiedTime;
    }

    public void setFileModifiedTime(long fileModifiedTime) {
        this.fileModifiedTime = fileModifiedTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    /***
     * Checks whether two snapshots refer to the same file location (path + file name). Case-sensitive!
     * 
     * @return true, for identical file locations.
     */
    public boolean sameFile(FileSnapshot file) {
        return Strings.CS.equals(fileName, file.fileName) && Strings.CS.equals(relativeDir, file.relativeDir);
    }

    /***
     * Generates a search key helpful for indexing file locations (using a hash map). Logic must be equivalent to the sameFile() method!
     * 
     * @return fileName + '/' + relativeDir
     */
    public String generateSearchKey() {
        StringBuilder b = new StringBuilder(fileName.length() + File.pathSeparator.length() + relativeDir.length());
        b.append(fileName);
        b.append(File.pathSeparator);
        b.append(relativeDir);
        return b.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (crc32Checksum ^ (crc32Checksum >>> 32));
        result = prime * result + (int) (fileModifiedTime ^ (fileModifiedTime >>> 32));
        result = prime * result + ((fileName == null) ? 0 : fileName.hashCode());
        result = prime * result + ((relativeDir == null) ? 0 : relativeDir.hashCode());
        result = prime * result + (int) (fileSize ^ (fileSize >>> 32));
        result = prime * result + ((lastEventType == null) ? 0 : lastEventType.hashCode());
        result = prime * result + ((triggerId == null) ? 0 : triggerId.hashCode());
        result = prime * result + ((routerId == null) ? 0 : routerId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FileSnapshot other = (FileSnapshot) obj;
        if (crc32Checksum != other.crc32Checksum)
            return false;
        if (fileModifiedTime != other.fileModifiedTime) {
            return false;
        }
        if (fileName == null) {
            if (other.fileName != null)
                return false;
        } else if (!fileName.equals(other.fileName))
            return false;
        if (relativeDir == null) {
            if (other.relativeDir != null)
                return false;
        } else if (!relativeDir.equals(other.relativeDir))
            return false;
        if (fileSize != other.fileSize)
            return false;
        if (lastEventType != other.lastEventType)
            return false;
        if (triggerId == null) {
            if (other.triggerId != null)
                return false;
        } else if (!triggerId.equals(other.triggerId))
            return false;
        if (routerId == null) {
            if (other.routerId != null)
                return false;
        } else if (!routerId.equals(other.routerId))
            return false;
        return true;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("fileName: ").append(fileName).append(", relativeDir: ").append(relativeDir);
        sb.append(", fileSize: ").append(fileSize).append(" fileModifiedTime: ").append(new Date(fileModifiedTime));
        return sb.toString();
    }
}
