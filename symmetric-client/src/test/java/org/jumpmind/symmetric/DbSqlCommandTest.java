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
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbSqlCommandTest {
    private File propertiesFile;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void setUp() throws Exception {
        propertiesFile = File.createTempFile("test-engine", ".properties");
        propertiesFile.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(propertiesFile)) {
            pw.println("engine.name=test");
            pw.println("db.driver=org.h2.Driver");
            pw.println("db.url=jdbc:h2:mem:dbsqltest;DB_CLOSE_DELAY=-1");
            pw.println("db.user=sa");
            pw.println("db.password=");
        }
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void executeSqlOption_runsQueryAndOutputsResult() {
        new DbSqlCommand().execute(new String[] {
                "--properties", propertiesFile.getAbsolutePath(),
                "--sql", "SELECT 1"
        });
        assertTrue(capturedOut.toString().contains("1 row"));
    }

    @Test
    void executeSqlFileOption_runsEachStatementAndEchoesSql() throws Exception {
        File sqlFile = File.createTempFile("test-sql", ".sql");
        sqlFile.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(sqlFile)) {
            pw.println("SELECT 1;");
            pw.println("SELECT 2;");
        }
        new DbSqlCommand().execute(new String[] {
                "--properties", propertiesFile.getAbsolutePath(),
                "--sqlfile", sqlFile.getAbsolutePath()
        });
        String output = capturedOut.toString();
        assertTrue(output.contains("SELECT 1"));
        assertTrue(output.contains("SELECT 2"));
    }

    @Test
    void executeWithOptions_invalidEncryptedPassword_throwsRuntimeException() throws Exception {
        File propsFile = File.createTempFile("test-engine-enc", ".properties");
        propsFile.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(propsFile)) {
            pw.println("engine.name=test");
            pw.println("db.driver=org.h2.Driver");
            pw.println("db.url=jdbc:h2:mem:dbsqltest2;DB_CLOSE_DELAY=-1");
            pw.println("db.user=sa");
            pw.println("db.password=enc:invalidencryptedvalue");
        }
        DbSqlCommand command = new DbSqlCommand();
        command.propertiesFile = propsFile;
        Options options = new Options();
        command.buildOptions(options);
        CommandLine line = new DefaultParser().parse(options, new String[] { "--sql", "SELECT 1" });
        RuntimeException ex = assertThrows(RuntimeException.class, () -> command.executeWithOptions(line));
        assertTrue(ex.getMessage().contains("Failed to decrypt a database credential in"));
        assertTrue(ex.getMessage().contains(propsFile.getAbsolutePath()));
    }

    @Test
    void executeSqlFileOption_missingFile_printsErrorToStderr() {
        new DbSqlCommand().execute(new String[] {
                "--properties", propertiesFile.getAbsolutePath(),
                "--sqlfile", "/nonexistent/path/missing.sql"
        });
        assertTrue(capturedErr.toString().contains("File does not exist"));
    }
}
