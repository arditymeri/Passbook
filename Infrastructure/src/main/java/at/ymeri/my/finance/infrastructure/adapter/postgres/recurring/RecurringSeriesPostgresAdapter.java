package at.ymeri.my.finance.infrastructure.adapter.postgres.recurring;

import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.spi.recurring.AddRecurringSeriesPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.UpdateRecurringSeriesStatusPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.RecurringSeriesEntity;
import at.ymeri.my.finance.infrastructure.mapper.RecurringSeriesMapper;
import at.ymeri.my.finance.infrastructure.repository.RecurringSeriesRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecurringSeriesPostgresAdapter implements GetRecurringSeriesPersistencePort,
        AddRecurringSeriesPersistencePort, UpdateRecurringSeriesStatusPersistencePort {

    private final RecurringSeriesRepository recurringSeriesRepository;

    public RecurringSeriesPostgresAdapter(RecurringSeriesRepository recurringSeriesRepository) {
        this.recurringSeriesRepository = recurringSeriesRepository;
    }

    @Override
    public List<RecurringSeriesDto> getAll() {
        return RecurringSeriesMapper.INSTANCE.map(recurringSeriesRepository.findAll());
    }

    @Override
    public Optional<RecurringSeriesDto> findById(String id) {
        return recurringSeriesRepository.findById(UUID.fromString(id)).map(RecurringSeriesMapper.INSTANCE::map);
    }

    @Override
    public Optional<RecurringSeriesDto> findByKey(TransactionType type, String groupKey, String description) {
        return recurringSeriesRepository
                .findByTransactionTypeAndGroupKeyAndDescription(type.name(), groupKey, description)
                .map(RecurringSeriesMapper.INSTANCE::map);
    }

    @Override
    public RecurringSeriesDto add(RecurringSeriesDto series) {
        RecurringSeriesEntity entity = RecurringSeriesMapper.INSTANCE.map(series);
        RecurringSeriesEntity saved = recurringSeriesRepository.save(entity);
        return RecurringSeriesMapper.INSTANCE.map(saved);
    }

    @Override
    public RecurringSeriesDto updateStatus(String id, RecurringSeriesStatus status) {
        RecurringSeriesEntity entity = recurringSeriesRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new java.util.NoSuchElementException("Recurring series not found: " + id));
        entity.setStatus(status.name());
        RecurringSeriesEntity saved = recurringSeriesRepository.save(entity);
        return RecurringSeriesMapper.INSTANCE.map(saved);
    }
}
