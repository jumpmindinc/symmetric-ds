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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.staging.api.IStagingManager;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StorageKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultStagingFactoryTest {
    @Test
    void file_provider_returnsFilesystemManager(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        Map<String, String> params = baseParams(stagingDir, scratchDir);
        StagingConfig config = new StagingParameterResolver(params, new HashMap<>()).buildConfig();
        IStagingManager manager = new DefaultStagingFactory().create(config);
        assertNotNull(manager);
        assertEquals(StorageKind.FILESYSTEM, manager.getStorageKind());
    }

    @Test
    void aws_s3_throwsNotImplemented(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        Map<String, String> params = baseParams(stagingDir, scratchDir);
        params.put(StagingParameterNames.STAGING_PROVIDER_TYPE, "aws_s3");
        StagingConfig config = new StagingParameterResolver(params, new HashMap<>()).buildConfig();
        assertThrows(NotImplementedException.class,
                () -> new DefaultStagingFactory().create(config));
    }

    @Test
    void azure_blob_throwsNotImplemented(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        Map<String, String> params = baseParams(stagingDir, scratchDir);
        params.put(StagingParameterNames.STAGING_PROVIDER_TYPE, "azure_blob");
        StagingConfig config = new StagingParameterResolver(params, new HashMap<>()).buildConfig();
        assertThrows(NotImplementedException.class,
                () -> new DefaultStagingFactory().create(config));
    }

    @Test
    void unknownProvider_throwsIllegalArgument(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        Map<String, String> params = baseParams(stagingDir, scratchDir);
        params.put(StagingParameterNames.STAGING_PROVIDER_TYPE, "blockchain");
        assertThrows(IllegalArgumentException.class,
                () -> new StagingParameterResolver(params, new HashMap<>()).buildConfig());
    }

    @Test
    void token_account_throwsNotImplemented(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        Map<String, String> params = baseParams(stagingDir, scratchDir);
        params.put(StagingParameterNames.STAGING_ACCOUNT_TYPE, "token");
        assertThrows(NotImplementedException.class,
                () -> new StagingParameterResolver(params, new HashMap<>()).buildConfig());
    }

    @Test
    void envVar_overridesEngineParameter(@TempDir Path stagingDir, @TempDir Path scratchDir) {
        Map<String, String> params = baseParams(stagingDir, scratchDir);
        params.put(StagingParameterNames.STAGING_DIR, stagingDir.resolve("from-param").toString());
        Map<String, String> env = new HashMap<>();
        env.put(StagingParameterNames.ENV_STAGING_DIR, stagingDir.resolve("from-env").toString());
        StagingConfig config = new StagingParameterResolver(params, env).buildConfig();
        assertEquals(stagingDir.resolve("from-env").toString(), config.getStagingDir());
    }

    @Test
    void defaults_areApplied() {
        Map<String, String> params = new HashMap<>();
        params.put(StagingParameterNames.STAGING_DIR, "/tmp/sym-staging");
        StagingConfig config = new StagingParameterResolver(params, new HashMap<>()).buildConfig();
        assertEquals(StorageKind.FILESYSTEM, config.getStorageKind());
        assertTrue(config.getLockTtl().toMillis() > 0);
        assertEquals(64L * 1024L, config.getMemoryThresholdBytes());
    }

    private static Map<String, String> baseParams(Path stagingDir, Path scratchDir) {
        Map<String, String> params = new HashMap<>();
        params.put(StagingParameterNames.STAGING_DIR, stagingDir.toString());
        params.put(StagingParameterNames.STAGING_SCRATCH_DIR, scratchDir.toString());
        params.put(StagingParameterNames.STAGING_PROVIDER_TYPE, "file");
        params.put(StagingParameterNames.STAGING_ACCOUNT_TYPE, "implied");
        return params;
    }
}
