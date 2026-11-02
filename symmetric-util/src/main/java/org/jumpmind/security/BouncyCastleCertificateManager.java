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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages certificates in the local keystore, using Bouncy Castle to generate self-signed certificates and to read encrypted PEM keys. Trusted certificates are
 * exported from the truststore of the {@link ITlsMaterialProvider}.
 */
public class BouncyCastleCertificateManager implements ICertificateManager {
    private static final int RSA_KEY_SIZE = 2048;
    private static final long MILLIS_PER_DAY = 86400000L;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final IKeyStoreRepository keyStoreRepository;
    private final ITlsMaterialProvider tlsMaterialProvider;

    public BouncyCastleCertificateManager(IKeyStoreRepository keyStoreRepository, ITlsMaterialProvider tlsMaterialProvider) {
        this.keyStoreRepository = keyStoreRepository;
        this.tlsMaterialProvider = tlsMaterialProvider;
    }

    @Override
    public synchronized void installDefaultSslCert(String host) {
        installDefaultSslCert(host, getSslCertAlias(), SecurityConstants.DEFAULT_CERT_LIFETIME_IN_DAYS);
    }

    @Override
    public synchronized void installDefaultSamlSslCert(String host, int lifetimeInDays) {
        synchronized (BouncyCastleCertificateManager.class) {
            installDefaultSslCert(host, SecurityConstants.ALIAS_SAML_PRIVATE_KEY, lifetimeInDays);
        }
    }

    private void installDefaultSslCert(String host, String alias, int lifetimeInDays) {
        try {
            KeyStore keyStore = keyStoreRepository.load();
            KeyStore.ProtectionParameter param = getProtectionParameter();
            if (keyStore.getEntry(alias, param) == null) {
                new BouncyCastleHelper().checkProviderInstalled();
                PrivateKeyEntry privateEntry = createDefaultSslCert(host, lifetimeInDays);
                String subject = ((X509Certificate) privateEntry.getCertificate()).getSubjectX500Principal().getName();
                if (alias.equals(SecurityConstants.ALIAS_SAML_PRIVATE_KEY)) {
                    log.info("Installing a default SSL certificate for SAML: {}", subject);
                } else {
                    log.info("Installing a default SSL certificate: {}", subject);
                }
                keyStore.setEntry(alias, privateEntry, param);
                keyStoreRepository.save(keyStore);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PrivateKeyEntry createDefaultSslCert(String host) {
        return createDefaultSslCert(host, SecurityConstants.DEFAULT_CERT_LIFETIME_IN_DAYS);
    }

    private PrivateKeyEntry createDefaultSslCert(String host, int lifetimeInDays) {
        new BouncyCastleHelper().checkProviderInstalled();
        try {
            KeyPair pair = generateRSAKeyPair();
            X509Certificate cert = generateV1Certificate(host, pair, lifetimeInDays);
            return new PrivateKeyEntry(pair.getPrivate(), new X509Certificate[] { cert });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    KeyPair generateRSAKeyPair() throws Exception {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpGen.initialize(RSA_KEY_SIZE, new SecureRandom());
        return kpGen.generateKeyPair();
    }

    /**
     * Bouncy Castle library is needed for signing a public key to generate a certificate
     */
    X509Certificate generateV1Certificate(String host, KeyPair pair, int lifetimeInDays) throws Exception {
        host = host == null || host.equals("0.0.0.0") ? AppUtils.getHostName() : host;
        String certString = String.format("CN=%s, OU=SymmetricDS, O=JumpMind", host);
        SubjectPublicKeyInfo publicKeyInfo = new BouncyCastleHelper().getInstance(pair.getPublic());
        if (lifetimeInDays < 1) {
            log.warn("Invalid lifetime for certificate: {} days. Setting lifetime to 25 years.", lifetimeInDays);
            lifetimeInDays = SecurityConstants.DEFAULT_CERT_LIFETIME_IN_DAYS;
        }
        X509v1CertificateBuilder builder = new X509v1CertificateBuilder(new X500Name(certString), BigInteger.valueOf(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() - MILLIS_PER_DAY), new Date(System.currentTimeMillis() + (lifetimeInDays * MILLIS_PER_DAY)),
                new X500Name(certString), publicKeyInfo);
        AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WithRSAEncryption");
        AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
        ContentSigner signer = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(new BouncyCastleHelper().createKey(pair.getPrivate()));
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    @Override
    public synchronized void installSslCert(PrivateKeyEntry entry) {
        installSslCert(entry, getSslCertAlias());
    }

    @Override
    public void installSslCert(PrivateKeyEntry entry, String alias) {
        try {
            new BouncyCastleHelper().checkProviderInstalled();
            KeyStore keyStore = keyStoreRepository.load();
            keyStore.setEntry(alias, entry, getProtectionParameter());
            log.info("Installing SSL certificate: {}", ((X509Certificate) entry.getCertificate()).getSubjectX500Principal().getName());
            keyStoreRepository.save(keyStore);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized PrivateKeyEntry createSslCert(byte[] content, String fileType, String alias, String password) {
        return (PrivateKeyEntry) createSslCert(content, fileType, alias, password, true);
    }

    @Override
    public synchronized TrustedCertificateEntry createTrustedCert(byte[] content, String fileType, String alias, String password) {
        return (TrustedCertificateEntry) createSslCert(content, fileType, alias, password, false);
    }

    Entry createSslCert(byte[] content, String fileType, String alias, String password, boolean isKeyEntry) {
        Entry entry = null;
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(content);
            PrivateKey key = null;
            Certificate[] chain = null;
            if (fileType.equalsIgnoreCase("pfx") || fileType.equalsIgnoreCase("p12")) {
                KeyStore store = KeyStore.getInstance("PKCS12");
                char[] passchar = password != null ? password.toCharArray() : null;
                store.load(is, passchar);
                List<String> aliases = Collections.list(store.aliases());
                if (alias == null && aliases.size() == 1) {
                    alias = aliases.get(0);
                } else if (alias != null && !store.containsAlias(alias)) {
                    throw new KeystoreAliasException("Entry for alias " + alias + " does not exist", aliases);
                } else if (alias == null) {
                    throw new KeystoreAliasException("Alias must be specified when keystore contains multiple entries", aliases);
                }
                if (isKeyEntry) {
                    chain = store.getCertificateChain(alias);
                    if (chain == null) {
                        throw new UnrecoverableKeyException();
                    }
                    key = (PrivateKey) store.getKey(alias, passchar);
                } else {
                    chain = new Certificate[1];
                    chain[0] = store.getCertificate(alias);
                }
            } else if (fileType.equalsIgnoreCase("pem") || fileType.equalsIgnoreCase("crt") || fileType.equalsIgnoreCase("cer")) {
                List<Certificate> certs = new ArrayList<Certificate>();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("BEGIN CERTIFICATE")) {
                        CertificateFactory factory = CertificateFactory.getInstance("X.509");
                        certs.add(factory.generateCertificate(new ByteArrayInputStream(readPemBytes(reader))));
                    } else if (line.contains("BEGIN PRIVATE KEY")) {
                        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(readPemBytes(reader));
                        key = KeyFactory.getInstance("RSA").generatePrivate(spec);
                    } else if (line.contains("BEGIN RSA PRIVATE KEY")) {
                        RSAPrivateKey rsaPrivKey = RSAPrivateKey.getInstance(ASN1Primitive.fromByteArray(readPemBytes(reader)));
                        RSAPrivateKeySpec rsaPrivKeySpec = new RSAPrivateKeySpec(rsaPrivKey.getModulus(), rsaPrivKey.getPrivateExponent());
                        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                        key = keyFactory.generatePrivate(rsaPrivKeySpec);
                    } else if (line.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
                        if (password != null) {
                            new BouncyCastleHelper().checkProviderInstalled();
                            InputDecryptorProvider inputDecryptorProvider = new JcePKCSPBEInputDecryptorProviderBuilder()
                                    .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(password.toCharArray());
                            PKCS8EncryptedPrivateKeyInfo pkInfo = new PKCS8EncryptedPrivateKeyInfo(readPemBytes(reader));
                            PrivateKeyInfo info = pkInfo.decryptPrivateKeyInfo(inputDecryptorProvider);
                            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                            key = converter.getPrivateKey(info);
                        } else {
                            throw new UnrecoverableKeyException();
                        }
                    }
                }
                chain = certs.toArray(new X509Certificate[certs.size()]);
            } else {
                throw new RuntimeException("Unknown TLS certificate format");
            }
            if (chain == null || chain.length == 0) {
                throw new RuntimeException("Missing TLS certificate");
            }
            if (isKeyEntry && key == null) {
                throw new RuntimeException("Missing TLS private key");
            }
            if (isKeyEntry) {
                entry = new PrivateKeyEntry(key, chain);
            } else {
                entry = new TrustedCertificateEntry(chain[0]);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return entry;
    }

    byte[] readPemBytes(BufferedReader reader) throws IOException, CertificateException {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = null;
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("----")) {
                bytes = Base64.decodeBase64(sb.toString());
                break;
            }
            sb.append(line);
        }
        return bytes;
    }

    @Override
    public synchronized X509Certificate getCurrentSslCert() {
        return getCurrentSslCert(getSslCertAlias());
    }

    @Override
    public synchronized X509Certificate getCurrentSamlSslCert() {
        return getCurrentSslCert(SecurityConstants.ALIAS_SAML_PRIVATE_KEY);
    }

    private synchronized X509Certificate getCurrentSslCert(String alias) {
        try {
            KeyStore keyStore = keyStoreRepository.load();
            if (keyStore.getEntry(alias, getProtectionParameter()) instanceof PrivateKeyEntry) {
                return (X509Certificate) keyStore.getCertificate(alias);
            }
            return null;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized String exportCurrentSslCert(boolean includePrivateKey) {
        try {
            KeyStore keyStore = keyStoreRepository.load();
            String alias = getSslCertAlias();
            Entry entry = keyStore.getEntry(alias, getProtectionParameter());
            if (!(entry instanceof PrivateKeyEntry)) {
                return null;
            }
            StringWriter writer = new StringWriter();
            writePem(writer, "CERTIFICATE", keyStore.getCertificate(alias).getEncoded());
            if (includePrivateKey) {
                writePem(writer, "PRIVATE KEY", ((PrivateKeyEntry) entry).getPrivateKey().getEncoded());
            }
            return writer.toString();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String exportTrustedCert(String alias) {
        try {
            KeyStore trustStore = tlsMaterialProvider.getTrustStore();
            if (!(trustStore.getEntry(alias, null) instanceof TrustedCertificateEntry)) {
                return null;
            }
            StringWriter writer = new StringWriter();
            writePem(writer, "CERTIFICATE", trustStore.getCertificate(alias).getEncoded());
            return writer.toString();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writePem(StringWriter writer, String type, byte[] encoded) {
        String nl = System.getProperty("line.separator");
        writer.write("-----BEGIN " + type + "-----" + nl);
        writer.write(new String(Base64.encodeBase64(encoded, true), Charset.defaultCharset()));
        writer.write("-----END " + type + "-----" + nl);
    }

    @Override
    public void deleteCertificate(String alias) throws Exception {
        KeyStore keyStore = keyStoreRepository.load();
        try {
            keyStore.deleteEntry(alias);
        } catch (KeyStoreException e) {
            log.info("Unable to delete keystore entry with name {}", alias);
        }
        keyStoreRepository.save(keyStore);
    }

    @Override
    public boolean supportsExportCertificate() {
        return true;
    }

    @Override
    public boolean supportsImportCertificate() {
        return true;
    }

    @Override
    public boolean supportsBackupCertificate() {
        return true;
    }

    @Override
    public boolean supportsGenerateSelfSignedCertificate() {
        return true;
    }

    private String getSslCertAlias() {
        return System.getProperty(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS, SecurityConstants.ALIAS_SYM_PRIVATE_KEY);
    }

    private KeyStore.ProtectionParameter getProtectionParameter() {
        return new KeyStore.PasswordProtection(keyStoreRepository.getPassword().toCharArray());
    }
}
