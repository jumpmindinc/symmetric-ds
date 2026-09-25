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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.io.data.DataContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.jms.core.JmsTemplate;

class SimpleJmsPublisherTest {
    private static final String TEMPLATE_BEAN_NAME = "jmsTemplate";
    private SimpleJmsPublisher publisher;
    private JmsTemplate jmsTemplate;

    @BeforeEach
    void setUp() {
        BeanFactory beanFactory = mock(BeanFactory.class);
        jmsTemplate = mock(JmsTemplate.class);
        when(beanFactory.getBean(TEMPLATE_BEAN_NAME)).thenReturn(jmsTemplate);
        publisher = new SimpleJmsPublisher();
        publisher.setBeanFactory(beanFactory);
        publisher.setJmsTemplateBeanName(TEMPLATE_BEAN_NAME);
    }

    @Test
    void testIsEnabled_isTrueByDefault() {
        assertTrue(publisher.isEnabled());
    }

    @Test
    void testSetEnabled() {
        publisher.setEnabled(false);
        assertFalse(publisher.isEnabled());
    }

    @Test
    void testPublish_sendsTextToJmsTemplate() {
        assertTrue(publisher.publish("hello"));
        verify(jmsTemplate).convertAndSend("hello");
    }

    @Test
    void testPublish_skipsSendWhenDisabled() {
        publisher.setEnabled(false);
        assertFalse(publisher.publish("hello"));
        verify(jmsTemplate, never()).convertAndSend("hello");
    }

    @Test
    void testPublish_rethrowsRuntimeException() {
        doThrow(new IllegalStateException("broker down")).when(jmsTemplate).convertAndSend("hello");
        assertThrows(IllegalStateException.class, () -> publisher.publish("hello"));
    }

    @Test
    void testPublish_withContextDelegatesToTextPublish() {
        publisher.publish(new DataContext(), "hello");
        verify(jmsTemplate).convertAndSend("hello");
    }
}
