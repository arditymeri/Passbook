package at.ymeri.my.finance.infrastructure.adapter.postgres.goal;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.spi.goal.AddSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.DeleteSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.UpdateSavingsGoalPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.SavingsGoalEntity;
import at.ymeri.my.finance.infrastructure.mapper.SavingsGoalMapper;
import at.ymeri.my.finance.infrastructure.repository.SavingsGoalRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class SavingsGoalPostgresAdapter implements GetSavingsGoalPersistencePort,
        AddSavingsGoalPersistencePort, UpdateSavingsGoalPersistencePort, DeleteSavingsGoalPersistencePort {

    private final SavingsGoalRepository savingsGoalRepository;

    public SavingsGoalPostgresAdapter(SavingsGoalRepository savingsGoalRepository) {
        this.savingsGoalRepository = savingsGoalRepository;
    }

    @Override
    public List<SavingsGoalDto> getAll() {
        return SavingsGoalMapper.INSTANCE.map(savingsGoalRepository.findAll());
    }

    @Override
    public Optional<SavingsGoalDto> findById(String id) {
        return savingsGoalRepository.findById(UUID.fromString(id)).map(SavingsGoalMapper.INSTANCE::map);
    }

    @Override
    public Optional<SavingsGoalDto> findByAccountId(String accountId) {
        return savingsGoalRepository.findByAccountId(accountId).map(SavingsGoalMapper.INSTANCE::map);
    }

    /**
     * {@code updatedAt} is stamped "now" only when the caller didn't already set one — see
     * {@code AddAccountPostgresAdapter} for why sync's import merge preserves the source
     * device's original timestamp instead.
     */
    @Override
    public SavingsGoalDto add(SavingsGoalDto goal) {
        SavingsGoalEntity entity = SavingsGoalMapper.INSTANCE.map(goal);
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(OffsetDateTime.now());
        }
        SavingsGoalEntity saved = savingsGoalRepository.save(entity);
        return SavingsGoalMapper.INSTANCE.map(saved);
    }

    @Override
    public SavingsGoalDto update(String id, SavingsGoalDto goal) {
        SavingsGoalEntity entity = savingsGoalRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new NoSuchElementException("Savings goal not found: " + id));
        entity.setName(goal.getName());
        entity.setTargetAmount(goal.getTargetAmount());
        entity.setTargetDate(goal.getTargetDate());
        entity.setUpdatedAt(goal.getUpdatedAt() != null ? goal.getUpdatedAt() : OffsetDateTime.now());
        SavingsGoalEntity saved = savingsGoalRepository.save(entity);
        return SavingsGoalMapper.INSTANCE.map(saved);
    }

    @Override
    public void delete(String id) {
        savingsGoalRepository.deleteById(UUID.fromString(id));
    }
}
