package at.ymeri.my.finance.domain.service.bill;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.spi.bill.AddBillPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddBillServiceImplTest {

    @Mock
    private AddBillPersistencePort billPersistencePort;

    @InjectMocks
    private AddBillServiceImpl addBillService;


    @Test
    void addBill() {
        // given
        BillDto bill = new BillDto();
        bill.setAmount(BigDecimal.ONE);
        bill.setTime(OffsetDateTime.now());
        bill.setDescription("test");
        when(billPersistencePort.addBill(bill)).thenReturn(bill);
        // when
        BillDto actual = addBillService.addBill(bill);
        // then
        assertEquals(bill, actual);
    }
}