package at.ymeri.my.finance.domain.service.ingestion;

import at.ymeri.my.finance.domain.data.ingestion.StatementRow;
import at.ymeri.my.finance.domain.data.ingestion.TransactionDirection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parsing and occurrence indexing, under plain JUnit with no context and no database.
 *
 * <p>The occurrence-index tests here are the ones that matter most: they are the difference between
 * two coffees on the same day both being recorded and one of them quietly never existing.
 */
class ParseStatementServiceImplTest {

    private static final String ACCOUNT = "acct-1";

    private final ParseStatementServiceImpl service = new ParseStatementServiceImpl();

    private List<StatementRow> parse(String csv) {
        return service.parse(csv, ACCOUNT);
    }

    @Nested
    class HappyPath {

        @Test
        void readsDateDescriptionAndAmount() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    """);

            assertThat(rows).hasSize(1);
            StatementRow row = rows.get(0);
            assertThat(row.rowIndex()).isZero();
            assertThat(row.date()).isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(row.description()).isEqualTo("COFFEE BAR");
            assertThat(row.amount()).isEqualByComparingTo("3.40");
            assertThat(row.isRejected()).isFalse();
        }

        @Test
        void negativeAmountsAreBillsAndPositiveOnesAreIncome() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    2026-01-28,SALARY,2400.00
                    """);

            assertThat(rows.get(0).direction()).isEqualTo(TransactionDirection.BILL);
            assertThat(rows.get(1).direction()).isEqualTo(TransactionDirection.INCOME);
        }

        @Test
        void storedAmountsAreAlwaysPositive() {
            // Both tables model amount as positive; the sign lives in the direction.
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    """);

            assertThat(rows.get(0).amount()).isEqualByComparingTo(new BigDecimal("3.40"));
        }

        @Test
        void headerOrderAndCaseDoNotMatter() {
            List<StatementRow> rows = parse("""
                    Amount,DATE,Description
                    -3.40,2026-01-15,COFFEE BAR
                    """);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).description()).isEqualTo("COFFEE BAR");
        }

        @Test
        void rowIndexesFollowFilePosition() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-10,A,-1.00
                    2026-01-11,B,-2.00
                    2026-01-12,C,-3.00
                    """);

            assertThat(rows).extracting(StatementRow::rowIndex).containsExactly(0, 1, 2);
        }
    }

    @Nested
    class CsvEdgeCases {

        @Test
        void quotedFieldContainingACommaIsOneField() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,"COFFEE BAR, CENTRAL",-3.40
                    """);

            assertThat(rows.get(0).description()).isEqualTo("COFFEE BAR, CENTRAL");
            assertThat(rows.get(0).amount()).isEqualByComparingTo("3.40");
        }

        @Test
        void escapedQuotesSurviveIntact() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,"THE ""OLD"" MILL",-3.40
                    """);

            assertThat(rows.get(0).description()).isEqualTo("THE \"OLD\" MILL");
        }

        @Test
        void aNewlineInsideAQuotedFieldDoesNotSplitTheRow() {
            // The case feature 017's line-oriented browser parser could not represent at all — it
            // would have produced two broken rows out of one good one.
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,"COFFEE BAR
                    CENTRAL",-3.40
                    2026-01-16,BAKERY,-2.10
                    """);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).description()).isEqualTo("COFFEE BAR\nCENTRAL");
            assertThat(rows.get(1).description()).isEqualTo("BAKERY");
        }

        @Test
        void blankLinesAreIgnored() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40

                    2026-01-16,BAKERY,-2.10
                    """);

            assertThat(rows).hasSize(2);
        }
    }

    @Nested
    class RejectedRows {

        @Test
        void anUnreadableDateIsRejectedWithAReasonAndDoesNotBlockItsNeighbours() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-10,SUPERMARKET,-54.20
                    not-a-date,BROKEN ROW,-1.00
                    2026-01-28,SALARY,2400.00
                    """);

            assertThat(rows).hasSize(3);
            assertThat(rows.get(0).isRejected()).isFalse();
            assertThat(rows.get(1).isRejected()).isTrue();
            assertThat(rows.get(1).rejectionReason()).contains("not-a-date");
            assertThat(rows.get(2).isRejected()).isFalse();
        }

        @Test
        void anUnreadableAmountIsRejectedWithAReason() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-10,SUPERMARKET,not-a-number
                    """);

            assertThat(rows.get(0).isRejected()).isTrue();
            assertThat(rows.get(0).rejectionReason()).contains("not-a-number");
        }

        @Test
        void aZeroAmountIsRejected() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-10,NOTHING HAPPENED,0.00
                    """);

            assertThat(rows.get(0).isRejected()).isTrue();
            assertThat(rows.get(0).rejectionReason()).contains("zero");
        }

        @Test
        void aRejectedRowStillOccupiesItsPosition() {
            // Row indexes must stay tied to file position, or an exclusion would name the wrong row.
            List<StatementRow> rows = parse("""
                    date,description,amount
                    bad,X,-1.00
                    2026-01-11,B,-2.00
                    """);

            assertThat(rows).extracting(StatementRow::rowIndex).containsExactly(0, 1);
        }
    }

    @Nested
    class UnusableFiles {

        @Test
        void anEmptyFileIsRejectedOutright() {
            assertThatThrownBy(() -> parse("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        void aFileWithoutTheRequiredColumnsIsRejectedOutright() {
            // Nothing may be recorded from a file that is not a statement (FR-015).
            assertThatThrownBy(() -> parse("""
                    name,favourite_colour
                    alice,blue
                    """))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("date");
        }
    }

    @Nested
    class OccurrenceIndexing {

        @Test
        void twoIdenticalRowsGetDistinctIdentities() {
            // The single most important assertion in the feature: without this, the second coffee is
            // silently never recorded, and the resulting history still looks entirely plausible.
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    2026-01-15,COFFEE BAR,-3.40
                    """);

            assertThat(rows.get(0).externalId()).endsWith(":0");
            assertThat(rows.get(1).externalId()).endsWith(":1");
            assertThat(rows.get(0).externalId()).isNotEqualTo(rows.get(1).externalId());
        }

        @Test
        void threeIdenticalRowsCountUpwards() {
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    2026-01-15,COFFEE BAR,-3.40
                    2026-01-15,COFFEE BAR,-3.40
                    """);

            assertThat(rows).extracting(r -> r.externalId().substring(r.externalId().indexOf(':')))
                    .containsExactly(":0", ":1", ":2");
        }

        @Test
        void separateIdentityGroupsCountIndependently() {
            // Counting globally across the file instead of per group would break convergence across
            // overlapping statements (022 research R1).
            List<StatementRow> rows = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    2026-01-15,BAKERY,-2.10
                    2026-01-15,COFFEE BAR,-3.40
                    2026-01-15,BAKERY,-2.10
                    """);

            assertThat(rows.get(0).externalId()).endsWith(":0");
            assertThat(rows.get(1).externalId()).endsWith(":0");
            assertThat(rows.get(2).externalId()).endsWith(":1");
            assertThat(rows.get(3).externalId()).endsWith(":1");
        }

        @Test
        void aFileWithOneOccurrenceAndAFileWithTwoOverlapOnTheFirst() {
            // This is what makes overlapping statements converge: importing either order ends with
            // two coffees, because the shared row derives the same identity both times.
            List<StatementRow> one = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    """);
            List<StatementRow> two = parse("""
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    2026-01-15,COFFEE BAR,-3.40
                    """);

            assertThat(one.get(0).externalId()).isEqualTo(two.get(0).externalId());
            assertThat(one.get(0).externalId()).isNotEqualTo(two.get(1).externalId());
        }

        @Test
        void reParsingTheSameFileGivesTheSameIdentities() {
            // Confirm re-uploads the file and re-derives identity rather than trusting the client
            // (022 research R7); that only works if parsing is deterministic.
            String csv = """
                    date,description,amount
                    2026-01-15,COFFEE BAR,-3.40
                    2026-01-15,COFFEE BAR,-3.40
                    """;

            assertThat(parse(csv)).extracting(StatementRow::externalId)
                    .isEqualTo(parse(csv).stream().map(StatementRow::externalId).toList());
        }

        @Test
        void aBankSuppliedIdentifierIsUsedVerbatimWithNoOccurrenceSuffix() {
            List<StatementRow> rows = parse("""
                    date,description,amount,transactionId
                    2026-01-15,COFFEE BAR,-3.40,TXN-1
                    2026-01-15,COFFEE BAR,-3.40,TXN-2
                    """);

            assertThat(rows.get(0).externalId()).isEqualTo("TXN-1");
            assertThat(rows.get(1).externalId()).isEqualTo("TXN-2");
        }
    }
}
