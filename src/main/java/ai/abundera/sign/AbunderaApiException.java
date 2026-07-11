package ai.abundera.sign;

/**
 * Raised on any non-2xx API response, after retries are exhausted.
 *
 * <p>Carries the v1 error envelope ({@code { ok:false, error:{ code, message
 * }, request_id }}) so callers can branch on a stable machine {@link #code()}
 * and quote {@link #requestId()} in a support ticket. Mirrors
 * {@code AbunderaSign::ApiError} in the Ruby SDK exactly.
 */
public final class AbunderaApiException extends AbunderaSignException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final String requestId;

    public AbunderaApiException(int status, String code, String message, String requestId) {
        super("[" + status + " " + code + "] " + message);
        this.status = status;
        this.code = code;
        this.requestId = requestId;
    }

    /** The HTTP status code of the failed response. */
    public int status() {
        return status;
    }

    /** The stable, machine-readable error code from the v1 error envelope. */
    public String code() {
        return code;
    }

    /** The request id echoed by the server, useful for support correlation. Never null-checked callers should use {@link java.util.Optional#ofNullable}. */
    public String requestId() {
        return requestId;
    }
}
