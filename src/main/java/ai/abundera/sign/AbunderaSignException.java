package ai.abundera.sign;

/**
 * Base class for every unchecked exception this SDK throws.
 *
 * <p>Unlike the Ruby reference (where {@code StandardError} is effectively
 * unchecked by convention), Java distinguishes checked and unchecked
 * exceptions explicitly. This SDK throws only unchecked exceptions so callers
 * are not forced to wrap every API call in a {@code try/catch} — the same
 * ergonomic default the TypeScript, Python, and Go SDKs give their callers.
 */
public class AbunderaSignException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AbunderaSignException(String message) {
        super(message);
    }

    public AbunderaSignException(String message, Throwable cause) {
        super(message, cause);
    }
}
