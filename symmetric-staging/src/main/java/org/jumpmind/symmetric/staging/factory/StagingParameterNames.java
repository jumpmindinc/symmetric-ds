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

public final class StagingParameterNames {
    public static final String STAGING_DIR = "staging.dir";
    public static final String STAGING_SCRATCH_DIR = "staging.scratch.dir";
    public static final String STAGING_PROVIDER_TYPE = "staging.provider.type";
    public static final String STAGING_PROVIDER_URL = "staging.provider.url";
    public static final String STAGING_PROVIDER_BUCKET = "staging.provider.bucket";
    public static final String STAGING_ACCOUNT_TYPE = "staging.account.type";
    public static final String STAGING_ACCOUNT_KEY = "staging.account.key";
    public static final String STAGING_ACCOUNT_SECRET = "staging.account.secret";
    public static final String STAGING_LOW_SPACE_THRESHOLD_MEGABYTES = "staging.low.space.threshold.megabytes";
    public static final String STAGING_MEMORY_THRESHOLD_BYTES = "staging.memory.threshold.bytes";
    public static final String STAGING_LOCK_TTL_MS = "staging.lock.ttl.ms";
    public static final String STAGING_CHARSET = "staging.charset";
    public static final String STAGING_ENCRYPTION_ENABLED = "staging.encryption.enabled";
    public static final String STAGING_COMPRESSION_ENABLED = "staging.compression.enabled";
    public static final String STAGING_COMPRESSION_ALGORITHM = "staging.compression.algorithm";
    public static final String STAGING_CHECKSUM_ENABLED = "staging.checksum.enabled";
    public static final String ENV_STAGING_DIR = "SYM_STAGING_DIR";
    public static final String ENV_STAGING_SCRATCH_DIR = "SYM_STAGING_SCRATCH_DIR";
    public static final String ENV_STAGING_PROVIDER_TYPE = "SYM_STAGING_PROVIDER_TYPE";
    public static final String ENV_STAGING_PROVIDER_URL = "SYM_STAGING_PROVIDER_URL";
    public static final String ENV_STAGING_PROVIDER_BUCKET = "SYM_STAGING_PROVIDER_BUCKET";
    public static final String ENV_STAGING_ACCOUNT_TYPE = "SYM_STAGING_ACCOUNT_TYPE";
    public static final String ENV_STAGING_ACCOUNT_KEY = "SYM_STAGING_ACCOUNT_KEY";
    public static final String ENV_STAGING_ACCOUNT_SECRET = "SYM_STAGING_ACCOUNT_SECRET";

    private StagingParameterNames() {
    }
}
