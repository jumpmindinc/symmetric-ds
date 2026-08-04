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
package org.jumpmind.symmetric.io.stage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Write to an internal buffer up until the threshold. When the threshold is reached, flush the buffer to the file and write to the file from that point
 * forward.
 */
public class ThresholdFileWriter extends Writer {
    protected File file;
    protected BufferedWriter fileWriter;
    protected StringBuilder buffer;
    protected long threshhold;
    protected boolean append;

    /**
     * @param threshold
     *            The number of bytes at which to start writing to a file
     * @param file
     *            The file to write to after the threshold has been reached
     */
    public ThresholdFileWriter(long threshold, StringBuilder buffer, File file) {
        this(threshold, buffer, file, false);
    }

    /**
     * @param threshold
     *            The number of bytes at which to start writing to a file
     * @param file
     *            The file to write to after the threshold has been reached
     * @param append
     *            When true, write to the end of an existing file instead of truncating it
     */
    public ThresholdFileWriter(long threshold, StringBuilder buffer, File file, boolean append) {
        this.file = file;
        this.buffer = buffer;
        this.threshhold = threshold;
        this.append = append;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public void close() throws IOException {
        if (fileWriter != null) {
            fileWriter.close();
            fileWriter = null;
        }
    }

    @Override
    public void flush() throws IOException {
        if (fileWriter != null) {
            fileWriter.flush();
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (fileWriter != null) {
            fileWriter.write(cbuf, off, len);
        } else if (buffer == null || len + buffer.length() > threshhold) {
            file.getParentFile().mkdirs();
            fileWriter = getWriter();
            if (buffer != null) {
                fileWriter.write(buffer.toString());
                buffer.setLength(0);
                buffer = null;
            }
            fileWriter.write(cbuf, off, len);
            fileWriter.flush();
        } else {
            buffer.append(new String(cbuf), off, len);
        }
    }

    protected BufferedWriter getWriter() throws IOException {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, append), StandardCharsets.UTF_8.name()));
    }

    public BufferedReader getReader() throws IOException {
        if (file != null && file.exists()) {
            return new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8.name()));
        } else {
            return new BufferedReader(new StringReader(buffer.toString()));
        }
    }

    public void delete() {
        if (file != null && file.exists()) {
            file.delete();
        }
        file = null;
        if (buffer != null) {
            buffer.setLength(0);
            buffer.trimToSize();
        }
    }
}