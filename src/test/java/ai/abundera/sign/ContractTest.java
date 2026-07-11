package ai.abundera.sign;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cross-implementation contract: the shared fixtures in {@code sdks/fixtures}
 * were signed by the REAL platform signer ({@code functions/lib/webhook-delivery.js}).
 * This SDK's verifier must accept those exact bytes. If the wire format
 * drifts on either side, this goes red before a customer's webhook does.
 * Mirrors {@code sdks/ruby/test/test_contract.rb}.
 */
class ContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // sdks/java/src/test/java/.../ContractTest.java -> sdks/fixtures/webhook-signatures.json
    private static final File FIXTURE = new File("../fixtures/webhook-signatures.json");

    private JsonNode fixture;
    private long now;

    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(FIXTURE.exists(), "fixtures not generated - run: node sdks/fixtures/generate.mjs");
        fixture = MAPPER.readTree(FIXTURE);
        // Pin "now" to the fixture timestamp so the +-300s skew guard doesn't
        // reject a deliberately-fixed historical timestamp.
        now = fixture.get("webhook_timestamp").asLong();
    }

    @Test
    void everyPlatformFixtureMatchesExpectation() {
        String webhookId = fixture.get("webhook_id").asText();
        String webhookTimestamp = fixture.get("webhook_timestamp").asText();
        String defaultBody = fixture.get("body").asText();
        String secretPrefix = fixture.has("secret_prefix") ? fixture.get("secret_prefix").asText() : "";
        String pubkeyPrefix = fixture.has("pubkey_prefix") ? fixture.get("pubkey_prefix").asText() : "";
        String rawSecret = fixture.get("secret").asText();
        String rawPublicKey = fixture.get("public_key").asText();

        Iterator<JsonNode> cases = fixture.get("cases").elements();
        while (cases.hasNext()) {
            JsonNode c = cases.next();
            String name = c.get("name").asText();
            String body = c.has("body_override") ? c.get("body_override").asText() : defaultBody;
            String signature = c.get("webhook_signature").asText();
            String verifiesWith = c.get("verifies_with").asText();
            boolean expect = c.get("expect").asBoolean();

            // Prepend the canonical prefix so this also exercises prefix-stripping
            // against real platform-signed bytes.
            String secret = verifiesWith.contains("secret") ? secretPrefix + rawSecret : null;
            String publicKey = verifiesWith.contains("public_key") ? pubkeyPrefix + rawPublicKey : null;

            if (expect) {
                assertTrue(
                        Webhook.verify(webhookId, webhookTimestamp, signature, body, secret, publicKey,
                                Webhook.DEFAULT_TOLERANCE_SECONDS, () -> now * 1000L),
                        "expected " + name + " to verify");
            } else {
                assertThrows(WebhookVerificationException.class,
                        () -> Webhook.verify(webhookId, webhookTimestamp, signature, body, secret, publicKey,
                                Webhook.DEFAULT_TOLERANCE_SECONDS, () -> now * 1000L),
                        "expected " + name + " to be rejected");
            }
        }
    }
}
