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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Wrapper {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            System.exit(Constants.RC_BAD_USAGE);
        }
        String appDir = null;
        String configFile = null;
        // Decode spaces and other special characters
        String jarFile = Wrapper.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
        if (args.length == 2) {
            configFile = args[1];
            appDir = getParentDir(configFile);
        } else if (args.length == 3) {
            configFile = args[1];
            appDir = args[2];
        } else {
            appDir = getParentDir(jarFile);
            configFile = findConfigFile(appDir + File.separator + "conf");
        }
        WrapperService service = WrapperService.getInstance();
        try {
            service.loadConfig(appDir, configFile, jarFile);
        } catch (FileNotFoundException e) {
            System.out.println("Missing config file " + configFile);
            System.out.println(e.getMessage());
            System.exit(Constants.RC_MISSING_CONFIG_FILE);
        } catch (IOException e) {
            System.out.println("Cannot read config file " + configFile);
            System.out.println(e.getMessage());
            System.exit(Constants.RC_FAIL_READ_CONFIG_FILE);
        }
        try {
            if (args[0].equalsIgnoreCase("start")) {
                service.start();
            } else if (args[0].equalsIgnoreCase("exec")) {
                service.execJava(false);
            } else if (args[0].equalsIgnoreCase("init")) {
                service.init();
            } else if (args[0].equalsIgnoreCase("stop")) {
                service.stop();
            } else if (args[0].equalsIgnoreCase("restart")) {
                service.restart();
            } else if (args[0].equalsIgnoreCase("install")) {
                service.install();
            } else if (args[0].equalsIgnoreCase("uninstall")) {
                service.uninstall();
            } else if (args[0].equalsIgnoreCase("status")) {
                service.status();
            } else if (args[0].equalsIgnoreCase("console")) {
                service.console();
            } else {
                System.out.println("ERROR: Invalid argument");
                printUsage();
                System.exit(Constants.RC_INVALID_ARGUMENT);
            }
        } catch (WrapperException e) {
            System.out.println("Error " + e.getErrorCode() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("Exception " + e.getCause().getClass().getSimpleName() + ": "
                        + e.getCause().getMessage());
            }
            if (e.getNativeErrorCode() > 0) {
                System.out.println("Native error " + e.getErrorCode());
            }
            System.exit(e.getErrorCode());
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.exit(-1);
        }
        System.exit(0);
    }

    protected static String getParentDir(String filepath) {
        int index = filepath.lastIndexOf(File.separator);
        if (index == -1) {
            return "..";
        } else {
            return filepath.substring(0, index + 1) + "..";
        }
    }

    protected static String findConfigFile(String dirpath) {
        File dir = new File(dirpath);
        if (dir.exists() && dir.isDirectory()) {
            String[] files = dir.list();
            if (files != null) {
                for (String name : files) {
                    if (name.endsWith("_service.conf")) {
                        return dirpath + File.separator + name;
                    }
                }
            }
        }
        return dirpath + File.separator + "wrapper_service.conf";
    }

    protected static void printUsage() {
        System.out.println("Usage: <start|stop|restart|install|uninstall|status|console>");
        System.out.println("   start      - Start service");
        System.out.println("   stop       - Stop service");
        System.out.println("   restart    - Restart service");
        System.out.println("   install    - Install service");
        System.out.println("   uninstall  - Uninstall service");
        System.out.println("   status     - Status of service");
        System.out.println("   console    - Run from console");
    }
}
