package org.jumpmind.symmetric.service.impl;

import org.jumpmind.properties.TypedProperties;

public interface IParameterAuditor {
    void audit(TypedProperties parameters, ParameterService parameterService);
}
