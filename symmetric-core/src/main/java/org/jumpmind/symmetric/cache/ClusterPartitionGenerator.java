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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
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

    public static String resolve() {
        if (clusterPartitionId == null) {
            synchronized (ClusterPartitionGenerator.class) {
                if (clusterPartitionId == null) {
                    clusterPartitionId = loadOrCreateClusterPartitionId();
                }
            }
        }
        return clusterPartitionId;
    }

    private static String loadOrCreateClusterPartitionId() {
        File clusterPartitionIdFile = getClusterPartitionIdFile();
        String configuredId = StringUtils.left(readConfiguredPartitionId(), 60);
        if (StringUtils.isNotBlank(configuredId)) {
            writeClusterPartitionId(clusterPartitionIdFile, configuredId);
            return configuredId;
        }
        String id = readClusterPartitionId(clusterPartitionIdFile);
        if (StringUtils.isNotBlank(id)) {
            return id;
        }
        id = UUID.randomUUID().toString();
        writeClusterPartitionId(clusterPartitionIdFile, id);
        return id;
    }

    private static String readConfiguredPartitionId() {
        String id = System.getProperty(ServerConstants.CLUSTER_PARTITION_ID);
        if (StringUtils.isBlank(id)) {
            id = System.getenv("SYM_CLUSTER_PARTITION_ID");
        }
        return id;
    }

    private static File getClusterPartitionIdFile() {
        return "true".equals(System.getProperty(SystemConstants.SYSPROP_LAUNCHER))
                ? new File(AppUtils.getSymHome() + "/conf/cluster-partition.uuid")
                : null;
    }

    private static String readClusterPartitionId(File clusterPartitionIdFile) {
        if (clusterPartitionIdFile != null) {
            try {
                return IOUtils.toString(new FileInputStream(clusterPartitionIdFile), Charset.defaultCharset()).trim();
            } catch (Exception ex) {
                log.debug("Failed to load cluster partition id from file '" + clusterPartitionIdFile + "'", ex);
                return null;
            }
        }
        URL clusterPartitionIdURL = ClusterPartitionGenerator.class.getClassLoader().getResource("/cluster-partition.uuid");
        if (clusterPartitionIdURL != null) {
            try {
                return IOUtils.toString(clusterPartitionIdURL.openStream(), Charset.defaultCharset()).trim();
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
                IOUtils.write(id, new FileOutputStream(clusterPartitionIdFile), Charset.defaultCharset());
            } catch (Exception ex) {
                log.warn("Failed to save cluster partition id to file '" + clusterPartitionIdFile + "'", ex);
            }
        }
    }
}
