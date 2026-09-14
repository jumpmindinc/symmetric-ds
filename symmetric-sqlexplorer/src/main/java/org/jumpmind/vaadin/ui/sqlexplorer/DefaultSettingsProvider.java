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
package org.jumpmind.vaadin.ui.sqlexplorer;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Serializable;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class DefaultSettingsProvider implements ISettingsProvider, Serializable {
    private static final long serialVersionUID = 1L;
    final Logger log = LoggerFactory.getLogger(getClass());
    File dir;
    Settings settings;

    public DefaultSettingsProvider(String dir) {
        this(dir, null);
    }

    public DefaultSettingsProvider(String dir, String user) {
        if (isNotBlank(user)) {
            this.dir = new File(dir, user);
            this.dir.mkdirs();
        } else {
            this.dir = new File(dir);
            this.dir.mkdirs();
        }
    }

    protected File getSettingsFile() {
        return new File(dir, "sqlexplorer-settings.json");
    }

    @Override
    public void save(Settings settings) {
        synchronized (getClass()) {
            Gson gson = new Gson();
            File file = getSettingsFile();
            ClassLoader classloader = setContextClassloader();
            try (FileWriter writer = new FileWriter(file, false)) {
                gson.toJson(settings, writer);
                this.settings = settings;
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            } finally {
                restoreContextClassloader(classloader);
            }
        }
    }

    public Settings load() {
        synchronized (getClass()) {
            Gson gson = new Gson();
            File file = getSettingsFile();
            if (file.exists() && file.length() > 0) {
                ClassLoader classloader = setContextClassloader();
                try (FileReader reader = new FileReader(file)) {
                    Settings settings = gson.fromJson(reader, Settings.class);
                    return settings;
                } catch (Exception ex) {
                    log.error("Failed to load settings", ex);
                    FileUtils.deleteQuietly(file);
                } finally {
                    restoreContextClassloader(classloader);
                }
            }
            return new Settings();
        }
    }

    protected ClassLoader setContextClassloader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(Settings.class.getClassLoader());
        } catch (Exception e) {
            log.warn("", e);
        }
        return classLoader;
    }

    protected void restoreContextClassloader(ClassLoader classloader) {
        try {
            Thread.currentThread().setContextClassLoader(classloader);
        } catch (Exception e) {
            log.warn("", e);
        }
    }

    @Override
    public Settings get() {
        if (settings == null) {
            settings = load();
        }
        return settings;
    }
}
