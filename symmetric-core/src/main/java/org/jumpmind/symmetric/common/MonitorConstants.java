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
package org.jumpmind.symmetric.common;

import java.util.HashMap;
import java.util.Map;

public class MonitorConstants {
    public static final String CPU = "cpu";
    public static final String DISK = "disk";
    public static final String MEMORY = "memory";
    public static final String BATCH_ERROR = "batchError";
    public static final String BATCH_UNSENT = "batchUnsent";
    public static final String DATA_UNROUTED = "dataUnrouted";
    public static final String DATA_GAP = "dataGap";
    public static final String OFFLINE_NODES = "offlineNodes";
    public static final String LOG = "log";
    public static final String FILE_HANDLES = "fileHandles";
    public static final String LICENSE_EXPIRE = "licenseExpire";
    public static final String LICENSE_ROWS = "licenseRows";
    public static final String LOAD_AVERAGE = "loadAverage";
    public static final String BATCH_UNSENT_OFFLINE = "batchUnsentOffline";
    public static final String CERT_EXPIRE = "certExpire";
    public static final String JVM_64_BIT = "jvm64Bit";
    public static final String JVM_CRASH = "jvmCrash";
    public static final String JVM_OOM = "jvmOutOfMemory";
    public static final String JVM_THREADS = "jvmThreads";
    public static final String BLOCK = "block";
    public static final String MYSQL_MODE = "mySqlMode";
    public static final String NEXT_DATA_IN_GAP = "nextDataInGap";
    public static final String CHANNELS_DISABLED = "channelsDisabled";
    public static final String MAX_BATCH_SIZE = "maxBatchSize";
    public static final String MAX_DATA_TO_ROUTE = "maxDataToRoute";
    public static final String MAX_BATCH_TO_SEND = "maxBatchToSend";
    public static final String MAX_CHANNELS = "maxChannels";
    public static final String CHANNEL_SUSPEND = "channelSuspend";
    public static final String MISSING_PRIMARY_KEY = "missingPrimaryKey";
    public static final String CHANNELS_FOREIGN_KEY = "channelsForeignKey";
    public static final String JOB_TRENDING = "jobTrending";
    public static final String JOB_ERROR = "jobError";
    public static final String CONNECTION_POOL = "connectionPool";
    public static final String CONNECTION_RESET = "connectionReset";
    public static final String LOB = "lob";
    public static final String STRANDED_OR_EXPIRED_DATA = "strandedOrExpiredData";
    public static final String UNKNOWN_CA = "unknownCa";
    public static final String INCOMPATIBLE_DATETIME = "incompatibleDatetime";
    public static final String FORM_KEYS = "formKeys";
    public static final String POSTGRES_WAL = "postgresWal";
    public static final String JVM_OOM_MONITOR = "jvmOutOfMemoryMonitor";

    public static Map<String, String> getMonitorTypesByVersion() {
        Map<String, String> map = new HashMap<String, String>();
        for (String name : new String[] { CPU, DISK, MEMORY, BATCH_ERROR, BATCH_UNSENT, DATA_UNROUTED, DATA_GAP, OFFLINE_NODES, LOG }) {
            map.put(name, "3.8.0");
        }
        for (String name : new String[] { LICENSE_EXPIRE, LICENSE_ROWS, CERT_EXPIRE, FILE_HANDLES, LOAD_AVERAGE, BATCH_UNSENT_OFFLINE }) {
            map.put(name, "3.14.0");
        }
        for (String name : new String[] { JVM_64_BIT, JVM_CRASH, JVM_OOM, JVM_THREADS, BLOCK, MYSQL_MODE, NEXT_DATA_IN_GAP, CHANNELS_DISABLED, MAX_BATCH_SIZE,
                MAX_DATA_TO_ROUTE, MAX_BATCH_TO_SEND, MAX_CHANNELS, CHANNEL_SUSPEND, MISSING_PRIMARY_KEY, CHANNELS_FOREIGN_KEY, JOB_TRENDING, JOB_ERROR,
                CONNECTION_POOL, CONNECTION_RESET, LOB, STRANDED_OR_EXPIRED_DATA, UNKNOWN_CA }) {
            map.put(name, "3.15.0");
        }
        for (String name : new String[] { INCOMPATIBLE_DATETIME }) {
            map.put(name, "3.16.2");
        }
        map.put(FORM_KEYS, "3.16.4");
        for (String name : new String[] { POSTGRES_WAL, JVM_OOM_MONITOR }) {
            map.put(name, "3.16.5");
        }
        return map;
    }
}
