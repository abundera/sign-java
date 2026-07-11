package ai.abundera.sign.model;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/verify} — public document verification by verification
 * id ({@code vrf_...}) or SHA-256 hash. Works without authentication;
 * authenticated calls may see the fuller disclosure tier.
 */
public record VerifyResult(
        boolean verified,
        VerifyEnvelopeSummary envelope,
        VerifyTemplateSummary template,
        String verifyId,
        String lookupMethod,
        List<Map<String, Object>> signers,
        List<Map<String, Object>> auditTrail,
        Map<String, Object> integrity) {
}
