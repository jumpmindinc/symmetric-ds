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
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.wrapper.jna.WinsvcEx;

public class WrapperConfig {
    protected String configFile;
    protected Map<String, ArrayList<String>> prop;
    protected File workingDirectory;
    protected String jarFile;

    public WrapperConfig(String applHomeDir, String configFile, String jarFile) throws IOException {
        prop = getProperties(configFile);
        this.configFile = new File(configFile).getAbsolutePath();
        this.jarFile = new File(jarFile).getAbsolutePath();
        workingDirectory = new File(applHomeDir);
    }

    public String getWrapperJarPath() {
        return jarFile;
    }

    public String getConfigFile() {
        return configFile;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public String getLogFile() {
        return getProperty(prop, "wrapper.logfile", "logs/wrapper.log");
    }

    public String getWrapperPidFile() {
        return getProperty(prop, "wrapper.pidfile", "tmp/wrapper.pid");
    }

    public String getServerPidFile() {
        return getProperty(prop, "wrapper.server.pidfile", "tmp/server.pid");
    }

    public long getLogFileMaxSize() {
        String str = getProperty(prop, "wrapper.logfile.maxsize", "10M").toUpperCase();
        int multiplier = 1;
        if (str.indexOf("K") != -1) {
            multiplier = 1024;
        } else if (str.indexOf("M") != -1) {
            multiplier = 1048576;
        }
        return Long.parseLong(str.replaceAll("[^0-9]", "")) * multiplier;
    }

    public int getLogFileMaxFiles() {
        return Integer.parseInt(getProperty(prop, "wrapper.logfile.maxfiles", "3"));
    }

    public String getLogFileLogLevel() {
        return getProperty(prop, "wrapper.logfile.loglevel", "INFO");
    }

    public String getName() {
        return getProperty(prop, "wrapper.name", "symmetricds");
    }

    public String getDisplayName() {
        return getProperty(prop, "wrapper.displayname", "SymmetricDS");
    }

    public String getDescription() {
        return getProperty(prop, "wrapper.description", "SymmetricDS Database Synchronization");
    }

    public boolean isAutoStart() {
        return getProperty(prop, "wrapper.ntservice.starttype", "auto").equalsIgnoreCase("auto");
    }

    public boolean isDelayStart() {
        return getProperty(prop, "wrapper.ntservice.starttype", "auto").equalsIgnoreCase("delay");
    }

    public String getFailureActionCommand() {
        return getProperty(prop, "wrapper.ntservice.failure.action.command", "");
    }

    public int getFailureResetPeriod() {
        return Integer.parseInt(getProperty(prop, "wrapper.ntservice.failure.reset.period", "300"));
    }

    public List<FailureAction> getFailureActions() {
        List<String> types = getListProperty(prop, "wrapper.ntservice.failure.action.type");
        List<String> delays = getListProperty(prop, "wrapper.ntservice.failure.action.delay");
        int count = types.size() >= 3 ? 3 : types.size();
        List<FailureAction> actions = new ArrayList<FailureAction>(count);
        int i = 0;
        for (String type : types) {
            String delay = "0";
            if (i < delays.size()) {
                delay = delays.get(i);
            }
            actions.add(new FailureAction(type, delay));
            i++;
        }
        return actions;
    }

    public List<String> getDependencies() {
        return prop.get("wrapper.ntservice.dependency");
    }

    public String getJavaCommand() {
        String javaCmd = getProperty(prop, "wrapper.java.command", "java");
        if (javaCmd.toLowerCase().endsWith("service.exe")) {
            javaCmd = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";
        }
        return javaCmd;
    }

    public String getServiceCommand() {
        return getProperty(prop, "wrapper.java.command", "java");
    }

    public List<String> getOptions() {
        return prop.get("wrapper.java.additional");
    }

    public String getRunAsUser() {
        return getProperty(prop, "wrapper.run.as.user", "");
    }

    public String getRunAsPassword() {
        return getProperty(prop, "wrapper.run.as.password", "");
    }

    public List<String> getApplicationParameters() {
        return getListProperty(prop, "wrapper.app.parameter");
    }

    public String getApplicationOutputStart() {
        return getProperty(prop, "wrapper.app.output.start", "");
    }

    public String getApplicationOutputRestart() {
        return getProperty(prop, "wrapper.app.output.restart", "");
    }

    public String getMaxMemory() {
        return getProperty(prop, "wrapper.java.maxmemory", "256");
    }

    public ArrayList<String> getCommand(boolean isConsole) {
        ArrayList<String> cmdList = new ArrayList<String>();
        cmdList.add(getJavaCommand());
        String initMem = getProperty(prop, "wrapper.java.initmemory", "256");
        if (!initMem.toUpperCase().endsWith("M")) {
            initMem += "M";
        }
        cmdList.add("-Xms" + initMem);
        String maxMem = getProperty(prop, "wrapper.java.maxmemory", "256");
        if (!maxMem.toUpperCase().endsWith("M")) {
            maxMem += "M";
        }
        cmdList.add("-Xmx" + maxMem);
        cmdList.add("-cp");
        cmdList.add(getClassPath());
        List<String> javaAdditional = getListProperty(prop, "wrapper.java.additional");
        cmdList.addAll(javaAdditional);
        List<String> appParams = getListProperty(prop, "wrapper.app.parameter");
        appParams.remove("--no-log-console");
        cmdList.addAll(appParams);
        if (!isConsole) {
            cmdList.add("--no-log-console");
        }
        return cmdList;
    }

    public String getClassPath() {
        String version = System.getProperty("java.version");
        boolean expandWildcard = version != null && version.startsWith("1.5");
        List<String> cp = getListProperty(prop, "wrapper.java.classpath");
        StringBuilder sb = new StringBuilder(cp.size());
        for (int i = 0; i < cp.size(); i++) {
            if (i > 0) {
                sb.append(File.pathSeparator);
            }
            String classPath = cp.get(i);
            if (expandWildcard) {
                classPath = expandWildcard(classPath);
            } else {
                classPath = classPath.replaceAll("\\*\\.jar", "*");
            }
            sb.append(classPath);
        }
        return sb.toString();
    }

    private String expandWildcard(String classPath) {
        int index = classPath.indexOf("*");
        if (index != -1) {
            String dirName = classPath.substring(0, index);
            File dir = new File(dirName);
            File[] files = dir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jar") || name.endsWith(".JAR");
                }
            });
            StringBuilder sb = new StringBuilder();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (i > 0) {
                        sb.append(File.pathSeparator);
                    }
                    sb.append(dirName).append(files[i].getName());
                }
            }
            classPath = sb.toString();
        }
        return classPath;
    }

    /**
     * Read wrapper properties from symmetric-server.properties file
     * 
     * @param filename
     *            String containing name location of symmetric-server.properties file
     * @return Map keyed by property name with value of an ArrayList containing all values
     * @throws IOException
     */
    private Map<String, ArrayList<String>> getProperties(String filename) throws IOException {
        HashMap<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (!line.matches("^\\s*#.*") && !line.matches("\\s*")) {
                int index = line.indexOf("=");
                if (index != -1) {
                    String name = line.substring(0, index).trim();
                    String value = line.substring(index + 1).trim();
                    if (name.matches(".*\\d{1,2}")) {
                        name = name.substring(0, name.lastIndexOf("."));
                    }
                    ArrayList<String> values = map.get(name);
                    if (values == null) {
                        values = new ArrayList<String>();
                        map.put(name, values);
                    }
                    values.add(value);
                }
            }
        }
        reader.close();
        return map;
    }

    private String getProperty(Map<String, ArrayList<String>> prop, String name, String defaultValue) {
        ArrayList<String> values = prop.get(name);
        String value = null;
        if (values != null && values.size() > 0) {
            value = values.get(0);
        } else {
            value = defaultValue;
        }
        return value;
    }

    private List<String> getListProperty(Map<String, ArrayList<String>> prop, String name) {
        ArrayList<String> value = prop.get(name);
        if (value == null) {
            value = new ArrayList<String>(0);
        }
        return value;
    }

    static public class FailureAction {
        int type;
        int delay;

        public FailureAction(String type, String delay) {
            if (type != null) {
                if (type.equalsIgnoreCase("restart")) {
                    this.type = WinsvcEx.SC_ACTION_RESTART;
                } else if (type.equalsIgnoreCase("reboot")) {
                    this.type = WinsvcEx.SC_ACTION_REBOOT;
                } else if (type.equalsIgnoreCase("run_command")) {
                    this.type = WinsvcEx.SC_ACTION_RUN_COMMAND;
                } else {
                    this.type = WinsvcEx.SC_ACTION_NONE;
                }
            }
            this.delay = Integer.parseInt(delay);
        }

        public int getType() {
            return type;
        }

        public int getDelay() {
            return delay;
        }
    }
}
