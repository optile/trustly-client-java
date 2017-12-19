/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Trustly Group AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.trustly.api.security;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.util.io.pem.PemObject;

import com.trustly.api.commons.exceptions.TrustlyAPIException;

class KeyChain {
    private static final String TEST_TRUSTLY_PUBLIC_KEY_PATH = "/keys/test_trustly_public.pem";
    private static final String LIVE_TRUSTLY_PUBLIC_KEY_PATH = "/keys/trustly_public.pem";

    private PrivateKey merchantPrivateKey;
    private PublicKey trustlyPublicKey;

    KeyChain(final boolean testEnvironment) {
        loadTrustlyPublicKey(testEnvironment);
    }

    /**
     * Loads the merchant private key.
     * @param privateKey private key as String
     * @param password Password for the private key. "" or null if key requires no password.
     * @throws KeyException if key failed to load. For example if the path is incorrect.
     */
    void loadMerchantPrivateKey(String privateKey, final String password) throws KeyException {
        try {
            // Add private key header and footer
        	privateKey = "-----BEGIN RSA PRIVATE KEY-----\n" + privateKey +
        	    "\n-----END RSA PRIVATE KEY-----";

            final PEMParser pemParser = new PEMParser(new StringReader(privateKey));
            final Object object = pemParser.readObject();

            final PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
            final JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

            final KeyPair kp;
            if (object instanceof PEMEncryptedKeyPair) { // Password required
                kp = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv));
            } else {
                kp = converter.getKeyPair((PEMKeyPair) object);
            }

            merchantPrivateKey = kp.getPrivate();
        }
        catch (final IOException e) {
            throw new KeyException("Failed to load private key", e);
        }
    }

    /**
     * Loads the Trustly public key.
     * @param testEnvironment whether to load the key for test environment or not.
     */
    private void loadTrustlyPublicKey(final boolean testEnvironment) {
        try {
            BufferedReader br;
            if (testEnvironment) {
            	br =  new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(TEST_TRUSTLY_PUBLIC_KEY_PATH)));
            }
            else {
            	br =  new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(LIVE_TRUSTLY_PUBLIC_KEY_PATH)));
            }

            final PEMParser pemParser = new PEMParser(br);
            final PemObject object = pemParser.readPemObject();

            final JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());

            final byte[] encoded = object.getContent();
            final SubjectPublicKeyInfo subjectPublicKeyInfo = new SubjectPublicKeyInfo(
                    ASN1Sequence.getInstance(encoded));

            trustlyPublicKey = converter.getPublicKey(subjectPublicKeyInfo);
        }
        catch (final IOException e) {
            throw new TrustlyAPIException("Failed to load Trustly public key", e);
        }
    }

    PrivateKey getMerchantPrivateKey() {
        return merchantPrivateKey;
    }

    PublicKey getTrustlyPublicKey() {
        return trustlyPublicKey;
    }
}
