package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesStatus;
import at.ymeri.my.finance.domain.data.recurring.TransactionType;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import at.ymeri.my.finance.domain.spi.recurring.UpdateRecurringSeriesStatusPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DismissRecurringSeriesServiceImplTest {

    @Mock
    private GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort;

    @Mock
    private UpdateRecurringSeriesStatusPersistencePort updateRecurringSeriesStatusPersistencePort;

    @InjectMocks
    private DismissRecurringSeriesServiceImpl service;

    @Test
    void dismiss_proposedSeries_transitionsToDismissed() {
        when(getRecurringSeriesPersistencePort.findById("series-1")).thenReturn(Optional.of(series(RecurringSeriesStatus.PROPOSED)));
        when(updateRecurringSeriesStatusPersistencePort.updateStatus("series-1", RecurringSeriesStatus.DISMISSED))
                .thenReturn(series(RecurringSeriesStatus.DISMISSED));

        RecurringSeriesDto result = service.dismiss("series-1");

        assertEquals(RecurringSeriesStatus.DISMISSED, result.getStatus());
    }

    @Test
    void dismiss_confirmedSeries_transitionsToDismissed() {
        when(getRecurringSeriesPersistencePort.findById("series-1")).thenReturn(Optional.of(series(RecurringSeriesStatus.CONFIRMED)));
        when(updateRecurringSeriesStatusPersistencePort.updateStatus("series-1", RecurringSeriesStatus.DISMISSED))
                .thenReturn(series(RecurringSeriesStatus.DISMISSED));

        RecurringSeriesDto result = service.dismiss("series-1");

        assertEquals(RecurringSeriesStatus.DISMISSED, result.getStatus());
        verify(updateRecurringSeriesStatusPersistencePort).updateStatus("series-1", RecurringSeriesStatus.DISMISSED);
    }

    @Test
    void dismiss_alreadyDismissedSeries_throwsAndDoesNotUpdate() {
        when(getRecurringSeriesPersistencePort.findById("series-1")).thenReturn(Optional.of(series(RecurringSeriesStatus.DISMISSED)));

        assertThrows(IllegalStateException.class, () -> service.dismiss("series-1"));
        verifyNoInteractions(updateRecurringSeriesStatusPersistencePort);
    }

    @Test
    void dismiss_unknownId_throwsNoSuchElement() {
        when(getRecurringSeriesPersistencePort.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.dismiss("unknown"));
        verifyNoInteractions(updateRecurringSeriesStatusPersistencePort);
    }

    private RecurringSeriesDto series(RecurringSeriesStatus status) {
        RecurringSeriesDto dto = new RecurringSeriesDto();
        dto.setId("series-1");
        dto.setTransactionType(TransactionType.BILL);
        dto.setGroupKey("cat-A");
        dto.setDescription("netflix");
        dto.setFrequency(RecurringFrequency.MONTHLY);
        dto.setStatus(status);
        dto.setCreatedAt(OffsetDateTime.now());
        return dto;
    }
}
