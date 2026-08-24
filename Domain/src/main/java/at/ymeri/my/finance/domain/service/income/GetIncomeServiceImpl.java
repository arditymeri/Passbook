package at.ymeri.my.finance.domain.service.income;

import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.spi.income.GetIncomePersistencePort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GetIncomeServiceImpl implements GetIncomeService {

    private final GetIncomePersistencePort getIncomePersistencePort;

    public GetIncomeServiceImpl(GetIncomePersistencePort getIncomePersistencePort) {
        this.getIncomePersistencePort = getIncomePersistencePort;
    }

    /**
     * Deliberately unfiltered: an original stays directly fetchable by id forever, even after it
     * has been corrected or removed (Constitution Principle I).
     */
    @Override
    public IncomeDto getIncomeById(UUID id) {
        return getIncomePersistencePort.getIncomeById(id)
                .orElseThrow(() -> new NoSuchElementException("Income not found: " + id));
    }

    /**
     * The human-facing list. Hides reversal rows and any row something else supersedes, so a
     * corrected income shows as a single row carrying its current value. Aggregation code paths do
     * <em>not</em> go through here — they read every row via the SPI ports so reversals can net out.
     */
    @Override
    public List<IncomeDto> getAll() {
        List<IncomeDto> all = getIncomePersistencePort.getAll();
        Set<String> superseded = supersededIds(all);
        return all.stream()
                .filter(i -> !i.isReversal())
                .filter(i -> !superseded.contains(i.getId()))
                .toList();
    }

    @Override
    public List<IncomeDto> getHistory(UUID id) {
        List<IncomeDto> all = getIncomePersistencePort.getAll();
        Map<String, IncomeDto> byId = all.stream()
                .filter(i -> !i.isReversal())
                .collect(Collectors.toMap(IncomeDto::getId, Function.identity(), (a, b) -> a));

        IncomeDto current = byId.get(id.toString());
        if (current == null) {
            throw new NoSuchElementException("Income not found: " + id);
        }

        List<IncomeDto> history = new ArrayList<>();
        String priorId = current.getCorrectsTransactionId();
        while (priorId != null) {
            IncomeDto prior = byId.get(priorId);
            if (prior == null) {
                break;
            }
            history.add(prior);
            priorId = prior.getCorrectsTransactionId();
        }
        return history;
    }

    private static Set<String> supersededIds(List<IncomeDto> all) {
        return all.stream()
                .map(IncomeDto::getCorrectsTransactionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
