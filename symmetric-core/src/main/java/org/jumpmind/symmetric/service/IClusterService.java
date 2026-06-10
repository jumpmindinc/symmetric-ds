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
package org.jumpmind.symmetric.service;

import java.util.Map;

import org.jumpmind.symmetric.model.Lock;

/**
 * Service API that is responsible for acquiring distributed locks for clustered SymmetricDS nodes.
 */
public interface IClusterService {
    public void init();

    public void refreshLockEntries();

    public boolean lock(String action);

    public boolean refreshLock(String action);

    public boolean lock(String action, String lockType);

    public boolean lock(String action, String lockType, long waitMillis);

    public void unlock(String action);

    public void unlock(String action, String lockType);

    public void clearAllLocks();

    public void addLock(String action, String lockType);

    public void removeLock(String action);

    public String getServerId();

    public boolean isClusteringEnabled();

    public Map<String, Lock> findLocks();

    public void aquireInfiniteLock(String action);

    public void clearInfiniteLock(String action);

    public boolean isInfiniteLocked(String action);

    public boolean isLocked(String action);

    public void persistToTableForSnapshot();

    public String getInstanceId();

    public void clearLocksForServer(String serverId);
}