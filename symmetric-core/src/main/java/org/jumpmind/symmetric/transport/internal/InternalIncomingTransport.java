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
package org.jumpmind.symmetric.transport.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.TransportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps an incoming stream that comes from memory
 */
public class InternalIncomingTransport implements IIncomingTransport {
    private static final Logger log = LoggerFactory.getLogger(InternalIncomingTransport.class);
    private static final long HEADERS_TIMEOUT_HOURS = 2;
    BufferedReader reader = null;
    InputStream is;
    Map<String, String> headers;
    CountDownLatch headersReady;

    public InternalIncomingTransport(InputStream is) {
        this(is, null, null);
    }

    public InternalIncomingTransport(InputStream is, Map<String, String> headers) {
        this(is, headers, null);
    }

    public InternalIncomingTransport(InputStream is, Map<String, String> headers, CountDownLatch headersReady) {
        this.is = is;
        this.reader = TransportUtils.toReader(is);
        this.headers = headers;
        this.headersReady = headersReady;
    }

    public InternalIncomingTransport(BufferedReader reader) {
        this(reader, null, null);
    }

    public InternalIncomingTransport(BufferedReader reader, Map<String, String> headers) {
        this(reader, headers, null);
    }

    public InternalIncomingTransport(BufferedReader reader, Map<String, String> headers, CountDownLatch headersReady) {
        this.reader = reader;
        this.headers = headers;
        this.headersReady = headersReady;
    }

    public void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
            }
            reader = null;
        }
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
            }
            is = null;
        }
    }

    public boolean isOpen() {
        return reader != null || is != null;
    }

    public BufferedReader openReader() throws IOException {
        return reader;
    }

    public InputStream openStream() throws IOException {
        return is;
    }

    public String getRedirectionUrl() {
        return null;
    }

    public String getUrl() {
        return "";
    }

    @Override
    public Map<String, String> getHeaders() {
        if (headersReady != null) {
            try {
                if (!headersReady.await(HEADERS_TIMEOUT_HOURS, TimeUnit.HOURS)) {
                    log.warn("Timeout waiting for internal transport headers");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return headers;
    }
}