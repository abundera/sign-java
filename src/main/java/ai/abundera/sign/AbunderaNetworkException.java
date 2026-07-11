package ai.abundera.sign;

/**
 * Raised when a request fails at the transport level (connection reset, DNS
 * failure, timeout, ...) and the retry budget has been exhausted.
 *
 * <p>The Ruby reference re-raises the raw {@code StandardError}; Java's
 * {@link java.io.IOException} is checked, so this SDK wraps it in an
 * unchecked exception instead of forcing every call site to declare
 * {@code throws IOException}. The original exception is always available via
 * {@link #getCause()}.
 */
public final class AbunderaNetworkException extends AbunderaSignException {

    private static final long serialVersionUID = 1L;

    public AbunderaNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
