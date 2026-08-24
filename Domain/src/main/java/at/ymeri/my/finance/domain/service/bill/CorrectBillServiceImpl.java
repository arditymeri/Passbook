package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.api.CorrectBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CorrectBillServiceImpl implements CorrectBillService {

    private final AddBillPersistencePort addBillPersistencePort;
    private final GetBillPersistencePort getBillPersistencePort;

    public CorrectBillServiceImpl(AddBillPersistencePort addBillPersistencePort,
                                  GetBillPersistencePort getBillPersistencePort) {
        this.addBillPersistencePort = addBillPersistencePort;
        this.getBillPersistencePort = getBillPersistencePort;
    }

    /**
     * Reversal and replacement are written as one unit: a failure between them would otherwise
     * leave an orphan reversal that permanently zeroes the bill out with nothing replacing it.
     *
     * <p>The read takes a write lock on the row being corrected, so concurrent corrections of the
     * same bill serialize: the second one sees the first one's reversal and fails the
     * "not yet superseded" check instead of double-reversing.
     */
    @Override
    @Transactional
    public BillDto correctBill(UUID id, BillDto correctedValues) {
        BillDto current = getBillPersistencePort.lockBillById(id)
                .orElseThrow(() -> new NoSuchElementException("Bill not found: " + id));

        validate(correctedValues);
        BillCorrections.assertNotSuperseded(getBillPersistencePort.getAll(), id, "Bill");

        addBillPersistencePort.addBill(BillCorrections.reversalOf(current));
        return addBillPersistencePort.addBill(replacement(current, correctedValues));
    }

    private static void validate(BillDto correctedValues) {
        if (correctedValues.getAmount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (correctedValues.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (correctedValues.getTime() == null) {
            throw new IllegalArgumentException("Time is required");
        }
    }

    /**
     * The corrected replacement carries the user's new values, dated per its own (possibly changed)
     * time, and points back at the row it replaces.
     */
    private static BillDto replacement(BillDto current, BillDto correctedValues) {
        BillDto replacement = new BillDto();
        replacement.setAmount(correctedValues.getAmount());
        replacement.setDescription(correctedValues.getDescription());
        replacement.setTime(correctedValues.getTime());
        replacement.setCategoryId(correctedValues.getCategoryId());
        replacement.setAccountId(correctedValues.getAccountId());
        replacement.setCurrency(current.getCurrency());
        replacement.setCorrectsTransactionId(current.getId());
        replacement.setReversal(false);
        return replacement;
    }
}
