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
package org.jumpmind.db.sql;

import static org.junit.Assert.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

public class JdbcSqlTemplateTest {
    @Test
    public void testToQueryTimeoutSecondsWholeSeconds() {
        assertEquals(33, JdbcSqlTemplate.toQueryTimeoutSeconds(Duration.ofSeconds(33)));
    }

    @Test
    public void testToQueryTimeoutSecondsRoundsUp() {
        assertEquals(34, JdbcSqlTemplate.toQueryTimeoutSeconds(Duration.ofMillis(33001)));
        assertEquals(1, JdbcSqlTemplate.toQueryTimeoutSeconds(Duration.ofMillis(1)));
    }
}
