package org.jumpmind.db.alter;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;

public class RemoveTriggerChange extends TableChangeImplBase {
    private Trigger _trigger;

    public RemoveTriggerChange(Table table, Trigger trigger) {
        super(table);
        this._trigger = trigger;
    }

    public Trigger getTrigger() {
        return _trigger;
    }

    @Override
    public void apply(Database database, boolean caseSensitive) {
        Table table = database.findTable(getChangedTable().getName(), caseSensitive);
        Trigger trigger = table.findTrigger(_trigger.getName(), caseSensitive);
        table.removeTrigger(trigger);
    }
}
