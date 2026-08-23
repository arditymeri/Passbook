package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.GetBillPersistencePort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GetBillServiceImpl implements GetBillService {

    private final GetBillPersistencePort getBillPersistencePort;

    public GetBillServiceImpl(GetBillPersistencePort getBillPersistencePort) {
        this.getBillPersistencePort = getBillPersistencePort;
    }

    /**
     * Deliberately unfiltered: an original stays directly fetchable by id forever, even after it
     * has been corrected or removed (Constitution Principle I).
     */
    @Override
    public BillDto getBillById(UUID uuid) {
        return this.getBillPersistencePort.getBillById(uuid)
                .orElseThrow();
    }

    /**
     * The human-facing list. Hides reversal rows and any row something else supersedes, so a
     * corrected bill shows as a single row carrying its current value. Aggregation code paths do
     * <em>not</em> go through here — they read every row via the SPI ports so reversals can net out.
     */
    @Override
    public List<BillDto> getAll() {
        List<BillDto> all = this.getBillPersistencePort.getAll();
        Set<String> superseded = supersededIds(all);
        return all.stream()
                .filter(b -> !b.isReversal())
                .filter(b -> !superseded.contains(b.getId()))
                .toList();
    }

    @Override
    public List<BillDto> getHistory(UUID id) {
        List<BillDto> all = this.getBillPersistencePort.getAll();
        Map<String, BillDto> byId = all.stream()
                .filter(b -> !b.isReversal())
                .collect(Collectors.toMap(BillDto::getId, Function.identity(), (a, b) -> a));

        BillDto current = byId.get(id.toString());
        if (current == null) {
            throw new NoSuchElementException("Bill not found: " + id);
        }

        List<BillDto> history = new ArrayList<>();
        String priorId = current.getCorrectsTransactionId();
        while (priorId != null) {
            BillDto prior = byId.get(priorId);
            if (prior == null) {
                break;
            }
            history.add(prior);
            priorId = prior.getCorrectsTransactionId();
        }
        return history;
    }

    private static Set<String> supersededIds(List<BillDto> all) {
        return all.stream()
                .map(BillDto::getCorrectsTransactionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
