/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigDecimal;
import java.util.Map;

import org.jumpmind.db.sql.SqlException;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractParameterServiceTest {
    private TypedProperties applicationParameters;
    private TestParameterService parameterService;

    @BeforeEach
    void setUp() {
        applicationParameters = new TypedProperties();
        parameterService = new TestParameterService(applicationParameters);
    }

    @Test
    void testGetDecimal() {
        applicationParameters.setProperty("amount", "12.50");
        assertEquals(new BigDecimal("12.50"), parameterService.getDecimal("amount"));
    }

    @Test
    void testGetDecimal_withMissingValueReturnsZero() {
        assertEquals(BigDecimal.ZERO, parameterService.getDecimal("amount"));
    }

    @Test
    void testGetDecimal_withUnparseableValueReturnsDefault() {
        applicationParameters.setProperty("amount", "not-a-number");
        assertEquals(BigDecimal.ONE, parameterService.getDecimal("amount", BigDecimal.ONE));
    }

    @Test
    void testIs_withOne() {
        applicationParameters.setProperty("flag", " 1 ");
        assertTrue(parameterService.is("flag"));
    }

    @Test
    void testIs_withTrueIgnoringCase() {
        applicationParameters.setProperty("flag", "TRUE");
        assertTrue(parameterService.is("flag"));
    }

    @Test
    void testIs_withUnrecognizedValue() {
        applicationParameters.setProperty("flag", "yes");
        assertFalse(parameterService.is("flag"));
    }

    @Test
    void testIs_withMissingValueReturnsDefault() {
        assertTrue(parameterService.is("flag", true));
        assertFalse(parameterService.is("flag"));
    }

    @Test
    void testGetInt() {
        applicationParameters.setProperty("count", " 25 ");
        assertEquals(25, parameterService.getInt("count"));
    }

    @Test
    void testGetInt_withMissingValueReturnsZero() {
        assertEquals(0, parameterService.getInt("count"));
    }

    @Test
    void testGetInt_withUnparseableValueReturnsDefault() {
        applicationParameters.setProperty("count", "lots");
        assertEquals(9, parameterService.getInt("count", 9));
    }

    @Test
    void testGetLong() {
        applicationParameters.setProperty("count", "9000000000");
        assertEquals(9000000000L, parameterService.getLong("count"));
    }

    @Test
    void testGetLong_withUnparseableValueReturnsDefault() {
        applicationParameters.setProperty("count", "lots");
        assertEquals(4L, parameterService.getLong("count", 4L));
    }

    @Test
    void testGetString_withBlankValueReturnsDefault() {
        applicationParameters.setProperty("name", "   ");
        assertEquals("fallback", parameterService.getString("name", "fallback"));
    }

    @Test
    void testGetString_withMissingValue() {
        assertNull(parameterService.getString("name"));
    }

    @Test
    void testGetTempDirectory_isQualifiedByTheEngineName() {
        applicationParameters.setProperty(ParameterConstants.ENGINE_NAME, "store-001");
        applicationParameters.setProperty("java.io.tmpdir", "/var/tmp");
        assertEquals("/var/tmp" + File.separator + "store-001", parameterService.getTempDirectory());
    }

    @Test
    void testGetParameters_cachesTheApplicationParameters() {
        parameterService.getString("name");
        parameterService.getString("name");
        assertEquals(1, parameterService.rereadCount);
    }

    @Test
    void testRereadParameters_withARefreshPeriodRereads() {
        applicationParameters.setProperty(ParameterConstants.PARAMETER_REFRESH_PERIOD_IN_MS, "1");
        parameterService.getString("name");
        parameterService.rereadParameters();
        assertEquals(2, parameterService.rereadCount);
    }

    // Reachable only when parameter.reload.timeout.ms is unset: the cache-timeout conjunct in
    // getParameters short circuits, so zeroing the cache timestamp does not force a reread.
    @Test
    void testRereadParameters_withoutARefreshPeriodKeepsTheCachedParameters() {
        parameterService.getString("name");
        parameterService.rereadParameters();
        assertEquals(1, parameterService.rereadCount);
    }

    @Test
    void testGetParameters_rethrowsWhenTheDatabaseCannotBeRead() {
        parameterService.failure = new SqlException("database is down");
        assertThrows(SqlException.class, () -> parameterService.getString("name"));
    }

    @Test
    void testGetLastTimeParameterWereCached_beforeAnyRead() {
        assertEquals(0L, parameterService.getLastTimeParameterWereCached().getTime());
    }

    @Test
    void testGetAllParameters() {
        applicationParameters.setProperty("name", "value");
        assertEquals("value", parameterService.getAllParameters().get("name"));
    }

    @Test
    void testGetExternalId() {
        applicationParameters.setProperty(ParameterConstants.EXTERNAL_ID, "store-001");
        assertEquals("store-001", parameterService.getExternalId());
    }

    @Test
    void testGetExternalId_isMemoized() {
        applicationParameters.setProperty(ParameterConstants.EXTERNAL_ID, "store-001");
        parameterService.getExternalId();
        applicationParameters.setProperty(ParameterConstants.EXTERNAL_ID, "store-002");
        assertEquals("store-001", parameterService.getExternalId());
    }

    @Test
    void testGetExternalId_evaluatesAnEmbeddedScript() {
        applicationParameters.setProperty(ParameterConstants.EXTERNAL_ID, "`\"store-\" + \"001\"`");
        assertEquals("store-001", parameterService.getExternalId());
    }

    @Test
    void testGetSyncUrl_stripsTrailingSlashes() {
        applicationParameters.setProperty(ParameterConstants.SYNC_URL, "  http://localhost:31415/sync/corp//  ");
        assertEquals("http://localhost:31415/sync/corp", parameterService.getSyncUrl());
    }

    @Test
    void testGetSyncUrl_withMissingValue() {
        assertNull(parameterService.getSyncUrl());
    }

    @Test
    void testGetNodeGroupId() {
        applicationParameters.setProperty(ParameterConstants.NODE_GROUP_ID, "store");
        assertEquals("store", parameterService.getNodeGroupId());
    }

    @Test
    void testGetRegistrationUrl_isTrimmed() {
        applicationParameters.setProperty(ParameterConstants.REGISTRATION_URL, "  http://localhost:31415/sync/corp  ");
        assertEquals("http://localhost:31415/sync/corp", parameterService.getRegistrationUrl());
    }

    @Test
    void testGetEngineName_withMissingValueFallsBackToTheProductName() {
        assertEquals("SymmetricDS", parameterService.getEngineName());
    }

    @Test
    void testGetReplacementValues_beforeAnythingHasBeenResolved() {
        Map<String, String> values = parameterService.getReplacementValues();
        assertEquals(5, values.size());
        assertNull(values.get("externalId"));
    }

    @Test
    void testGetReplacementValues_afterResolvingTheIdentity() {
        applicationParameters.setProperty(ParameterConstants.EXTERNAL_ID, "store-001");
        applicationParameters.setProperty(ParameterConstants.NODE_GROUP_ID, "store");
        parameterService.getExternalId();
        parameterService.getNodeGroupId();
        Map<String, String> values = parameterService.getReplacementValues();
        assertEquals("store-001", values.get("externalId"));
        assertEquals("store", values.get("nodeGroupId"));
    }

    @Test
    void testSetDatabaseHasBeenInitialized_discardsTheCachedParameters() {
        parameterService.getString("name");
        parameterService.setDatabaseHasBeenInitialized(true);
        parameterService.getString("name");
        assertEquals(2, parameterService.rereadCount);
    }

    @Test
    void testSetDatabaseHasBeenInitialized_withTheSameValueKeepsTheCachedParameters() {
        parameterService.getString("name");
        parameterService.setDatabaseHasBeenInitialized(false);
        parameterService.getString("name");
        assertEquals(1, parameterService.rereadCount);
    }

    @Test
    void testHasDatabaseBeenSetup() {
        assertFalse(parameterService.hasDatabaseBeenSetup());
        parameterService.setDatabaseHasBeenSetup(true);
        assertTrue(parameterService.hasDatabaseBeenSetup());
    }

    @Test
    void testRereadDatabaseParameters_withAnUninitializedDatabase() {
        assertTrue(parameterService.rereadDatabaseParameters(new TypedProperties()).isEmpty());
    }

    @Test
    void testRereadDatabaseParameters_layersTheMostSpecificScopeLast() {
        parameterService.setDatabaseHasBeenInitialized(true);
        TypedProperties identity = new TypedProperties();
        identity.setProperty(ParameterConstants.EXTERNAL_ID, "store-001");
        identity.setProperty(ParameterConstants.NODE_GROUP_ID, "store");
        assertEquals("store-001/store", parameterService.rereadDatabaseParameters(identity).get("scope"));
    }

    private static class TestParameterService extends AbstractParameterService {
        private final TypedProperties applicationParameters;
        private int rereadCount;
        private SqlException failure;

        TestParameterService(TypedProperties applicationParameters) {
            this.applicationParameters = applicationParameters;
        }

        @Override
        protected TypedProperties rereadApplicationParameters() {
            rereadCount++;
            if (failure != null) {
                throw failure;
            }
            return applicationParameters;
        }

        @Override
        public TypedProperties getDatabaseParameters(String externalId, String nodeGroupId) {
            TypedProperties properties = new TypedProperties();
            properties.setProperty("scope", externalId + "/" + nodeGroupId);
            return properties;
        }
    }
}
