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
package org.jumpmind.symmetric.transport.http;

/**
 * Tracks the one (or, in a Pro implementation, several) batch pull(s) currently in flight for a node, so that if the connection drops mid-transfer, the next
 * pull attempt can resume from where it left off instead of re-downloading the whole batch. Scoped per-engine, held as an instance field on
 * {@link HttpTransportManager}, and resolved via {@link AppUtils#newInstance(Class, Class)} so a Pro engine can substitute a multi-entry implementation with
 * zero open-source changes.
 */
public interface IHttpResumeCache {
    void put(String nodeId, long batchId, ResumeCacheEntry entry);

    ResumeCacheEntry get(String nodeId, long batchId);

    /**
     * @return whichever pending resume entry exists for this node <em>and queue</em>, if any, so a caller can discover a batch worth resuming without already
     *         knowing its batch id. Scoped by queue as well as node because a single node can have multiple active pull queues (e.g. "default" and "system") -
     *         without the queue filter, every queue's pull loop would independently discover the same entry and attach its resume parameters to a request that
     *         has nothing to do with the batch in question. Only meaningful for table-sync, whose channels each belong to exactly one queue and whose batch
     *         selection is itself queue-partitioned end to end ({@code PullUriHandler} passes the requesting queue into
     *         {@code DataExtractorService.extract(...)}, and even validates it against the batch's own channel before honoring a resume) - see
     *         {@link #getPendingFileSyncEntryForNode(String)} for file sync, whose batch selection is not partitioned by queue at all.
     */
    ResumeCacheEntry getPendingForNode(String nodeId, String queue);

    /**
     * Same intent as {@link #getPendingForNode(String, String)}, but ignores queue entirely and matches only on {@link ResumeCacheEntry#isFileSync()}. Used
     * exclusively by file sync: unlike table-sync, {@code FileSyncService.getBatchesToProcess(Node)} selects every outstanding batch across all file-sync
     * channels for a node regardless of which queue is asking, so any of that node's active pull queues may legitimately end up delivering - and therefore
     * should be able to resume - the one pending file-sync batch, not just the specific queue whose pull happened to be interrupted.
     */
    ResumeCacheEntry getPendingFileSyncEntryForNode(String nodeId);

    void remove(String nodeId, long batchId);
}
