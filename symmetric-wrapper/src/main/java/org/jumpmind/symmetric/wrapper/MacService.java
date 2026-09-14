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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MacService extends UnixService {
    // private static final Logger logger = Logger.getLogger(MacService.class.getName());
    private static final String LAUNCH_DAEMON_DIR = "/Library/LaunchDaemons";
    private static final String LAUNCH_DAEMON_NAME_PREFIX = "com.jumpmind.";
    private static final String LAUNCH_DAEMON_NAME_SUFFIX = ".plist";
    private static final String LAUNCH_DAEMON_LOAD_COMMAND = "load";
    private static final String LAUNCH_DAEMON_UNLOAD_COMMAND = "unload";
    private static final String LAUNCH_DAEMON_STOP_COMMAND = "stop";
    private static final String LAUNCH_DAEMON_START_COMMAND = "start";

    @Override
    public void install() {
        // Write the /Library/LaunchDaemons file
        File fileToWrite = new File(LAUNCH_DAEMON_DIR, getLaunchDaemonsFileName(config.getName()));
        System.out.println("Writing launch daemon file " + fileToWrite.getAbsolutePath());
        writeLaunchDaemonFile(fileToWrite);
        // Run launchctl to register
        // launchctl load -w /Library/LaunchDaemons/com.jumpmind.symmetricds.plist
        ArrayList<String> loadCmd = getLaunchDaemonLoadCommand();
        boolean success = runLaunchCtlCmd(loadCmd);
        if (!success) {
            throw new WrapperException(Constants.RC_FAIL_REGISTER_SERVICE, 0, "Failed to install service");
        }
    }

    private ArrayList<String> getLaunchDaemonLoadCommand() {
        return getLaunchDaemonCmd(
                LAUNCH_DAEMON_LOAD_COMMAND,
                "-w",
                LAUNCH_DAEMON_DIR + "/" + getLaunchDaemonsFileName(config.getName()));
    }

    private ArrayList<String> getLaunchDaemonStopCmd() {
        return getLaunchDaemonCmd(
                LAUNCH_DAEMON_STOP_COMMAND,
                null,
                getLaunchDaemonsName(config.getName()));
    }

    private ArrayList<String> getLaunchDaemonUnloadCmd() {
        return getLaunchDaemonCmd(
                LAUNCH_DAEMON_UNLOAD_COMMAND,
                "-w",
                LAUNCH_DAEMON_DIR + "/" + getLaunchDaemonsFileName(config.getName()));
    }

    private ArrayList<String> getLaunchDaemonStartCmd() {
        return getLaunchDaemonCmd(
                LAUNCH_DAEMON_START_COMMAND,
                null,
                getLaunchDaemonsName(config.getName()));
    }

    private ArrayList<String> getLaunchDaemonCmd(String command, String override, String option) {
        ArrayList<String> cmdList = new ArrayList<String>();
        cmdList.add("launchctl");
        cmdList.add(command);
        if (override != null && override.length() > 0) {
            cmdList.add(override);
        }
        cmdList.add(option);
        return cmdList;
    }

    @Override
    public void uninstall() {
        // Run launchctl to unregister and stop
        // launchctl unload -w /Library/LaunchDaemons/com.jumpmind.symmetricds.plist
        ArrayList<String> unloadCmd = getLaunchDaemonUnloadCmd();
        boolean success = runLaunchCtlCmd(unloadCmd);
        if (!success) {
            throw new WrapperException(Constants.RC_FAIL_REGISTER_SERVICE, 0, "Failed to uninstall service");
        }
        // Remove /Library/LaunchDaemons file
        File fileToDelete = new File(LAUNCH_DAEMON_DIR, getLaunchDaemonsFileName(config.getName()));
        boolean ret = fileToDelete.delete();
        System.out.println("Delete of file " + fileToDelete.getAbsolutePath() + (ret ? " successful" : " failed"));
    }

    @Override
    public boolean isInstalled() {
        return new File(LAUNCH_DAEMON_DIR, getLaunchDaemonsFileName(config.getName())).exists();
    }

    private String getLaunchDaemonsFileName(String wrapperName) {
        return getLaunchDaemonsName(wrapperName) + LAUNCH_DAEMON_NAME_SUFFIX;
    }

    private String getLaunchDaemonsName(String wrapperName) {
        return LAUNCH_DAEMON_NAME_PREFIX + wrapperName;
    }

    private boolean runLaunchCtlCmd(ArrayList<String> cmd) {
        int ret = -1;
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        System.out.println("Running " + pb.command());
        Process process = null;
        try {
            process = pb.start();
            ret = process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println(e.getMessage());
        }
        if (process != null) {
            ArrayList<String> cmdOutput = new ArrayList<String>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    cmdOutput.add(line);
                }
            } catch (Exception e) {
            }
            if (cmdOutput.size() > 0) {
                System.err.println(commandToString(cmd));
                for (String line : cmdOutput) {
                    System.err.println(line);
                }
            }
        }
        return ret == 0;
    }

    @Override
    protected boolean isPidRunning(int pid) {
        boolean ret = false;
        if (pid != 0) {
            ArrayList<String> cmd = getPsCommand(pid);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = null;
            try {
                process = pb.start();
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                System.err.println(e.getMessage());
            }
            if (process != null) {
                ArrayList<String> cmdOutput = new ArrayList<String>();
                ArrayList<String> cmdError = new ArrayList<String>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        cmdOutput.add(line);
                    }
                } catch (Exception e) {
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        cmdError.add(line);
                    }
                } catch (Exception e) {
                }
                for (String line : cmdOutput) {
                    if (line.contains(config.getJavaCommand())) {
                        ret = true;
                        break;
                    }
                }
                if (cmdError.size() > 0) {
                    System.err.println(commandToString(cmd));
                    for (String line : cmdError) {
                        System.err.println(line);
                    }
                    throw new WrapperException(Constants.RC_FAIL_EXECUTION, 8, "Failed isPidRunning");
                }
            }
        }
        return ret;
    }

    private ArrayList<String> getPsCommand(int pid) {
        ArrayList<String> cmdList = new ArrayList<String>();
        cmdList.add("/bin/ps");
        cmdList.add("-p");
        cmdList.add(String.valueOf(pid));
        cmdList.add("-opid=,comm=");
        return cmdList;
    }

    private void writeLaunchDaemonFile(File runFile) {
        try (FileWriter writer = new FileWriter(runFile);
                BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/symmetricds.plist")))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("\\$\\{wrapper.displayname}", getLaunchDaemonsName(config.getDisplayName()));
                line = line.replaceAll("\\$\\{wrapper.home}", config.getWorkingDirectory().getAbsolutePath());
                line = line.replaceAll("\\$\\{wrapper.run.as.user}", config.getRunAsUser());
                writer.write(line + "\n");
            }
        } catch (IOException e) {
            throw new WrapperException(Constants.RC_FAIL_INSTALL, 0, "Failed while writing run file", e);
        }
    }

    @Override
    protected void stopProcesses(boolean isStopAbandoned) {
        if (isInstalled()) {
            ArrayList<String> stopCmd = getLaunchDaemonStopCmd();
            boolean success = runLaunchCtlCmd(stopCmd);
            if (!success) {
                throw new WrapperException(Constants.RC_FAIL_STOP_SERVER, 0, "Server did not stop");
            }
        } else {
            super.stopProcesses(isStopAbandoned);
        }
    }

    @Override
    public void start() {
        if (isInstalled()) {
            if (!isPrivileged()) {
                throw new WrapperException(Constants.RC_MUST_BE_ROOT, 0, "You must be root to start a service");
            }
            if (isRunning()) {
                throw new WrapperException(Constants.RC_SERVER_ALREADY_RUNNING, 0, "Server is already running");
            }
            stopProcesses(true);
            System.out.println("Waiting for server to start");
            boolean success = runLaunchCtlCmd(getLaunchDaemonStartCmd());
            if (!success) {
                throw new WrapperException(Constants.RC_FAIL_EXECUTION, 0, "Server did not start");
            }
        } else {
            super.start();
        }
    }
}
