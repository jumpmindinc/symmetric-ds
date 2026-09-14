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
package org.jumpmind.symmetric.wrapper;

public class Constants {
    public enum Status {
        START_PENDING, RUNNING, STOP_PENDING, STOPPED;
    }

    public static final int RC_BAD_USAGE = 1;
    public static final int RC_INVALID_ARGUMENT = 2;
    public static final int RC_MISSING_CONFIG_FILE = 3;
    public static final int RC_FAIL_READ_CONFIG_FILE = 4;
    public static final int RC_SERVER_ALREADY_RUNNING = 5;
    public static final int RC_SERVER_NOT_RUNNING = 6;
    public static final int RC_FAIL_WRITE_LOG_FILE = 7;
    public static final int RC_FAIL_EXECUTION = 8;
    public static final int RC_FAIL_STOP_SERVER = 9;
    public static final int RC_NO_INSTALL_WHEN_RUNNING = 10;
    public static final int RC_NOT_INSTALLED = 11;
    public static final int RC_ALREADY_INSTALLED = 12;
    public static final int RC_FAIL_REGISTER_SERVICE = 13;
    public static final int RC_MUST_BE_ROOT = 14;
    public static final int RC_MISSING_INIT_FOLDER = 15;
    public static final int RC_SERVER_EXITED = 16;
    public static final int RC_FAIL_INSTALL = 17;
    public static final int RC_FAIL_UNINSTALL = 18;
    public static final int RC_NATIVE_ERROR = 19;
    public static final int RC_MISSING_LIB_FOLDER = 20;
    public static final int RC_MISSING_SERVER_PROPERTIES = 21;
    public static final int RC_FAIL_CHECK_STATUS = 23;
    public static final int RC_ALREADY_RUNNING = 24;
}
