/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractCommandLauncherTest {
    private static final SymmetricAdmin LAUNCHER = new SymmetricAdmin("symadmin", "<subcommand> [options] [args]", "SymAdmin.Option.");

    @BeforeEach
    void setUp() {
        System.clearProperty(ServerConstants.CONTAINER_MODE_ENABLED);
        System.clearProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(ServerConstants.CONTAINER_MODE_ENABLED);
        System.clearProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
    }

    @Test
    void setContainerized_true_setsContainerModeEnabledSystemProperty() {
        LAUNCHER.setContainerized(true);
        assertEquals("true", System.getProperty(ServerConstants.CONTAINER_MODE_ENABLED));
    }

    @Test
    void setContainerized_false_setsContainerModeEnabledSystemPropertyToFalse() {
        LAUNCHER.setContainerized(false);
        assertEquals("false", System.getProperty(ServerConstants.CONTAINER_MODE_ENABLED));
    }

    @Test
    void setContainerized_true_updatesIsContainerized() {
        LAUNCHER.setContainerized(true);
        assertTrue(LAUNCHER.isContainerized());
    }

    @Test
    void setContainerized_false_updatesIsContainerized() {
        LAUNCHER.setContainerized(false);
        assertFalse(LAUNCHER.isContainerized());
    }

    @Test
    void setContainerized_true_neverTouchesClusterLockingEnabled() {
        LAUNCHER.setContainerized(true);
        assertNull(System.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }

    @Test
    void setContainerized_true_doesNotOverrideExplicitlySetClusterLockingEnabled() {
        System.setProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED, "false");
        LAUNCHER.setContainerized(true);
        assertEquals("false", System.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED));
    }
}
