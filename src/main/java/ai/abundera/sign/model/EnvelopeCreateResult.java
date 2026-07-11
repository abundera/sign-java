package ai.abundera.sign.model;

import java.util.List;
import java.util.Map;

/** {@code POST /api/v1/envelopes}. */
public record EnvelopeCreateResult(
        String id,
        String status,
        String templateId,
        List<Map<String, Object>> signers,
        String expiresAt,
        Integer retentionYears,
        String callbackSecret,
        Boolean testMode,
        Boolean embedded,
        Boolean inPerson,
        List<CcRecipient> cc) {
}
