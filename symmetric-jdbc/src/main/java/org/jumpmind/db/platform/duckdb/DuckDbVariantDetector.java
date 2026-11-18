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
package org.jumpmind.db.platform.duckdb;

import org.apache.commons.lang3.Strings;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;

/*
 * Detects if a JDBC connection is to MotherDuck instead of local DuckDB. Both report the same
 * product name, so detection is based on the JDBC URL, which uses the "md:" prefix only for MotherDuck.
 */
public final class DuckDbVariantDetector {
    private DuckDbVariantDetector() {
    }

    public static boolean isMotherDuck(String url) {
        return Strings.CI.startsWith(url, JdbcDatabasePlatformFactory.JDBC_PREFIX + DuckDbDatabasePlatform.JDBC_SUBPROTOCOL + ":md:");
    }
}
