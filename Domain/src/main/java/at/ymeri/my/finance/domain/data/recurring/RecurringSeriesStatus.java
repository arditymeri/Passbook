package at.ymeri.my.finance.domain.data.recurring;

public enum RecurringSeriesStatus {
    PROPOSED,
    CONFIRMED,
    DISMISSED,

    /**
     * The series was real and has ended — a cancelled subscription, a tenancy moved out of.
     * Deliberately distinct from {@link #DISMISSED}, which says the detection was wrong in the first
     * place: collapsing the two would lose the difference between a bad guess and a real thing that
     * finished, and would make a stopped series look like a detector error.
     */
    STOPPED
}
