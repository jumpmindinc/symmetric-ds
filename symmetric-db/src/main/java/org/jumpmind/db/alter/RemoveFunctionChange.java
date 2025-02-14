package org.jumpmind.db.alter;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Function;
import org.jumpmind.db.model.PlatformTrigger;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;

public class RemoveFunctionChange extends TableChangeImplBase {
    private Function function;
    private Trigger trigger;

    public RemoveFunctionChange(Table table, Trigger trigger, Function function) {
        super(table);
        this.trigger = trigger;
        this.function = function;
    }

    public Function getFunction() {
        return function;
    }

    @Override
    public void apply(Database database, boolean caseSensitive) {
        Table table = database.findTable(getChangedTable().getName(), caseSensitive);
        Trigger trigger = table.findTrigger(this.trigger.getName(), caseSensitive);
        PlatformTrigger platformTrigger = trigger.findPlatformTrigger(database.getName());
        if (platformTrigger != null) {
            platformTrigger.setFunction(null);
        }
    }
}
