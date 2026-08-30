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
package org.jumpmind.symmetric.staging.api;

public enum StorageKind {
    FILESYSTEM, MEMORY, MINIO, AWS_S3, AZURE_BLOB;

    public static StorageKind fromProviderType(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            return FILESYSTEM;
        }
        switch (providerType.trim().toLowerCase()) {
            case "file":
                return FILESYSTEM;
            case "memory":
                return MEMORY;
            case "minio":
                return MINIO;
            case "aws_s3":
                return AWS_S3;
            case "azure_blob":
                return AZURE_BLOB;
            default:
                throw new IllegalArgumentException("Unknown staging.provider.type: " + providerType);
        }
    }
}
