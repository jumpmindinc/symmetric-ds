package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Properties;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ParametersUtilTest {
    private ISymmetricEngine engine;

    @BeforeEach
    void setup() {
        engine = mock(ISymmetricEngine.class);
    }

    @Test
    void deepCopyTest() {
        Properties effectiveParameters = engine.getParameterService().getAllParameters();
        Properties newParameters = ParametersUtil.deepCopy(effectiveParameters);
        for (String key : effectiveParameters.stringPropertyNames()) {
            String oldValue = effectiveParameters.getProperty(key);
            String newValue = newParameters.getProperty(key);
            assertEquals(oldValue, newValue);
        }
    }

    @Test
    void redactParametersTest() {
        Properties effectiveParameters = engine.getParameterService().getAllParameters();
        Properties parameters = ParametersUtil.deepCopy(effectiveParameters);
        ParametersUtil.redactParameters(parameters);
        for (String key : parameters.stringPropertyNames()) {
            if (Arrays.asList(ParameterConstants.REDACTED_PROPERTIES).contains(key)) {
                String value = parameters.getProperty(key);
                assertEquals(ParameterConstants.REDACTED, value);
            }
        }
    }
}
