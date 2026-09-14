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
package org.jumpmind.symmetric.service.impl;

import java.util.Map;
import java.util.TreeMap;

import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;

public class ClientExtensionService extends ExtensionService {
    private final static Logger log = LoggerFactory.getLogger(ClientExtensionService.class);
    protected ApplicationContext springContext;

    public ClientExtensionService(ISymmetricEngine engine, ApplicationContext springContext) {
        super(engine);
        this.springContext = springContext;
    }

    @Override
    public synchronized void refresh() {
        super.refresh();
        if (springContext != null) {
            Map<String, IExtensionPoint> extensionPointMap = new TreeMap<String, IExtensionPoint>();
            extensionPointMap.putAll(springContext.getBeansOfType(IExtensionPoint.class));
            BeanFactory factory = springContext.getParentBeanFactory();
            if (factory instanceof ListableBeanFactory) {
                ListableBeanFactory beanFactory = (ListableBeanFactory) factory;
                extensionPointMap.putAll(beanFactory.getBeansOfType(IExtensionPoint.class));
            }
            log.info("Found {} extension points from spring that will be registered", extensionPointMap.size());
            for (String name : extensionPointMap.keySet()) {
                registerExtension(name, extensionPointMap.get(name), true);
            }
        }
    }

    public void setSpringContext(ApplicationContext springContext) {
        this.springContext = springContext;
    }
}
