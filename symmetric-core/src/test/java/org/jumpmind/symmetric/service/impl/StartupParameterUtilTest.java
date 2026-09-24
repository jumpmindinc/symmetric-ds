package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jumpmind.symmetric.common.ServerConstants;
import org.junit.jupiter.api.Test;

class StartupParameterUtilTest {
    @Test
    void testGetEquivalentEnvVarNameForParam() {
        assertEquals("SYM_DB_POOL_MAX_ACTIVE", StartupParameterUtil.getEquivalentEnvVarNameForParam("db.pool.max.active"));
    }

    @Test
    void testGetEquivalentEnvVarNameForParam_withPrefixedParam() {
        assertEquals("SYM_TARGET_DB_POOL_MAX_ACTIVE", StartupParameterUtil.getEquivalentEnvVarNameForParam("target.db.pool.max.active"));
    }

    @Test
    void testGetEquivalentEnvVarNameForParam_withoutSeparators() {
        assertEquals("SYM_CLUSTER", StartupParameterUtil.getEquivalentEnvVarNameForParam("cluster"));
    }

    @Test
    void testGetEquivalentEnvVarNameForParam_withMixedCase() {
        assertEquals("SYM_DB_POOL_MAX_ACTIVE", StartupParameterUtil.getEquivalentEnvVarNameForParam("Db.Pool.Max.Active"));
    }

    @Test
    void testGetEquivalentEnvVarNameForParam_withEmptyParam() {
        assertEquals(ServerConstants.SYM_ENV_PREFIX, StartupParameterUtil.getEquivalentEnvVarNameForParam(""));
    }

    @Test
    void testGetEquivalentEnvVarNameForParam_throwsWhenParamIsNull() {
        assertThrows(NullPointerException.class, () -> StartupParameterUtil.getEquivalentEnvVarNameForParam(null));
    }
}
