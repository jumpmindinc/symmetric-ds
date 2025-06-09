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
package org.jumpmind.symmetric.model;

import java.io.Serializable;

import org.jumpmind.symmetric.common.ParameterConstants;

public class DatabaseParameter implements Serializable {
    private static final long serialVersionUID = 1L;
    private String key;
    private String value;
    private String externalId = ParameterConstants.ALL;
    private String nodeGroupId = ParameterConstants.ALL;
    private boolean inEngineFile = false;

    public DatabaseParameter() {
    }

    public DatabaseParameter(String key) {
        this.key = key;
    }

    public DatabaseParameter(String key, String value, String externalId, String nodeGroupId) {
        this.key = key;
        this.value = value;
        this.externalId = externalId;
        this.nodeGroupId = nodeGroupId;
    }

    public DatabaseParameter(String key, String value, String externalId, String nodeGroupId, boolean inEngineFile) {
        this.key = key;
        this.value = value;
        this.externalId = externalId;
        this.nodeGroupId = nodeGroupId;
        this.inEngineFile = inEngineFile;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getNodeGroupId() {
        return nodeGroupId;
    }

    public void setNodeGroupId(String nodeGroupId) {
        this.nodeGroupId = nodeGroupId;
    }

    public boolean getInEngineFile() {
        return inEngineFile;
    }

    public void setInEngineFile(boolean inEngineFile) {
        this.inEngineFile = inEngineFile;
    }
}
