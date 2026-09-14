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
package org.jumpmind.symmetric.integrate;

import org.jumpmind.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "The management interface for an jms publisher")
public class SimpleJmsPublisher implements IPublisher, BeanFactoryAware {
    private static final Logger log = LoggerFactory.getLogger(SimpleJmsPublisher.class);
    private BeanFactory beanFactory;
    private String jmsTemplateBeanName;
    public boolean enabled = true;

    public void publish(Context context, String text) {
        publish(text);
    }

    public boolean isEnabled() {
        return enabled;
    }

    @ManagedOperation(description = "Publishes the message text passed in as an argument")
    @ManagedOperationParameters({ @ManagedOperationParameter(name = "text", description = "The message text that will be published") })
    public boolean publish(String text) {
        try {
            log.debug("Publishing {}", text);
            if (enabled) {
                ((JmsTemplate) beanFactory.getBean(jmsTemplateBeanName)).convertAndSend(text);
                return true;
            } else {
                log.info("Message was not published because the publisher is not enabled: \n"
                        + text);
                return false;
            }
        } catch (RuntimeException ex) {
            log.error("Failed to publish message: \n" + text, ex);
            throw ex;
        }
    }

    public void setEnabled(boolean enable) {
        this.enabled = enable;
    }

    public void setJmsTemplateBeanName(String jmsTemplateBeanName) {
        this.jmsTemplateBeanName = jmsTemplateBeanName;
    }

    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
