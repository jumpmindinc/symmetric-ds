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
package org.jumpmind.symmetric.ext;

import java.io.Serializable;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.extension.IExtensionPoint;

/**
 * Wrapper class for installed extension points.
 */
public class ExtensionPointMetaData implements Serializable {
    private static final long serialVersionUID = 1L;
    private IExtensionPoint extensionPoint;
    private String name;
    private Class<? extends IExtensionPoint> type;
    private boolean installed;
    private String extraInfo;

    public ExtensionPointMetaData(IExtensionPoint extensionPoint, String name, boolean installed) {
        this(extensionPoint, name, null, installed);
    }

    public ExtensionPointMetaData(IExtensionPoint extensionPoint, String name,
            Class<? extends IExtensionPoint> type, boolean installed) {
        this(extensionPoint, name, type, installed, null);
    }

    public ExtensionPointMetaData(IExtensionPoint extensionPoint, String name,
            Class<? extends IExtensionPoint> type, boolean installed, String extraInfo) {
        this.extensionPoint = extensionPoint;
        this.name = name;
        this.type = type;
        this.installed = installed;
        this.extraInfo = extraInfo;
        assignExtensionPointInterface();
        this.installed |= this.type != null
                && (this.type.getSimpleName().equals("IServletExtension") ||
                        this.type.getSimpleName().equals("IServletFilterExtension"));
    }

    public String getTypeText() {
        StringBuilder typeText = new StringBuilder();
        if (type != null) {
            String simpleName = type.getSimpleName();
            boolean justAddedSpace = true;
            for (int i = 1; i < simpleName.length(); i++) {
                if (!justAddedSpace && Character.isUpperCase(simpleName.charAt(i))) {
                    typeText.append(" ");
                    typeText.append(simpleName.charAt(i));
                } else {
                    justAddedSpace = false;
                    typeText.append(Character.toUpperCase(simpleName.charAt(i)));
                }
            }
        }
        return typeText.toString();
    }

    protected void assignExtensionPointInterface() {
        if (this.extensionPoint != null) {
            Class<?> base = this.extensionPoint.getClass();
            while (base != null && this.type == null) {
                this.type = findExtensionPoint(base.getInterfaces());
                base = base.getSuperclass();
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected Class<? extends IExtensionPoint> findExtensionPoint(Class<?>[] types) {
        for (Class<?> ifc : types) {
            if (ifc.equals(IExtensionPoint.class)) {
                return (Class<? extends IExtensionPoint>) ifc;
            } else {
                Class<? extends IExtensionPoint> searchValue = findExtensionPoint(ifc.getInterfaces());
                if (searchValue != null) {
                    return (Class<? extends IExtensionPoint>) ifc;
                }
            }
        }
        return null;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setType(Class<? extends IExtensionPoint> type) {
        this.type = type;
    }

    public Class<? extends IExtensionPoint> getType() {
        return type;
    }

    public IExtensionPoint getExtensionPoint() {
        return extensionPoint;
    }

    public void setExtensionPoint(IExtensionPoint extensionPoint) {
        this.extensionPoint = extensionPoint;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public boolean isBuiltIn() {
        return extensionPoint instanceof IBuiltInExtensionPoint;
    }
}
