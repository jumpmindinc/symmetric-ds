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
package org.jumpmind.symmetric.db.firebird;

import org.jumpmind.symmetric.db.ISymmetricDialect;

/*
 * Trigger templates for Firebird version 2.0.
 */
public class Firebird20TriggerTemplate extends FirebirdTriggerTemplate {
    public Firebird20TriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        if (isDialect1) {
            numberColumnTemplate = "case when $(tableAlias)." + quo + "$(columnName)" + quo
                    + " is null then '' else '\"' || trim(trailing '.000000' from trim(trailing '.0' from $(tableAlias)." + quo + "$(columnName)" + quo
                    + ")) || '\"' end";
            datetimeColumnTemplate = "case when $(tableAlias)." + quo + "$(columnName)" + quo + " is null then '' else '\"' || extract(year from $(tableAlias)."
                    + quo + "$(columnName)" + quo + ") || '-'|| extract(month from $(tableAlias)." + quo + "$(columnName)" + quo
                    + ") || '-' || extract(day from $(tableAlias)." + quo + "$(columnName)" + quo + ") || case when position(' ', $(tableAlias)." + quo
                    + "$(columnName)" + quo + ") > 0 then substring($(tableAlias)." + quo + "$(columnName)" + quo + " from position(' ', $(tableAlias)." + quo
                    + "$(columnName)" + quo + ")) else ' 00:00:00' end || '\"' end";
        }
    }
}
