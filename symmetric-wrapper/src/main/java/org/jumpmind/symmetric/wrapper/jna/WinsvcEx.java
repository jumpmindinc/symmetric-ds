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
package org.jumpmind.symmetric.wrapper.jna;

import java.util.Arrays;
import java.util.List;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.jumpmind.symmetric.wrapper.jna.Advapi32Ex.SERVICE_INFO;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

@IgnoreJRERequirement
public interface WinsvcEx extends Winsvc {
    int SERVICE_WIN32_OWN_PROCESS = 0x00000010;
    int SERVICE_AUTO_START = 0x00000002;
    int SERVICE_DEMAND_START = 0x00000003;
    int SERVICE_ERROR_NORMAL = 0x00000001;
    int SERVICE_CONFIG_DESCRIPTION = 1;
    int SERVICE_CONFIG_FAILURE_ACTIONS = 2;
    int SERVICE_CONFIG_DELAYED_AUTO_START_INFO = 3;
    int SERVICE_CONFIG_FAILURE_ACTIONS_FLAG = 4;
    int SC_ACTION_NONE = 0;
    int SC_ACTION_RESTART = 1;
    int SC_ACTION_REBOOT = 2;
    int SC_ACTION_RUN_COMMAND = 3;

    public interface SERVICE_MAIN_FUNCTION extends StdCallCallback {
        void serviceMain(int argc, Pointer argv);
    }

    public static class SERVICE_TABLE_ENTRY extends Structure {
        public String serviceName;
        public SERVICE_MAIN_FUNCTION serviceCallback;

        public SERVICE_TABLE_ENTRY() {
        }

        public SERVICE_TABLE_ENTRY(String serviceName, SERVICE_MAIN_FUNCTION serviceCallback) {
            this.serviceName = serviceName;
            this.serviceCallback = serviceCallback;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "serviceName", "serviceCallback" });
        }
    }

    public static class SERVICE_DELAYED_AUTO_START_INFO extends SERVICE_INFO {
        public int fDelayedAutostart;

        public SERVICE_DELAYED_AUTO_START_INFO() {
        }

        public SERVICE_DELAYED_AUTO_START_INFO(boolean fDelayedAutostart) {
            this.fDelayedAutostart = fDelayedAutostart ? 1 : 0;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "fDelayedAutostart" });
        }
    }

    public static class SERVICE_FAILURE_ACTIONS_FLAG extends SERVICE_INFO {
        public boolean fFailureActionsOnNonCrashFailures;

        public SERVICE_FAILURE_ACTIONS_FLAG() {
        }

        public SERVICE_FAILURE_ACTIONS_FLAG(boolean fFailureActionsOnNonCrashFailures) {
            this.fFailureActionsOnNonCrashFailures = fFailureActionsOnNonCrashFailures;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "fFailureActionsOnNonCrashFailures" });
        }
    }

    public static class SERVICE_FAILURE_ACTIONS extends SERVICE_INFO {
        public int dwResetPeriod;
        public String lpRebootMsg;
        public WString lpCommand;
        public int cActions;
        public SC_ACTION.ByReference lpsaActions;

        public SERVICE_FAILURE_ACTIONS() {
        }

        public SERVICE_FAILURE_ACTIONS(int dwResetPeriod, String lpRebootMsg, WString lpCommand, int cActions, SC_ACTION.ByReference lpsaActions) {
            this.dwResetPeriod = dwResetPeriod;
            this.lpRebootMsg = lpRebootMsg;
            this.lpCommand = lpCommand;
            this.cActions = cActions;
            this.lpsaActions = lpsaActions;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "dwResetPeriod", "lpRebootMsg", "lpCommand", "cActions", "lpsaActions" });
        }
    }

    public static class SC_ACTION extends Structure {
        public static class ByReference extends SC_ACTION implements Structure.ByReference {
        }

        public int type;
        public int delay;

        public SC_ACTION() {
        }

        public SC_ACTION(int type, int delay) {
            this.type = type;
            this.delay = delay;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "type", "delay" });
        }
    }
}
