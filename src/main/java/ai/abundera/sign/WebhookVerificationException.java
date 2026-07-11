package ai.abundera.sign;

/**
 * Raised when a Standard Webhooks signature cannot be verified: tampered
 * body, unknown key, timestamp skew, malformed input, or missing key
 * material. Never carries the secret/public key itself in the message.
 */
public final class WebhookVerificationException extends AbunderaSignException {

    private static final long serialVersionUID = 1L;

    public WebhookVerificationException(String message) {
        super(message);
    }

    public WebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
