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
package org.jumpmind.symmetric.wrapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ErrorManager;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class WrapperLogHandler extends StreamHandler {
    protected String filename;
    protected long maxByteCount;
    protected int maxLogCount;
    protected BufferedWriter writer;
    protected long byteCount;
    protected ScheduledExecutorService executor;

    public WrapperLogHandler(String filename, long maxByteCount, int maxLogCount) throws IOException {
        this.filename = filename;
        this.maxByteCount = maxByteCount;
        this.maxLogCount = maxLogCount;
        rotate();
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(new LogFlushTask(), 0, 2000, TimeUnit.MILLISECONDS);
    }

    protected final void rotate() throws IOException {
        if (writer != null) {
            writer.close();
        }
        File file = new File(filename);
        byteCount = 0;
        if (file.exists()) {
            byteCount = file.length();
        }
        if (byteCount >= maxByteCount) {
            new File(filename + "." + maxLogCount).delete();
            for (int i = maxLogCount - 1; i > 0; i--) {
                new File(filename + "." + i).renameTo(new File(filename + "." + (i + 1)));
            }
            new File(filename).renameTo(new File(filename + ".1"));
        }
        writer = new BufferedWriter(new FileWriter(file, true), 2048);
    }

    @Override
    public void publish(LogRecord record) {
        if (writer != null) {
            try {
                String msg = getFormatter().format(record);
                try {
                    writer.write(msg);
                    byteCount += msg.length();
                    if (byteCount >= maxByteCount) {
                        rotate();
                    }
                } catch (Exception e) {
                    reportError(null, e, ErrorManager.WRITE_FAILURE);
                }
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
            }
        }
    }

    @Override
    public void flush() {
        if (writer != null) {
            try {
                writer.flush();
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FLUSH_FAILURE);
            }
        }
    }

    @Override
    public void close() throws SecurityException {
        if (writer != null) {
            try {
                writer.close();
                writer = null;
            } catch (Exception e) {
                reportError(null, e, ErrorManager.CLOSE_FAILURE);
            }
        }
        if (executor != null) {
            try {
                executor.shutdown();
            } catch (Exception e) {
                reportError(null, e, ErrorManager.CLOSE_FAILURE);
            }
        }
    }

    class LogFlushTask implements Runnable {
        public void run() {
            flush();
        }
    }
}
