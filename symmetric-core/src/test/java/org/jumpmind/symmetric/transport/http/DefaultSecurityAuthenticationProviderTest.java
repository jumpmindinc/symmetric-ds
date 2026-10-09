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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.web.WebConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultSecurityAuthenticationProviderTest {
    private HttpConnection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://host/path/action").toURL());
    }

    @Test
    void testAuthenticate_withHeaderSecurityTokenEnabled_setsSecurityTokenHeader() {
        newProvider(true, false).authenticate(conn, "secret");
        verify(conn).setRequestProperty(WebConstants.HEADER_SECURITY_TOKEN, "secret");
    }

    @Test
    void testAuthenticate_withHeaderSecurityTokenDisabled_omitsSecurityTokenHeader() {
        newProvider(false, false).authenticate(conn, "secret");
        verify(conn, never()).setRequestProperty(eq(WebConstants.HEADER_SECURITY_TOKEN), anyString());
    }

    @Test
    void testAuthenticate_withNullSecurityToken_omitsSecurityTokenHeader() {
        newProvider(true, false).authenticate(conn, null);
        verify(conn, never()).setRequestProperty(eq(WebConstants.HEADER_SECURITY_TOKEN), anyString());
    }

    @Test
    void testAuthenticate_withCachedSession_setsSessionIdInsteadOfSecurityToken() {
        DefaultSecurityAuthenticationProvider provider = newProvider(true, true);
        provider.sessionIdByUri.put("http://host/path", "sess-1");
        provider.authenticate(conn, "secret");
        verify(conn).setRequestProperty(WebConstants.HEADER_SESSION_ID, "sess-1");
        verify(conn, never()).setRequestProperty(eq(WebConstants.HEADER_SECURITY_TOKEN), anyString());
    }

    @Test
    void testAuthenticate_withSessionAuthEnabledAndNoCachedSession_setsSecurityToken() {
        newProvider(true, true).authenticate(conn, "secret");
        verify(conn).setRequestProperty(WebConstants.HEADER_SECURITY_TOKEN, "secret");
        verify(conn, never()).setRequestProperty(eq(WebConstants.HEADER_SESSION_ID), anyString());
    }

    @Test
    void testAuthenticate_withSessionAuthDisabled_ignoresCachedSession() {
        DefaultSecurityAuthenticationProvider provider = newProvider(true, false);
        provider.sessionIdByUri.put("http://host/path", "sess-1");
        provider.authenticate(conn, "secret");
        verify(conn, never()).setRequestProperty(eq(WebConstants.HEADER_SESSION_ID), anyString());
        verify(conn).setRequestProperty(WebConstants.HEADER_SECURITY_TOKEN, "secret");
    }

    @Test
    void testIsSecurityTokenInHeader_whenEnabled() {
        assertTrue(newProvider(true, false).isSecurityTokenInHeader());
    }

    @Test
    void testIsSecurityTokenInHeader_whenDisabled() {
        assertFalse(newProvider(false, false).isSecurityTokenInHeader());
    }

    @Test
    void testUpdateSession_withSessionAuthEnabledAndHeaderPresent_storesSessionId() {
        DefaultSecurityAuthenticationProvider provider = newProvider(false, true);
        when(conn.getHeaderField(WebConstants.HEADER_SET_SESSION_ID)).thenReturn("sess-9");
        provider.updateSession(conn);
        assertEquals("sess-9", provider.sessionIdByUri.get("http://host/path"));
    }

    @Test
    void testUpdateSession_withSessionAuthDisabled_doesNothing() {
        DefaultSecurityAuthenticationProvider provider = newProvider(false, false);
        when(conn.getHeaderField(WebConstants.HEADER_SET_SESSION_ID)).thenReturn("sess-9");
        provider.updateSession(conn);
        assertTrue(provider.sessionIdByUri.isEmpty());
    }

    @Test
    void testUpdateSession_withSessionAuthEnabledButHeaderAbsent_doesNothing() {
        DefaultSecurityAuthenticationProvider provider = newProvider(false, true);
        when(conn.getHeaderField(WebConstants.HEADER_SET_SESSION_ID)).thenReturn(null);
        provider.updateSession(conn);
        assertTrue(provider.sessionIdByUri.isEmpty());
    }

    @Test
    void testClearSession_withSessionAuthEnabled_removesEntry() {
        DefaultSecurityAuthenticationProvider provider = newProvider(false, true);
        provider.sessionIdByUri.put(provider.getUri(conn), "sess-1");
        provider.clearSession(conn);
        assertTrue(provider.sessionIdByUri.isEmpty());
    }

    @Test
    void testClearSession_withSessionAuthDisabled_doesNothing() {
        DefaultSecurityAuthenticationProvider provider = newProvider(false, false);
        provider.sessionIdByUri.put(provider.getUri(conn), "sess-1");
        provider.clearSession(conn);
        assertFalse(provider.sessionIdByUri.isEmpty());
    }

    @Test
    void testGetUri_returnsUrlWithoutLastPathSegment() {
        assertEquals("http://host/path", newProvider(false, false).getUri(conn));
    }

    private DefaultSecurityAuthenticationProvider newProvider(boolean useHeaderSecurityToken, boolean useSessionAuth) {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_USE_HEADER_SECURITY_TOKEN)).thenReturn(useHeaderSecurityToken);
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_USE_SESSION_AUTH)).thenReturn(useSessionAuth);
        return new DefaultSecurityAuthenticationProvider(engine);
    }
}
