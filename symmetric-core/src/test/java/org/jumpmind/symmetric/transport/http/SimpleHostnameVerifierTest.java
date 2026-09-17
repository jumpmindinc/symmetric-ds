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
package org.jumpmind.symmetric.transport.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.common.Constants;
import org.junit.jupiter.api.Test;

class SimpleHostnameVerifierTest {
    @Test
    void testVerify_withAllKeyword() {
        SimpleHostnameVerifier verifier = new SimpleHostnameVerifier(Constants.TRANSPORT_HTTPS_VERIFIED_SERVERS_ALL);
        assertTrue(verifier.verify("anyhost.example.com", null));
    }

    @Test
    void testVerify_withAllKeywordIsCaseInsensitive() {
        SimpleHostnameVerifier verifier = new SimpleHostnameVerifier(Constants.TRANSPORT_HTTPS_VERIFIED_SERVERS_ALL.toUpperCase());
        assertTrue(verifier.verify("anyhost.example.com", null));
    }

    @Test
    void testVerify_withMatchingHostname() {
        SimpleHostnameVerifier verifier = new SimpleHostnameVerifier("host1.example.com");
        assertTrue(verifier.verify("host1.example.com", null));
    }

    @Test
    void testVerify_withMatchingHostnameInCommaSeparatedList() {
        SimpleHostnameVerifier verifier = new SimpleHostnameVerifier("host1.example.com, host2.example.com");
        assertTrue(verifier.verify("host2.example.com", null));
    }

    @Test
    void testVerify_withNonMatchingHostname() {
        SimpleHostnameVerifier verifier = new SimpleHostnameVerifier("host1.example.com");
        assertFalse(verifier.verify("host2.example.com", null));
    }

    @Test
    void testVerify_withNullHostname() {
        SimpleHostnameVerifier verifier = new SimpleHostnameVerifier("host1.example.com");
        assertFalse(verifier.verify(null, null));
    }

    @Test
    void testVerify_withBlankConfig() {
        SimpleHostnameVerifier verifier = new SimpleHostnameVerifier("");
        assertFalse(verifier.verify("host1.example.com", null));
    }

    @Test
    void testVerify_withNullConfig() {
        SimpleHostnameVerifier verifier = new SimpleHostnameVerifier(null);
        assertFalse(verifier.verify("host1.example.com", null));
    }
}
