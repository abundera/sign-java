package ai.abundera.sign.model;

import java.util.Map;

/** One hash-chained audit trail entry. */
public record AuditEvent(
        String id,
        String action,
        String actor,
        String signerId,
        String ipAddress,
        Map<String, Object> metadata,
        String entryHash,
        String previousHash,
        String createdAt) {
}
