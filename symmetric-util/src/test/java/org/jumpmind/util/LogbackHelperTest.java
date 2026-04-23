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
package org.jumpmind.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

class LogbackHelperTest {
    private static final String TEST_LOGGER = "org.jumpmind.test.LogbackHelperTest";
    private LogbackHelper helper;
    private ch.qos.logback.classic.Level originalLevel;

    @BeforeEach
    void setUp() {
        helper = new LogbackHelper();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        originalLevel = context.getLogger(TEST_LOGGER).getLevel();
    }

    @AfterEach
    void tearDown() {
        helper.removeAppender("CONSOLE");
        helper.removeAppender("CONSOLE_ERR");
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(TEST_LOGGER).setLevel(originalLevel);
    }

    @Test
    void testConvertFromLevelTrace() {
        assertEquals(Level.TRACE, helper.convertFromLevel(ch.qos.logback.classic.Level.TRACE));
    }

    @Test
    void testConvertFromLevelDebug() {
        assertEquals(Level.DEBUG, helper.convertFromLevel(ch.qos.logback.classic.Level.DEBUG));
    }

    @Test
    void testConvertFromLevelInfo() {
        assertEquals(Level.INFO, helper.convertFromLevel(ch.qos.logback.classic.Level.INFO));
    }

    @Test
    void testConvertFromLevelWarn() {
        assertEquals(Level.WARN, helper.convertFromLevel(ch.qos.logback.classic.Level.WARN));
    }

    @Test
    void testConvertFromLevelError() {
        assertEquals(Level.ERROR, helper.convertFromLevel(ch.qos.logback.classic.Level.ERROR));
    }

    @Test
    void testConvertFromLevelNull() {
        assertEquals(Level.INFO, helper.convertFromLevel(null));
    }

    @Test
    void testConvertToLevelTrace() {
        assertEquals(ch.qos.logback.classic.Level.TRACE, helper.convertToLevel(Level.TRACE));
    }

    @Test
    void testConvertToLevelDebug() {
        assertEquals(ch.qos.logback.classic.Level.DEBUG, helper.convertToLevel(Level.DEBUG));
    }

    @Test
    void testConvertToLevelInfo() {
        assertEquals(ch.qos.logback.classic.Level.INFO, helper.convertToLevel(Level.INFO));
    }

    @Test
    void testConvertToLevelWarn() {
        assertEquals(ch.qos.logback.classic.Level.WARN, helper.convertToLevel(Level.WARN));
    }

    @Test
    void testConvertToLevelError() {
        assertEquals(ch.qos.logback.classic.Level.ERROR, helper.convertToLevel(Level.ERROR));
    }

    @Test
    void testSetLevelAndGetLevel() {
        helper.setLevel(TEST_LOGGER, Level.DEBUG);
        assertEquals(Level.DEBUG, helper.getLevel(TEST_LOGGER));
    }

    @Test
    void testProtectedLoggerBlocksOverlyRestrictiveSetting() {
        helper.addProtectedLogger(TEST_LOGGER, ch.qos.logback.classic.Level.WARN);
        helper.setLevel(TEST_LOGGER, Level.WARN);
        Level levelBeforeBlock = helper.getLevel(TEST_LOGGER);
        helper.setLevel(TEST_LOGGER, Level.ERROR);
        assertEquals(levelBeforeBlock, helper.getLevel(TEST_LOGGER));
    }

    @Test
    void testProtectedLoggerAllowsMoreVerboseThanMinimum() {
        helper.addProtectedLogger(TEST_LOGGER, ch.qos.logback.classic.Level.ERROR);
        helper.setLevel(TEST_LOGGER, Level.DEBUG);
        assertEquals(Level.DEBUG, helper.getLevel(TEST_LOGGER));
    }

    @Test
    void testProtectedLoggerAllowsSettingAtMinimum() {
        helper.addProtectedLogger(TEST_LOGGER, ch.qos.logback.classic.Level.ERROR);
        helper.setLevel(TEST_LOGGER, Level.ERROR);
        assertEquals(Level.ERROR, helper.getLevel(TEST_LOGGER));
    }

    @Test
    void testProtectedLoggerAllowsWarn() {
        helper.addProtectedLogger(TEST_LOGGER, ch.qos.logback.classic.Level.ERROR);
        helper.setLevel(TEST_LOGGER, Level.WARN);
        assertEquals(Level.WARN, helper.getLevel(TEST_LOGGER));
    }

    @Test
    void testRegisterConsoleAppenderAddsTwo() {
        helper.registerConsoleAppender();
        assertNotNull(helper.getAppender("CONSOLE"));
        assertNotNull(helper.getAppender("CONSOLE_ERR"));
    }

    @Test
    void testRemoveAppender() {
        helper.registerConsoleAppender();
        helper.removeAppender("CONSOLE");
        assertNull(helper.getAppender("CONSOLE"));
    }

    @Test
    void testRegisterConsoleAppenderIsIdempotent() {
        helper.registerConsoleAppender();
        helper.registerConsoleAppender();
        assertNotNull(helper.getAppender("CONSOLE"));
    }

    @Test
    void testGetRootLevel() {
        assertNotNull(helper.getRootLevel());
    }
}
