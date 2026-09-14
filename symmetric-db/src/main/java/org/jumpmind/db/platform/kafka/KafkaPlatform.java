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
package org.jumpmind.db.platform.kafka;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.AbstractDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;

public class KafkaPlatform extends AbstractDatabasePlatform {
    public KafkaPlatform(SqlTemplateSettings settings) {
        super(settings);
        super.ddlBuilder = new KafkaDdlBuilder();
        super.ddlReader = new KafkaDdlReader(this);
        supportsTruncate = false;
    }

    @Override
    public String getName() {
        return "kafka";
    }

    @Override
    public String getClassName() {
        return KafkaPlatform.class.getName();
    }

    @Override
    public String getDefaultSchema() {
        return null;
    }

    @Override
    public String getDefaultCatalog() {
        return null;
    }

    @Override
    public <T> T getDataSource() {
        return null;
    }

    @Override
    public boolean isLob(Column column) {
        return false;
    }

    @Override
    public ISqlTemplate getSqlTemplate() {
        return new KafkaSqlTemplate();
    }

    @Override
    public ISqlTemplate getSqlTemplateDirty() {
        return new KafkaSqlTemplate();
    }
}
