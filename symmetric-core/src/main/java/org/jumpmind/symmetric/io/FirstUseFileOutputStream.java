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
package org.jumpmind.symmetric.io;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A FileOutputStream that delays creating the physical file until the first write operation, to avoid empty files.
 */
public class FirstUseFileOutputStream extends OutputStream {
    private String fileName;
    private FileOutputStream fileOutputStream;

    public FirstUseFileOutputStream(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void write(int b) throws IOException {
        if (fileOutputStream == null) {
            fileOutputStream = new FileOutputStream(fileName);
        }
        fileOutputStream.write(b);
    }
}