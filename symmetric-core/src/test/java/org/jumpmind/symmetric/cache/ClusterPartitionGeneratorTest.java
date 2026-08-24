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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.jumpmind.symmetric.service.impl.StartupParameterService;
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
        System.clearProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        clearServerIdProperties();
    }

    @AfterEach
    public void tearDown() throws Exception {
        resetCache();
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
    public void resolveWithStartupParameterService_configuredValue_usesConfiguredValue() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "configured-partition-id");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals("configured-partition-id", ClusterPartitionGenerator.resolve(startupParameterService));
    }

    @Test
    public void resolveWithStartupParameterService_configuredValueLongerThanMax_isTruncated() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "a".repeat(100));
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals(MAX_CONFIGURED_ID_LENGTH, ClusterPartitionGenerator.resolve(startupParameterService).length());
    }

    @Test
    public void resolveWithStartupParameterService_launcherModeWithExistingFile_readsFileWithoutGeneratingNewId(@TempDir File tempDir) throws Exception {
        File confDir = new File(tempDir, "conf");
        confDir.mkdirs();
        File partitionFile = new File(confDir, "cluster-partition.uuid");
        try (FileOutputStream out = new FileOutputStream(partitionFile)) {
            out.write("existing-file-partition-id".getBytes(Charset.defaultCharset()));
        }
        TypedProperties merged = new TypedProperties();
        merged.setProperty(SystemConstants.SYSPROP_LAUNCHER, "true");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getSymHome).thenReturn(tempDir.getAbsolutePath());
            assertEquals("existing-file-partition-id", ClusterPartitionGenerator.resolve(startupParameterService));
        }
    }

    @Test
    public void resolveWithStartupParameterService_launcherModeNoExistingFile_generatesAndPersistsNewId(@TempDir File tempDir) throws Exception {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(SystemConstants.SYSPROP_LAUNCHER, "true");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getSymHome).thenReturn(tempDir.getAbsolutePath());
            String id = ClusterPartitionGenerator.resolve(startupParameterService);
            File partitionFile = new File(tempDir, "conf/cluster-partition.uuid");
            assertTrue(partitionFile.exists());
            assertEquals(id, FileUtils.readFileToString(partitionFile, Charset.defaultCharset()).trim());
        }
    }

    @Test
    public void resolveWithStartupParameterService_noCachedValueAnywhere_generatesRandomUuidWithAutoMarker(@TempDir File tempDir) throws Exception {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(SystemConstants.SYSPROP_LAUNCHER, "true");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getSymHome).thenReturn(tempDir.getAbsolutePath());
            String id = ClusterPartitionGenerator.resolve(startupParameterService);
            assertEquals(UUID_STRING_LENGTH, id.length());
            int byte4 = Integer.parseInt(id.substring(9, 11), 16);
            int byte5 = Integer.parseInt(id.substring(11, 13), 16);
            assertEquals(0xaa, byte4);
            assertEquals(0xaa, byte5);
        }
    }

    @Test
    public void resolveWithStartupParameterService_calledTwice_onlyResolvesOnceAndReturnsSameValue() {
        IStartupParameterService startupParameterService = new StartupParameterService(new TypedProperties(), new TypedProperties(), Map.of(), Map.of());
        String first = ClusterPartitionGenerator.resolve(startupParameterService);
        String second = ClusterPartitionGenerator.resolve(startupParameterService);
        assertEquals(first, second);
    }

    @Test
    public void resolveWithStartupParameterService_calledTwiceWithDifferentConfiguredValues_ignoresSecondValue() {
        TypedProperties firstMerged = new TypedProperties();
        firstMerged.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "first-value");
        IStartupParameterService firstStartupParameterService = new StartupParameterService(firstMerged, new TypedProperties(), Map.of(), Map.of());
        String first = ClusterPartitionGenerator.resolve(firstStartupParameterService);
        TypedProperties secondMerged = new TypedProperties();
        secondMerged.setProperty(ServerConstants.CLUSTER_PARTITION_ID, "second-value");
        IStartupParameterService secondStartupParameterService = new StartupParameterService(secondMerged, new TypedProperties(), Map.of(), Map.of());
        String second = ClusterPartitionGenerator.resolve(secondStartupParameterService);
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
    public void resolveServerIdWithProperties_configuredValue_usesConfiguredValue() {
        Properties properties = new Properties();
        properties.setProperty(ServerConstants.CLUSTER_SERVER_ID, "configured-server-id");
        assertEquals("configured-server-id", ClusterPartitionGenerator.resolveServerId(properties));
    }

    @Test
    public void resolveServerIdWithProperties_configuredValueLongerThanMax_isTruncated() {
        Properties properties = new Properties();
        properties.setProperty(ServerConstants.CLUSTER_SERVER_ID, "a".repeat(300));
        assertEquals(MAX_SERVER_ID_LENGTH, ClusterPartitionGenerator.resolveServerId(properties).length());
    }

    @Test
    public void resolveServerIdWithProperties_blankConfiguredValue_fallsBackToHostname() throws Exception {
        assertEquals(AppUtils.getHostName(), ClusterPartitionGenerator.resolveServerId(new Properties()));
    }

    @Test
    public void resolveServerIdWithProperties_nullProperties_fallsBackToHostname() {
        assertEquals(AppUtils.getHostName(), ClusterPartitionGenerator.resolveServerId((Properties) null));
    }

    @Test
    public void resolveServerIdWithStartupParameterService_configuredValue_usesConfiguredValue() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "configured-server-id");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals("configured-server-id", ClusterPartitionGenerator.resolveServerId(startupParameterService));
    }

    @Test
    public void resolveServerIdWithStartupParameterService_clusterServerIdBlank_fallsBackToBindAddress() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "");
        merged.setProperty("bind.address", "10.0.0.1");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals("10.0.0.1", ClusterPartitionGenerator.resolveServerId(startupParameterService));
    }

    @Test
    public void resolveServerIdWithStartupParameterService_noConfiguration_fallsBackToDefaultHostnameToken() {
        IStartupParameterService startupParameterService = new StartupParameterService(new TypedProperties(), new TypedProperties(), Map.of(), Map.of());
        assertEquals(AppUtils.getHostName(), ClusterPartitionGenerator.resolveServerId(startupParameterService));
    }

    @Test
    public void resolveServerIdWithStartupParameterService_configuredValueLongerThanMax_isTruncated() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "a".repeat(300));
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals(MAX_SERVER_ID_LENGTH, ClusterPartitionGenerator.resolveServerId(startupParameterService).length());
    }

    @Test
    public void resolveServerIdWithStartupParameterService_clusterServerIdTakesPrecedenceOverBindAddress() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "configured-server-id");
        merged.setProperty("bind.address", "10.0.0.1");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals("configured-server-id", ClusterPartitionGenerator.resolveServerId(startupParameterService));
    }

    @Test
    public void resolveServerIdWithStartupParameterService_bindAddressTakesPrecedenceOverJbossBindAddress() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "");
        merged.setProperty("bind.address", "10.0.0.1");
        merged.setProperty("jboss.bind.address", "10.0.0.2");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals("10.0.0.1", ClusterPartitionGenerator.resolveServerId(startupParameterService));
    }

    @Test
    public void resolveServerIdWithStartupParameterService_clusterServerIdAndBindAddressBlank_fallsBackToJbossBindAddress() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "");
        merged.setProperty("jboss.bind.address", "10.0.0.2");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals("10.0.0.2", ClusterPartitionGenerator.resolveServerId(startupParameterService));
    }

    @Test
    public void resolveServerIdWithStartupParameterService_allConfigurationBlank_fallsBackToRealHostname() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        assertEquals(AppUtils.getHostName(), ClusterPartitionGenerator.resolveServerId(startupParameterService));
    }

    @Test
    public void resolveServerIdWithStartupParameterService_hostnameLookupThrows_fallsBackToUnknown() {
        TypedProperties merged = new TypedProperties();
        merged.setProperty(ServerConstants.CLUSTER_SERVER_ID, "");
        IStartupParameterService startupParameterService = new StartupParameterService(merged, new TypedProperties(), Map.of(), Map.of());
        try (MockedStatic<AppUtils> mocked = mockStatic(AppUtils.class)) {
            mocked.when(AppUtils::getHostName).thenThrow(new RuntimeException("no hostname available"));
            assertEquals("unknown", ClusterPartitionGenerator.resolveServerId(startupParameterService));
        }
    }

    @Test
    public void isClusterLockingEnabled_propertiesValueTrue_returnsTrue() {
        Properties properties = new Properties();
        properties.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        assertTrue(ClusterPartitionGenerator.isClusterLockingEnabled(properties));
    }

    @Test
    public void isClusterLockingEnabled_propertiesBlank_returnsFalse() {
        assertFalse(ClusterPartitionGenerator.isClusterLockingEnabled(new Properties()));
    }

    @Test
    public void isClusterLockingEnabled_propertiesValueFalse_returnsFalse() {
        Properties properties = new Properties();
        properties.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "false");
        assertFalse(ClusterPartitionGenerator.isClusterLockingEnabled(properties));
    }

    @Test
    public void isClusterLockingEnabled_systemPropertyIgnored_returnsFalse() {
        Properties properties = new Properties();
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
