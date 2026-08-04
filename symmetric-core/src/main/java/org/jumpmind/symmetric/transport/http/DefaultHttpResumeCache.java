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
package org.jumpmind.symmetric.transport.http;

import java.util.Objects;

import org.jumpmind.symmetric.ISymmetricEngine;

/**
 * Open-source default {@link IHttpResumeCache}: a single-slot holder, since a base engine only ever has one batch in flight at a time per (node, queue) pair
 * and only the one batch that was in flight when a connection dropped is ever a resume candidate. A Pro implementation may back this with a bounded,
 * multi-entry map instead.
 */
public class DefaultHttpResumeCache implements IHttpResumeCache {
    private ResumeCacheEntry entry;

    /**
     * Matches the {@code (ISymmetricEngine)} constructor shape {@code AppUtils.newInstance} requires so a Pro override can use the engine's parameter service;
     * the single-slot default has no need for it.
     */
    public DefaultHttpResumeCache(ISymmetricEngine engine) {
    }

    @Override
    public synchronized void put(String nodeId, long batchId, ResumeCacheEntry newEntry) {
        this.entry = newEntry;
    }

    @Override
    public synchronized ResumeCacheEntry get(String nodeId, long batchId) {
        if (matches(nodeId, batchId)) {
            return entry;
        }
        return null;
    }

    @Override
    public synchronized ResumeCacheEntry getPendingForNode(String nodeId, String queue) {
        if (entry != null && entry.getNodeId().equals(nodeId) && Objects.equals(entry.getQueue(), queue)) {
            return entry;
        }
        return null;
    }

    @Override
    public synchronized ResumeCacheEntry getPendingFileSyncEntryForNode(String nodeId) {
        if (entry != null && entry.getNodeId().equals(nodeId) && entry.isFileSync()) {
            return entry;
        }
        return null;
    }

    @Override
    public synchronized void remove(String nodeId, long batchId) {
        if (matches(nodeId, batchId)) {
            entry = null;
        }
    }

    private boolean matches(String nodeId, long batchId) {
        return entry != null && entry.getBatchId() == batchId && entry.getNodeId().equals(nodeId);
    }
}
