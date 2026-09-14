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
package org.jumpmind.symmetric.util;

import java.util.ArrayList;
import java.util.List;

public class MavenArtifact {
    public static final String DEFAULT_PACKAGING = "jar";
    public static final String ZIP_PACKAGING = "zip";
    public static final String REGEX_LIST = "\\s*,\\s*";
    public static final String REGEX_COMPONENTS = "\\s*:\\s*";
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging = DEFAULT_PACKAGING;

    public MavenArtifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public MavenArtifact(String dependency, String defaultVersion) {
        if (dependency != null) {
            String[] array = dependency.trim().split(REGEX_COMPONENTS);
            if (array.length >= 1) {
                this.groupId = array[0];
            }
            if (array.length >= 2) {
                this.artifactId = array[1];
            }
            if (array.length >= 3) {
                this.version = array[2].replace("$version", defaultVersion.replaceAll("x-SNAPSHOT", "0"));
            }
            if (array.length >= 4) {
                this.packaging = array[3];
            }
        }
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public String toFileName() {
        return artifactId + "-" + version + "." + packaging;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MavenArtifact other = (MavenArtifact) obj;
        if (artifactId == null) {
            if (other.artifactId != null)
                return false;
        } else if (!artifactId.equals(other.artifactId))
            return false;
        if (groupId == null) {
            if (other.groupId != null)
                return false;
        } else if (!groupId.equals(other.groupId))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    public static List<MavenArtifact> parseCsv(String dependencies, String defaultVersion) {
        List<MavenArtifact> list = new ArrayList<MavenArtifact>();
        if (dependencies != null) {
            for (String dependency : dependencies.split(REGEX_LIST)) {
                MavenArtifact artifact = new MavenArtifact(dependency, defaultVersion);
                if (artifact != null) {
                    list.add(artifact);
                }
            }
        }
        return list;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }
}
