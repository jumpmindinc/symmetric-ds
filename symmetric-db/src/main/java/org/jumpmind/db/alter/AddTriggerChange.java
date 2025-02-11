package org.jumpmind.db.alter;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;

public class AddTriggerChange extends TableChangeImplBase {
    private Trigger _newTrigger;

    public AddTriggerChange(Table table, Trigger newTrigger) {
        super(table);
        this._newTrigger = newTrigger;
    }

    public Trigger getNewTrigger() {
        return _newTrigger;
    }

    @Override
    public void apply(Database database, boolean caseSensitive) {
        Table table = database.findTable(getChangedTable().getName(), caseSensitive);
        Trigger trigger = table.findTrigger(_newTrigger.getName(), caseSensitive);
        table.addTrigger(trigger);
    }
}
