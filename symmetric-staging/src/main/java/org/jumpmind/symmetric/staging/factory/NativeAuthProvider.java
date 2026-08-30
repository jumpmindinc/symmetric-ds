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
package org.jumpmind.symmetric.staging.factory;

import org.jumpmind.symmetric.staging.api.AccountType;
import org.jumpmind.symmetric.staging.api.IStagingAuthProvider;

public class NativeAuthProvider implements IStagingAuthProvider {
    private final String accountKey;
    private final String accountSecret;

    public NativeAuthProvider(String accountKey, String accountSecret) {
        if (accountKey == null || accountKey.isBlank()) {
            throw new IllegalArgumentException("staging.account.key is required for native account type");
        }
        if (accountSecret == null || accountSecret.isBlank()) {
            throw new IllegalArgumentException("staging.account.secret is required for native account type");
        }
        this.accountKey = accountKey;
        this.accountSecret = accountSecret;
    }

    @Override
    public AccountType getAccountType() {
        return AccountType.NATIVE;
    }

    @Override
    public String getAccountKey() {
        return accountKey;
    }

    @Override
    public String getAccountSecret() {
        return accountSecret;
    }
}
