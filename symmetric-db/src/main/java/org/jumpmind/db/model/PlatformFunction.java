package org.jumpmind.db.model;

import java.io.Serializable;

public class PlatformFunction implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String functionText;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFunctionText() {
        return functionText;
    }

    public void setFunctionText(String functionText) {
        this.functionText = functionText;
    }

    public Object clone() throws CloneNotSupportedException {
        PlatformFunction result = (PlatformFunction) super.clone();
        result.name = name;
        result.functionText = functionText;
        return result;
    }
}
