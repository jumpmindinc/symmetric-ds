package org.jumpmind.db.model;

import java.io.Serializable;

public class PlatformTrigger implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String triggerText;
    private Function function;

    public PlatformTrigger() {
    }

    public PlatformTrigger(String name, String triggerText) {
        this.name = name;
        this.triggerText = triggerText;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTriggerText() {
        return triggerText;
    }

    public void setTriggerText(String triggerText) {
        this.triggerText = triggerText;
    }

    public Object clone() throws CloneNotSupportedException {
        PlatformTrigger result = (PlatformTrigger) super.clone();
        result.name = name;
        result.triggerText = triggerText;
        if (function != null) {
            result.function = (Function) function.clone();
        }
        return result;
    }

    public Function getFunction() {
        return function;
    }

    public void setFunction(Function function) {
        this.function = function;
    }
}
