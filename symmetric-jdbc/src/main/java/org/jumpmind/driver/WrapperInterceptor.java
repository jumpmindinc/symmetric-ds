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
package org.jumpmind.driver;

import java.lang.reflect.Constructor;

import org.jumpmind.properties.TypedProperties;

public abstract class WrapperInterceptor {
    public static WrapperInterceptor createInterceptor(Object wrapped, TypedProperties systemPlusEngineProperties) {
        String property = wrapped.getClass().getName() + ".interceptor";
        if (systemPlusEngineProperties == null) {
            systemPlusEngineProperties = new TypedProperties();
            systemPlusEngineProperties.putAll(System.getProperties());
        }
        String className = systemPlusEngineProperties.get(property);
        if (className != null && className.length() > 0) {
            try {
                Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
                Constructor<?> constructor = clazz.getConstructor(Object.class, TypedProperties.class);
                return (WrapperInterceptor) constructor.newInstance(wrapped, systemPlusEngineProperties);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to load and instantiate interceptor class [" + className + "]", ex);
            }
        }
        if (wrapped instanceof PreparedStatementWrapper || wrapped instanceof StatementWrapper) {
            return new StatementInterceptor(wrapped, systemPlusEngineProperties);
        } else {
            return new DummyInterceptor(wrapped);
        }
    }

    private Object wrapped;

    public WrapperInterceptor(Object wrapped) {
        this.wrapped = wrapped;
    }

    public abstract InterceptResult preExecute(String methodName, Object... parameters);

    public abstract InterceptResult postExecute(String methodName, Object result, long startTime, long endTime, Object... parameters);

    public Object getWrapped() {
        return wrapped;
    }
}
