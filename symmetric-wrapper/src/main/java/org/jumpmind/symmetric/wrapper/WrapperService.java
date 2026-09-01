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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.jumpmind.symmetric.wrapper.Constants.Status;

import com.sun.jna.Platform;

public abstract class WrapperService {
    private static final Logger logger = Logger.getLogger(WrapperService.class.getName());
    protected WrapperConfig config;
    protected boolean keepRunning = true;
    protected Process child;
    private static WrapperService instance;

    public static WrapperService getInstance() {
        if (Platform.isWindows()) {
            instance = new WindowsService();
        } else if (Platform.isMac()) {
            instance = new MacService();
        } else {
            instance = new UnixService();
        }
        return instance;
    }

    public void loadConfig(String applHomeDir, String configFile, String jarFile) throws IOException {
        config = new WrapperConfig(applHomeDir, configFile, jarFile);
        setWorkingDirectory(config.getWorkingDirectory().getAbsolutePath());
    }

    public WrapperConfig getConfig() {
        return config;
    }

    public void start() {
        if (isRunning()) {
            throw new WrapperException(Constants.RC_SERVER_ALREADY_RUNNING, 0, "Server is already running");
        }
        stopProcesses(true);
        System.out.println("Waiting for server to start");
        ArrayList<String> cmdLine = getWrapperCommand("exec", false);
        List<String> output = new ArrayList<String>();
        Process process = null;
        boolean success = false;
        int rc = 0;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmdLine).redirectErrorStream(true);
            process = pb.start();
            if (!config.getApplicationOutputStart().equals("")) {
                int pid = getProcessPid(process);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = null;
                    int seconds = 0;
                    waitLoop: while (seconds <= 60) {
                        System.out.print(".");
                        if (!isPidRunning(pid)) {
                            break;
                        }
                        while (reader.ready() && (line = reader.readLine()) != null) {
                            output.add(line);
                            System.out.print(":");
                            if (line.equals(config.getApplicationOutputStart())) {
                                break waitLoop;
                            }
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        seconds++;
                    }
                    System.out.println("");
                } catch (Exception e) {
                }
                success = isPidRunning(pid);
            } else {
                if (!(success = waitForPid(getProcessPid(process)))) {
                    rc = process.exitValue();
                }
            }
        } catch (IOException e) {
            rc = -1;
            System.out.println(e.getMessage());
        }
        if (success) {
            System.out.println("Started");
        } else {
            System.err.println(scrubCommand(commandToString(cmdLine)));
            if (output.size() > 0) {
                for (String line : output) {
                    System.err.println(line);
                }
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        System.err.println(line);
                    }
                } catch (Exception e) {
                }
            }
            throw new WrapperException(Constants.RC_FAIL_EXECUTION, rc, "Failed second stage");
        }
    }

    public void init() {
        execJava(false);
    }

    public void console() {
        if (isRunning()) {
            throw new WrapperException(Constants.RC_SERVER_ALREADY_RUNNING, 0, "Server is already running");
        }
        execJava(true);
    }

    protected void execJava(boolean isConsole) {
        try {
            LogManager.getLogManager().reset();
            WrapperLogHandler handler = new WrapperLogHandler(config.getLogFile(),
                    config.getLogFileMaxSize(), config.getLogFileMaxFiles());
            handler.setFormatter(new WrapperLogFormatter());
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.parse(config.getLogFileLogLevel()));
            rootLogger.addHandler(handler);
        } catch (IOException e) {
            throw new WrapperException(Constants.RC_FAIL_WRITE_LOG_FILE, 0, "Cannot open log file " + config.getLogFile(), e);
        }
        try {
            int pid = getCurrentPid();
            writePidToFile(pid, config.getWrapperPidFile());
            logger.log(Level.INFO, "Started wrapper as PID " + pid);
            ArrayList<String> cmd = config.getCommand(isConsole);
            String cmdString = commandToString(cmd);
            boolean usingHeapDump = cmdString.indexOf("-XX:+HeapDumpOnOutOfMemoryError") != -1;
            logger.log(Level.INFO, "Working directory is " + System.getProperty("user.dir"));
            long startTime = 0;
            int startCount = 0;
            boolean startProcess = true, restartDetected = false;
            int serverPid = 0;
            while (keepRunning) {
                if (startProcess) {
                    logger.log(Level.INFO, "Executing " + scrubCommand(cmdString));
                    if (startCount == 0) {
                        updateStatus(Status.START_PENDING);
                    }
                    startTime = System.currentTimeMillis();
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectErrorStream(true);
                    initEnvironment(pb);
                    try {
                        child = pb.start();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Failed to execute: " + e.getMessage());
                        updateStatus(Status.STOPPED);
                        throw new WrapperException(Constants.RC_FAIL_EXECUTION, -1, "Failed executing server", e);
                    }
                    serverPid = getProcessPid(child);
                    logger.log(Level.INFO, "Started server as PID " + serverPid);
                    writePidToFile(serverPid, config.getServerPidFile());
                    if (startCount == 0) {
                        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
                        if (config.getApplicationOutputStart().equals("")) {
                            updateStatus(Status.RUNNING);
                        }
                    }
                    startProcess = false;
                    startCount++;
                } else {
                    try (BufferedReader childReader = new BufferedReader(new InputStreamReader(child.getInputStream()))) {
                        logger.log(Level.INFO, "Watching output of java process");
                        String line = null;
                        while ((line = childReader.readLine()) != null) {
                            System.out.println(line);
                            logger.log(Level.INFO, line, "java");
                            if ((usingHeapDump && line.matches("Heap dump file created.*")) ||
                                    (!usingHeapDump && line.matches("java.lang.OutOfMemoryError.*")) ||
                                    line.matches(".*java.net.BindException.*") ||
                                    line.matches(".*A fatal error has been detected.*")) {
                                logger.log(Level.SEVERE, "Stopping server because its output matches a failure condition");
                                child.destroy();
                                stopProcess(serverPid, "server");
                                break;
                            }
                            if (!config.getApplicationOutputStart().equals("") && line.equalsIgnoreCase(config.getApplicationOutputStart())) {
                                logger.log(Level.INFO, "Setting status to running");
                                updateStatus(Status.RUNNING);
                            } else if (!config.getApplicationOutputRestart().equals("") && line.equalsIgnoreCase(config.getApplicationOutputRestart())) {
                                restartDetected = true;
                            }
                        }
                        logger.log(Level.INFO, "End of output from java process");
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Error while reading from process");
                    }
                    if (restartDetected) {
                        logger.log(Level.INFO, "Restart detected");
                        restartDetected = false;
                        startProcess = true;
                    } else if (keepRunning) {
                        long tenminutesinms = 60 * 10 * 1000;
                        long twelveminutesinms = 60 * 12 * 1000;
                        long tensecondsinms = 10 * 1000;
                        long now = System.currentTimeMillis();
                        while (child.isAlive()) {
                            logger.log(Level.WARNING, "Server process has not stopped yet");
                            if (System.currentTimeMillis() - now > twelveminutesinms) {
                                logger.log(Level.SEVERE, "Server process never exited, exiting now");
                                child.destroyForcibly();
                                // If it has not exited by now, it probably never will.
                                break;
                            }
                            if (System.currentTimeMillis() - now > tenminutesinms) {
                                logger.log(Level.SEVERE, "Server process never exited, trying to force the exit of server process");
                                child.destroyForcibly();
                            }
                            try {
                                Thread.sleep(tensecondsinms);
                            } catch (InterruptedException e) {
                            }
                        }
                        logger.log(Level.SEVERE, "Unexpected exit from server: " + child.exitValue());
                        long runTime = System.currentTimeMillis() - startTime;
                        if (System.currentTimeMillis() - startTime < 7000) {
                            logger.log(Level.SEVERE, "Stopping because server exited too quickly after only " + runTime + " milliseconds");
                            updateStatus(Status.STOPPED);
                            deletePidFile(config.getServerPidFile());
                            throw new WrapperException(Constants.RC_SERVER_EXITED, child.exitValue(), "Unexpected exit from server");
                        } else {
                            startProcess = true;
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            // The default logging config doesn't show the stack trace here, so include it in the message.
            try {
                logger.log(Level.SEVERE, "Exception caught.\r\n" + getStackTrace(ex));
                updateStatus(Status.STOPPED);
                throw new WrapperException(Constants.RC_SERVER_EXITED, child.exitValue(), "Exception caught.", ex);
            } catch (Throwable ex2) {
                ex2.printStackTrace();
            }
        }
    }

    protected String scrubCommand(String cmdLine) {
        cmdLine = cmdLine.replaceFirst("(-Djavax.net.ssl.keyStorePassword)=\\S+", "$1=***");
        cmdLine = cmdLine.replaceFirst("(-Djavax.net.ssl.trustStorePassword)=\\S+", "$1=***");
        return cmdLine;
    }

    protected void initEnvironment(ProcessBuilder pb) {
    }

    public void stop() {
        stopProcesses(false);
        deletePidFile(config.getServerPidFile());
        deletePidFile(config.getWrapperPidFile());
        System.out.println("Stopped");
    }

    protected void stopProcesses(boolean isStopAbandoned) {
        int serverPid = readPidFromFile(config.getServerPidFile());
        int wrapperPid = readPidFromFile(config.getWrapperPidFile());
        boolean isServerRunning = isPidRunning(serverPid);
        boolean isWrapperRunning = isPidRunning(wrapperPid);
        if (!isStopAbandoned) {
            if (!isServerRunning && !isWrapperRunning) {
                throw new WrapperException(Constants.RC_SERVER_NOT_RUNNING, 0, "Server is not running");
            }
            System.out.println("Waiting for server to stop");
        }
        if (isWrapperRunning) {
            if (isStopAbandoned) {
                System.out.println("Stopping abandoned wrapper PID " + wrapperPid);
            }
            isWrapperRunning = !stopProcess(wrapperPid, "wrapper");
        }
        if (isServerRunning) {
            if (isStopAbandoned) {
                System.out.println("Stopping abandoned server PID " + serverPid);
            }
            isServerRunning = !stopProcess(serverPid, "server");
        }
        if (isWrapperRunning || isServerRunning) {
            throw new WrapperException(Constants.RC_FAIL_STOP_SERVER, 0, "Server did not stop");
        }
    }

    protected boolean stopProcess(int pid, String name) {
        killProcess(pid, false);
        if (waitForPid(pid)) {
            killProcess(pid, true);
            if (waitForPid(pid)) {
                System.out.println("ERROR: '" + name + "' did not stop");
                return false;
            }
        }
        return true;
    }

    protected void shutdown() {
        if (keepRunning) {
            keepRunning = false;
            new Thread() {
                @Override
                public void run() {
                    logger.log(Level.INFO, "Stopping server");
                    child.destroy();
                    logger.log(Level.INFO, "Stopping wrapper");
                    deletePidFile(config.getWrapperPidFile());
                    deletePidFile(config.getServerPidFile());
                    updateStatus(Status.STOPPED);
                    System.exit(0);
                }
            }.start();
        }
    }

    public void restart() {
        if (isRunning()) {
            stop();
        }
        start();
    }

    public void relaunchAsPrivileged(String className) {
    }

    public void status() {
        boolean isRunning = isRunning();
        int wrapperPid = readPidFromFile(config.getWrapperPidFile());
        int serverPid = readPidFromFile(config.getServerPidFile());
        System.out.println("Installed: " + isInstalled());
        System.out.println("Running: " + isRunning);
        System.out.println("Wrapper PID: " + wrapperPid);
        System.out.println("Wrapper Running: " + isPidRunning(wrapperPid));
        System.out.println("Server PID: " + serverPid);
        System.out.println("Server Running: " + isPidRunning(serverPid));
    }

    public boolean isRunning() {
        return isPidRunning(readPidFromFile(config.getWrapperPidFile()))
                && isPidRunning(readPidFromFile(config.getServerPidFile()));
    }

    public int getWrapperPid() {
        return readPidFromFile(config.getWrapperPidFile());
    }

    public int getServerPid() {
        return readPidFromFile(config.getServerPidFile());
    }

    protected String commandToString(ArrayList<String> cmd) {
        StringBuilder sb = new StringBuilder();
        for (String c : cmd) {
            sb.append(c).append(" ");
        }
        return sb.toString();
    }

    protected ArrayList<String> getWrapperCommand(String arg, boolean isQuotedArguments) {
        ArrayList<String> cmd = new ArrayList<String>();
        String quote = isQuotedArguments ? getWrapperCommandQuote() : "";
        cmd.add(quote + config.getJavaCommand() + quote);
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null && tmpDir.endsWith("\\")) {
            tmpDir = tmpDir.substring(0, tmpDir.length() - 1);
        }
        cmd.add("-Djava.io.tmpdir=" + quote + tmpDir + quote);
        cmd.add("-jar");
        cmd.add(quote + config.getWrapperJarPath() + quote);
        cmd.add(arg);
        cmd.add(quote + config.getConfigFile() + quote);
        return cmd;
    }

    protected String getWrapperCommandQuote() {
        return "";
    }

    protected int readPidFromFile(String filename) {
        int pid = 0;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            pid = Integer.parseInt(reader.readLine());
            reader.close();
        } catch (FileNotFoundException e) {
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pid;
    }

    protected void writePidToFile(int pid, String filename) {
        try {
            FileWriter writer = new FileWriter(filename, false);
            writer.write(String.valueOf(pid));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void deletePidFile(String filename) {
        new File(filename).delete();
    }

    protected boolean waitForPid(int pid) {
        int seconds = 0;
        while (seconds <= 5) {
            System.out.print(".");
            if (!isPidRunning(pid)) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            seconds++;
        }
        System.out.println("");
        return isPidRunning(pid);
    }

    protected void updateStatus(Status status) {
    }

    private static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    class ShutdownHook extends Thread {
        public void run() {
            shutdown();
        }
    }

    public abstract void install();

    public abstract void uninstall();

    public abstract boolean isInstalled();

    public abstract boolean isPrivileged();

    protected abstract boolean setWorkingDirectory(String dir);

    protected abstract int getProcessPid(Process process);

    protected abstract int getCurrentPid();

    protected abstract boolean isPidRunning(int pid);

    protected abstract void killProcess(int pid, boolean isTerminate);
}
