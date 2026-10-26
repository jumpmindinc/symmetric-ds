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
package org.jumpmind.symmetric.test;

import static org.junit.Assert.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PurgeFirstPassIntegrationTest extends AbstractPurgeIntegrationTest {
    private static final String COUNT_OTHER_TEST_DATA_SQL = "select count(*) from sym_data where table_name = ? and data_id <> ?";

    @Test
    public void test03PurgeDataHeldBehindPurgePositionWithoutRecapture() {
        logTestRunning();
        Assertions.assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
            prepareForPurge();
            markStuckWithoutEvents(insertAndRoute(101));
            long heldDataId = insertAndRoute(102);
            setBatchesOk(insertAndRoute(103));
            setBatchesOk(insertAndRoute(104));
            purgeOutgoing();
            assertEquals(1, countData(heldDataId));
            setBatchesOk(heldDataId);
            setBatchesOk(insertAndRoute(105));
            long outstandingDataId = insertAndRoute(106);
            purgeOutgoing();
            assertEquals(0, countData(heldDataId));
            assertEquals(1, countData(outstandingDataId));
            assertEquals(0, getServer().getSqlTemplate().queryForInt(COUNT_OTHER_TEST_DATA_SQL, TEST_TABLE, outstandingDataId));
        });
    }
}
