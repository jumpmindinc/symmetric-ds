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
package org.jumpmind.symmetric.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerReBuildReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TriggerUtils {
    protected static final Logger log = LoggerFactory.getLogger(TriggerUtils.class);

    private TriggerUtils() {
    }

    /**
     * Resolves the catalog and schema for a trigger, substituting concrete values for any wildcarded names by searching the provided active trigger histories,
     * then returns the corresponding table from the platform cache.
     */
    public static Table resolveTableForTrigger(Trigger trigger, String tableName,
            List<TriggerHistory> activeTriggerHistories, IDatabasePlatform platform) {
        String catalogName = trigger.getSourceCatalogName();
        String schemaName = trigger.getSourceSchemaName();
        if (trigger.isSourceCatalogNameWildCarded() || trigger.isSourceSchemaNameWildCarded()) {
            TriggerHistory resolvedHist = activeTriggerHistories != null
                    ? activeTriggerHistories.stream()
                            .filter(h -> trigger.getTriggerId().equals(h.getTriggerId())
                                    && tableName.equalsIgnoreCase(h.getSourceTableName()))
                            .findFirst().orElse(null)
                    : null;
            if (trigger.isSourceCatalogNameWildCarded()) {
                catalogName = resolvedHist != null ? resolvedHist.getSourceCatalogName() : null;
            }
            if (trigger.isSourceSchemaNameWildCarded()) {
                schemaName = resolvedHist != null ? resolvedHist.getSourceSchemaName() : null;
            }
        }
        return platform.getTableFromCache(catalogName, schemaName, tableName, false);
    }

    /**
     * Builds a new TriggerHistory for the given trigger and table, names its database triggers, and inserts it. The caller is responsible for adding the
     * returned history to any local caches.
     */
    public static TriggerHistory createNewTriggerHistory(Trigger trigger, Table table,
            int triggerHistId, boolean isExistingTriggerHist, long dataId,
            List<TriggerHistory> activeTriggerHistories, ISymmetricEngine engine) {
        TriggerHistory triggerHistory = new TriggerHistory(table, trigger, engine.getSymmetricDialect().getTriggerTemplate());
        triggerHistory.setTriggerHistoryId(isExistingTriggerHist ? 0 : triggerHistId);
        triggerHistory.setLastTriggerBuildReason(TriggerReBuildReason.TRIGGER_HIST_MISSING);
        List<String> triggerNamesGeneratedThisSession = new ArrayList<String>();
        triggerHistory.setNameForInsertTrigger(engine.getTriggerRouterService().getTriggerName(DataEventType.INSERT,
                engine.getSymmetricDialect().getMaxTriggerNameLength(), trigger, table, activeTriggerHistories, null, triggerNamesGeneratedThisSession));
        triggerHistory.setNameForUpdateTrigger(engine.getTriggerRouterService().getTriggerName(DataEventType.UPDATE,
                engine.getSymmetricDialect().getMaxTriggerNameLength(), trigger, table, activeTriggerHistories, null, triggerNamesGeneratedThisSession));
        triggerHistory.setNameForDeleteTrigger(engine.getTriggerRouterService().getTriggerName(DataEventType.DELETE,
                engine.getSymmetricDialect().getMaxTriggerNameLength(), trigger, table, activeTriggerHistories, null, triggerNamesGeneratedThisSession));
        log.warn("Could not find trigger history {} for table {} for data_id {}.  Generating a new trigger history row.",
                triggerHistId, table.getName(), dataId);
        engine.getTriggerRouterService().insert(triggerHistory);
        return triggerHistory;
    }

    /**
     * Creates a stub TriggerHistory for a table that is not configured to sync. Logs a warning once per triggerHistId using the provided deduplication set.
     */
    public static TriggerHistory buildStubTriggerHistory(int triggerHistId, String tableName,
            long dataId, Set<Integer> missingConfigTriggerHist) {
        if (missingConfigTriggerHist.add(triggerHistId)) {
            log.warn("Could not find trigger history {} for table {} for data_id {}.  Table is not configured to sync, so ignoring it.",
                    triggerHistId, tableName, dataId);
        }
        TriggerHistory triggerHistory = new TriggerHistory(tableName, "", "");
        triggerHistory.setTriggerHistoryId(triggerHistId);
        return triggerHistory;
    }
}
