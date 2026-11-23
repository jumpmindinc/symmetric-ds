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
package org.jumpmind.security;

/**
 * Stores named secret values, such as startup parameters saved with the {@link SecurityConstants#PREFIX_KEYSTORE_STORAGE} prefix. This is the implementor side
 * of a Bridge behind {@link ISecurityService#getKeystoreEntry(String)}, {@link ISecurityService#setKeystoreEntry(String, String)} and
 * {@link ISecurityService#deleteKeystoreEntry(String)}, so a backend service such as a cloud secrets manager can hold them instead of the local keystore.
 */
public interface ISecretStore {
    /**
     * @return the value stored under the alias, or null when there is none
     */
    public String getEntry(String alias) throws Exception;

    public void setEntry(String alias, String value) throws Exception;

    public void deleteEntry(String alias) throws Exception;
}
