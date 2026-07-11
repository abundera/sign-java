package ai.abundera.sign;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.function.LongSupplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Standard Webhooks signature verification for Abundera Sign deliveries.
 *
 * <p>Every delivery carries three headers:
 * <ul>
 *   <li>{@code webhook-id} — per-event idempotency key ({@code msg_<uuid>})</li>
 *   <li>{@code webhook-timestamp} — unix seconds</li>
 *   <li>{@code webhook-signature} — space-separated {@code v1,<base64>} and/or
 *       {@code v1a,<base64>} tokens</li>
 * </ul>
 *
 * <p>Signed content is {@code "{webhook_id}.{webhook_timestamp}.{body}"}.
 * <ul>
 *   <li>{@code v1} — HMAC-SHA256 over the {@code whsec_} secret (constant-time compare)</li>
 *   <li>{@code v1a} — Ed25519 over the {@code whpk_} public key (Business+, non-repudiation)</li>
 * </ul>
 *
 * <p>Multiple tokens in one header let a secret rotate with zero downtime —
 * pass the old and new secret (or secret + public key) and any one match
 * verifies.
 *
 * <p>This class has no mutable state and every method is safe to call
 * concurrently.
 */
public final class Webhook {

    /** Default clock-skew tolerance: ±300 seconds. */
    public static final long DEFAULT_TOLERANCE_SECONDS = 300;

    static final String SECRET_PREFIX = "whsec_";
    static final String PUBKEY_PREFIX = "whpk_";

    private Webhook() {
    }

    /**
     * Verify a delivery using the default ±300s tolerance and the system
     * clock. Returns {@code true} on success; throws
     * {@link WebhookVerificationException} on any mismatch.
     */
    public static boolean verify(String webhookId, String webhookTimestamp, String webhookSignature,
                                  String body, String secret, String publicKey) {
        return verify(webhookId, webhookTimestamp, webhookSignature, body, secret, publicKey,
                DEFAULT_TOLERANCE_SECONDS, System::currentTimeMillis);
    }

    /**
     * Verify a delivery with a caller-supplied tolerance and clock — the
     * {@code now} supplier lets tests pin a fixed instant instead of racing
     * the system clock.
     *
     * @param webhookId         the {@code webhook-id} header
     * @param webhookTimestamp  the {@code webhook-timestamp} header (unix seconds, as text)
     * @param webhookSignature  the {@code webhook-signature} header
     * @param body              the exact raw request body bytes, as received (never a re-serialized copy)
     * @param secret            {@code whsec_} secret for {@code v1}, or {@code null}
     * @param publicKey         {@code whpk_} public key for {@code v1a}, or {@code null}
     * @param toleranceSeconds  max clock skew, in seconds
     * @param nowMillisSupplier supplies "now" in epoch milliseconds (injectable for tests)
     * @return {@code true} always (a failed verification throws instead of returning {@code false})
     * @throws WebhookVerificationException on any mismatch, timestamp skew, or missing key material
     */
    public static boolean verify(String webhookId, String webhookTimestamp, String webhookSignature,
                                  String body, String secret, String publicKey,
                                  long toleranceSeconds, LongSupplier nowMillisSupplier) {
        boolean hasSecret = secret != null && !secret.isEmpty();
        boolean hasPublicKey = publicKey != null && !publicKey.isEmpty();
        if (!hasSecret && !hasPublicKey) {
            throw new WebhookVerificationException("verify requires secret and/or public_key");
        }

        long ts;
        try {
            ts = Long.parseLong(webhookTimestamp.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new WebhookVerificationException("invalid webhook input: malformed webhook-timestamp");
        }
        long nowSeconds = Math.floorDiv(nowMillisSupplier.getAsLong(), 1000L);
        if (Math.abs(nowSeconds - ts) > toleranceSeconds) {
            throw new WebhookVerificationException("webhook-timestamp outside tolerance");
        }

        String signed = webhookId + "." + webhookTimestamp + "." + body;
        byte[] signedBytes = signed.getBytes(StandardCharsets.UTF_8);

        byte[] expectedHmac = hasSecret ? computeHmac(stripPrefix(secret, SECRET_PREFIX), signedBytes) : null;
        PublicKey ed25519Key = hasPublicKey ? decodeEd25519PublicKey(stripPrefix(publicKey, PUBKEY_PREFIX)) : null;

        if (webhookSignature != null) {
            for (String token : webhookSignature.trim().split("\\s+")) {
                int comma = token.indexOf(',');
                if (comma < 0) {
                    continue;
                }
                String scheme = token.substring(0, comma);
                String b64 = token.substring(comma + 1);
                if (b64.isEmpty()) {
                    continue;
                }
                byte[] sig;
                try {
                    sig = Base64.getDecoder().decode(b64);
                } catch (IllegalArgumentException e) {
                    continue; // malformed base64 -> not a valid token, keep trying
                }

                if ("v1".equals(scheme) && expectedHmac != null) {
                    if (sig.length == expectedHmac.length && MessageDigest.isEqual(sig, expectedHmac)) {
                        return true;
                    }
                } else if ("v1a".equals(scheme) && ed25519Key != null) {
                    if (verifyEd25519(ed25519Key, signedBytes, sig)) {
                        return true;
                    }
                }
            }
        }

        throw new WebhookVerificationException("webhook signature mismatch");
    }

    private static byte[] computeHmac(String base64Secret, byte[] signedBytes) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException e) {
            throw new WebhookVerificationException("invalid webhook input: malformed secret", e);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(signedBytes);
        } catch (GeneralSecurityException e) {
            throw new WebhookVerificationException("invalid webhook input: " + e.getMessage(), e);
        }
    }

    private static PublicKey decodeEd25519PublicKey(String base64PublicKey) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64PublicKey);
        } catch (IllegalArgumentException e) {
            throw new WebhookVerificationException("invalid webhook input: malformed public_key", e);
        }
        try {
            // Ed25519 raw public keys (RFC 8032, 32 bytes) need to be wrapped in an
            // X.509 SubjectPublicKeyInfo envelope for java.security.KeyFactory. The
            // envelope prefix below is the fixed 12-byte ASN.1 header for the
            // Ed25519 OID (1.3.101.112) followed by a BIT STRING of the 32-byte key.
            byte[] encoded = wrapRawEd25519PublicKey(raw);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new WebhookVerificationException("invalid webhook input: " + e.getMessage(), e);
        }
    }

    private static final byte[] ED25519_X509_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private static byte[] wrapRawEd25519PublicKey(byte[] raw) {
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key must be 32 raw bytes, got " + raw.length);
        }
        byte[] encoded = new byte[ED25519_X509_PREFIX.length + raw.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, encoded, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(raw, 0, encoded, ED25519_X509_PREFIX.length, raw.length);
        return encoded;
    }

    private static boolean verifyEd25519(PublicKey key, byte[] signedBytes, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(signedBytes);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false; // malformed signature -> not a match, keep trying
        }
    }

    private static String stripPrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }
}
