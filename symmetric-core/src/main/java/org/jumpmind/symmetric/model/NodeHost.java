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
package org.jumpmind.symmetric.model;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.Version;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.SimpleClassCompiler;

/**
 * 
 */
public class NodeHost implements Serializable {
    private static final long serialVersionUID = 1L;
    public static Date LAST_RESTART_TIME = new Date();
    public static final String SECURITY_MODE_STANDARD = "standard";
    public static final String SECURITY_MODE_RESTRICTED = "restricted";
    protected final static int MAX_IP_ADDRESS_SIZE = 50;
    private String nodeId;
    private String hostName;
    private String instanceId;
    private String ipAddress;
    private String osUser;
    private String osName;
    private String osArch;
    private String osVersion;
    private int availableProcessors;
    private long freeMemoryBytes;
    private long totalMemoryBytes;
    private long maxMemoryBytes;
    private String javaVersion;
    private String javaVendor;
    private String securityMode;
    private String jdbcVersion;
    private String symmetricVersion;
    private String timezoneOffset;
    private Date heartbeatTime;
    private Date lastRestartTime = LAST_RESTART_TIME;
    private Date createTime;

    public NodeHost() {
        this.createTime = new Date();
    }

    public NodeHost(String nodeId, String instanceId) {
        this();
        this.nodeId = nodeId;
        this.instanceId = instanceId;
    }

    public void refresh(IDatabasePlatform platform, String instanceId, String serverId) {
        this.instanceId = instanceId;
        this.hostName = serverId;
        setIpAddress(AppUtils.getIpAddress());
        this.osUser = System.getProperty("user.name");
        this.osName = System.getProperty("os.name");
        this.osArch = System.getProperty("os.arch");
        this.osVersion = System.getProperty("os.version");
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
        this.freeMemoryBytes = Runtime.getRuntime().freeMemory();
        this.totalMemoryBytes = Runtime.getRuntime().totalMemory();
        this.maxMemoryBytes = Runtime.getRuntime().maxMemory();
        this.javaVersion = System.getProperty("java.version");
        this.javaVendor = System.getProperty("java.vendor");
        this.securityMode = SimpleClassCompiler.isJdkAvailable() ? SECURITY_MODE_STANDARD : SECURITY_MODE_RESTRICTED;
        this.jdbcVersion = platform.getSqlTemplate().getDriverVersion();
        this.symmetricVersion = Version.version();
        this.timezoneOffset = AppUtils.getTimezoneOffset();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MILLISECOND, 0);
        this.heartbeatTime = cal.getTime();
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = StringUtils.left(ipAddress, MAX_IP_ADDRESS_SIZE);
    }

    public String getOsUser() {
        return osUser;
    }

    public void setOsUser(String osUser) {
        this.osUser = osUser;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsArch() {
        return osArch;
    }

    public void setOsArch(String osArch) {
        this.osArch = osArch;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public void setAvailableProcessors(int availableProcessors) {
        this.availableProcessors = availableProcessors;
    }

    public long getFreeMemoryBytes() {
        return freeMemoryBytes;
    }

    public void setFreeMemoryBytes(long freeMemoryBytes) {
        this.freeMemoryBytes = freeMemoryBytes;
    }

    public long getTotalMemoryBytes() {
        return totalMemoryBytes;
    }

    public void setTotalMemoryBytes(long totalMemoryBytes) {
        this.totalMemoryBytes = totalMemoryBytes;
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public void setMaxMemoryBytes(long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getJavaVendor() {
        return javaVendor;
    }

    public void setJavaVendor(String javaVendor) {
        this.javaVendor = javaVendor;
    }

    public String getSecurityMode() {
        return securityMode;
    }

    public void setSecurityMode(String securityMode) {
        this.securityMode = securityMode;
    }

    public void setJdbcVersion(String jdbcVersion) {
        this.jdbcVersion = jdbcVersion;
    }

    public String getJdbcVersion() {
        return jdbcVersion;
    }

    public String getSymmetricVersion() {
        return symmetricVersion;
    }

    public void setSymmetricVersion(String symmetricVersion) {
        this.symmetricVersion = symmetricVersion;
    }

    public String getTimezoneOffset() {
        return timezoneOffset;
    }

    public void setTimezoneOffset(String timezoneOffset) {
        this.timezoneOffset = timezoneOffset;
    }

    public Date getHeartbeatTime() {
        return heartbeatTime;
    }

    public void setHeartbeatTime(Date heartbeatTime) {
        this.heartbeatTime = heartbeatTime;
    }

    public Date getLastRestartTime() {
        return lastRestartTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public void setLastRestartTime(Date lastRestartTime) {
        this.lastRestartTime = lastRestartTime;
    }
}