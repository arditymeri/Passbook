package at.ymeri.my.finance.domain.service.auth;

/**
 * A password was rejected for being too short to be worth guarding.
 *
 * <p><strong>Why this is not just an {@link IllegalArgumentException}.</strong> The auth controller
 * already maps that to <em>401 Unauthorized</em>, because the one place it was previously thrown is
 * "your current password is wrong". A weak new password reusing it would answer "unauthorized" to
 * an operator who is perfectly well authorized and simply chose a short password — and would do so
 * while their session is valid, which reads as being logged out. It extends
 * {@code IllegalArgumentException} because it genuinely is one, so anything that does not know
 * about it still behaves sensibly; the controller handles it specifically to answer 400 instead.
 */
public class WeakPasswordException extends IllegalArgumentException {

    public WeakPasswordException(String message) {
        super(message);
    }
}
