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
package org.jumpmind.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ISqlTemplateTest {
    @Test
    void testQueryForLongWithTimeout_defaultDelegatesToQueryForLong() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        doCallRealMethod().when(sqlTemplate).queryForLongWithTimeout("select count(*) from item where id between ? and ?", Duration.ofSeconds(5),
                1L, 2L);
        when(sqlTemplate.queryForLong("select count(*) from item where id between ? and ?", 1L, 2L)).thenReturn(7L);
        assertEquals(7L, sqlTemplate.queryForLongWithTimeout("select count(*) from item where id between ? and ?", Duration.ofSeconds(5), 1L, 2L));
    }

    @Test
    void testQueryForLong_anyMatchersStubTheVarargsMethod() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(sqlTemplate.queryForLong(anyString(), any(), any())).thenReturn(3L);
        assertEquals(3L, sqlTemplate.queryForLong("select count(*) from item where id between ? and ?", 49L, 50L));
    }
}
