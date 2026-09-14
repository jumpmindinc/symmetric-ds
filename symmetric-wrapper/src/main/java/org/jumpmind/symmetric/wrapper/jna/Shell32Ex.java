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
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.win32.W32APIOptions;

@IgnoreJRERequirement
public interface Shell32Ex extends Shell32 {
    Shell32Ex INSTANCE = (Shell32Ex) Native.load("shell32", Shell32Ex.class, W32APIOptions.UNICODE_OPTIONS);
    int SW_HIDE = 0;
    int SW_MAXIMIZE = 3;
    int SW_MINIMIZE = 6;
    int SW_RESTORE = 9;
    int SW_SHOW = 5;
    int SW_SHOWDEFAULT = 10;
    int SW_SHOWMAXIMIZED = 3;
    int SW_SHOWMINIMIZED = 2;
    int SW_SHOWMINNOACTIVE = 7;
    int SW_SHOWNA = 8;
    int SW_SHOWNOACTIVATE = 4;
    int SW_SHOWNORMAL = 1;
    int SE_ERR_FNF = 2;
    int SE_ERR_PNF = 3;
    int SE_ERR_ACCESSDENIED = 5;
    int SE_ERR_OOM = 8;
    int SE_ERR_DLLNOTFOUND = 32;
    int SE_ERR_SHARE = 26;
    int SEE_MASK_NOCLOSEPROCESS = 0x00000040;

    int ShellExecute(int i, String lpVerb, String lpFile, String lpParameters, String lpDirectory, int nShow);

    boolean ShellExecuteEx(SHELLEXECUTEINFO lpExecInfo);

    public static class SHELLEXECUTEINFO extends Structure {
        public int cbSize = size();
        public int fMask;
        public HWND hwnd;
        public WString lpVerb;
        public WString lpFile;
        public WString lpParameters;
        public WString lpDirectory;
        public int nShow;
        public HINSTANCE hInstApp;
        public Pointer lpIDList;
        public WString lpClass;
        public HKEY hKeyClass;
        public int dwHotKey;
        public HANDLE hMonitor;
        public HANDLE hProcess;

        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "cbSize", "fMask", "hwnd", "lpVerb", "lpFile", "lpParameters", "lpDirectory", "nShow",
                    "hInstApp", "lpIDList", "lpClass", "hKeyClass", "dwHotKey", "hMonitor", "hProcess", });
        }
    }
}