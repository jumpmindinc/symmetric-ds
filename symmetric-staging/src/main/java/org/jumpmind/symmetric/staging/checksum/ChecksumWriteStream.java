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
package org.jumpmind.symmetric.staging.checksum;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.jumpmind.symmetric.staging.api.IStagedResource;

public class ChecksumWriteStream extends FilterOutputStream {
    public static final String DEFAULT_ALGORITHM = "SHA-256";
    public static final String SIDECAR_SUFFIX = ".sha256";
    private final MessageDigest md;
    private final IStagedResource resource;
    private final String suffix;
    private boolean closed;

    public ChecksumWriteStream(OutputStream delegate, IStagedResource resource) {
        this(delegate, resource, DEFAULT_ALGORITHM, SIDECAR_SUFFIX);
    }

    public ChecksumWriteStream(OutputStream delegate, IStagedResource resource, String algorithm, String suffix) {
        super(delegate);
        this.resource = resource;
        this.suffix = suffix;
        try {
            this.md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("Unsupported checksum algorithm: " + algorithm, ex);
        }
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        md.update((byte) b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        md.update(b, off, len);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            super.close();
        } finally {
            resource.writeSidecar(suffix, md.digest());
        }
    }
}
