package at.ymeri.my.finance.domain.api;

import java.util.List;

/**
 * Reconciles newly ingested transactions against the predictions this app posted for the same
 * periods, so an operator who imports a statement is not left holding both.
 */
public interface ReconcileAutoPostedService {

    /** @return how many auto-posted bills were superseded */
    int reconcileBills(List<String> incomingBillIds);

    /** @return how many auto-posted incomes were superseded */
    int reconcileIncomes(List<String> incomingIncomeIds);
}
