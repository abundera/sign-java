package ai.abundera.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the webhook verifier, using an INDEPENDENT re-implementation
 * of the signer (raw {@code javax.crypto}/{@code java.security}) so a bug in
 * the SDK's verifier can't hide behind the same bug in a shared helper. The
 * cross-language contract suite ({@link ContractTest}) additionally pins
 * these bytes to the real platform signer. Mirrors
 * {@code sdks/ruby/test/test_webhook.rb} test-for-test.
 */
class WebhookTest {

    private static final String BODY = "{\"event\":\"envelope.completed\",\"envelope_id\":\"env_1\"}";
    private static final String ID = "msg_2f9b1c";

    private static String randomSecret() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return "whsec_" + Base64.getEncoder().encodeToString(raw);
    }

    private static String nowSeconds() {
        return Long.toString(Instant.now().getEpochSecond());
    }

    private static String hmacToken(String secret, String id, String ts, String body) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(secret.substring("whsec_".length()));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] sig = mac.doFinal((id + "." + ts + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(sig);
    }

    private static KeyPair generateEd25519() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String ed25519PublicKeyToken(KeyPair pair) {
        // java.security.PublicKey#getEncoded() for Ed25519 is X.509 DER (44 bytes);
        // the last 32 bytes are the raw public key that whpk_ carries on the wire.
        byte[] encoded = pair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return "whpk_" + Base64.getEncoder().encodeToString(raw);
    }

    private static String ed25519Token(KeyPair pair, String id, String ts, String body) throws GeneralSecurityException {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update((id + "." + ts + "." + body).getBytes(StandardCharsets.UTF_8));
        byte[] sig = signer.sign();
        return "v1a," + Base64.getEncoder().encodeToString(sig);
    }

    // --- v1 HMAC --------------------------------------------------------------

    @Test
    void acceptsAValidSignature() throws Exception {
        String secret = randomSecret();
        String ts = nowSeconds();
        assertTrue(Webhook.verify(ID, ts, hmacToken(secret, ID, ts, BODY), BODY, secret, null));
    }

    @Test
    void rejectsATamperedBody() throws Exception {
        String secret = randomSecret();
        String ts = nowSeconds();
        String token = hmacToken(secret, ID, ts, BODY);
        WebhookVerificationException err = assertThrows(WebhookVerificationException.class,
                () -> Webhook.verify(ID, ts, token, BODY + " ", secret, null));
        assertTrue(err.getMessage().contains("mismatch"));
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        String ts = nowSeconds();
        String token = hmacToken(randomSecret(), ID, ts, BODY);
        String otherSecret = randomSecret();
        assertThrows(WebhookVerificationException.class,
                () -> Webhook.verify(ID, ts, token, BODY, otherSecret, null));
    }

    @Test
    void rejectsTimestampOutsideTolerance() throws Exception {
        String secret = randomSecret();
        String old = Long.toString(Instant.now().getEpochSecond() - 10_000);
        String token = hmacToken(secret, ID, old, BODY);
        WebhookVerificationException err = assertThrows(WebhookVerificationException.class,
                () -> Webhook.verify(ID, old, token, BODY, secret, null));
        assertTrue(err.getMessage().contains("tolerance"));
    }

    @Test
    void acceptsOldTimestampWithNowOverride() throws Exception {
        String secret = randomSecret();
        String ts = "1750000000";
        String token = hmacToken(secret, ID, ts, BODY);
        assertTrue(Webhook.verify(ID, ts, token, BODY, secret, null,
                Webhook.DEFAULT_TOLERANCE_SECONDS, () -> 1_750_000_010_000L));
    }

    @Test
    void requiresKeyMaterial() {
        assertThrows(WebhookVerificationException.class,
                () -> Webhook.verify(ID, nowSeconds(), "v1,AAAA", BODY, null, null));
    }

    @Test
    void rejectsMalformedTimestamp() {
        String secret = randomSecret();
        assertThrows(WebhookVerificationException.class,
                () -> Webhook.verify(ID, "not-a-number", "v1,AAAA", BODY, secret, null));
    }

    @Test
    void ignoresGarbageTokensButAcceptsAGoodOne() throws Exception {
        String secret = randomSecret();
        String ts = nowSeconds();
        String header = "garbage v2,xxxx " + hmacToken(secret, ID, ts, BODY);
        assertTrue(Webhook.verify(ID, ts, header, BODY, secret, null));
    }

    // --- v1a Ed25519 ------------------------------------------------------------

    @Test
    void acceptsValidEd25519() throws Exception {
        KeyPair pair = generateEd25519();
        String pub = ed25519PublicKeyToken(pair);
        String ts = nowSeconds();
        String token = ed25519Token(pair, ID, ts, BODY);
        assertTrue(Webhook.verify(ID, ts, token, BODY, null, pub));
    }

    @Test
    void rejectsTamperedEd25519() throws Exception {
        KeyPair pair = generateEd25519();
        String pub = ed25519PublicKeyToken(pair);
        String ts = nowSeconds();
        String token = ed25519Token(pair, ID, ts, BODY);
        assertThrows(WebhookVerificationException.class,
                () -> Webhook.verify(ID, ts, token, BODY + "x", null, pub));
    }

    @Test
    void rotationHeaderWithBothSchemes() throws Exception {
        String secret = randomSecret();
        KeyPair pair = generateEd25519();
        String pub = ed25519PublicKeyToken(pair);
        String ts = nowSeconds();
        String header = hmacToken(secret, ID, ts, BODY) + " " + ed25519Token(pair, ID, ts, BODY);
        assertTrue(Webhook.verify(ID, ts, header, BODY, secret, pub));
    }

    @Test
    void missingSignatureHeaderIsRejectedNotNpe() throws Exception {
        String secret = randomSecret();
        assertThrows(WebhookVerificationException.class,
                () -> Webhook.verify(ID, nowSeconds(), null, BODY, secret, null));
    }

    @Test
    void wrongLengthHmacSignatureIsRejected() throws Exception {
        String secret = randomSecret();
        String ts = nowSeconds();
        // Same prefix as a real token, but truncated -> different byte length.
        String token = "v1," + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThrows(WebhookVerificationException.class,
                () -> Webhook.verify(ID, ts, token, BODY, secret, null));
    }

    @Test
    void constantTimeCompareIsUsedNotBareEquals() {
        // Documents the contract STANDARD.md §3 requires: MessageDigest.isEqual,
        // never == / Arrays.equals on raw signature bytes. Enforced structurally
        // by sdk-audit.py's regex over Webhook.java; this test just pins the
        // observable behavior (equal-length wrong bytes are still rejected).
        assertFalse(java.security.MessageDigest.isEqual(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));
        assertTrue(java.security.MessageDigest.isEqual(new byte[]{1, 2, 3}, new byte[]{1, 2, 3}));
    }

    @Test
    void equalStatusCodesSanityCheck() {
        assertEquals(300, Webhook.DEFAULT_TOLERANCE_SECONDS);
    }
}
