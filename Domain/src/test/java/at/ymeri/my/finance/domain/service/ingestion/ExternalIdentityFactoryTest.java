package at.ymeri.my.finance.domain.service.ingestion;

import at.ymeri.my.finance.domain.data.ingestion.TransactionDirection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The identity rules, tested where they can be tested: plain JUnit, no context, no database.
 *
 * <p>This is the highest-value test class in feature 022. Everything downstream — the unique index,
 * the ingestion endpoint, the preview — is machinery around the answer this class gives, and the way
 * it fails is quiet: a history that merged two transactions still looks entirely plausible.
 */
class ExternalIdentityFactoryTest {

    private static final String ACCOUNT = "acct-1";
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
    private static final BigDecimal AMOUNT = new BigDecimal("3.40");
    private static final String DESCRIPTION = "COFFEE BAR";

    private String identity(String accountId, LocalDate date, BigDecimal amount,
                            String description, TransactionDirection direction) {
        return ExternalIdentityFactory.identityFor(accountId, date, amount, description, direction, null);
    }

    private String baseline() {
        return identity(ACCOUNT, DATE, AMOUNT, DESCRIPTION, TransactionDirection.BILL);
    }

    @Nested
    class Determinism {

        @Test
        void sameInputsAlwaysGiveTheSameIdentity() {
            assertThat(baseline()).isEqualTo(baseline());
        }

        @Test
        void identityIsShortEnoughToStore() {
            // The column is varchar(255); a SHA-256 hex digest plus an occurrence suffix must fit
            // with room to spare, or ingestion would fail at the database rather than here.
            assertThat(baseline()).hasSizeLessThan(200);
        }
    }

    @Nested
    class EachFieldMatters {

        @Test
        void differentAccountGivesDifferentIdentity() {
            // FR-006: the same statement row imported into two accounts is two transactions.
            assertThat(identity("acct-2", DATE, AMOUNT, DESCRIPTION, TransactionDirection.BILL))
                    .isNotEqualTo(baseline());
        }

        @Test
        void differentDateGivesDifferentIdentity() {
            assertThat(identity(ACCOUNT, DATE.plusDays(1), AMOUNT, DESCRIPTION, TransactionDirection.BILL))
                    .isNotEqualTo(baseline());
        }

        @Test
        void differentAmountGivesDifferentIdentity() {
            assertThat(identity(ACCOUNT, DATE, new BigDecimal("3.41"), DESCRIPTION, TransactionDirection.BILL))
                    .isNotEqualTo(baseline());
        }

        @Test
        void differentDescriptionGivesDifferentIdentity() {
            assertThat(identity(ACCOUNT, DATE, AMOUNT, "TEA HOUSE", TransactionDirection.BILL))
                    .isNotEqualTo(baseline());
        }

        @Test
        void differentDirectionGivesDifferentIdentity() {
            // The one that is easy to leave out and expensive to get wrong: both tables store
            // positive amounts, so without direction a refund collides with the charge it reverses —
            // and a refund usually carries the merchant's own description.
            assertThat(identity(ACCOUNT, DATE, AMOUNT, DESCRIPTION, TransactionDirection.INCOME))
                    .isNotEqualTo(baseline());
        }

        @Test
        void fieldBoundariesCannotBeConfused() {
            // Without a separator that cannot appear in a field, "AB" + "C" and "A" + "BC" would
            // canonicalise identically and two unrelated transactions would merge.
            String left = identity(ACCOUNT, DATE, AMOUNT, "AB", TransactionDirection.BILL);
            String right = identity(ACCOUNT + "AB", DATE, AMOUNT, "", TransactionDirection.BILL);
            assertThat(left).isNotEqualTo(right);
        }
    }

    @Nested
    class Normalisation {

        @Test
        void trailingZerosInTheAmountDoNotChangeIdentity() {
            // A statement is free to write 3.4 or 3.40 for the same money.
            assertThat(identity(ACCOUNT, DATE, new BigDecimal("3.4"), DESCRIPTION, TransactionDirection.BILL))
                    .isEqualTo(baseline());
        }

        @Test
        void surroundingAndRepeatedWhitespaceDoNotChangeIdentity() {
            assertThat(identity(ACCOUNT, DATE, AMOUNT, "  COFFEE   BAR ", TransactionDirection.BILL))
                    .isEqualTo(baseline());
        }

        @Test
        void caseIsSignificant() {
            // Deliberate, and the opposite of what a "be forgiving" instinct suggests. Folding case
            // would merge two genuinely different merchant strings some of the time. A spurious
            // duplicate is visible in a balance; a silent merge is money that never existed.
            assertThat(identity(ACCOUNT, DATE, AMOUNT, "coffee bar", TransactionDirection.BILL))
                    .isNotEqualTo(baseline());
        }

        @Test
        void anEmptyDescriptionStillYieldsAStableIdentity() {
            String first = identity(ACCOUNT, DATE, AMOUNT, "", TransactionDirection.BILL);
            String second = identity(ACCOUNT, DATE, AMOUNT, null, TransactionDirection.BILL);
            assertThat(first).isNotBlank().isEqualTo(second);
        }
    }

    @Nested
    class SourceSuppliedIdentifier {

        @Test
        void theBanksOwnIdentifierWinsOverTheDerivedForm() {
            String derived = baseline();
            String supplied = ExternalIdentityFactory.identityFor(
                    ACCOUNT, DATE, AMOUNT, DESCRIPTION, TransactionDirection.BILL, "TXN-99887766");

            assertThat(supplied).isEqualTo("TXN-99887766").isNotEqualTo(derived);
        }

        @Test
        void aBlankIdentifierFallsBackToTheDerivedForm() {
            assertThat(ExternalIdentityFactory.identityFor(
                    ACCOUNT, DATE, AMOUNT, DESCRIPTION, TransactionDirection.BILL, "   "))
                    .isEqualTo(baseline());
        }

        @Test
        void aSuppliedIdentifierIsTrimmed() {
            assertThat(ExternalIdentityFactory.identityFor(
                    ACCOUNT, DATE, AMOUNT, DESCRIPTION, TransactionDirection.BILL, " TXN-1 "))
                    .isEqualTo("TXN-1");
        }
    }
}
