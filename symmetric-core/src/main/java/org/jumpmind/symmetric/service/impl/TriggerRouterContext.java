package org.jumpmind.symmetric.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.util.CounterStat;
import org.jumpmind.util.Context;

public class TriggerRouterContext extends Context {
    Map<String, CounterStat> triggerReadTableFromDatabase = new HashMap<String, CounterStat>();
    Map<String, CounterStat> triggerCopyTable = new HashMap<String, CounterStat>();
    Map<Integer, CounterStat> multipleActiveTriggerRouter = new HashMap<Integer, CounterStat>();
    int triggersToSyncCount;
    int triggersSyncedCount;
    int triggersFailedCount;
    int tablesNotFoundCount;
    String tablesNotFoundMessage;
    String triggersFailedMessage;
    Exception triggersFailedException;
    long fixMultipleActiveTriggerHistoriesTime;
    long triggersForCurrentNodeTime;
    long syncTriggersStartedTime;
    long activeTriggerHistoriesTime;
    long updateOrCreateDdlTriggersTime;
    long syncTriggersEndedTime;
    long tablesForTriggerTime;
    long dropTriggerTime;
    long doesTriggerExistTime;
    long inactivateTriggerHistTime;
    long triggerToRelationSupportingInfoTime;
    long tableDoesNotExistTime;
    long updateOrCreateDatabaseTriggersTime;
    long triggerInactivatedTime;

    synchronized public void incrementReadTableCount(Trigger trigger) {
        if (trigger != null) {
            CounterStat counterStat = triggerReadTableFromDatabase.get(trigger.getTriggerId());
            if (counterStat == null) {
                counterStat = new CounterStat(trigger);
                triggerReadTableFromDatabase.put(trigger.getTriggerId(), counterStat);
            }
            counterStat.incrementCount();
        }
    }

    synchronized public Set<String> getTriggerReadTableFromDatabaseKeyset() {
        return triggerReadTableFromDatabase.keySet();
    }

    synchronized public long getTriggerReadTableFromDatabaseCount(String triggerId) {
        CounterStat counterStat = triggerReadTableFromDatabase.get(triggerId);
        if (counterStat != null) {
            return counterStat.getCount();
        }
        return 0l;
    }

    synchronized public void incrementCopyTableCount(Trigger trigger) {
        if (trigger != null) {
            CounterStat counterStat = triggerCopyTable.get(trigger.getTriggerId());
            if (counterStat == null) {
                counterStat = new CounterStat(trigger);
                triggerCopyTable.put(trigger.getTriggerId(), counterStat);
            }
            counterStat.incrementCount();
        }
    }

    synchronized public Set<String> getTriggerCopyTableKeyset() {
        return triggerCopyTable.keySet();
    }

    synchronized public long getTriggerCopyTableCount(String triggerId) {
        CounterStat counterStat = triggerCopyTable.get(triggerId);
        if (counterStat != null) {
            return counterStat.getCount();
        }
        return 0l;
    }

    synchronized public Set<Integer> getMultipleActiveTriggerRouterKeyset() {
        return multipleActiveTriggerRouter.keySet();
    }

    synchronized public long getMultipleActiveTriggerRouterCount(Integer triggerRouterId) {
        CounterStat counterStat = multipleActiveTriggerRouter.get(triggerRouterId);
        if (counterStat != null) {
            return counterStat.getCount();
        }
        return 0l;
    }

    synchronized public void incrementMultipleActiveTriggerRouterCount(Integer triggerRouterId) {
        if (triggerRouterId != null) {
            CounterStat counterStat = multipleActiveTriggerRouter.get(triggerRouterId);
            if (counterStat == null) {
                counterStat = new CounterStat(triggerRouterId);
                multipleActiveTriggerRouter.put(triggerRouterId, counterStat);
            }
            counterStat.incrementCount();
        }
    }

    synchronized public void incrementFixMultipleActiveTriggerHistoriesTime(long t) {
        fixMultipleActiveTriggerHistoriesTime += t;
    }

    synchronized public long getFixMultipleActiveTriggerHistoriesTime() {
        return fixMultipleActiveTriggerHistoriesTime;
    }

    synchronized public void incrementTriggersForCurrentNodeTime(long t) {
        triggersForCurrentNodeTime += t;
    }

    synchronized public long getTriggersForCurrentNodeTime() {
        return triggersForCurrentNodeTime;
    }

    synchronized public void incrementSyncTriggersStartedTime(long t) {
        syncTriggersStartedTime += t;
    }

    synchronized public long getSyncTriggersStartedTime() {
        return syncTriggersStartedTime;
    }

    synchronized public void incrementActiveTriggerHistoriesTime(long t) {
        activeTriggerHistoriesTime += t;
    }

    synchronized public long getActiveTriggerHistoriesTime() {
        return activeTriggerHistoriesTime;
    }

    synchronized public void incrementUpdateOrCreateDdlTriggersTime(long t) {
        updateOrCreateDdlTriggersTime += t;
    }

    synchronized public long getUpdateOrCreateDdlTriggersTime() {
        return updateOrCreateDdlTriggersTime;
    }

    synchronized public void incrementSyncTriggersEndedTime(long t) {
        syncTriggersEndedTime += t;
    }

    synchronized public long getSyncTriggersEndedTime() {
        return syncTriggersEndedTime;
    }

    synchronized public void incrementTablesForTriggerTime(long t) {
        tablesForTriggerTime += t;
    }

    synchronized public long getTablesForTriggerTime() {
        return tablesForTriggerTime;
    }

    synchronized public void incrementDropTriggerTime(long t) {
        dropTriggerTime += t;
    }

    synchronized public long getDropTriggerTime() {
        return dropTriggerTime;
    }

    synchronized public void incrementDoesTriggerExistTime(long t) {
        doesTriggerExistTime += t;
    }

    synchronized public long getDoesTriggerExistTime() {
        return doesTriggerExistTime;
    }

    synchronized public void incrementInactivateTriggerHistTime(long t) {
        inactivateTriggerHistTime += t;
    }

    synchronized public long getInactivateTriggerHistTime() {
        return inactivateTriggerHistTime;
    }

    synchronized public void incrementTriggerToRelationSupportingInfoTime(long t) {
        triggerToRelationSupportingInfoTime += t;
    }

    synchronized public long getTriggerToRelationSupportingInfoTime() {
        return triggerToRelationSupportingInfoTime;
    }

    synchronized public void incrementTableDoesNotExistTime(long t) {
        tableDoesNotExistTime += t;
    }

    synchronized public long getTableDoesNotExistTime() {
        return tableDoesNotExistTime;
    }

    synchronized public void incrementUpdateOrCreateDatabaseTriggersTime(long t) {
        updateOrCreateDatabaseTriggersTime += t;
    }

    synchronized public long getUpdateOrCreateDatabaseTriggersTime() {
        return updateOrCreateDatabaseTriggersTime;
    }

    synchronized public void incrementTriggerInactivatedTime(long t) {
        triggerInactivatedTime += t;
    }

    synchronized public long getTriggerInactivatedTime() {
        return triggerInactivatedTime;
    }

    synchronized public void incrementTriggersToSyncCount(int c) {
        triggersToSyncCount += c;
    }

    synchronized public int getTriggersToSyncCount() {
        return triggersToSyncCount;
    }

    synchronized public void incrementTriggersSyncedCount(int c) {
        triggersSyncedCount += c;
    }

    synchronized public int getTriggersSyncedCount() {
        return triggersSyncedCount;
    }

    synchronized public int getTriggersFailedCount() {
        return triggersFailedCount;
    }

    synchronized public int getTablesNotFoundCount() {
        return tablesNotFoundCount;
    }

    synchronized public void addTablesNotFound(String tableName) {
        tablesNotFoundCount++;
        tablesNotFoundMessage = abbreviateMessage(tablesNotFoundMessage, tableName);
    }

    synchronized public void addTriggersFailed(String tableName, Exception e) {
        triggersFailedCount++;
        if (this.triggersFailedException == null) {
            this.triggersFailedException = e;
        }
        triggersFailedMessage = abbreviateMessage(triggersFailedMessage, tableName);
    }

    protected String abbreviateMessage(String message, String name) {
        if (message == null) {
            message = name;
        } else if (message.length() < 500) {
            message += ", " + name;
        } else if (!message.endsWith("...")) {
            message += "...";
        }
        return message;
    }

    synchronized public String getErrorMessage() {
        String message = "";
        if (tablesNotFoundCount > 0) {
            message += tablesNotFoundCount + " tables were not found: " + tablesNotFoundMessage;
        }
        if (triggersFailedCount > 0) {
            message += (message.isEmpty() ? "" : "\n") + triggersFailedCount + " table triggers failed: " + triggersFailedMessage;
            if (triggersFailedException != null) {
                message += "\n" + triggersFailedException.getClass().getSimpleName() + (triggersFailedException.getMessage() == null ? ""
                        : ": " + triggersFailedException.getMessage());
            }
        }
        return message;
    }
}
