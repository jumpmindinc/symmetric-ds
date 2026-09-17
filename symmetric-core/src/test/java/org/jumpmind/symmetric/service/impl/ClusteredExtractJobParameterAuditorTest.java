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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.impl.IParameterAuditor.AuditedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusteredExtractJobParameterAuditorTest {
    private ClusteredExtractJobParameterAuditor auditor;

    @BeforeEach
    void setUp() {
        auditor = new ClusteredExtractJobParameterAuditor();
    }

    @Test
    void testAudit_withNoParametersSetLeavesThemAlone() {
        AuditedProperties audited = auditor.audit(new TypedProperties());
        assertFalse(audited.isModified());
        assertEquals("", audited.violationMessage());
    }

    @Test
    void testAudit_withClusterLockingDisabledLeavesParametersAlone() {
        TypedProperties parameters = newParameters("false", "true", "true");
        AuditedProperties audited = auditor.audit(parameters);
        assertFalse(audited.isModified());
        assertSame(parameters, audited.parameters());
    }

    @Test
    void testAudit_withExtractJobDisabledLeavesParametersAlone() {
        AuditedProperties audited = auditor.audit(newParameters("true", "true", "false"));
        assertFalse(audited.isModified());
        assertEquals("", audited.violationMessage());
    }

    // Defect pinned, not endorsed: the guard fires when cluster.staging.enabled is true, yet the
    // message it reports says staging is not clustered. Because the shipped default is false, the
    // audit never trips for the combination it is meant to catch.
    @Test
    void testAudit_withClusteredStagingEnabledDisablesTheExtractJob() {
        TypedProperties parameters = newParameters("true", "true", "true");
        AuditedProperties audited = auditor.audit(parameters);
        assertTrue(audited.isModified());
        assertNotSame(parameters, audited.parameters());
        assertFalse(audited.parameters().is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB));
        assertTrue(audited.parameters().is(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }

    @Test
    void testAudit_withClusteredStagingEnabledLeavesTheInputPropertiesUntouched() {
        TypedProperties parameters = newParameters("true", "true", "true");
        auditor.audit(parameters);
        assertTrue(parameters.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB));
    }

    @Test
    void testAudit_withClusteredStagingEnabledNamesAllThreeParametersInTheMessage() {
        String message = auditor.audit(newParameters("true", "true", "true")).violationMessage();
        assertTrue(message.contains(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB + "=true"));
        assertTrue(message.contains(ParameterConstants.CLUSTER_LOCKING_ENABLED + "=true"));
        assertTrue(message.contains(ParameterConstants.CLUSTER_STAGING_ENABLED + "=false"));
    }

    @Test
    void testAudit_withStagingUnsetUsesTheDefaultOfTrue() {
        TypedProperties parameters = new TypedProperties();
        parameters.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "true");
        parameters.setProperty(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB, "true");
        assertTrue(auditor.audit(parameters).isModified());
    }

    private TypedProperties newParameters(String clusterLocking, String clusterStaging, String extractJob) {
        TypedProperties parameters = new TypedProperties();
        parameters.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, clusterLocking);
        parameters.setProperty(ParameterConstants.CLUSTER_STAGING_ENABLED, clusterStaging);
        parameters.setProperty(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB, extractJob);
        return parameters;
    }
}
