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
package org.jumpmind.symmetric.io.data;

public class DmlWeight {
    private int insertWeight;
    private int updateWeight;
    private int deleteWeight;

    public DmlWeight() {
    }

    public DmlWeight(int insertWeight, int updateWeight, int deleteWeight) {
        this.insertWeight = insertWeight;
        this.updateWeight = updateWeight;
        this.deleteWeight = deleteWeight;
    }

    public DmlWeight(String csv) {
        if (csv != null) {
            String[] iud = csv.split(",");
            if (iud.length > 0) {
                insertWeight = Integer.valueOf(iud[0]);
            }
            if (iud.length > 1) {
                updateWeight = Integer.valueOf(iud[1]);
            }
            if (iud.length > 2) {
                deleteWeight = Integer.valueOf(iud[2]);
            }
        }
    }

    public int getInsertWeight() {
        return insertWeight;
    }

    public int getUpdateWeight() {
        return updateWeight;
    }

    public int getDeleteWeight() {
        return deleteWeight;
    }

    public void setInsertWeight(int insertWeight) {
        this.insertWeight = insertWeight;
    }

    public void setUpdateWeight(int updateWeight) {
        this.updateWeight = updateWeight;
    }

    public void setDeleteWeight(int deleteWeight) {
        this.deleteWeight = deleteWeight;
    }
}
