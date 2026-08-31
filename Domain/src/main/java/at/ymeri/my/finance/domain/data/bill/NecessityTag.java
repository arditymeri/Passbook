package at.ymeri.my.finance.domain.data.bill;

/**
 * The user's own judgment of whether a bill was worth spending, independent of any category or
 * budget it belongs to. Set and cleared directly on the bill (see {@code BillDto#necessityTag}),
 * outside the correction/reversal mechanism — this is an opinion about a fact, not a claim about
 * what happened.
 */
public enum NecessityTag {
    NECESSARY,
    AVOIDABLE,
    UNNECESSARY
}
