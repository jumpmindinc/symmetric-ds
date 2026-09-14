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
package org.jumpmind.security;

import org.jumpmind.properties.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityServiceFactory {
    protected static Logger log = LoggerFactory.getLogger(SecurityServiceFactory.class);

    public enum SecurityServiceType {
        CLIENT, SERVER
    }

    public static ISecurityService create() {
        return create(SecurityServiceType.CLIENT, null);
    }

    public static ISecurityService create(SecurityServiceType serviceType, TypedProperties properties) {
        try {
            if (properties == null) {
                properties = new TypedProperties(System.getProperties());
            }
            String className = properties.get(SecurityConstants.CLASS_NAME_SECURITY_SERVICE);
            log.debug("security class name from properties: {}", className == null ? "null" : className);
            if (className == null || className.trim().equals("")) {
                className = serviceType == SecurityServiceType.SERVER ? "org.jumpmind.security.BouncyCastleSecurityService" : SecurityService.class.getName();
            }
            log.debug("Instantiating security using class name: {}", className);
            ISecurityService securityService = (ISecurityService) Class.forName(className).getDeclaredConstructor().newInstance();
            securityService.init();
            return securityService;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
