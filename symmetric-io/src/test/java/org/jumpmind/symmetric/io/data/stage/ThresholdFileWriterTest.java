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
package org.jumpmind.symmetric.io.data.stage;

import java.io.BufferedReader;
import java.io.File;

import org.apache.commons.io.IOUtils;
import org.jumpmind.symmetric.io.stage.ThresholdFileWriter;
import static org.junit.Assert.*;
import org.junit.jupiter.api.Test;

public class ThresholdFileWriterTest {
    final String TEST_STR = "The quick brown fox jumped over the lazy dog";

    @Test
    public void testNoWriteToFile() throws Exception {
        File file = getTestFile();
        ThresholdFileWriter writer = new ThresholdFileWriter(TEST_STR.length() + 1, new StringBuilder(), file);
        writer.write(TEST_STR);
        // File does not exist since we did not meet the threshold
        assertFalse(file.exists());
        // Check the contents of the buffer (not yet written to a file)
        assertEquals(TEST_STR, IOUtils.toString(writer.getReader()));
        writer.close();
    }

    @Test
    public void testWriteToFile() throws Exception {
        File file = getTestFile();
        assertFalse(file.exists());
        ThresholdFileWriter writer = new ThresholdFileWriter(TEST_STR.length() - 1, new StringBuilder(), file);
        writer.write(TEST_STR);
        writer.close();
        // The write string exceeded the threshold so the writer should have created/written to the file
        assertTrue(file.exists());
        BufferedReader reader = writer.getReader();
        assertEquals(TEST_STR, IOUtils.toString(reader));
        reader.close();
        assertTrue(file.delete());
    }

    private File getTestFile() {
        File file = new File("target/test/buffered.file.writer.tst");
        file.getParentFile().mkdirs();
        // Make sure the file doesn't already exist
        file.delete();
        return file;
    }
}