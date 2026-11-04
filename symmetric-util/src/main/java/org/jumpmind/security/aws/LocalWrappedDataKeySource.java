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
package org.jumpmind.security.aws;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.security.SecurityConstants;

/**
 * Keeps the KMS-encrypted data key on the node: inline in {@link SecurityConstants#AWS_KMS_WRAPPED_KEY} when that is set, otherwise in the file named by
 * {@link SecurityConstants#AWS_KMS_WRAPPED_KEY_FILE}. The value is Base64 text, so the file's contents can be copied into the property for other nodes in a
 * cluster.
 */
class LocalWrappedDataKeySource implements WrappedDataKeySource {
    private static final String OWNER_READ_WRITE = "rw-------";
    private final String inlineKey;
    private final Path file;

    /**
     * @param file
     *            where the key is saved; may be null when the key is set inline
     */
    LocalWrappedDataKeySource(String inlineKey, Path file) {
        this.inlineKey = StringUtils.trimToNull(inlineKey);
        this.file = file;
    }

    @Override
    public Optional<byte[]> read() throws IOException {
        if (inlineKey != null) {
            return Optional.of(Base64.decodeBase64(inlineKey));
        }
        if (Files.exists(file)) {
            return Optional.of(Base64.decodeBase64(Files.readString(file, StandardCharsets.UTF_8).trim()));
        }
        return Optional.empty();
    }

    /**
     * Saves the encrypted data key to the file, readable only by its owner where the file system supports it.
     *
     * @return false when the file already exists, for example because another node sharing the directory saved its key first
     */
    @Override
    public boolean writeIfAbsent(byte[] wrappedKey) throws IOException {
        if (inlineKey != null) {
            throw new IllegalStateException("The encrypted data key is set by " + SecurityConstants.AWS_KMS_WRAPPED_KEY + " and is not written to a file");
        }
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        try {
            restrictToOwner(temp);
            Files.writeString(temp, Base64.encodeBase64String(wrappedKey), StandardCharsets.UTF_8);
            publish(temp);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Links the finished temporary file into place, which fails atomically when the file exists, so two nodes that start at once never overwrite each other's
     * key. File systems without hard links fall back to a move, which can still lose that race.
     */
    private void publish(Path temp) throws IOException {
        try {
            Files.createLink(file, temp);
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (UnsupportedOperationException | FileSystemException e) {
            Files.move(temp, file);
        }
    }

    @Override
    public String describe() {
        return inlineKey != null ? "system property " + SecurityConstants.AWS_KMS_WRAPPED_KEY : file.toAbsolutePath().toString();
    }

    private void restrictToOwner(Path path) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(OWNER_READ_WRITE));
        }
    }
}
