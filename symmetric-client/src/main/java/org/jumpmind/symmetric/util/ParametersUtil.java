package org.jumpmind.symmetric.util;

import java.util.Properties;

import org.jumpmind.symmetric.common.ParameterConstants;

public class ParametersUtil {
    static Properties deepCopy(Properties parameters) {
        Properties newParameters = new Properties();
        for (String key : parameters.stringPropertyNames()) {
            String value = parameters.getProperty(key);
            newParameters.setProperty(key, value);
        }
        return newParameters;
    }

    static void redactParameters(Properties parameters) {
        for (String name : ParameterConstants.REDACTED_PROPERTIES) {
            if (parameters.containsKey(name)) {
                parameters.put(name, ParameterConstants.REDACTED);
            }
        }
    }
}
