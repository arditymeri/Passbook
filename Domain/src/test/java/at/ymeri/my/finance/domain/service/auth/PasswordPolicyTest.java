package at.ymeri.my.finance.domain.service.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Quickstart scenario 8. The rule is one line; what these tests pin is <em>where</em> it applies.
 */
class PasswordPolicyTest {

    @Test
    void aPasswordBelowTheMinimumIsRejected() {
        assertThatThrownBy(() -> PasswordPolicy.requireAcceptable("hunter2"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void aPasswordAtTheMinimumIsAccepted() {
        assertThatCode(() -> PasswordPolicy.requireAcceptable("123456789012"))
                .doesNotThrowAnyException();
    }

    @Test
    void aLongerPasswordIsAccepted() {
        assertThatCode(() -> PasswordPolicy.requireAcceptable("correct horse battery staple"))
                .doesNotThrowAnyException();
    }

    @Test
    void theRejectionSaysWhatIsRequired() {
        // FR-011. An operator meeting this rule for the first time is standing at a setup form with
        // no way to guess the number; "invalid password" would leave them trying lengths.
        assertThatThrownBy(() -> PasswordPolicy.requireAcceptable("short"))
                .hasMessageContaining(String.valueOf(PasswordPolicy.MINIMUM_LENGTH));
    }

    @Test
    void anAbsentPasswordIsRejectedRatherThanCrashing() {
        assertThatThrownBy(() -> PasswordPolicy.requireAcceptable(null))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void whitespaceIsNotStrippedBeforeCounting() {
        // A passphrase is mostly spaces by character count, and trimming or collapsing them would
        // quietly reject one that is perfectly good. The rule counts what the operator typed.
        assertThat("a b c d e f g".length()).isGreaterThanOrEqualTo(PasswordPolicy.MINIMUM_LENGTH);
        assertThatCode(() -> PasswordPolicy.requireAcceptable("a b c d e f g"))
                .doesNotThrowAnyException();
    }
}
