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
package org.jumpmind.symmetric.staging.factory;

import java.util.ServiceLoader;

import org.jumpmind.symmetric.staging.api.IStagingFactory;
import org.jumpmind.symmetric.staging.api.IStagingManager;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StorageKind;
import org.jumpmind.symmetric.staging.fs.FileSystemStagingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultStagingFactory implements IStagingFactory {
    private static final Logger log = LoggerFactory.getLogger(DefaultStagingFactory.class);

    @Override
    public IStagingManager create(StagingConfig config) {
        StorageKind storageKind = config.getStorageKind();
        switch (storageKind) {
            case FILESYSTEM:
                return new FileSystemStagingManager(config);
            case MEMORY:
                throw new NotImplementedException("staging.provider.type=memory is not yet supported. "
                        + "Use staging.provider.type=file with a tmpfs-mounted staging.dir for an in-memory experience.");
            case MINIO:
            case AWS_S3:
            case AZURE_BLOB:
                return createFromExtension(config, storageKind);
            default:
                throw new IllegalArgumentException("Unsupported storage kind: " + storageKind);
        }
    }

    @Override
    public boolean supports(StorageKind storageKind) {
        return storageKind == StorageKind.FILESYSTEM;
    }

    private IStagingManager createFromExtension(StagingConfig config, StorageKind storageKind) {
        ServiceLoader<IStagingFactory> loader = ServiceLoader.load(IStagingFactory.class);
        for (IStagingFactory factory : loader) {
            if (factory.getClass().equals(this.getClass())) {
                continue;
            }
            if (factory.supports(storageKind)) {
                log.info("Delegating staging factory to {} for storage kind {}",
                        factory.getClass().getName(), storageKind);
                return factory.create(config);
            }
        }
        switch (storageKind) {
            case MINIO:
                throw new NotImplementedException("staging.provider.type=minio requires the symmetric-pro-staging "
                        + "module on the classpath but it was not found");
            case AWS_S3:
                throw new NotImplementedException("staging.provider.type=aws_s3 is not yet implemented "
                        + "(tracked by SYM-7428)");
            case AZURE_BLOB:
                throw new NotImplementedException("staging.provider.type=azure_blob is not yet implemented "
                        + "(tracked by SYM-7427)");
            default:
                throw new IllegalStateException("Unreachable");
        }
    }
}
