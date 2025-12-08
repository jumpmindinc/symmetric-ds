/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.util;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jumpmind.exception.IoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General application utility methods
 */
public class AppUtils {
    public static final String SYSPROP_HOST_NAME = "host.name";
    public static final String SYSPROP_PORT_NUMBER = "port.number";
    public static final String SYSPROP_IP_ADDRESS = "ip.address";
    public static final String ENV_VAR_HOSTNAME = "HOSTNAME";
    public static final String ENV_VAR_COMPUTERNAME = "COMPUTERNAME";
    public static final String OS_COMMAND_HOSTNAME = "hostname";
    private static String UNKNOWN = "unknown";
    private static String DEFAULT_LOCALHOST = "localhost";
    static String hostName = DEFAULT_LOCALHOST;
    private static Logger log = LoggerFactory.getLogger(AppUtils.class);
    private static FastDateFormat timezoneFormatter = FastDateFormat.getInstance("Z");
    private static Properties implProp = new Properties();
    static {
        loadProperties(implProp, "/symmetric-impl.properties");
    }

    private static void loadProperties(Properties prop, String resourceName) {
        URL url = ClassLoader.getSystemResource(resourceName);
        if (url != null) {
            try (InputStream fis = url.openStream()) {
                prop.load(fis);
            } catch (IOException ex) {
                log.debug("Failed to load properties from url ({}): {}", resourceName, ex.getMessage());
            }
        } else {
            url = AppUtils.class.getResource(resourceName);
            if (url != null) {
                try (InputStream fis = url.openStream()) {
                    prop.load(fis);
                } catch (IOException ex) {
                    log.debug("Failed to load properties from resource ({}): {}", resourceName, ex.getMessage());
                }
            }
        }
    }

    public static String getSymHome() {
        String home = System.getenv("SYM_HOME");
        if (home == null) {
            home = ".";
        }
        return home;
    }

    public static String getCanonicalSymHome(String dirName) {
        if (!dirName.startsWith("/") && !dirName.startsWith("\\")) {
            dirName = getSymHome() + "/" + dirName;
        }
        return dirName;
    }

    public static String getHostName() {
        if (!isBlank(hostName) && !DEFAULT_LOCALHOST.equals(hostName)) {
            if (log.isDebugEnabled()) {
                log.debug("Hostname is already set to {}", hostName);
            }
            return hostName;
        }
        try {
            String osHostName = fetchHostNameFromJvm();
            osHostName = isBlank(osHostName) ? fetchHostNameFromEnvironmentVariable(ENV_VAR_HOSTNAME) : osHostName;
            osHostName = isBlank(osHostName) ? fetchHostNameFromEnvironmentVariable(ENV_VAR_COMPUTERNAME) : osHostName;
            osHostName = isBlank(osHostName) ? fetchHostNameFromOsCommand() : osHostName;
            osHostName = isBlank(osHostName) ? fetchHostNameFromInetAddress() : osHostName;
            if (isBlank(osHostName)) {
                log.error("Unable to identify hostname! Using " + DEFAULT_LOCALHOST
                        + " instead. Report this incident to server/system administrator as it can break network communication!");
            } else {
                hostName = osHostName;
            }
        } catch (Exception ex) {
            log.error("Failed to identify hostname (using " + DEFAULT_LOCALHOST + " instead): " + ex.getMessage());
        }
        return hostName;
    }

    public static String fetchHostNameFromJvm() {
        try {
            // Note: Embedded deployments or unit tests might not define this JVM property.
            String jvmHostName = System.getProperty(SYSPROP_HOST_NAME, UNKNOWN);
            if (isBlank(jvmHostName) || UNKNOWN.equals(jvmHostName)) {
                log.info("Skipping undefined JVM system property: {}", SYSPROP_HOST_NAME);
                jvmHostName = "";
            } else {
                jvmHostName = jvmHostName.trim();
                log.info("Hostname from JVM system property {}={}", SYSPROP_HOST_NAME, jvmHostName);
            }
            return jvmHostName;
        } catch (Exception ex) {
            log.warn("Failed to get hostname from JVM system property " + SYSPROP_HOST_NAME, ex);
            return "";
        }
    }

    public static String fetchHostNameFromEnvironmentVariable(String environmentVariableName) {
        try {
            // Note: In many Linux shells, HOSTNAME is a variable set by the shell itself (e.g., Bash) but is not automatically available in child processes.
            String osHostName = System.getenv(environmentVariableName);
            if (isBlank(osHostName)) {
                log.info("Skipping undefined environment variable: {}", environmentVariableName);
            } else {
                osHostName = osHostName.trim();
                log.info("Hostname from environment variable {}={}", environmentVariableName, osHostName);
            }
            return osHostName;
        } catch (Exception ex) {
            log.warn("Failed to get hostname from the " + environmentVariableName + " environment variable", ex);
            return "";
        }
    }

    public static String fetchHostNameFromOsCommand() {
        try {
            // Note: This requires permissions to run an external process and capture it's output.
            String osHostName = IOUtils.toString(Runtime.getRuntime().exec(OS_COMMAND_HOSTNAME).getInputStream(), Charset.defaultCharset());
            if (isBlank(osHostName)) {
                log.warn("Got blank hostname value from the {} OS command!", OS_COMMAND_HOSTNAME);
            } else {
                osHostName = osHostName.trim();
                log.info("Hostname from the {} OS command={}", OS_COMMAND_HOSTNAME, osHostName);
            }
            return osHostName;
        } catch (Exception ex) {
            log.error("Failed to execute the " + OS_COMMAND_HOSTNAME + " OS command: " + ex.getMessage());
            return "";
        }
    }

    public static String fetchHostNameFromInetAddress() {
        try {
            // Note: This fails if hostname isn't resolvable through DNS due to broken network configuration.
            String osHostName = InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()).getHostName();
            if (isBlank(osHostName)) {
                log.warn("Got blank hostname value from InetAddress");
            } else {
                log.info("Hostname from InetAddress={}", osHostName);
            }
            return osHostName;
        } catch (Exception ex) {
            log.error("Failed to get hostname from InetAddress: " + ex.getMessage());
            return "";
        }
    }

    public static String getPortNumber() {
        String httpPort = System.getProperty(SYSPROP_PORT_NUMBER, System.getProperty("http.port"));
        String port = httpPort == null ? "31415" : httpPort;
        String httpsEnable = System.getProperty("https.enable");
        if (httpsEnable != null && httpsEnable.equalsIgnoreCase("true")) {
            String httpsPort = System.getProperty("https.port");
            port = httpsPort == null ? "31417" : httpsPort;
        }
        return port;
    }

    public static String getProtocol() {
        String protocol = "http";
        String httpsEnable = System.getProperty("https.enable");
        if (httpsEnable != null && httpsEnable.equalsIgnoreCase("true")) {
            protocol = "https";
        }
        return protocol;
    }

    public static String getIpAddress() {
        String ipAddress = System.getProperty(SYSPROP_IP_ADDRESS, UNKNOWN);
        if (UNKNOWN.equals(ipAddress)) {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (!inetAddress.isLoopbackAddress()) {
                            ipAddress = inetAddress.getHostAddress();
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to get IP from NetworkInterfaces: ", ex);
            } finally {
            }
        }
        if (UNKNOWN.equals(ipAddress)) {
            try {
                ipAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                log.warn("Failed to get IP from InetAddress: ", ex);
                ipAddress = "127.0.0.1";
            }
        }
        return ipAddress;
    }

    /**
     * This method will return the timezone in RFC822 format.
     * <p>
     * The format ("-+HH:MM") has advantages over the older timezone codes ("AAA"). The difference of 5 hours from GMT is obvious with "-05:00" but only implied
     * with "EST". There is no ambiguity saying "-06:00", but you don't know if "CST" means Central Standard Time ("-06:00") or China Standard Time ("+08:00").
     * The timezone codes need to be loaded on the system, and definitions are not standardized between systems. Therefore, to remain agnostic to operating
     * systems and databases, the RFC822 format is the best choice.
     */
    public static String getTimezoneOffset() {
        String tz = timezoneFormatter.format(new Date());
        if (tz != null && tz.length() == 5) {
            return tz.substring(0, 3) + ":" + tz.substring(3, 5);
        }
        return null;
    }

    /**
     * @param timezoneOffset
     *            see description for {@link #getTimezoneOffset()}
     * @return a date object that represents the local date and time at the passed in offset
     */
    public static Date getLocalDateForOffset(String timezoneOffset) {
        long currentTime = System.currentTimeMillis();
        int myOffset = TimeZone.getDefault().getOffset(currentTime);
        int theirOffset = TimeZone.getTimeZone("GMT" + timezoneOffset).getOffset(currentTime);
        return new Date(currentTime - myOffset + theirOffset);
    }

    /**
     * Useful method to sleep that catches and ignores the {@link InterruptedException}
     *
     * @param ms
     *            milliseconds to sleep
     */
    public static void sleep(long ms) {
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                log.info("Interrupted while sleeping for " + ms);
            }
        }
    }

    public static boolean isSystemPropertySet(String propName, boolean defaultValue) {
        return "true"
                .equalsIgnoreCase(System.getProperty(propName, Boolean.toString(defaultValue)));
    }

    public static void unzip(InputStream in, File toDir) {
        try {
            ZipInputStream is = new ZipInputStream(in);
            ZipEntry entry = null;
            do {
                entry = is.getNextEntry();
                if (entry != null) {
                    if (entry.isDirectory()) {
                        File dir = new File(toDir, entry.getName());
                        dir.mkdirs();
                        dir.setLastModified(entry.getTime());
                    } else {
                        File file = new File(toDir, entry.getName());
                        if (!file.getParentFile().exists()) {
                            file.getParentFile().mkdirs();
                            file.getParentFile().setLastModified(entry.getTime());
                        }
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            IOUtils.copy(is, fos);
                            file.setLastModified(entry.getTime());
                        }
                    }
                }
            } while (entry != null);
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        File file = File.createTempFile(prefix, suffix);
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
        return file;
    }

    public static String formatStackTrace(StackTraceElement[] stackTrace) {
        return formatStackTrace(stackTrace, 0, true);
    }

    public static String formatStackTrace(StackTraceElement[] stackTrace, int indentSpaces, boolean indentFirst) {
        StringBuilder buff = new StringBuilder(256);
        boolean first = true;
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (!first || indentFirst) {
                buff.append(StringUtils.rightPad("", indentSpaces));
            } else {
                first = false;
            }
            buff.append(stackTraceElement.getClassName());
            buff.append(".");
            buff.append(stackTraceElement.getMethodName());
            buff.append("()");
            int lineNumber = stackTraceElement.getLineNumber();
            if (lineNumber > 0) {
                buff.append(":");
                buff.append(Integer.toString(stackTraceElement.getLineNumber()));
            }
            buff.append("\r\n");
        }
        return buff.toString();
    }

    public static <T> T newInstance(Class<T> clazz, Class<?> defaultClass) {
        return newInstance(clazz, defaultClass, new Object[0], new Class[0]);
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clazz, Class<?> defaultClass, Object[] args, Class<?>[] argTypes) {
        T instance = null;
        String propertyName = clazz.getSimpleName();
        String className = implProp.getProperty(propertyName, defaultClass == null ? null : defaultClass.getName());
        if (className != null) {
            try {
                Constructor<T> cons = (Constructor<T>) Class.forName(className).getDeclaredConstructor(argTypes);
                cons.setAccessible(true);
                instance = cons.newInstance(args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }
}
