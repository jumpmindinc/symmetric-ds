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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.FtpDataWriter;
import org.jumpmind.symmetric.io.FtpDataWriter.Format;
import org.jumpmind.symmetric.io.FtpDataWriter.Protocol;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FtpDataLoaderFactoryTest {
    private FtpDataLoaderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new FtpDataLoaderFactory();
    }

    @Test
    void testSetBeanName_setsBeanName() {
        factory.setBeanName("ftpDataLoaderFactory");
        assertEquals("ftpDataLoaderFactory", factory.beanName);
    }

    @Test
    void testSetSymmetricEngine_setsEngine() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        factory.setSymmetricEngine(engine);
        assertEquals(engine, factory.engine);
    }

    @Test
    void testGetTypeName_withoutBeanNameSet_returnsNull() {
        assertNull(factory.getTypeName());
    }

    @Test
    void testGetTypeName_withBeanNameSet_returnsBeanName() {
        factory.setBeanName("ftpDataLoaderFactory");
        assertEquals("ftpDataLoaderFactory", factory.getTypeName());
    }

    @Test
    void testGetDataWriter_withDefaultClazzName_returnsFtpDataWriter() {
        factory.setServer("ftp.example.com");
        factory.setUsername("user");
        factory.setPassword("pass");
        factory.setStagingDir("/staging");
        factory.setRemoteDir("/remote");
        factory.setFormat(Format.CSV);
        factory.setProtocol(Protocol.SFTP);
        IDataWriter writer = factory.getDataWriter(null, null, null, null, null, null, null, null);
        assertTrue(writer instanceof FtpDataWriter);
    }

    @Test
    void testGetDataWriter_withUnknownClazzName_throwsRuntimeException() {
        factory.setClazzName("org.jumpmind.symmetric.ext.DoesNotExist");
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> factory.getDataWriter(null, null, null, null, null, null, null, null));
        assertTrue(exception.getCause() instanceof ClassNotFoundException);
    }

    @Test
    void testGetDataWriter_withNonFtpDataWriterClazzName_throwsClassCastException() {
        factory.setClazzName("java.lang.Object");
        assertThrows(ClassCastException.class,
                () -> factory.getDataWriter(null, null, null, null, null, null, null, null));
    }

    @Test
    void testIsPlatformSupported_returnsTrue() {
        assertTrue(factory.isPlatformSupported(mock(IDatabasePlatform.class)));
    }

    @Test
    void testSetClazzName_setsClazzName() {
        factory.setClazzName("org.jumpmind.symmetric.io.FtpDataWriter");
        assertEquals("org.jumpmind.symmetric.io.FtpDataWriter", factory.clazzName);
    }

    @Test
    void testSetFormat_setsFormat() {
        factory.setFormat(Format.CSV);
        assertEquals(Format.CSV, factory.format);
    }

    @Test
    void testSetProtocol_setsProtocol() {
        factory.setProtocol(Protocol.SFTP);
        assertEquals(Protocol.SFTP, factory.protocol);
    }

    @Test
    void testSetServer_setsServer() {
        factory.setServer("ftp.example.com");
        assertEquals("ftp.example.com", factory.server);
    }

    @Test
    void testSetUsername_setsUsername() {
        factory.setUsername("user");
        assertEquals("user", factory.username);
    }

    @Test
    void testSetPassword_setsPassword() {
        factory.setPassword("secret");
        assertEquals("secret", factory.password);
    }

    @Test
    void testSetStagingDir_setsStagingDir() {
        factory.setStagingDir("/staging");
        assertEquals("/staging", factory.stagingDir);
    }

    @Test
    void testSetRemoteDir_setsRemoteDir() {
        factory.setRemoteDir("/remote");
        assertEquals("/remote", factory.remoteDir);
    }
}
