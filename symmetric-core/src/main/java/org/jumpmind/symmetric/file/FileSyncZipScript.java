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

import java.io.File;

import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;

public abstract class FileSyncZipScript {
    private StringBuilder buff = new StringBuilder();

    public abstract String getScriptFileName(Batch batch);

    public abstract void buildScriptStart(Batch batch);

    public abstract void buildScriptFileSnapshot(Batch batch, FileSnapshot snapshot, FileTriggerRouter triggerRouter,
            FileTrigger fileTrigger, File file, String targetBaseDir, String targetFile);

    public abstract void buildScriptEnd(Batch batch);

    public StringBuilder getScript() {
        return buff;
    }

    public void appendln() {
        append("\n");
    }

    public void appendln(String string) {
        append(string);
        appendln();
    }

    public void append(String string) {
        getScript().append(string);
    }
}
