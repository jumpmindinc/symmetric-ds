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
package org.jumpmind.symmetric.staging.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;

public interface IStagedResource extends AutoCloseable {
    StagingKey getKey();

    String getPath();

    ResourceState getState();

    void setState(ResourceState state);

    InputStream openInputStream();

    OutputStream openOutputStream();

    OutputStream openOutputStream(boolean append);

    BufferedReader openReader(Charset charset);

    BufferedWriter openWriter(Charset charset, long memoryThresholdBytes);

    ILineReader openLineReader(Charset charset);

    ILineWriter openLineWriter(Charset charset, long memoryThresholdBytes);

    ResourceLocation getCurrentLocation();

    Set<ResourceLocation> getAllLocations();

    File getFilesystemFile();

    String getRemoteObjectKey();

    StagingOptions getOptions();

    long getSize();

    boolean exists();

    boolean isMemoryResource();

    boolean isFileResource();

    boolean isBinary();

    boolean isInUse();

    boolean delete();

    void reference();

    void dereference();

    long getLastUpdateTime();

    void refreshLastUpdateTime();

    void closeReaders();

    @Override
    void close();

    void writeSidecar(String suffix, byte[] payload) throws java.io.IOException;

    byte[] readSidecar(String suffix) throws java.io.IOException;
}
