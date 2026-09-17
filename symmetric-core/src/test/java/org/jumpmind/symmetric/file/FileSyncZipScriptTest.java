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
package org.jumpmind.symmetric.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileSyncZipScriptTest {
    private FileSyncZipScript script;

    @BeforeEach
    void setUp() {
        script = new RecordingScript();
    }

    @Test
    void testGetScript_startsEmpty() {
        assertEquals("", script.getScript().toString());
    }

    @Test
    void testAppend() {
        script.append("echo hello");
        assertEquals("echo hello", script.getScript().toString());
    }

    @Test
    void testAppendln_withoutArgumentWritesNewline() {
        script.appendln();
        assertEquals("\n", script.getScript().toString());
    }

    @Test
    void testAppendln_withStringAppendsNewline() {
        script.appendln("echo hello");
        assertEquals("echo hello\n", script.getScript().toString());
    }

    @Test
    void testAppend_accumulatesAcrossCalls() {
        script.appendln("one");
        script.append("two");
        script.appendln();
        assertEquals("one\ntwo\n", script.getScript().toString());
    }

    private static class RecordingScript extends FileSyncZipScript {
        @Override
        public String getScriptFileName(Batch batch) {
            return "recording.sh";
        }

        @Override
        public void buildScriptStart(Batch batch) {
            appendln("start");
        }

        @Override
        public void buildScriptFileSnapshot(Batch batch, FileSnapshot snapshot, FileTriggerRouter triggerRouter,
                FileTrigger fileTrigger, File file, String targetBaseDir, String targetFile) {
            appendln("snapshot " + snapshot.getFileName());
        }

        @Override
        public void buildScriptEnd(Batch batch) {
            appendln("end");
        }
    }
}
