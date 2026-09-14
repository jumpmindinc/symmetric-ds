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
package org.jumpmind.symmetric.db.voltdb;

import java.util.HashMap;

import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class VoltDbTriggerTemplate extends AbstractTriggerTemplate {
    public VoltDbTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        emptyColumnTemplate = "''";
        stringColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace($(tableAlias).\"$(columnName)\",'\\','\\\\'),'\"','\\\"')|| '\"' end ";
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"'||cast($(tableAlias).\"$(columnName)\" as varchar(50))||'\"' end ";
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"'|| cast($(tableAlias).\"$(columnName)\" as varchar) || '\"' end ";
        clobColumnTemplate = stringColumnTemplate;
        blobColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"'||replace(replace(sym_BASE64_ENCODE($(tableAlias).\"$(columnName)\"),'\\','\\\\'),'\"','\\\"')||'\"' end ";
        booleanColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' when $(tableAlias).\"$(columnName)\" then '\"1\"' else '\"0\"' end ";
        triggerConcatCharacter = "||";
        newTriggerValue = "";
        oldTriggerValue = "";
        sqlTemplates = new HashMap<String, String>();
        sqlTemplates.put("insertTriggerTemplate", "");
        sqlTemplates.put("updateTriggerTemplate", "");
        sqlTemplates.put("deleteTriggerTemplate", "");
        sqlTemplates.put("initialLoadSqlTemplate",
                "select $(columns) from $(schemaName)$(tableName) t where $(whereClause)                                                                                                                                ");
    }
}
