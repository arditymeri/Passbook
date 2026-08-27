package at.ymeri.my.finance.domain.spi.recurring;

import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;

import java.util.List;
import java.util.Optional;

public interface GetRecurringSeriesPersistencePort {

    List<RecurringSeriesDto> getAll();

    Optional<RecurringSeriesDto> findById(String id);

    Optional<RecurringSeriesDto> findByKey(TransactionType type, String groupKey, String description);
}
