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
package org.jumpmind.db.platform.ase;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AseJdbcSqlTransaction extends JdbcSqlTransaction {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    public AseJdbcSqlTransaction(JdbcSqlTemplate sqltemplate) {
        super(sqltemplate);
    }

    @Override
    public void allowInsertIntoAutoIncrementColumns(boolean allow, Table table, String quote, String catalogSeparator, String schemaSepartor) {
        if (table == null) {
            return;
        }
        String fullName = table.getQualifiedTableName(quote, catalogSeparator, schemaSepartor);
        if (table.getAutoIncrementColumns().length < 1) {
            log.debug("Skipped IDENTITY_INSERT mode for table={}", fullName);
            return;
        }
        if (allow) {
            log.debug("Enabled IDENTITY_INSERT for table={}", fullName);
            execute(String.format("SET IDENTITY_INSERT %s ON", fullName));
        } else {
            log.debug("Disabled IDENTITY_INSERT for table={}", fullName);
            execute(String.format("SET IDENTITY_INSERT %s OFF", fullName));
        }
    }
}
