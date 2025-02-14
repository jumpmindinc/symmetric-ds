package org.jumpmind.db.alter;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Function;
import org.jumpmind.db.model.PlatformTrigger;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;

public class AddFunctionChange extends TableChangeImplBase {
    private Function _newFunction;
    private Trigger trigger;

    public AddFunctionChange(Table table, Trigger trigger, Function newFunction) {
        super(table);
        _newFunction = newFunction;
        this.trigger = trigger;
    }

    public Function getNewFunction() {
        return _newFunction;
    }

    @Override
    public void apply(Database database, boolean caseSensitive) {
        Trigger trigger = this.trigger;
        if (trigger != null) {
            if (trigger.getPlatformTriggers() != null && trigger.getPlatformTriggers().size() > 0) {
                PlatformTrigger platformTrigger = trigger.getPlatformTriggers().entrySet().iterator().next().getValue();
                platformTrigger.setFunction(_newFunction);
            }
        }
    }
}
