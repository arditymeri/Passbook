package at.ymeri.my.finance.domain.service.ingestion;

import at.ymeri.my.finance.domain.data.ingestion.TransactionDirection;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Decides what a transaction's identity is. This is the whole of Principle II, and the one place in
 * the codebase allowed to answer the question — no client, and no other service, derives an identity
 * (FR-010).
 *
 * <p><strong>Two forms.</strong> When the statement supplies its own transaction identifier, that is
 * used verbatim: it is the bank's own answer, it is stable by construction, and it sidesteps every
 * ambiguity below. Otherwise identity is a SHA-256 over the row's own content.
 *
 * <p><strong>What goes into the hash, and why each field is there</strong> (022 research R1):
 * <ul>
 *   <li><em>account</em> — identity is scoped per account (FR-006). The same row imported into two
 *       accounts is two transactions.</li>
 *   <li><em>calendar date</em> — not a timestamp. Statements state dates; any time-of-day is
 *       invented by the parser, and hashing an invented value would make identity depend on parser
 *       behaviour rather than on the statement.</li>
 *   <li><em>amount</em>, normalised so {@code 42.50} and {@code 42.5} are the same value rather than
 *       two different strings.</li>
 *   <li><em>description</em>, whitespace-normalised only — see below.</li>
 *   <li><em>direction</em> — load-bearing, and easy to miss. {@code bill} and {@code income} both
 *       store positive amounts, so without it a €50 refund collides with the €50 charge it reverses,
 *       and a refund usually carries the merchant's own string.</li>
 * </ul>
 *
 * <p><strong>Why description normalisation stops at whitespace.</strong> Case-folding or stripping
 * punctuation would make identity more forgiving of a bank that changes its formatting between
 * statements — at the cost of occasionally merging two genuinely different merchants. The two
 * failure modes are not symmetric: a spurious duplicate is visible in a balance and can be
 * corrected, while a silently merged transaction is money that quietly never existed. This feature
 * consistently prefers the visible failure, which is the same reasoning that puts an occurrence
 * index in the identity at all.
 */
public final class ExternalIdentityFactory {

    /**
     * ASCII unit separator (U+001F). Used to join fields so that {@code "a" + "b"} can never collide
     * with {@code "ab"}: the character cannot occur inside a statement field.
     */
    private static final String FIELD_SEPARATOR = "\u001F";

    private ExternalIdentityFactory() {
    }

    /**
     * The identity a row will be stored under.
     *
     * @param sourceTransactionId the bank's own identifier, or null/blank when it supplies none
     */
    public static String identityFor(String accountId,
                                     LocalDate date,
                                     BigDecimal amount,
                                     String description,
                                     TransactionDirection direction,
                                     String sourceTransactionId) {
        if (sourceTransactionId != null && !sourceTransactionId.isBlank()) {
            return sourceTransactionId.trim();
        }
        return hashOf(accountId, date, amount, description, direction);
    }

    /**
     * The hash alone, without any occurrence suffix — the identity <em>group</em> a row belongs to.
     * Rows sharing this value are candidates for being the same transaction, and are told apart by
     * their occurrence index.
     */
    public static String hashOf(String accountId,
                                LocalDate date,
                                BigDecimal amount,
                                String description,
                                TransactionDirection direction) {
        String canonical = String.join(FIELD_SEPARATOR,
                nullSafe(accountId),
                date == null ? "" : date.toString(),
                normalizeAmount(amount),
                normalizeDescription(description),
                direction == null ? "" : direction.name());
        return sha256Hex(canonical);
    }

    /**
     * {@code 42.50} and {@code 42.5} are the same amount and must hash alike; a statement is free to
     * write either.
     */
    static String normalizeAmount(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }

    /**
     * Trims and collapses internal whitespace runs. Deliberately does not fold case or strip
     * punctuation — see the class comment on why the conservative normalisation is the safe one.
     */
    static String normalizeDescription(String description) {
        return description == null ? "" : description.trim().replaceAll("\\s+", " ");
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated for every conforming JRE; unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
