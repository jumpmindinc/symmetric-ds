package org.jumpmind.symmetric.util;

import java.util.Properties;

import org.jumpmind.symmetric.common.ParameterConstants;

public class RedactionUtil {
    public static void redactParameters(Properties parameters) {
        for (String name : ParameterConstants.REDACTED_PROPERTIES) {
            if (parameters.containsKey(name)) {
                parameters.put(name, ParameterConstants.REDACTED);
            }
        }
    }
}
