package org.jumpmind.symmetric.service.impl;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;

public class ClusteredExtractJobParameterAuditor implements IParameterAuditor {
    @Override
    public void audit(TypedProperties parameters, ParameterService parameterService) {
        if (parameters.is(ParameterConstants.CLUSTER_LOCKING_ENABLED, false)
                && parameters.is(ParameterConstants.CLUSTER_STAGING_ENABLED, true)
                && parameters.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB, true)) {
            parameters.setProperty(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB, "false");
            parameterService.setDatabaseHasBeenInitialized(true);
        }
    }
}
