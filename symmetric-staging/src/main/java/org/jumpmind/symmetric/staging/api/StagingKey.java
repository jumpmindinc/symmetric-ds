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

import java.util.Arrays;
import java.util.Objects;

public final class StagingKey {
    private final Object[] path;
    private final String asPath;

    public StagingKey(Object... path) {
        if (path == null || path.length == 0) {
            throw new IllegalArgumentException("StagingKey path must not be empty");
        }
        this.path = path.clone();
        this.asPath = buildPath(path);
    }

    public static StagingKey ofPath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        return new StagingKey((Object[]) path.split("/"));
    }

    public Object[] getPath() {
        return path.clone();
    }

    public String asPath() {
        return asPath;
    }

    private static String buildPath(Object[] parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(String.valueOf(parts[i]));
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StagingKey)) {
            return false;
        }
        StagingKey that = (StagingKey) other;
        return Objects.equals(this.asPath, that.asPath);
    }

    @Override
    public int hashCode() {
        return asPath.hashCode();
    }

    @Override
    public String toString() {
        return asPath;
    }

    @Override
    @SuppressWarnings("unused")
    protected Object clone() throws CloneNotSupportedException {
        return new StagingKey(Arrays.copyOf(path, path.length));
    }
}
