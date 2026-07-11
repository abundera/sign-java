package ai.abundera.sign.model;

import java.util.List;

/** {@code GET /api/v1/envelopes/{id}/audit}. */
public record AuditTrail(
        String envelopeId,
        boolean chainValid,
        List<String> chainErrors,
        int eventCount,
        List<AuditEvent> events) {
}
