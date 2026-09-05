package at.ymeri.my.finance.domain.service.auth;

/**
 * What counts as an acceptable password, and — the part worth being careful about — when the
 * question is asked at all.
 *
 * <p><strong>Applied where a password is set, never where one is used.</strong> Setup and
 * change-password enforce this; authentication does not, and must not. Two reasons, and both
 * matter. Rejecting a short password at login would tell whoever submitted it that the stored
 * password is short, which is a gift to someone guessing. And it would lock every operator whose
 * account predates this rule out of their own financial history at the moment they upgraded —
 * turning a hardening change into data loss, which is worse than the exposure it was fixing.
 *
 * <p><strong>Twelve rather than the more usual eight.</strong> NIST's floor assumes one account
 * among many, behind other controls, with a reset path when it goes wrong. Here it is a single
 * credential, chosen once, protecting an entire ledger, on a URL strangers can reach, with no
 * password-reset email behind it. The same number is stated in the OpenAPI schema so the constraint
 * is discoverable and not merely enforced; it lives here as well so the rule is business logic with
 * a test, rather than something that exists only in a generated artifact.
 */
public final class PasswordPolicy {

    public static final int MINIMUM_LENGTH = 12;

    private PasswordPolicy() {
    }

    /**
     * Throws if the password may not be used as a new password.
     *
     * <p>Length is counted as typed. Trimming or collapsing whitespace first would reject
     * passphrases, which are mostly spaces and are exactly what an operator should be encouraged
     * to use here.
     */
    public static void requireAcceptable(String password) {
        if (password == null || password.length() < MINIMUM_LENGTH) {
            throw new WeakPasswordException(
                    "Password must be at least " + MINIMUM_LENGTH + " characters");
        }
    }
}
