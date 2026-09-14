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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.jumpmind.symmetric.wrapper.jna.CLibrary;

@IgnoreJRERequirement
public class UnixService extends WrapperService {
    private static final String[] RUN_LEVELS_START = new String[] { "2", "3", "5" };
    private static final String[] RUN_LEVELS_STOP = new String[] { "0", "1", "6" };
    private static final String RUN_SEQUENCE_START = "20";
    private static final String RUN_SEQUENCE_STOP = "80";
    private static final String RC_DIR = "/etc";
    private static final String INITD_DIR = "/etc/init.d";
    private static final String SYSTEMD_INSTALL_DIR = "/lib/systemd/system";
    private static final String SYSTEMD_ETC_DIR = "/etc/systemd/system";
    private static final String SYSTEMD_RUNTIME_DIR = "/run/systemd/system";
    private static final String INITD_SCRIPT_START = "start";
    private static final String INITD_SCRIPT_STOP = "stop";
    private static final String SYSTEMD_SCRIPT_START = "start";
    private static final String SYSTEMD_SCRIPT_STOP = "stop";
    private static final String SYSTEMD_SCRIPT_ENABLE = "enable";
    private static final String SYSTEMD_SCRIPT_DISABLE = "disable";

    @Override
    protected boolean setWorkingDirectory(String dir) {
        return CLibrary.INSTANCE.chdir(dir) == 0;
    }

    @Override
    public void install() {
        if (!isPrivileged()) {
            throw new WrapperException(Constants.RC_MUST_BE_ROOT, 0, "Must be root to install");
        }
        System.out.println("Installing " + config.getName() + " ...");
        if (isSystemdRunning()) {
            installSystemd();
        } else {
            installInitd();
        }
        System.out.println("Done");
    }

    private boolean isSystemdRunning() {
        File systemddir = new File(SYSTEMD_RUNTIME_DIR);
        return systemddir.exists();
    }

    private void installSystemd() {
        String runFile = getSystemdScriptFile();
        try (FileWriter writer = new FileWriter(runFile);
                BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
                        "/symmetricds.systemd")))) {
            String javaCommand = config.getJavaCommand();
            if (javaCommand == null) {
                javaCommand = "java";
            }
            if (!javaCommand.startsWith(File.separator)) {
                if (!javaCommand.contains(File.separator)) {
                    javaCommand = getAbsolutePath(javaCommand);
                } else {
                    String cwd = System.getProperty("user.dir");
                    javaCommand = cwd + File.separator + javaCommand;
                }
            }
            String line = null;
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("\\$\\{wrapper.description}", config.getDescription());
                line = line.replaceAll("\\$\\{wrapper.pidfile}", getWrapperPidFile());
                line = line.replaceAll("\\$\\{wrapper.home}", config.getWorkingDirectory().getAbsolutePath());
                line = line.replaceAll("\\$\\{wrapper.jarfile}", config.getWrapperJarPath());
                line = line.replaceAll("\\$\\{wrapper.java.command}", javaCommand);
                line = line.replaceAll("\\$\\{wrapper.run.as.user}",
                        config.getRunAsUser() == null || config.getRunAsUser().length() == 0 ? "root" : config.getRunAsUser());
                writer.write(line + "\n");
            }
        } catch (IOException e) {
            throw new WrapperException(Constants.RC_FAIL_INSTALL, 0, "Failed while writing run file", e);
        }
        runServiceCommand(getSystemdCommand(SYSTEMD_SCRIPT_ENABLE, config.getName()));
    }

    private String getSystemdScriptFile() {
        // original implementation for systemd installed under /lib/systemd, so this is for backwards compatibility
        String fileName = SYSTEMD_INSTALL_DIR + "/" + config.getName() + ".service";
        if (!new File(fileName).exists()) {
            // correct implementation for systemd installs under /etc/systemd
            fileName = SYSTEMD_ETC_DIR + "/" + config.getName() + ".service";
        }
        return fileName;
    }

    private String getInitdRunFile() {
        return INITD_DIR + "/" + config.getName();
    }

    private String getWrapperPidFile() throws IOException {
        // Make location absolute (starting with / )
        return (config.getWrapperPidFile() != null && config.getWrapperPidFile().startsWith("/") ? config.getWrapperPidFile()
                : config.getWorkingDirectory().getCanonicalPath() + "/" + config.getWrapperPidFile());
    }

    private void installInitd() {
        String rcDir = getRunCommandDir();
        String runFile = getInitdRunFile();
        try {
            FileWriter writer = new FileWriter(runFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
                    "/symmetricds.initd")));
            String line = null;
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("\\$\\{wrapper.name}", config.getName());
                line = line.replaceAll("\\$\\{wrapper.displayname}", config.getDisplayName());
                line = line.replaceAll("\\$\\{wrapper.description}", config.getDescription());
                line = line.replaceAll("\\$\\{wrapper.home}", config.getWorkingDirectory().getAbsolutePath());
                line = line.replaceAll("\\$\\{wrapper.java.command}", config.getJavaCommand());
                line = line.replaceAll("\\$\\{wrapper.jarfile}", config.getWrapperJarPath());
                line = line.replaceAll("\\$\\{wrapper.run.as.user}", config.getRunAsUser());
                writer.write(line + "\n");
            }
            reader.close();
            writer.close();
        } catch (IOException e) {
            throw new WrapperException(Constants.RC_FAIL_INSTALL, 0, "Failed while writing run file", e);
        }
        new File(runFile).setExecutable(true, false);
        for (String runLevel : RUN_LEVELS_START) {
            CLibrary.INSTANCE.symlink(runFile, rcDir + "/rc" + runLevel + ".d/S" + RUN_SEQUENCE_START
                    + config.getName());
        }
        for (String runLevel : RUN_LEVELS_STOP) {
            CLibrary.INSTANCE.symlink(runFile, rcDir + "/rc" + runLevel + ".d/K" + RUN_SEQUENCE_STOP
                    + config.getName());
        }
    }

    @Override
    public void uninstall() {
        if (!isPrivileged()) {
            throw new WrapperException(Constants.RC_MUST_BE_ROOT, 0, "Must be root to uninstall");
        }
        System.out.println("Uninstalling " + config.getName() + " ...");
        uninstallSystemd();
        uninstallInitd();
        System.out.println("Done");
    }

    private void uninstallSystemd() {
        runServiceCommand(getSystemdCommand(SYSTEMD_SCRIPT_DISABLE, config.getName()));
        String runFile = getSystemdScriptFile();
        new File(runFile).delete();
    }

    private void uninstallInitd() {
        String rcDir = getRunCommandDir();
        String runFile = getInitdRunFile();
        for (String runLevel : RUN_LEVELS_START) {
            new File(rcDir + "/rc" + runLevel + ".d/S" + RUN_SEQUENCE_START + config.getName()).delete();
        }
        for (String runLevel : RUN_LEVELS_STOP) {
            new File(rcDir + "/rc" + runLevel + ".d/K" + RUN_SEQUENCE_STOP + config.getName()).delete();
        }
        new File(runFile).delete();
    }

    protected String getRunCommandDir() {
        String rcDir = "";
        if (new File(INITD_DIR + "/rc0.d").exists()) {
            rcDir = INITD_DIR;
        } else if (new File(RC_DIR + "/rc0.d").exists()) {
            rcDir = RC_DIR;
        } else {
            throw new WrapperException(Constants.RC_MISSING_INIT_FOLDER, 0, "Unable to locate run level folders");
        }
        return rcDir;
    }

    @Override
    public boolean isPrivileged() {
        return CLibrary.INSTANCE.geteuid() == 0;
    }

    @Override
    public boolean isInstalled() {
        return (new File(getSystemdScriptFile()).exists() ||
                new File(getInitdRunFile()).exists());
    }

    @Override
    protected boolean isPidRunning(int pid) {
        File procFile = new File("/proc/" + pid + "/cmdline");
        if (procFile.canRead()) {
            try {
                List<String> args = null;
                int cnt = 0;
                while (cnt < 10) {
                    args = readProcFile(procFile);
                    if (args.size() > 0) {
                        break;
                    }
                    cnt++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
                for (String arg : args) {
                    if (arg.contains(config.getJavaCommand())) {
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
            }
        }
        return pid != 0 && CLibrary.INSTANCE.kill(pid, 0) == 0;
    }

    private List<String> readProcFile(File procFile) throws IOException {
        FileInputStream in = new FileInputStream(procFile);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        List<String> args = new ArrayList<String>();
        byte buffer[] = new byte[512];
        int len = 0;
        while ((len = in.read(buffer)) != -1) {
            for (int i = 0; i < len; i++) {
                if (buffer[i] == (byte) 0x0) {
                    bout.flush();
                    args.add(new String(bout.toString()));
                    bout.reset();
                } else {
                    bout.write(buffer[i]);
                }
            }
        }
        in.close();
        return args;
    }

    @Override
    protected int getCurrentPid() {
        return CLibrary.INSTANCE.getpid();
    }

    @Override
    protected int getProcessPid(Process process) {
        int pid = 0;
        try {
            // Java 9
            Method method = Process.class.getDeclaredMethod("pid", (Class[]) null);
            Object object = method.invoke(process);
            pid = ((Long) object).intValue();
        } catch (Exception e) {
            try {
                // Prior to Java 9
                Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
                pid = field.getInt(process);
            } catch (Exception ex) {
            }
        }
        return pid;
    }

    @Override
    protected void killProcess(int pid, boolean isTerminate) {
        CLibrary.INSTANCE.kill(pid, isTerminate ? 9 : 1);
    }

    private ArrayList<String> getServiceCommand(String command) {
        ArrayList<String> s = new ArrayList<String>();
        String runFile = getInitdRunFile();
        s.add(runFile);
        s.add(command);
        return s;
    }

    private ArrayList<String> getSystemdCommand(String command, String serviceName) {
        ArrayList<String> s = new ArrayList<String>();
        s.add("systemctl");
        s.add(command);
        s.add(serviceName);
        return s;
    }

    @Override
    public void start() {
        if (isInstalled()) {
            if (!canRunService()) {
                throw new WrapperException(Constants.RC_MUST_BE_ROOT, 0, "You must be root to start a service");
            }
            if (isRunning()) {
                throw new WrapperException(Constants.RC_SERVER_ALREADY_RUNNING, 0, "Server is already running");
            }
            stopProcesses(true);
            System.out.println("Waiting for server to start");
            if (isSystemdRunning() && new File(getSystemdScriptFile()).exists()) {
                boolean success = true;
                if (shouldRunService()) {
                    success = runServiceCommand(getSystemdCommand(SYSTEMD_SCRIPT_START, config.getName()));
                } else {
                    super.start();
                }
                if (!success) {
                    throw new WrapperException(Constants.RC_FAIL_EXECUTION, 0, "Server did not start");
                }
            } else {
                boolean success = true;
                if (shouldRunService()) {
                    success = runServiceCommand(getServiceCommand(INITD_SCRIPT_START));
                } else {
                    super.start();
                }
                if (!success) {
                    throw new WrapperException(Constants.RC_FAIL_EXECUTION, 0, "Server did not start");
                }
            }
        } else {
            super.start();
        }
    }

    private boolean canRunService() {
        // Either privileged (e.g. root) or effective user id is equal to user id of run as user
        boolean ret = false;
        if (isPrivileged()) {
            ret = true;
        }
        String runasuser = config.getRunAsUser();
        if (runasuser != null && runasuser.length() > 0) {
            int euid = CLibrary.INSTANCE.geteuid();
            int uid = getuid(runasuser);
            if (euid == uid) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean shouldRunService() {
        // The only case where we should not run the service is if run as user is set,
        // and if the effective user id and the run as user are the same
        // OR
        // if run as user is not set
        boolean ret = true;
        String runasuser = config.getRunAsUser();
        if (runasuser != null && runasuser.length() > 0) {
            int euid = CLibrary.INSTANCE.geteuid();
            int uid = getuid(runasuser);
            if (euid == uid) {
                ret = false;
            }
        } else {
            ret = false;
        }
        return ret;
    }

    private int getuid(String login) {
        int ret = -1;
        List<String> cmd = new ArrayList<String>();
        cmd.add("id");
        cmd.add("-u");
        cmd.add(login);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = null;
        try {
            process = pb.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        if (process != null) {
            ArrayList<String> cmdOutput = new ArrayList<String>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    cmdOutput.add(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new WrapperException(Constants.RC_FAIL_EXECUTION, 0, "Unable to read from command: " + cmd, e);
            }
            if (cmdOutput != null && cmdOutput.size() > 0) {
                ret = Integer.parseInt(cmdOutput.get(0));
            }
        }
        return ret;
    }

    private String getAbsolutePath(String command) {
        String ret = command;
        List<String> cmd = new ArrayList<String>();
        cmd.add("which");
        cmd.add(command);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = null;
        try {
            process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        if (process != null) {
            ArrayList<String> cmdOutput = new ArrayList<String>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    cmdOutput.add(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new WrapperException(Constants.RC_FAIL_EXECUTION, 0, "Unable to read from command: " + cmd, e);
            }
            if (cmdOutput != null && cmdOutput.size() > 0) {
                ret = cmdOutput.get(0);
            }
        }
        return ret;
    }

    @Override
    protected void stopProcesses(boolean isStopAbandoned) {
        if (isInstalled()) {
            if (!canRunService()) {
                throw new WrapperException(Constants.RC_MUST_BE_ROOT, 0, "You must be root to stop a service");
            }
            int serverPid = readPidFromFile(config.getServerPidFile());
            int wrapperPid = readPidFromFile(config.getWrapperPidFile());
            boolean isServerRunning = isPidRunning(serverPid);
            boolean isWrapperRunning = isPidRunning(wrapperPid);
            if (!isStopAbandoned) {
                if (!isServerRunning && !isWrapperRunning) {
                    throw new WrapperException(Constants.RC_SERVER_NOT_RUNNING, 0, "Server is not running");
                }
            }
            if (isSystemdRunning() && new File(getSystemdScriptFile()).exists()) {
                if (shouldRunService()) {
                    runServiceCommand(getSystemdCommand(SYSTEMD_SCRIPT_STOP, config.getName()));
                } else {
                    super.stopProcesses(isStopAbandoned);
                }
            } else {
                if (shouldRunService()) {
                    runServiceCommand(getServiceCommand(INITD_SCRIPT_STOP));
                } else {
                    super.stopProcesses(isStopAbandoned);
                }
            }
        } else {
            super.stopProcesses(isStopAbandoned);
        }
    }

    private boolean runServiceCommand(ArrayList<String> cmd) {
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
                throw new WrapperException(Constants.RC_FAIL_EXECUTION, 0,
                        "Unable to read from service command: " + cmd, e);
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
}
