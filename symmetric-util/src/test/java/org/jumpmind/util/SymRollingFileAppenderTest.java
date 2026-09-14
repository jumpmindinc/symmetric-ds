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
package org.jumpmind.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;

class SymRollingFileAppenderTest {
    private SymRollingFileAppender appender;

    @BeforeEach
    void setUp() {
        appender = new SymRollingFileAppender();
    }

    private ILoggingEvent mockEvent(Level level) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(level);
        when(event.getThrowableProxy()).thenReturn(null);
        when(event.getFormattedMessage()).thenReturn("test message");
        return event;
    }

    private ILoggingEvent mockEventWithThrowable(Level level, String className, String message,
            StackTraceElement... frames) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(level);
        when(event.getFormattedMessage()).thenReturn("Error occurred");
        IThrowableProxy proxy = mock(IThrowableProxy.class);
        when(proxy.getClassName()).thenReturn(className);
        when(proxy.getMessage()).thenReturn(message);
        StackTraceElementProxy[] proxyArray = new StackTraceElementProxy[frames.length];
        for (int i = 0; i < frames.length; i++) {
            proxyArray[i] = new StackTraceElementProxy(frames[i]);
        }
        when(proxy.getStackTraceElementProxyArray()).thenReturn(proxyArray);
        when(event.getThrowableProxy()).thenReturn(proxy);
        return event;
    }

    @Test
    void testToKeyNullWhenNoThrowable() {
        assertNull(appender.toKey(mockEvent(Level.ERROR)));
    }

    @Test
    void testToKeyNullWhenNullStackTrace() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        IThrowableProxy proxy = mock(IThrowableProxy.class);
        when(proxy.getStackTraceElementProxyArray()).thenReturn(null);
        when(event.getThrowableProxy()).thenReturn(proxy);
        assertNull(appender.toKey(event));
    }

    @Test
    void testToKeyConsistentForSameException() {
        StackTraceElement frame = new StackTraceElement("com.example.Foo", "bar", "Foo.java", 42);
        ILoggingEvent e1 = mockEventWithThrowable(Level.ERROR, "java.io.IOException", "timeout", frame);
        ILoggingEvent e2 = mockEventWithThrowable(Level.ERROR, "java.io.IOException", "timeout", frame);
        assertEquals(appender.toKey(e1), appender.toKey(e2));
    }

    @Test
    void testToKeyDifferentForDifferentMessage() {
        StackTraceElement frame = new StackTraceElement("com.example.Foo", "bar", "Foo.java", 42);
        ILoggingEvent e1 = mockEventWithThrowable(Level.ERROR, "java.io.IOException", "timeout", frame);
        ILoggingEvent e2 = mockEventWithThrowable(Level.ERROR, "java.io.IOException", "connection refused", frame);
        assertNotEquals(appender.toKey(e1), appender.toKey(e2));
    }

    @Test
    void testToKeyDifferentForDifferentLineNumber() {
        StackTraceElement frame1 = new StackTraceElement("com.example.Foo", "bar", "Foo.java", 42);
        StackTraceElement frame2 = new StackTraceElement("com.example.Foo", "bar", "Foo.java", 99);
        ILoggingEvent e1 = mockEventWithThrowable(Level.ERROR, "java.io.IOException", "msg", frame1);
        ILoggingEvent e2 = mockEventWithThrowable(Level.ERROR, "java.io.IOException", "msg", frame2);
        assertNotEquals(appender.toKey(e1), appender.toKey(e2));
    }

    @Test
    void testToKeyUsesSimpleClassName() {
        StackTraceElement frame = new StackTraceElement("com.example.Foo", "bar", "Foo.java", 1);
        ILoggingEvent event = mockEventWithThrowable(Level.ERROR, "java.io.IOException", "msg", frame);
        String key = appender.toKey(event);
        assertNotNull(key);
        assertTrue(key.startsWith("IOException:"));
    }

    @Test
    void testGetThrowableHashConsistency() {
        StackTraceElement frame = new StackTraceElement("com.example.Foo", "doIt", "Foo.java", 10);
        StackTraceElementProxy proxy = new StackTraceElementProxy(frame);
        long hash1 = appender.getThrowableHash(new StackTraceElementProxy[] { proxy }, "msg");
        long hash2 = appender.getThrowableHash(new StackTraceElementProxy[] { proxy }, "msg");
        assertEquals(hash1, hash2);
    }

    @Test
    void testGetThrowableHashDiffersForDifferentMessage() {
        StackTraceElement frame = new StackTraceElement("com.example.Foo", "doIt", "Foo.java", 10);
        StackTraceElementProxy proxy = new StackTraceElementProxy(frame);
        long hash1 = appender.getThrowableHash(new StackTraceElementProxy[] { proxy }, "msg1");
        long hash2 = appender.getThrowableHash(new StackTraceElementProxy[] { proxy }, "msg2");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void testBuildMessageWithKeyInitSuffix() {
        ILoggingEvent event = mockEvent(Level.ERROR);
        String result = appender.buildMessageWithKey(event, "IOException:12345", ".init");
        assertTrue(result.contains("test message"));
        assertTrue(result.contains("StackTraceKey.init"));
        assertTrue(result.contains("[IOException:12345]"));
    }

    @Test
    void testBuildMessageWithKeyNoSuffix() {
        ILoggingEvent event = mockEvent(Level.ERROR);
        String result = appender.buildMessageWithKey(event, "IOException:12345", null);
        assertTrue(result.contains("StackTraceKey"));
        assertFalse(result.contains(".init"));
        assertTrue(result.contains("[IOException:12345]"));
    }

    @Test
    void testAppendKeyWrapsMessage() {
        ILoggingEvent event = mockEvent(Level.ERROR);
        ILoggingEvent wrapped = appender.appendKey(event, "SomeException:99");
        assertTrue(wrapped.getFormattedMessage().contains("StackTraceKey.init"));
    }

    @Test
    void testSuppressStackTraceRemovesThrowable() {
        StackTraceElement frame = new StackTraceElement("com.example.Foo", "bar", "Foo.java", 1);
        ILoggingEvent event = mockEventWithThrowable(Level.ERROR, "java.io.IOException", "msg", frame);
        ILoggingEvent suppressed = appender.suppressStackTrace(event, "IOException:42");
        assertNull(suppressed.getThrowableProxy());
        assertTrue(suppressed.getFormattedMessage().contains("StackTraceKey"));
    }
}
