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
package org.jumpmind.security;

public class SecurityConstants {
    public static final String SYSPROP_KEYSTORE_TYPE = "sym.keystore.type";
    public static final String SYSPROP_KEYSTORE_CERT_ALIAS = "sym.keystore.ssl.cert.alias";
    public static final String SYSPROP_KEYSTORE = "sym.keystore.file";
    public static final String SYSPROP_TRUSTSTORE = "javax.net.ssl.trustStore";
    public static final String SYSPROP_TRUSTSTORE_TYPE = "javax.net.ssl.trustStoreType";
    public static final String SYSPROP_TRUSTSTORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    public static final String SYSPROP_KEYSTORE_PASSWORD = "javax.net.ssl.keyStorePassword";
    public static final String SYSPROP_SSL_IGNORE_PROTOCOLS = "symmetric.ssl.ignore.protocols";
    public static final String SYSPROP_SSL_IGNORE_CIPHERS = "symmetric.ssl.ignore.ciphers";
    public static final String SYSPROP_KEY_MANAGER_FACTORY_ALGORITHM = "sym.key.manager.factory.algorithm";
    public final static String CLASS_NAME_SECURITY_SERVICE = "security.service.class.name";
    public static final String PREFIX_ENC = "enc:";
    public static final String PREFIX_OBF = "obf:";
    public static final String PREFIX_KEYSTORE_STORAGE = "ks:";
    public static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static final String[] CIPHERS = new String[] { "AES/GCM/PKCS5Padding", "AES/GCM/PKCS5Padding",
            "DESede/ECB/PKCS5Padding", "DES/ECB/PKCS5Padding" };
    public static final String[] KEYSPECS = new String[] { "AES", "AES", "DESede", "DES" };
    public static final int[] BYTESIZES = new int[] { 32, 16, 25, 8 };
    public static final int ITERATION_COUNT = 3;
    public static final String CHARSET = "UTF8";
    public static final String KEYSTORE_PASSWORD = "changeit";
    public static final String KEYSTORE_TYPE = "JCEKS";
    public static final String KEYSTORE_TYPE_PKCS12 = "PKCS12";
    public static final String KEYSTORE_TYPE_JKS = "JKS";
    public static final byte[] SALT = { (byte) 0x01, (byte) 0x03, (byte) 0x05, (byte) 0x07, (byte) 0xA2,
            (byte) 0xB4, (byte) 0xC6, (byte) 0xD8 };
    public static final String ALIAS_SYM_PRIVATE_KEY = "sym";
    public static final String ALIAS_SYM_SECRET_KEY = "sym.secret";
    public static final String ALIAS_SAML_PRIVATE_KEY = "saml";
    public static final String EMBEDDED_WEBSERVER_DEFAULT_ROLE = "symmetric";
    public static final String AZURE_KEYVAULT_URI = "azure.keyvault.uri";
    public static final String AZURE_CREDENTIAL_BUILDER_CLASSNAME = "azure.credential.builder.classname";
}