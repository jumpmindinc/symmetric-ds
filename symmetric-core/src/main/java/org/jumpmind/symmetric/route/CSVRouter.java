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
package org.jumpmind.symmetric.route;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;

public class CSVRouter extends AbstractFileParsingRouter implements IDataRouter, IBuiltInExtensionPoint {
    private ISymmetricEngine engine;
    private String columns;

    public CSVRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @Override
    public ISymmetricEngine getEngine() {
        return this.engine;
    }

    @Override
    public List<String> parse(InputStream in, String fileName, int lineNumber, int tableIndex) {
        List<String> rows = new ArrayList<String>();
        int currentLine = 1;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                if (currentLine == 1) {
                    columns = line;
                } else {
                    if (currentLine > lineNumber) {
                        rows.add(line);
                    }
                }
                currentLine++;
            }
            reader.close();
        } catch (IOException e) {
            log.error("Unable to parse CSV file " + fileName + " line number " + currentLine, e);
        }
        return rows;
    }

    @Override
    public String getColumnNames() {
        return this.columns;
    }
}
