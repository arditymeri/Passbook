package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.bill.NecessityTag;
import at.ymeri.my.finance.domain.spi.bill.UpdateBillNecessityTagPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateBillNecessityTagServiceImplTest {

    @Mock
    private UpdateBillNecessityTagPersistencePort updateBillNecessityTagPersistencePort;

    @Mock
    private GetBillService getBillService;

    @InjectMocks
    private UpdateBillNecessityTagServiceImpl updateBillNecessityTagService;

    @Test
    void updateNecessityTag_visibleBill_setsNecessary() {
        assertSetsTag(NecessityTag.NECESSARY);
    }

    @Test
    void updateNecessityTag_visibleBill_setsAvoidable() {
        assertSetsTag(NecessityTag.AVOIDABLE);
    }

    @Test
    void updateNecessityTag_visibleBill_setsUnnecessary() {
        assertSetsTag(NecessityTag.UNNECESSARY);
    }

    private void assertSetsTag(NecessityTag tag) {
        when(getBillService.getAll()).thenReturn(List.of(visibleBill("bill-1")));
        BillDto updated = visibleBill("bill-1");
        updated.setNecessityTag(tag);
        when(updateBillNecessityTagPersistencePort.updateNecessityTag("bill-1", tag)).thenReturn(updated);

        BillDto result = updateBillNecessityTagService.updateNecessityTag("bill-1", tag);

        assertThat(result.getNecessityTag()).isEqualTo(tag);
        verify(updateBillNecessityTagPersistencePort).updateNecessityTag("bill-1", tag);
    }

    @Test
    void updateNecessityTag_nullTag_clearsIt() {
        when(getBillService.getAll()).thenReturn(List.of(visibleBill("bill-1")));
        BillDto cleared = visibleBill("bill-1");
        when(updateBillNecessityTagPersistencePort.updateNecessityTag("bill-1", null)).thenReturn(cleared);

        BillDto result = updateBillNecessityTagService.updateNecessityTag("bill-1", null);

        assertThat(result.getNecessityTag()).isNull();
        verify(updateBillNecessityTagPersistencePort).updateNecessityTag("bill-1", null);
    }

    @Test
    void updateNecessityTag_billNotVisible_throwsNoSuchElementException() {
        when(getBillService.getAll()).thenReturn(List.of(visibleBill("other-bill")));

        assertThatThrownBy(() -> updateBillNecessityTagService.updateNecessityTag("missing-bill", NecessityTag.UNNECESSARY))
                .isInstanceOf(NoSuchElementException.class);
    }

    /**
     * Covers reversal rows and rows superseded by a correction too: {@link GetBillService#getAll()}
     * already excludes both, so a bill id absent from its result exercises the same not-found path
     * regardless of which of the three reasons ("never existed", "is a reversal",
     * "has been corrected/removed") applies.
     */
    @Test
    void updateNecessityTag_reversalOrSupersededBill_throwsNoSuchElementException() {
        when(getBillService.getAll()).thenReturn(List.of());

        assertThatThrownBy(() -> updateBillNecessityTagService.updateNecessityTag("reversal-or-superseded", NecessityTag.NECESSARY))
                .isInstanceOf(NoSuchElementException.class);
    }

    private BillDto visibleBill(String id) {
        BillDto bill = new BillDto();
        bill.setId(id);
        return bill;
    }
}
