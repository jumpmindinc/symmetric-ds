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
package org.jumpmind.symmetric;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.util.AppUtils;

public class JmxCommand extends AbstractCommandLauncher {
    private static final String OPTION_LISTBEANS = "listbeans";
    private static final String OPTION_LISTMETHODS = "listmethods";
    private static final String OPTION_BEAN = "bean";
    private static final String OPTION_METHOD = "method";
    private static final String OPTION_ARGS = "args";
    private static final String OPTION_ARGS_DELIM = "args-delimiter";

    public JmxCommand() {
        super("jmx", "", "Jmx.Option.");
    }

    public static void main(String[] args) {
        new JmxCommand().execute(args);
    }

    @Override
    protected void printHelp(CommandLine cmd, Options options) {
        System.out.println(app + " version " + Version.version());
        System.out
                .println("Provides a command line interface to execute JMX methods for a specific engine");
        super.printHelp(cmd, options);
    }

    @Override
    protected void buildOptions(Options options) {
        super.buildOptions(options);
        addOption(options, null, OPTION_LISTBEANS, false);
        addOption(options, null, OPTION_LISTMETHODS, false);
        addOption(options, null, OPTION_BEAN, true);
        addOption(options, null, OPTION_METHOD, true);
        addOption(options, null, OPTION_ARGS, true);
        addOption(options, null, OPTION_ARGS_DELIM, true);
    }

    @Override
    protected boolean printHelpIfNoOptionsAreProvided() {
        return true;
    }

    @Override
    protected boolean requiresPropertiesFile(CommandLine line) {
        return true;
    }

    @Override
    protected boolean executeWithOptions(final CommandLine line) throws Exception {
        if (line.hasOption(OPTION_LISTBEANS)) {
            execute(new IJmxTemplate<Object>() {
                @Override
                public Object execute(String engineName, MBeanServerConnection mbeanConn)
                        throws Exception {
                    Set<ObjectName> beanSet = mbeanConn.queryNames(null, null);
                    for (ObjectName objectName : beanSet) {
                        if (objectName.getDomain().startsWith(
                                "org.jumpmind.symmetric." + engineName)) {
                            System.out.println(objectName.toString());
                        }
                    }
                    return null;
                }
            });
        } else if (line.hasOption(OPTION_LISTMETHODS) || line.hasOption(OPTION_METHOD)) {
            if (line.hasOption(OPTION_BEAN)) {
                execute(new IJmxTemplate<Object>() {
                    @Override
                    public Object execute(String engineName, MBeanServerConnection mbeanConn)
                            throws Exception {
                        String beanName = line.getOptionValue(OPTION_BEAN);
                        MBeanInfo info = mbeanConn.getMBeanInfo(new ObjectName(beanName));
                        if (info != null) {
                            if (line.hasOption(OPTION_LISTMETHODS)) {
                                MBeanOperationInfo[] operations = info.getOperations();
                                Map<String, MBeanOperationInfo> orderedMap = new TreeMap<String, MBeanOperationInfo>();
                                for (MBeanOperationInfo methodInfo : operations) {
                                    orderedMap.put(methodInfo.getName(), methodInfo);
                                }
                                for (MBeanOperationInfo methodInfo : orderedMap.values()) {
                                    System.out.print(methodInfo.getName() + "(");
                                    MBeanParameterInfo[] params = methodInfo.getSignature();
                                    int index = 0;
                                    for (MBeanParameterInfo p : params) {
                                        if (index > 0) {
                                            System.out.print(", ");
                                        }
                                        System.out.print(p.getType() + " " + p.getName());
                                        index++;
                                    }
                                    System.out.print(")");
                                    if (methodInfo.getReturnType() != null && !methodInfo.getReturnType().equals("void")) {
                                        System.out.print(" : " + methodInfo.getReturnType());
                                    }
                                    System.out.println();
                                }
                            } else if (line.hasOption(OPTION_METHOD)) {
                                String argsDelimiter = line.getOptionValue(OPTION_ARGS_DELIM);
                                if (isBlank(argsDelimiter)) {
                                    argsDelimiter = ",";
                                } else {
                                    argsDelimiter = argsDelimiter.trim();
                                }
                                String methodName = line.getOptionValue(OPTION_METHOD);
                                String[] args = null;
                                if (line.hasOption(OPTION_ARGS)) {
                                    String argLine = line.getOptionValue(OPTION_ARGS);
                                    args = argsDelimiter.equals(",") ? CsvUtils.tokenizeCsvData(argLine) : argLine.split(argsDelimiter);
                                    ;
                                } else {
                                    args = new String[0];
                                }
                                MBeanOperationInfo[] operations = info.getOperations();
                                for (MBeanOperationInfo methodInfo : operations) {
                                    MBeanParameterInfo[] paramInfos = methodInfo.getSignature();
                                    if (methodInfo.getName().equals(methodName)
                                            && paramInfos.length == args.length) {
                                        String[] signature = new String[args.length];
                                        Object[] objArgs = new Object[args.length];
                                        int index = 0;
                                        for (MBeanParameterInfo paramInfo : paramInfos) {
                                            signature[index] = paramInfo.getType();
                                            if (!paramInfo.getType().equals(String.class.getName())) {
                                                Class<?> clazz = Class.forName(paramInfo.getType());
                                                Constructor<?> constructor = clazz
                                                        .getConstructor(String.class);
                                                objArgs[index] = constructor
                                                        .newInstance(args[index]);
                                            } else {
                                                objArgs[index] = args[index];
                                            }
                                            index++;
                                        }
                                        Object returnValue = mbeanConn.invoke(new ObjectName(
                                                beanName), methodName, objArgs, signature);
                                        if (methodInfo.getReturnType() != null
                                                && !methodInfo.getReturnType().equals("void")) {
                                            System.out.println(returnValue);
                                        }
                                        System.exit(0);
                                    }
                                }
                                System.out.println("ERROR: Could not locate a JMX method named: "
                                        + methodName + " with " + args.length + " arguments on bean: " + beanName);
                                System.exit(1);
                                return null;
                            }
                        } else {
                            System.out
                                    .println("ERROR: Could not locate a JMX bean with the name of: "
                                            + beanName);
                            System.exit(1);
                        }
                        return null;
                    }
                });
            } else {
                System.out.println("ERROR: Must specifiy the --bean option.");
                System.exit(1);
            }
        } else {
            return false;
        }
        return true;
    }

    protected <T> T execute(IJmxTemplate<T> template) throws Exception {
        String host = "localhost";
        String url = "service:jmx:rmi:///jndi/rmi://" + host + ":"
                + System.getProperty("jmx.agent.port", "31418") + "/jmxrmi";
        JMXServiceURL serviceUrl = new JMXServiceURL(url);
        HashMap<String, Object> env = new HashMap<String, Object>();
        File jmxPassFile = new File(AppUtils.getSymHome() + "/security/jmxremote.password");
        if (jmxPassFile.canRead()) {
            try (FileReader reader = new FileReader(jmxPassFile)) {
                TypedProperties jmxPassProp = new TypedProperties();
                jmxPassProp.load(reader);
                if (jmxPassProp.size() > 0) {
                    String user = "admin";
                    if (!jmxPassProp.containsKey(user)) {
                        user = (String) jmxPassProp.keySet().iterator().next();
                    }
                    String[] credentials = new String[] { user, jmxPassProp.get(user) };
                    env.put(JMXConnector.CREDENTIALS, credentials);
                }
            }
        }
        JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceUrl, env);
        TypedProperties properties = getTypedProperties();
        String engineName = properties.get(ParameterConstants.ENGINE_NAME, "unknown");
        try {
            MBeanServerConnection mbeanConn = jmxConnector.getMBeanServerConnection();
            return template.execute(engineName, mbeanConn);
        } finally {
            jmxConnector.close();
        }
    }

    interface IJmxTemplate<T> {
        public T execute(String engineName, MBeanServerConnection mbeanConn) throws Exception;
    }
}
