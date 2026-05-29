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
package org.jumpmind.symmetric.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.Row;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.ext.ExtensionPointMetaData;
import org.jumpmind.symmetric.ext.INodeGroupExtensionPoint;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.io.data.transform.IColumnTransform;
import org.jumpmind.symmetric.model.Extension;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.util.SimpleClassCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bsh.EvalError;
import bsh.Interpreter;

/**
 * This service registers {@link IExtensionPoint}s defined both by SymmetricDS and others found in the {@link ApplicationContext}. It also reads the
 * sym_extension table for {@link IExtensionPoint}s defined there.
 * <P>
 * SymmetricDS reads in any Spring XML file found in the classpath of the application that matches the following pattern: /META-INF/services/symmetric-*-ext.xml
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ExtensionService extends AbstractService implements IExtensionService {
    private final static Logger log = LoggerFactory.getLogger(ExtensionService.class);
    protected ISymmetricEngine engine;
    protected SimpleClassCompiler simpleClassCompiler;
    protected Map<Class, Map<String, IExtensionPoint>> staticExtensionsByClassByName = new HashMap<Class, Map<String, IExtensionPoint>>();
    protected Map<Class, Map<String, IExtensionPoint>> extensionsByClassByName;
    protected List<ExtensionPointMetaData> extensionMetaData;

    public ExtensionService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        simpleClassCompiler = new SimpleClassCompiler();
        setSqlMap(new ExtensionServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    public synchronized void refresh() {
        extensionsByClassByName = new HashMap<Class, Map<String, IExtensionPoint>>();
        extensionMetaData = new ArrayList<ExtensionPointMetaData>();
        for (Class extensionClass : staticExtensionsByClassByName.keySet()) {
            Map<String, IExtensionPoint> byNameMap = staticExtensionsByClassByName.get(extensionClass);
            for (String name : byNameMap.keySet()) {
                IExtensionPoint ext = byNameMap.get(name);
                getExtensionsByNameMap(extensionClass).put(name, ext);
                addExtensionPointMetaData(ext, name, extensionClass, true);
            }
        }
        String prefix = parameterService.getString(ParameterConstants.RUNTIME_CONFIG_TABLE_PREFIX);
        if (platform.getRelationFromCache(TableConstants.getTableName(prefix, TableConstants.SYM_EXTENSION), false) != null) {
            List<Extension> extensionList = sqlTemplate.query(getSql("selectEnabled"), new ExtensionRowMapper(), parameterService.getNodeGroupId());
            log.info("Found {} extension points from the database that will be registered", extensionList.size());
            for (Extension extension : extensionList) {
                registerExtension(extension);
            }
        }
    }

    protected void registerExtension(Extension extension) {
        if (extension.getExtensionText() != null) {
            if (extension.getExtensionType().equalsIgnoreCase(Extension.EXTENSION_TYPE_JAVA)) {
                if (!SimpleClassCompiler.isJdkAvailable()) {
                    log.warn("Skipping Java extension '{}': Java compilation is not available in restricted security mode (JRE only).",
                            extension.getExtensionId());
                    return;
                }
                try {
                    Object ext = simpleClassCompiler.getCompiledClass(extension.getExtensionText());
                    registerExtension(extension.getExtensionId(), (IExtensionPoint) ext);
                } catch (Exception e) {
                    log.error("Error while compiling Java extension " + extension.getExtensionId(), e);
                }
            } else if (extension.getExtensionType().equalsIgnoreCase(Extension.EXTENSION_TYPE_BSH)) {
                try {
                    Interpreter interpreter = new Interpreter();
                    interpreter.set("engine", engine);
                    interpreter.set("sqlTemplate", engine.getDatabasePlatform().getSqlTemplate());
                    interpreter.set("log", log);
                    String globalScript = parameterService.getString(ParameterConstants.BSH_EXTENSION_GLOBAL_SCRIPT);
                    if (StringUtils.isNotBlank(globalScript)) {
                        interpreter.eval(globalScript);
                    }
                    interpreter.eval(extension.getExtensionText());
                    Object ext = interpreter.getInterface(Class.forName(extension.getInterfaceName()));
                    registerExtension(extension.getExtensionId(), (IExtensionPoint) ext);
                } catch (EvalError e) {
                    log.error("Error while parsing BSH extension " + extension.getExtensionId(), e);
                } catch (ClassNotFoundException e) {
                    log.error("Interface class not found for BSH extension " + extension.getExtensionId(), e);
                } catch (Error e) {
                    log.error("Failed to register BSH extension " + extension.getExtensionId(), e);
                }
            } else {
                log.error("Skipping extension " + extension.getExtensionId() + ", unknown extension type " + extension.getExtensionType());
            }
        }
    }

    protected boolean registerExtension(String name, IExtensionPoint ext) {
        if (!(ext instanceof IExtensionPoint)) {
            log.error("Missing IExtensionPoint interface for extension " + name);
        }
        return registerExtension(name, ext, true);
    }

    protected boolean registerExtension(String name, IExtensionPoint ext, boolean shouldLog) {
        boolean installed = false;
        if (initializeExtension(ext)) {
            for (Class extensionClass : getExtensionClassList(ext)) {
                if (ext instanceof IBuiltInExtensionPoint) {
                    log.debug("Registering built-in extension named '{}' of type '{}'", name, extensionClass.getSimpleName());
                } else if (shouldLog) {
                    log.info("Registering extension named '{}' of type '{}'", name, extensionClass.getSimpleName());
                }
                installed = true;
                addExtensionPointMetaData(ext, name, extensionClass, true);
                getExtensionsByNameMap(extensionClass).put(name, ext);
            }
            if (!installed) {
                addExtensionPointMetaData(ext, name, null, false);
            }
        }
        return installed;
    }

    protected void unRegisterExtension(String name, IExtensionPoint ext) {
        for (Class extensionClass : getExtensionClassList(ext)) {
            getExtensionsByNameMap(extensionClass).remove(name);
        }
        Iterator<ExtensionPointMetaData> iterator = extensionMetaData.iterator();
        while (iterator.hasNext()) {
            ExtensionPointMetaData metaData = iterator.next();
            if (metaData.getExtensionPoint() == ext && metaData.getName().equals(name)) {
                iterator.remove();
            }
        }
    }

    protected boolean initializeExtension(IExtensionPoint ext) {
        boolean shouldInstall = false;
        if (ext instanceof ISymmetricEngineAware) {
            ((ISymmetricEngineAware) ext).setSymmetricEngine(engine);
        }
        if (ext instanceof INodeGroupExtensionPoint) {
            String nodeGroupId = parameterService.getNodeGroupId();
            INodeGroupExtensionPoint nodeExt = (INodeGroupExtensionPoint) ext;
            String[] ids = nodeExt.getNodeGroupIdsToApplyTo();
            if (ids != null) {
                for (String targetNodeGroupId : ids) {
                    if (nodeGroupId.equals(targetNodeGroupId)) {
                        shouldInstall = true;
                    }
                }
            } else {
                shouldInstall = true;
            }
        } else {
            shouldInstall = true;
        }
        return shouldInstall;
    }

    protected List<Class> getExtensionClassList(IExtensionPoint ext) {
        List<Class> classList = new ArrayList<Class>();
        List<Class<?>> interfaces = ClassUtils.getAllInterfaces(ext.getClass());
        for (Class clazz : interfaces) {
            if (IExtensionPoint.class.isAssignableFrom(clazz) && !clazz.equals(IExtensionPoint.class)) {
                classList.add(clazz);
            }
        }
        return classList;
    }

    public synchronized List<ExtensionPointMetaData> getExtensionPointMetaData() {
        return new ArrayList<ExtensionPointMetaData>(extensionMetaData);
    }

    protected void addExtensionPointMetaData(IExtensionPoint extensionPoint, String name,
            Class<? extends IExtensionPoint> extensionClass, boolean installed) {
        if (!installed || (!extensionClass.equals(IBuiltInExtensionPoint.class) && !extensionClass.equals(IColumnTransform.class))) {
            extensionMetaData.add(new ExtensionPointMetaData(extensionPoint, name, extensionClass, installed));
        }
    }

    public synchronized <T extends IExtensionPoint> T getExtensionPoint(Class<T> extensionClass) {
        List<T> availableExtensions = getExtensionPointList(extensionClass);
        for (T extension : availableExtensions) {
            if (!(extension instanceof IBuiltInExtensionPoint)) {
                return extension;
            }
        }
        for (T extension : availableExtensions) {
            return extension;
        }
        return null;
    }

    public synchronized <T extends IExtensionPoint> List<T> getExtensionPointList(Class<T> extensionClass) {
        return new ArrayList<T>(getExtensionPointMap(extensionClass).values());
    }

    public synchronized <T extends IExtensionPoint> Map<String, T> getExtensionPointMap(Class<T> extensionClass) {
        return (Map<String, T>) getExtensionsByNameMap(extensionClass);
    }

    public synchronized void addExtensionPoint(IExtensionPoint extension) {
        for (Class extensionClass : getExtensionClassList(extension)) {
            getStaticExtensionsByNameMap(extensionClass).put(extension.getClass().getCanonicalName(), extension);
        }
        registerExtension(extension.getClass().getCanonicalName(), extension, false);
    }

    public synchronized void addExtensionPoint(String name, IExtensionPoint extension) {
        for (Class extensionClass : getExtensionClassList(extension)) {
            getStaticExtensionsByNameMap(extensionClass).put(name, extension);
        }
        registerExtension(name, extension, false);
    }

    public synchronized void removeExtensionPoint(IExtensionPoint extension) {
        for (Class extensionClass : getExtensionClassList(extension)) {
            getStaticExtensionsByNameMap(extensionClass).remove(extension.getClass().getCanonicalName());
        }
        unRegisterExtension(extension.getClass().getCanonicalName(), extension);
    }

    protected Map<String, IExtensionPoint> getStaticExtensionsByNameMap(Class extensionClass) {
        return getExtensionsByNameMap(staticExtensionsByClassByName, extensionClass);
    }

    protected Map<String, IExtensionPoint> getExtensionsByNameMap(Class extensionClass) {
        return getExtensionsByNameMap(extensionsByClassByName, extensionClass);
    }

    protected Map<String, IExtensionPoint> getExtensionsByNameMap(Map<Class, Map<String, IExtensionPoint>> byClassByNameMap,
            Class extensionClass) {
        Map<String, IExtensionPoint> byNameMap = byClassByNameMap.get(extensionClass);
        if (byNameMap == null) {
            byNameMap = new HashMap<String, IExtensionPoint>();
            byClassByNameMap.put(extensionClass, byNameMap);
        }
        return byNameMap;
    }

    public List<Extension> getExtensions() {
        return sqlTemplate.query(getSql("selectAll"), new ExtensionRowMapper());
    }

    public void saveExtension(Extension extension) {
        if (sqlTemplate.update(getSql("updateExtensionSql"),
                new Object[] { extension.getExtensionType(), extension.getInterfaceName(), extension.getNodeGroupId(),
                        extension.isEnabled() ? 1 : 0, extension.getExtensionOrder(), extension.getExtensionText(),
                        extension.getLastUpdateBy(), new Date(), extension.getExtensionId() }) <= 0) {
            sqlTemplate.update(getSql("insertExtensionSql"),
                    new Object[] { extension.getExtensionType(), extension.getInterfaceName(), extension.getNodeGroupId(),
                            extension.isEnabled() ? 1 : 0, extension.getExtensionOrder(), extension.getExtensionText(),
                            new Date(), extension.getLastUpdateBy(), new Date(), extension.getExtensionId() });
            if (extension.isEnabled()) {
                registerExtension(extension);
            }
        } else {
            refresh();
        }
    }

    public void saveExtensionAsCopy(Extension extension) {
        String newId = extension.getExtensionId();
        List<Extension> extensions = sqlTemplate.query(getSql("selectAll", "whereExtensionIdLike"),
                new ExtensionRowMapper(), newId + "%");
        List<String> ids = extensions.stream().map(Extension::getExtensionId).collect(Collectors.toList());
        String suffix = "";
        for (int i = 2; ids.contains(newId + suffix); i++) {
            suffix = "_" + i;
        }
        extension.setExtensionId(newId + suffix);
        saveExtension(extension);
    }

    public void renameExtension(String oldId, Extension extension) {
        deleteExtension(oldId);
        saveExtension(extension);
    }

    public void deleteExtension(String extensionId) {
        sqlTemplate.update(getSql("deleteExtensionSql"), extensionId);
        refresh();
    }

    public void deleteAllExtensions() {
        sqlTemplate.update(getSql("deleteAllExtensionsSql"));
        refresh();
    }

    public Object getCompiledClass(String javaCode) throws Exception {
        return simpleClassCompiler.getCompiledClass(javaCode);
    }

    static class ExtensionRowMapper implements ISqlRowMapper<Extension> {
        @Override
        public Extension mapRow(Row row) {
            Extension extension = new Extension();
            extension.setExtensionId(row.getString("extension_id"));
            extension.setExtensionType(row.getString("extension_type"));
            extension.setInterfaceName(row.getString("interface_name"));
            extension.setNodeGroupId(row.getString("node_group_id"));
            extension.setEnabled(row.getBoolean("enabled"));
            extension.setExtensionOrder(row.getInt("extension_order"));
            extension.setExtensionText(row.getString("extension_text"));
            extension.setCreateTime(row.getDateTime("create_time"));
            extension.setLastUpdateBy(row.getString("last_update_by"));
            extension.setLastUpdateTime(row.getDateTime("last_update_time"));
            return extension;
        }
    }
}
