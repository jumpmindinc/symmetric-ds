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
package org.jumpmind.symmetric.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jumpmind.symmetric.common.ServerConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClusterPartitionGeneratorTest {
    private static final int UUID_STRING_LENGTH = 36;
    private static final int MAX_CONFIGURED_ID_LENGTH = 60;

    @BeforeEach
    public void setUp() throws Exception {
        resetCache();
        System.clearProperty(ServerConstants.CLUSTER_PARTITION_ID);
    }

    @AfterEach
    public void tearDown() throws Exception {
        resetCache();
        System.clearProperty(ServerConstants.CLUSTER_PARTITION_ID);
    }

    private void resetCache() throws Exception {
        Field f = ClusterPartitionGenerator.class.getDeclaredField("clusterPartitionId");
        f.setAccessible(true);
        f.set(null, null);
    }

    @Test
    public void resolve_configuredSystemProperty_usesConfiguredValue() {
        System.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "configured-partition-id");
        assertEquals("configured-partition-id", ClusterPartitionGenerator.resolve());
    }

    @Test
    public void resolve_configuredValueLongerThanMax_isTruncated() {
        System.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "a".repeat(100));
        assertEquals(MAX_CONFIGURED_ID_LENGTH, ClusterPartitionGenerator.resolve().length());
    }

    @Test
    public void resolve_noConfiguration_generatesRandomUuid() {
        String id = ClusterPartitionGenerator.resolve();
        assertNotNull(id);
        assertEquals(UUID_STRING_LENGTH, id.length());
    }

    @Test
    public void resolve_calledTwice_onlyResolvesOnceAndReturnsSameValue() {
        String first = ClusterPartitionGenerator.resolve();
        String second = ClusterPartitionGenerator.resolve();
        assertEquals(first, second);
    }

    @Test
    public void resolve_calledTwiceWithDifferentConfiguredValues_ignoresSecondValue() {
        System.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "first-value");
        String first = ClusterPartitionGenerator.resolve();
        System.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "second-value");
        String second = ClusterPartitionGenerator.resolve();
        assertEquals(first, second);
        assertEquals("first-value", second);
    }

    @Test
    public void writeAndReadClusterPartitionId_roundTripsThroughFile(@TempDir File tempDir) throws Exception {
        File clusterPartitionIdFile = new File(tempDir, "cluster-partition.uuid");
        Method write = ClusterPartitionGenerator.class.getDeclaredMethod("writeClusterPartitionId", File.class, String.class);
        write.setAccessible(true);
        write.invoke(null, clusterPartitionIdFile, "file-cluster-partition-id");
        Method read = ClusterPartitionGenerator.class.getDeclaredMethod("readClusterPartitionId", File.class);
        read.setAccessible(true);
        assertEquals("file-cluster-partition-id", read.invoke(null, clusterPartitionIdFile));
    }

    @Test
    public void readClusterPartitionId_missingFile_returnsNull(@TempDir File tempDir) throws Exception {
        File clusterPartitionIdFile = new File(tempDir, "does-not-exist.uuid");
        Method read = ClusterPartitionGenerator.class.getDeclaredMethod("readClusterPartitionId", File.class);
        read.setAccessible(true);
        assertNull(read.invoke(null, clusterPartitionIdFile));
    }
}
