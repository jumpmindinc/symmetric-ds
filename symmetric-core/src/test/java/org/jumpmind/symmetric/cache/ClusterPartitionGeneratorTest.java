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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.util.AppUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

public class ClusterPartitionGeneratorTest {
    private static final int UUID_STRING_LENGTH = 36;
    private static final int MAX_CONFIGURED_ID_LENGTH = 60;
    private static final int MAX_SERVER_ID_LENGTH = 255;

    @BeforeEach
    public void setUp() throws Exception {
        resetCache();
        System.clearProperty(ServerConstants.CLUSTER_PARTITION_ID);
        System.clearProperty(SystemConstants.SYSPROP_LAUNCHER);
        System.clearProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        clearServerIdProperties();
    }

    @AfterEach
    public void tearDown() throws Exception {
        resetCache();
        System.clearProperty(ServerConstants.CLUSTER_PARTITION_ID);
        System.clearProperty(SystemConstants.SYSPROP_LAUNCHER);
        System.clearProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        clearServerIdProperties();
    }

    private void clearServerIdProperties() {
        System.clearProperty(ServerConstants.CLUSTER_SERVER_ID);
        System.clearProperty("bind.address");
        System.clearProperty("jboss.bind.address");
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
    public void resolve_noConfiguration_generatesRandomUuid(@TempDir File tempDir) throws Exception {
        String id = resolveWithNoCachedValueAnywhere(tempDir);
        assertNotNull(id);
        assertEquals(UUID_STRING_LENGTH, id.length());
    }

    @Test
    public void resolve_noConfiguration_generatedIdHasAutoMarker(@TempDir File tempDir) throws Exception {
        String id = resolveWithNoCachedValueAnywhere(tempDir);
        int byte4 = Integer.parseInt(id.substring(9, 11), 16);
        int byte5 = Integer.parseInt(id.substring(11, 13), 16);
        assertEquals(0xaa, byte4);
        assertEquals(0xaa, byte5);
    }

    /**
     * Forces resolve() down the "nothing cached yet" path deterministically: launcher mode with an empty conf dir means readClusterPartitionId() hits the file
     * branch (and finds nothing) rather than the classpath-resource branch, which src/test/resources/cluster-partition.uuid makes non-empty for the whole
     * module.
     */
    private String resolveWithNoCachedValueAnywhere(File tempDir) {
        System.setProperty(SystemConstants.SYSPROP_LAUNCHER, "true");
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getSymHome).thenReturn(tempDir.getAbsolutePath());
            return ClusterPartitionGenerator.resolve();
        }
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

    @Test
    public void readClusterPartitionId_noFile_fallsBackToClasspathResource() throws Exception {
        Method read = ClusterPartitionGenerator.class.getDeclaredMethod("readClusterPartitionId", File.class);
        read.setAccessible(true);
        assertEquals("classpath-cluster-partition-id", read.invoke(null, (File) null));
    }

    @Test
    public void resolve_launcherModeWithExistingFile_readsFileWithoutGeneratingNewId(@TempDir File tempDir) throws Exception {
        System.setProperty(SystemConstants.SYSPROP_LAUNCHER, "true");
        File confDir = new File(tempDir, "conf");
        confDir.mkdirs();
        File partitionFile = new File(confDir, "cluster-partition.uuid");
        try (FileOutputStream out = new FileOutputStream(partitionFile)) {
            out.write("existing-file-partition-id".getBytes(Charset.defaultCharset()));
        }
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getSymHome).thenReturn(tempDir.getAbsolutePath());
            assertEquals("existing-file-partition-id", ClusterPartitionGenerator.resolve());
        }
    }

    @Test
    public void resolve_launcherModeNoExistingFile_generatesAndPersistsNewId(@TempDir File tempDir) throws Exception {
        System.setProperty(SystemConstants.SYSPROP_LAUNCHER, "true");
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getSymHome).thenReturn(tempDir.getAbsolutePath());
            String id = ClusterPartitionGenerator.resolve();
            File partitionFile = new File(tempDir, "conf/cluster-partition.uuid");
            assertTrue(partitionFile.exists());
            assertEquals(id, FileUtils.readFileToString(partitionFile, Charset.defaultCharset()).trim());
        }
    }

    @Test
    public void writeClusterPartitionId_parentPathIsRegularFile_swallowsExceptionAndDoesNotThrow(@TempDir File tempDir) throws Exception {
        File notADirectory = new File(tempDir, "not-a-directory");
        assertTrue(notADirectory.createNewFile());
        File target = new File(notADirectory, "cluster-partition.uuid");
        Method write = ClusterPartitionGenerator.class.getDeclaredMethod("writeClusterPartitionId", File.class, String.class);
        write.setAccessible(true);
        assertDoesNotThrow(() -> write.invoke(null, target, "some-id"));
        assertFalse(target.exists());
    }

    @Test
    public void resolveServerId_configuredSystemProperty_usesConfiguredValue() {
        System.setProperty(ServerConstants.CLUSTER_SERVER_ID, "configured-server-id");
        assertEquals("configured-server-id", ClusterPartitionGenerator.resolveServerId());
    }

    @Test
    public void resolveServerId_configuredValueLongerThanMax_isTruncated() {
        System.setProperty(ServerConstants.CLUSTER_SERVER_ID, "a".repeat(300));
        assertEquals(MAX_SERVER_ID_LENGTH, ClusterPartitionGenerator.resolveServerId().length());
    }

    @Test
    public void resolveServerId_noClusterServerId_fallsBackToBindAddress() {
        System.setProperty("bind.address", "10.0.0.1");
        assertEquals("10.0.0.1", ClusterPartitionGenerator.resolveServerId());
    }

    @Test
    public void resolveServerId_noClusterServerIdOrBindAddress_fallsBackToJbossBindAddress() {
        System.setProperty("jboss.bind.address", "10.0.0.2");
        assertEquals("10.0.0.2", ClusterPartitionGenerator.resolveServerId());
    }

    @Test
    public void resolveServerId_clusterServerIdTakesPrecedenceOverBindAddress() {
        System.setProperty(ServerConstants.CLUSTER_SERVER_ID, "configured-server-id");
        System.setProperty("bind.address", "10.0.0.1");
        assertEquals("configured-server-id", ClusterPartitionGenerator.resolveServerId());
    }

    @Test
    public void resolveServerId_bindAddressTakesPrecedenceOverJbossBindAddress() {
        System.setProperty("bind.address", "10.0.0.1");
        System.setProperty("jboss.bind.address", "10.0.0.2");
        assertEquals("10.0.0.1", ClusterPartitionGenerator.resolveServerId());
    }

    @Test
    public void resolveServerId_noConfiguration_fallsBackToHostname() throws Exception {
        assertEquals(AppUtils.getHostName(), ClusterPartitionGenerator.resolveServerId());
    }

    @Test
    public void resolveServerId_hostnameLookupThrows_fallsBackToUnknown() {
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getHostName).thenThrow(new RuntimeException("no hostname available"));
            assertEquals("unknown", ClusterPartitionGenerator.resolveServerId());
        }
    }

    @Test
    public void isClusterLockingEnabled_propertiesValueTrue_returnsTrue() {
        Properties properties = new Properties();
        properties.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        assertTrue(ClusterPartitionGenerator.isClusterLockingEnabled(properties));
    }

    @Test
    public void isClusterLockingEnabled_propertiesBlank_fallsBackToSystemProperty() {
        System.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        assertTrue(ClusterPartitionGenerator.isClusterLockingEnabled(new Properties()));
    }

    @Test
    public void isClusterLockingEnabled_noProperties_fallsBackToSystemProperty() {
        System.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        assertTrue(ClusterPartitionGenerator.isClusterLockingEnabled(null));
    }

    @Test
    public void isClusterLockingEnabled_propertiesValueTakesPrecedenceOverSystemProperty() {
        Properties properties = new Properties();
        properties.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "false");
        System.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        assertFalse(ClusterPartitionGenerator.isClusterLockingEnabled(properties));
    }

    @Test
    public void isClusterLockingEnabled_noConfiguration_returnsFalse() {
        assertFalse(ClusterPartitionGenerator.isClusterLockingEnabled(null));
    }

    @Test
    void testApplyUuidMarker_embeddingAutoMarker() {
        UUID original = UUID.fromString("12345678-9abc-def0-1234-567890abcdef");
        UUID marked = ClusterPartitionGenerator.applyUuidMarker(original, ServerConstants.PARTITION_ID_MARKER_AUTO);
        assertEquals("12345678-aaaa-def0-1234-567890abcdef", marked.toString());
    }

    @Test
    void testApplyUuidMarker_embeddingHardwareMarker() {
        UUID original = UUID.fromString("12345678-9abc-def0-1234-567890abcdef");
        UUID marked = ClusterPartitionGenerator.applyUuidMarker(original, ServerConstants.PARTITION_ID_MARKER_HARDWARE);
        assertEquals("12345678-bbbb-def0-1234-567890abcdef", marked.toString());
    }

    @Test
    void testApplyUuidMarker_embeddingConfiguredMarker() {
        UUID original = UUID.fromString("12345678-9abc-def0-1234-567890abcdef");
        UUID marked = ClusterPartitionGenerator.applyUuidMarker(original, ServerConstants.PARTITION_ID_MARKER_CONFIGURED);
        assertEquals("12345678-cccc-def0-1234-567890abcdef", marked.toString());
    }

    @Test
    void testApplyUuidMarker_preservesAllOtherBytes() {
        UUID original = UUID.fromString("aabbccdd-eeff-1122-3344-556677889900");
        UUID marked = ClusterPartitionGenerator.applyUuidMarker(original, ServerConstants.PARTITION_ID_MARKER_AUTO);
        String s = marked.toString();
        assertEquals("aabbccdd", s.substring(0, 8));
        assertEquals("1122", s.substring(14, 18));
        assertEquals("3344-556677889900", s.substring(19));
    }

    @Test
    void testApplyUuidMarkerToId_hostnamePrefix() {
        String input = "myhost-12345678-9abc-def0-1234-567890abcdef";
        String result = ClusterPartitionGenerator.applyUuidMarkerToId(input, ServerConstants.PARTITION_ID_MARKER_AUTO);
        assertEquals("myhost-12345678-aaaa-def0-1234-567890abcdef", result);
    }

    @Test
    void testApplyUuidMarkerToId_plainUuid() {
        String input = "12345678-9abc-def0-1234-567890abcdef";
        String result = ClusterPartitionGenerator.applyUuidMarkerToId(input, ServerConstants.PARTITION_ID_MARKER_CONFIGURED);
        assertEquals("12345678-cccc-def0-1234-567890abcdef", result);
    }

    @Test
    void testApplyUuidMarkerToId_nullUsesZeroUuidWithMarker() {
        String result = ClusterPartitionGenerator.applyUuidMarkerToId(null, ServerConstants.PARTITION_ID_MARKER_AUTO);
        assertEquals(36, result.length());
        int byte4 = Integer.parseInt(result.substring(9, 11), 16);
        int byte5 = Integer.parseInt(result.substring(11, 13), 16);
        assertEquals(0xaa, byte4);
        assertEquals(0xaa, byte5);
    }

    @Test
    void testApplyUuidMarkerToId_shortStringPrefixedBeforeZeroUuid() {
        String result = ClusterPartitionGenerator.applyUuidMarkerToId("abc", ServerConstants.PARTITION_ID_MARKER_AUTO);
        assertEquals(39, result.length());
        assertTrue(result.startsWith("abc"));
        int uuidStart = result.length() - 36;
        int byte4 = Integer.parseInt(result.substring(uuidStart + 9, uuidStart + 11), 16);
        int byte5 = Integer.parseInt(result.substring(uuidStart + 11, uuidStart + 13), 16);
        assertEquals(0xaa, byte4);
        assertEquals(0xaa, byte5);
    }

    @Test
    void testApplyUuidMarkerToId_nonUuidSuffixReturnedUnchanged() {
        String input = "prefix-GGGGGGGG-GGGG-GGGG-GGGG-GGGGGGGGGGGG";
        assertEquals(input, ClusterPartitionGenerator.applyUuidMarkerToId(input, ServerConstants.PARTITION_ID_MARKER_AUTO));
    }
}
