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
package org.jumpmind.symmetric.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.service.IStartupParameterService;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the JCS cluster partition ID without any database access, so {@link ClusteredCacheManager} can start announcing itself to peers before the database
 * is known to be reachable (e.g. before {@code ClusterService.checkSymDbOwnership()} has had a chance to run or fail). Resolution order: the configured
 * {@code cluster.partition.id} system property/environment variable, then a locally cached value (file for the standalone launcher, classpath resource
 * otherwise), then a freshly generated random UUID. The resolved value is cached in this class for the life of the JVM and mirrored to the local cache
 * file/resource so that restarts of the same installation converge on the same value without a database round-trip.
 */
public class ClusterPartitionGenerator {
    private static final Logger log = LoggerFactory.getLogger(ClusterPartitionGenerator.class);
    private static volatile String clusterPartitionId;

    private ClusterPartitionGenerator() {
    }

    public static synchronized String resolve(IStartupParameterService startupParameterService) {
        if (clusterPartitionId == null) {
            clusterPartitionId = loadOrCreateClusterPartitionId(startupParameterService);
        }
        return clusterPartitionId;
    }

    /** Resolves server ID from properties or hostname. First checks properties for cluster.server.id, falls back to hostname. */
    public static String resolveServerId(Properties properties) {
        String id = properties != null ? properties.getProperty(ServerConstants.CLUSTER_SERVER_ID) : null;
        if (StringUtils.isBlank(id)) {
            id = AppUtils.getHostName();
        }
        return StringUtils.left(id, 255);
    }

    public static String resolveServerId(IStartupParameterService startupParameterService) {
        String id = startupParameterService.getGlobalString(ServerConstants.CLUSTER_SERVER_ID);
        if (StringUtils.isBlank(id)) {
            // JBoss uses this system property to identify a server in a cluster
            id = startupParameterService.getGlobalString("bind.address");
        }
        if (StringUtils.isBlank(id)) {
            // JBoss uses this system property to identify a server in a cluster
            id = startupParameterService.getGlobalString("jboss.bind.address");
        }
        if (StringUtils.isBlank(id)) {
            try {
                id = AppUtils.getHostName();
            } catch (Exception ex) {
                id = "unknown";
            }
        }
        return StringUtils.left(id, 255);
    }

    public static boolean isClusterLockingEnabled(Properties properties) {
        String value = properties != null ? properties.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED) : null;
        return Boolean.parseBoolean(value);
    }

    private static String loadOrCreateClusterPartitionId(IStartupParameterService startupParameterService) {
        File clusterPartitionIdFile = getClusterPartitionIdFile(startupParameterService);
        String configuredId = StringUtils.left(readConfiguredPartitionId(startupParameterService), 60);
        if (StringUtils.isNotBlank(configuredId)) {
            writeClusterPartitionId(clusterPartitionIdFile, configuredId);
            return configuredId;
        }
        String id = readClusterPartitionId(clusterPartitionIdFile);
        if (StringUtils.isNotBlank(id)) {
            return id;
        }
        id = applyUuidMarker(UUID.randomUUID(), ServerConstants.PARTITION_ID_MARKER_AUTO).toString();
        writeClusterPartitionId(clusterPartitionIdFile, id);
        return id;
    }

    public static UUID applyUuidMarker(UUID uuid, int marker) {
        long msb = (uuid.getMostSignificantBits() & 0xFFFFFFFF0000FFFFL) | ((long) (marker & 0xFFFF)) << 16;
        return new UUID(msb, uuid.getLeastSignificantBits());
    }

    public static String applyUuidMarkerToId(String partitionId, int marker) {
        if (partitionId == null || partitionId.length() < 36) {
            String prefix = partitionId == null ? "" : partitionId;
            return prefix + applyUuidMarker(new UUID(0L, 0L), marker);
        }
        String uuidPart = partitionId.substring(partitionId.length() - 36);
        try {
            return partitionId.substring(0, partitionId.length() - 36)
                    + applyUuidMarker(UUID.fromString(uuidPart), marker);
        } catch (IllegalArgumentException e) {
            return partitionId;
        }
    }

    private static String readConfiguredPartitionId(IStartupParameterService startupParameterService) {
        return startupParameterService.getGlobalString(ServerConstants.CLUSTER_PARTITION_ID);
    }

    private static File getClusterPartitionIdFile(IStartupParameterService startupParameterService) {
        return startupParameterService.isGlobal(SystemConstants.SYSPROP_LAUNCHER, false)
                ? new File(AppUtils.getSymHome() + "/conf/cluster-partition.uuid")
                : null;
    }

    private static String readClusterPartitionId(File clusterPartitionIdFile) {
        if (clusterPartitionIdFile != null) {
            try (FileInputStream in = new FileInputStream(clusterPartitionIdFile)) {
                return IOUtils.toString(in, Charset.defaultCharset()).trim();
            } catch (Exception ex) {
                log.debug("Failed to load cluster partition id from file '" + clusterPartitionIdFile + "'", ex);
                return null;
            }
        }
        URL clusterPartitionIdURL = ClusterPartitionGenerator.class.getClassLoader().getResource("cluster-partition.uuid");
        if (clusterPartitionIdURL != null) {
            try (InputStream in = clusterPartitionIdURL.openStream()) {
                return IOUtils.toString(in, Charset.defaultCharset()).trim();
            } catch (Exception ex) {
                log.debug("Failed to load cluster partition id from classpath '" + clusterPartitionIdURL + "'", ex);
            }
        }
        return null;
    }

    private static void writeClusterPartitionId(File clusterPartitionIdFile, String id) {
        if (clusterPartitionIdFile != null) {
            try {
                clusterPartitionIdFile.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(clusterPartitionIdFile)) {
                    IOUtils.write(id, out, Charset.defaultCharset());
                }
            } catch (Exception ex) {
                log.warn("Failed to save cluster partition id to file '" + clusterPartitionIdFile + "'", ex);
            }
        }
    }
}
