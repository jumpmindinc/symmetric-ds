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

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.jumpmind.symmetric.staging.api.IStagedResource;

public final class ChecksumVerifier {
    private ChecksumVerifier() {
    }

    public static boolean verify(IStagedResource resource) {
        return verify(resource, ChecksumWriteStream.DEFAULT_ALGORITHM, ChecksumWriteStream.SIDECAR_SUFFIX);
    }

    public static boolean verify(IStagedResource resource, String algorithm, String suffix) {
        byte[] expected;
        try {
            expected = resource.readSidecar(suffix);
        } catch (IOException ex) {
            return true;
        }
        if (expected == null) {
            return true;
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("Unsupported checksum algorithm: " + algorithm, ex);
        }
        try (InputStream in = resource.openInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        } catch (IOException ex) {
            return false;
        }
        return Arrays.equals(expected, md.digest());
    }
}
