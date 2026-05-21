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
package org.jumpmind.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypedProperties extends Properties {
    private static final long serialVersionUID = 1L;
    private static Logger log = LoggerFactory.getLogger(TypedProperties.class);

    public TypedProperties(InputStream is) {
        try {
            load(is);
        } catch (IOException ex) {
            throw new IoException(ex);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public TypedProperties() {
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        if (value != null) {
            return super.put(key, value);
        } else {
            return null;
        }
    }

    public TypedProperties(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            load(fis);
        } catch (IOException ex) {
            throw new IoException(ex);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public TypedProperties(Properties properties) {
        this();
        putAll(properties);
    }

    public TypedProperties(URL url) {
        try (InputStream fis = url.openStream()) {
            load(fis);
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    public final void putAll(Properties properties) {
        for (Object key : properties.keySet()) {
            put(key, properties.getProperty((String) key));
        }
    }

    public long getLong(String key) {
        return getLong(key, -1);
    }

    public long getLong(String key, long defaultValue) {
        long returnValue = defaultValue;
        String value = getProperty(key);
        if (value != null) {
            try {
                returnValue = Long.parseLong(value);
            } catch (NumberFormatException ex) {
                logPropertiesException(log, key, value);
            }
        }
        return returnValue;
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        int returnValue = defaultValue;
        String value = getProperty(key);
        if (value != null) {
            try {
                returnValue = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                logPropertiesException(log, key, value);
            }
        }
        return returnValue;
    }

    public boolean is(String key) {
        return is(key, false);
    }

    public boolean is(String key, boolean defaultValue) {
        boolean returnValue = defaultValue;
        String value = getProperty(key);
        if (value != null) {
            returnValue = Boolean.parseBoolean(value);
        }
        return returnValue;
    }

    public void collectFrom(String prefixToDrop, String[] names, Function<String, String> lookup) {
        for (String name : names) {
            put(name, lookup.apply(prefixToDrop + name));
        }
    }

    public void collectFrom(TypedProperties source, String prefixToMatch, boolean dropPrefix) {
        for (String key : source.stringPropertyNames()) {
            String upperKey = key.toUpperCase();
            if (upperKey.startsWith(prefixToMatch)) {
                String propKey = dropPrefix
                        ? FormatUtils.removePrefix(upperKey, prefixToMatch).toLowerCase().replace('_', '.')
                        : key;
                put(propKey, source.getProperty(key));
                log.debug("Collected {} as {}={}", key, propKey, source.getProperty(key));
            }
        }
    }

    public String get(String key) {
        return get(key, null);
    }

    public String get(String key, String defaultValue) {
        String returnValue = defaultValue;
        String value = getProperty(key);
        if (value != null) {
            returnValue = value;
        }
        return returnValue;
    }

    public void setProperty(String key, int value) {
        setProperty(key, Integer.toString(value));
    }

    public void setProperty(String key, long value) {
        setProperty(key, Long.toString(value));
    }

    public String[] getArray(String key, String[] defaultValue) {
        String value = getProperty(key);
        String[] retValue = defaultValue;
        if (value != null) {
            retValue = value.split(",");
        }
        return retValue;
    }

    /**
     * Modifies TypedProperties object to override only existing property values with specified properties.
     */
    public void merge(Properties properties) {
        Set<Object> keys = properties.keySet();
        for (Object key : keys) {
            if (containsKey(key)) {
                setProperty((String) key, properties.getProperty((String) key));
            }
        }
    }

    public TypedProperties copy() {
        return new TypedProperties(this);
    }

    public static void logPropertiesException(Logger logger, String key, String val) {
        if (StringUtils.isNotBlank(val)) {
            logger.error("Could not parse integer from parameter \"" + key + "\"=\"" + val + "\"");
        }
    }
}
