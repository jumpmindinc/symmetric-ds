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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SYM-7915. Pins the statements behind stuck-extract-request detection and recovery.
 */
class DataExtractorServiceSqlMapTest {
    private DataExtractorServiceSqlMap sqlMap() {
        return new DataExtractorServiceSqlMap(null, (Map<String, String>) null);
    }

    @ParameterizedTest
    @ValueSource(
            strings = { "extracted_rows = 0", "extracted_millis = 0", "transferred_rows = 0", "loaded_rows = 0",
                    "last_transferred_batch_id = null", "last_loaded_batch_id = null", "parent_request_id = 0", "status = ?" })
    void restartExtractRequestResetsEveryCounter(String fragment) {
        /*
         * The extraction counters are the ones this change added: without them a restarted request keeps the counters from the interrupted run and still
         * reports rows it no longer has, which is the misleading state the recovery exists to clear. The rest guard against the additions displacing what was
         * already there.
         */
        String sql = sqlMap().getSql("restartExtractRequest");
        assertTrue(sql.contains(fragment), sql);
    }

    @Test
    void stuckRequestDetectionRequiresAllFourConditions() {
        /*
         * The detection is only sound with every part present: status OK, no load time, a batch in the range still requested, and a settling period so a status
         * write that is still in flight is not mistaken for a wedge.
         */
        String sql = sqlMap().getSql("selectStuckExtractRequestsSql");
        assertTrue(sql.contains("r.status = ?"), sql);
        assertTrue(sql.contains("r.loaded_time is null"), sql);
        assertTrue(sql.contains("r.last_update_time < ?"), sql);
        assertTrue(sql.contains("b.status = 'RQ'"), sql);
        assertTrue(sql.contains("b.batch_id between r.start_batch_id and r.end_batch_id"), sql);
    }

    @Test
    void stuckRequestDetectionSkipsChildRequests() {
        // Children are restarted through their parent, so selecting them independently would double up the recovery.
        assertTrue(sqlMap().getSql("selectStuckExtractRequestsSql").contains("r.parent_request_id = 0"));
    }

    @Test
    void requestedAndDeliveredLookupsUseDifferentStatusesAndDoNotCount() {
        /*
         * The first decides whether a request is stuck; the second decides whether restarting it would re-send rows. Both answers are booleans, so they select
         * rows and stop at the first match rather than counting -- a count would have to visit the whole batch range.
         */
        String requested = sqlMap().getSql("selectRequestedBatchesForExtractRequestSql");
        String delivered = sqlMap().getSql("selectDeliveredBatchesForExtractRequestSql");
        assertTrue(requested.contains("status = 'RQ'"), requested);
        assertTrue(delivered.contains("status in ('OK','IG')"), delivered);
        assertFalse(delivered.contains("'RQ'"), delivered);
        assertFalse(requested.contains("count("), requested);
        assertFalse(delivered.contains("count("), delivered);
    }

    @Test
    void extractedStatsUpdateAccumulatesRatherThanOverwriting() {
        // A partial run must add to what previous batches recorded, or progress on a multi-batch request is lost.
        String sql = sqlMap().getSql("updateExtractRequestExtractedStats");
        assertTrue(sql.contains("extracted_rows = extracted_rows + ?"), sql);
        assertTrue(sql.contains("extracted_millis = extracted_millis + ?"), sql);
        assertFalse(sql.contains("status"), "the incremental update must not touch status: " + sql);
    }

    @Test
    void updateExtractRequestStatusIsUnchangedForTheCompletionPath() {
        // Still an absolute write, since execute() uses it when the request genuinely finished.
        String sql = sqlMap().getSql("updateExtractRequestStatus");
        assertTrue(sql.contains("status=?"), sql);
        assertTrue(sql.contains("extracted_rows=?"), sql);
    }
}
