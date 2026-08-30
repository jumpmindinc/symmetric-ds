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

import java.util.ServiceLoader;

import org.jumpmind.symmetric.staging.factory.NotImplementedException;

public final class StreamCompressionRegistry {
    private StreamCompressionRegistry() {
    }

    public static IStreamCompressionProvider lookup(String compressionId) {
        if (compressionId == null || compressionId.isBlank()) {
            return null;
        }
        ServiceLoader<IStreamCompressionProvider> loader = ServiceLoader.load(IStreamCompressionProvider.class);
        for (IStreamCompressionProvider provider : loader) {
            if (provider.getCompressionId().equalsIgnoreCase(compressionId)) {
                return provider;
            }
        }
        throw new NotImplementedException("Staging compression '" + compressionId
                + "' not available — install symmetric-pro-staging for LZ4 or an equivalent provider");
    }
}
