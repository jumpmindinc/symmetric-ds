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
package org.jumpmind.symmetric.transport.file;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.transport.IIncomingTransport;

public class FileIncomingTransport implements IIncomingTransport {
    Node remoteNode;
    String incomingDir;
    String archiveDir;
    String errorDir;
    File incomingFile;
    BufferedReader reader;
    InputStream in;
    boolean open = true;

    public FileIncomingTransport(Node remoteNode, Node localNode, String incomingDir, String archiveDir, String errorDir) {
        this.remoteNode = remoteNode;
        this.incomingDir = incomingDir;
        this.archiveDir = archiveDir;
        this.errorDir = errorDir;
    }

    @Override
    public BufferedReader openReader() throws IOException {
        incomingFile = getIncomingFile("csv");
        if (incomingFile != null) {
            reader = new BufferedReader(new FileReader(incomingFile));
        } else {
            reader = new BufferedReader(new StringReader(""));
        }
        return reader;
    }

    @Override
    public InputStream openStream() throws IOException {
        incomingFile = getIncomingFile("zip");
        if (incomingFile != null) {
            in = new FileInputStream(incomingFile);
        } else {
            in = new ByteArrayInputStream(new byte[0]);
        }
        return in;
    }

    protected File getIncomingFile(String fileExtension) {
        File file = null;
        String[] files = new File(incomingDir).list(new FileIncomingFilter(remoteNode, fileExtension));
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            File firstFile = new File(incomingDir + File.separator + files[0]);
            long lastModified = firstFile.lastModified();
            if (lastModified > 0 && System.currentTimeMillis() - lastModified > 3000) {
                file = firstFile;
            }
        }
        return file;
    }

    @Override
    public void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
        }
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
        }
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public String getRedirectionUrl() {
        return null;
    }

    @Override
    public String getUrl() {
        return "";
    }

    public void complete(boolean success) {
        if (incomingFile != null) {
            if (success) {
                if (StringUtils.isNotBlank(archiveDir)) {
                    incomingFile.renameTo(new File(archiveDir + File.separator + incomingFile.getName()));
                } else {
                    incomingFile.delete();
                }
            } else if (StringUtils.isNotBlank(errorDir)) {
                incomingFile.renameTo(new File(errorDir + File.separator + incomingFile.getName()));
            }
        }
    }

    public static class FileIncomingFilter implements FilenameFilter {
        String startFilter;
        String endFilter;

        public FileIncomingFilter(Node remoteNode, String fileExtension) {
            startFilter = remoteNode.getNodeGroupId() + "-" + remoteNode.getNodeId();
            endFilter = "." + fileExtension;
        }

        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith(startFilter) && name.endsWith(endFilter);
        }
    }

    @Override
    public Map<String, String> getHeaders() {
        return null;
    }
}
