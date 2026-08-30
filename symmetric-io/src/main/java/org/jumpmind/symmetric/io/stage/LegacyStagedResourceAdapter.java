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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.staging.api.IStreamCipherProvider;
import org.jumpmind.symmetric.staging.api.IStreamCompressionProvider;
import org.jumpmind.symmetric.staging.api.ResourceState;

public class LegacyStagedResourceAdapter implements IStagedResource {
    private final org.jumpmind.symmetric.staging.api.IStagedResource delegate;
    private final IStreamCipherProvider cipher;
    private final IStreamCompressionProvider compression;
    private final boolean checksumEnabled;

    public LegacyStagedResourceAdapter(org.jumpmind.symmetric.staging.api.IStagedResource delegate,
            IStreamCipherProvider cipher) {
        this(delegate, cipher, null, false);
    }

    public LegacyStagedResourceAdapter(org.jumpmind.symmetric.staging.api.IStagedResource delegate,
            IStreamCipherProvider cipher, boolean checksumEnabled) {
        this(delegate, cipher, null, checksumEnabled);
    }

    public LegacyStagedResourceAdapter(org.jumpmind.symmetric.staging.api.IStagedResource delegate,
            IStreamCipherProvider cipher, IStreamCompressionProvider compression, boolean checksumEnabled) {
        this.delegate = delegate;
        this.cipher = cipher;
        this.compression = compression;
        this.checksumEnabled = checksumEnabled;
    }

    public org.jumpmind.symmetric.staging.api.IStagedResource getDelegate() {
        return delegate;
    }

    private byte[] aad() {
        return delegate.getPath().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public BufferedReader getReader() {
        if (cipher == null) {
            return delegate.openReader(getCharset());
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), getCharset()));
    }

    @Override
    public BufferedWriter getWriter(long threshold) {
        if (cipher == null) {
            return delegate.openWriter(getCharset(), threshold);
        }
        return new BufferedWriter(new OutputStreamWriter(getOutputStream(), getCharset()));
    }

    @Override
    public OutputStream getOutputStream() {
        return getOutputStream(false);
    }

    @Override
    public OutputStream getOutputStream(boolean append) {
        try {
            OutputStream stream = delegate.openOutputStream(append);
            if (checksumEnabled) {
                stream = new org.jumpmind.symmetric.staging.checksum.ChecksumWriteStream(stream, delegate);
            }
            if (cipher != null) {
                stream = cipher.wrapEncrypt(stream, aad());
            }
            if (compression != null) {
                stream = compression.wrapCompress(stream);
            }
            return stream;
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    @Override
    public InputStream getInputStream() {
        try {
            InputStream stream = delegate.openInputStream();
            if (cipher != null) {
                stream = cipher.wrapDecrypt(stream, aad());
            }
            if (compression != null) {
                stream = compression.wrapDecompress(stream);
            }
            return stream;
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    @Override
    public File getFile() {
        return delegate.getFilesystemFile();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void closeReaders() {
        delegate.closeReaders();
    }

    @Override
    public long getSize() {
        return delegate.getSize();
    }

    @Override
    public State getState() {
        return delegate.getState() == ResourceState.DONE ? State.DONE : State.CREATE;
    }

    @Override
    public String getPath() {
        return delegate.getPath();
    }

    @Override
    public void setState(State state) {
        delegate.setState(state == State.DONE ? ResourceState.DONE : ResourceState.CREATE);
    }

    @Override
    public long getLastUpdateTime() {
        return delegate.getLastUpdateTime();
    }

    @Override
    public void refreshLastUpdateTime() {
        delegate.refreshLastUpdateTime();
    }

    @Override
    public boolean isFileResource() {
        return delegate.isFileResource();
    }

    @Override
    public boolean isMemoryResource() {
        return delegate.isMemoryResource();
    }

    @Override
    public boolean delete() {
        return delegate.delete();
    }

    @Override
    public boolean exists() {
        return delegate.exists();
    }

    @Override
    public boolean isInUse() {
        return delegate.isInUse();
    }

    @Override
    public void dereference() {
        delegate.dereference();
    }

    @Override
    public void reference() {
        delegate.reference();
    }

    private Charset getCharset() {
        Charset configured = delegate.getOptions() != null ? delegate.getOptions().getCharset() : null;
        return configured != null ? configured : StandardCharsets.UTF_8;
    }
}
