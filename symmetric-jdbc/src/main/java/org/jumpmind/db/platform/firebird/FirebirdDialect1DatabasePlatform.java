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
package org.jumpmind.db.platform.firebird;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.SqlTemplateSettings;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Firebird has two SQL dialect modes: mode 3 is the default modern sql dialect, while mode 1 is the compatibility
 * mode for moving to Firebird from an older Interbase 6 database.  Maybe we should have an isDialect1 boolean instead, since this
 * mode is available on any version of Firebird.  The JdbcDatabasePlatformFactory currently runs query tests to determine
 * which sql dialect mode is being used, in order to get the right IDatabasePlatform.
 */
public class FirebirdDialect1DatabasePlatform extends FirebirdDatabasePlatform {
    public FirebirdDialect1DatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    protected FirebirdDdlBuilder createDdlBuilder() {
        return new FirebirdDialect1DdlBuilder();
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.FIREBIRD_DIALECT1;
    }

    @Override
    public String getClassName() {
        return FirebirdDialect1DatabasePlatform.class.getName();
    }
}
