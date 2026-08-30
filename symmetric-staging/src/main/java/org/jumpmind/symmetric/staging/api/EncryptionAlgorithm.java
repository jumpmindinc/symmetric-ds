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

public enum EncryptionAlgorithm {
    NONE((byte) 0x00), AES_GCM_128((byte) 0x01), AES_GCM_256((byte) 0x02), AES_GCM_128_FIPS((byte) 0x03), AES_GCM_256_FIPS((byte) 0x04);

    private final byte algorithmId;

    EncryptionAlgorithm(byte algorithmId) {
        this.algorithmId = algorithmId;
    }

    public byte getAlgorithmId() {
        return algorithmId;
    }

    public static EncryptionAlgorithm fromAlgorithmId(byte id) {
        for (EncryptionAlgorithm value : values()) {
            if (value.algorithmId == id) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown encryption algorithm id: " + id);
    }
}
