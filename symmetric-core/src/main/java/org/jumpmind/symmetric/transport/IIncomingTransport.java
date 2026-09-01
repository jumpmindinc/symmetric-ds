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
package org.jumpmind.symmetric.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface IIncomingTransport {
    public BufferedReader openReader() throws IOException;

    public InputStream openStream() throws IOException;

    public void close();

    public boolean isOpen();

    public String getRedirectionUrl();

    public String getUrl();

    /**
     * @throws IOException
     *             if the underlying connection could not be established - callers must not treat this as a definitive "no such header" result (e.g. a resume
     *             request that hasn't been honored), since that would misclassify a transient connectivity failure as a real server response.
     */
    public Map<String, String> getHeaders() throws IOException;
}