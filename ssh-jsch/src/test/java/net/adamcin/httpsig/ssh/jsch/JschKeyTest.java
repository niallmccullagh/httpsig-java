/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */

package net.adamcin.httpsig.ssh.jsch;

import com.jcraft.jsch.JSch;
import net.adamcin.commons.testing.junit.FailUtil;
import net.adamcin.httpsig.api.Authorization;
import net.adamcin.httpsig.api.Challenge;
import net.adamcin.httpsig.api.Constants;
import net.adamcin.httpsig.api.DefaultKeychain;
import net.adamcin.httpsig.api.DefaultVerifier;
import net.adamcin.httpsig.api.Keychain;
import net.adamcin.httpsig.api.RequestContent;
import net.adamcin.httpsig.api.Signer;
import net.adamcin.httpsig.ssh.jce.AuthorizedKeys;
import net.adamcin.httpsig.ssh.jce.KeyFormat;
import net.adamcin.httpsig.ssh.jce.SSHKey;
import net.adamcin.httpsig.testutil.KeyTestUtil;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JschKeyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JschKeyTest.class);

    @Test
    public void testSignature() {

        Reader reader = null;
        try {
            roundTrip(KeyFormat.SSH_RSA, "b1024", "id_rsa", null);
            roundTrip(KeyFormat.SSH_DSS, "b1024", "id_dsa", null);
            roundTrip(KeyFormat.SSH_RSA, "b2048", "id_rsa", null);
            roundTrip(KeyFormat.SSH_RSA, "b4096", "id_rsa", null);
            roundTrip(KeyFormat.SSH_DSS, "withpass", "id_dsa", "dummydummy");
            roundTrip(KeyFormat.SSH_RSA, "withpass", "id_rsa", "dummydummy");
        } catch (Exception e) {
            FailUtil.sprintFail(e);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    public void roundTrip(KeyFormat format, String parentName, String keyName, String passphrase)
            throws Exception {

        final String id = "[" + parentName + "/" + keyName + "] ";

        DefaultVerifier dverifier = new DefaultVerifier(AuthorizedKeys.newKeychain(KeyTestUtil.getPublicKeyAsFile(parentName, keyName)));
        String fingerprint = dverifier.getKeychain().currentKey().getId();

        Challenge challenge = new Challenge("myRealm", Constants.DEFAULT_HEADERS, format.getSignatureAlgorithms());

        JSch jSchSigner = new JSch();

        jSchSigner.addIdentity(KeyTestUtil.getPrivateKeyAsFile(parentName, keyName).getAbsolutePath(), passphrase);

        Keychain sprovider = JschKey.getIdentities(jSchSigner);

        assertEquals(id + "sprovider should contain only one identity", 1, sprovider.toMap(null).size());
        assertEquals(id + "fingerprints should match", fingerprint, sprovider.iterator().next().getId());

        Signer jsigner = new Signer(sprovider);
        DefaultVerifier jverifier = new DefaultVerifier(sprovider);
        RequestContent requestContent = new RequestContent.Builder().addDateNow().build();

        Signer dsigner = new Signer(new DefaultKeychain(
                        Arrays.asList(new SSHKey(format, KeyTestUtil.getKeyPairFromProperties(parentName, keyName)))));

        jsigner.rotateKeys(challenge);
        Authorization jpacket = jsigner.sign(requestContent);

        dsigner.rotateKeys(challenge);
        Authorization dpacket = dsigner.sign(requestContent);

        LOGGER.info(id + "jpacket={}, dpacket={}", KeyTestUtil.bytesToHex(jpacket.getSignatureBytes()),
                    KeyTestUtil.bytesToHex(dpacket.getSignatureBytes()));

        assertEquals(id + "jce fingerprints should match", fingerprint, dsigner.getKeychain().currentKey().getId());
        assertTrue(id + "round trip using jce identities", dverifier.verify(challenge, requestContent, dpacket));
        assertTrue(id + "round trip using JschIdentities", jverifier.verify(challenge, requestContent, jpacket));

        assertTrue(id + "round trip using jverifier + dsigner", jverifier.verify(challenge, requestContent, dpacket));
        assertTrue(id + "round trip using dverifier + jsigner", dverifier.verify(challenge, requestContent, jpacket));
    }

}
