package at.ymeri.my.finance.domain.data.recurring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What one posting run did (FR-013's operator-facing answer, and what the on-demand endpoint
 * returns).
 *
 * <p>{@code alreadyPosted} is routinely non-zero and is not a problem: every run recomputes the
 * whole due set rather than tracking where it left off, so occurrences posted on previous days are
 * re-offered and refused. Reporting it rather than hiding it means an operator who runs posting by
 * hand can see that it considered the period and found it handled.
 */
public record PostingRunResult(List<PostedOccurrence> posted,
                               int alreadyPostedCount,
                               int skippedSeriesCount) {

    /**
     * @param seriesId       the series that produced this transaction
     * @param occurrenceDate the period it covers
     * @param transactionId  the transaction created for it
     */
    public record PostedOccurrence(String seriesId, LocalDate occurrenceDate,
                                   String transactionId, BigDecimal amount) {
    }

    public int postedCount() {
        return posted.size();
    }
}
