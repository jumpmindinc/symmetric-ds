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

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.Winsvc.SC_HANDLE;
import com.sun.jna.platform.win32.Winsvc.SERVICE_STATUS;
import com.sun.jna.win32.W32APIOptions;

@IgnoreJRERequirement
public interface Advapi32Ex extends Advapi32 {
    Advapi32Ex INSTANCE = (Advapi32Ex) Native.load("Advapi32", Advapi32Ex.class,
            W32APIOptions.UNICODE_OPTIONS);

    boolean ChangeServiceConfig2(SC_HANDLE service, int infoLevel, SERVICE_INFO info);

    boolean DeleteService(SC_HANDLE serviceHandle);

    boolean StartServiceCtrlDispatcher(Structure[] serviceTable);

    boolean SetServiceStatus(Pointer serviceStatusHandle, SERVICE_STATUS serviceStatus);

    SERVICE_STATUS_HANDLE RegisterServiceCtrlHandlerEx(String serviceName,
            HANDLER_FUNCTION handler, Object context);

    public static class SERVICE_INFO extends Structure {
        @Override
        protected List<String> getFieldOrder() {
            return null;
        }
    }

    public static class SERVICE_DESCRIPTION extends SERVICE_INFO {
        public String description;

        public SERVICE_DESCRIPTION(String description) {
            this.description = description;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "description" });
        }
    }

    public static class SERVICE_STATUS_HANDLE extends HANDLE {
        public SERVICE_STATUS_HANDLE() {
        }

        public SERVICE_STATUS_HANDLE(Pointer p) {
            super(p);
        }
    }

    public interface HANDLER_FUNCTION extends StdCallCallback {
        void serviceControlHandler(int controlCode);
    }
}
