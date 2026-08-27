package at.ymeri.my.finance.domain.service.goal;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.domain.spi.goal.DeleteSavingsGoalPersistencePort;
import at.ymeri.my.finance.domain.spi.goal.GetSavingsGoalPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteSavingsGoalServiceImplTest {

    @Mock
    private GetSavingsGoalPersistencePort getSavingsGoalPersistencePort;

    @Mock
    private DeleteSavingsGoalPersistencePort deleteSavingsGoalPersistencePort;

    @InjectMocks
    private DeleteSavingsGoalServiceImpl service;

    @Test
    void deleteGoal_existingGoal_deletes() {
        SavingsGoalDto existing = new SavingsGoalDto();
        existing.setId("goal-1");
        existing.setCreatedAt(OffsetDateTime.now());
        when(getSavingsGoalPersistencePort.findById("goal-1")).thenReturn(Optional.of(existing));

        service.deleteGoal("goal-1");

        verify(deleteSavingsGoalPersistencePort).delete("goal-1");
    }

    @Test
    void deleteGoal_unknownId_throwsNoSuchElementAndNeverDeletes() {
        when(getSavingsGoalPersistencePort.findById("missing")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.deleteGoal("missing"));
        verify(deleteSavingsGoalPersistencePort, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }
}
