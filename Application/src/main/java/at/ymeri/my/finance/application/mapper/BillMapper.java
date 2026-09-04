package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface BillMapper {

    BillMapper INSTANCE = Mappers.getMapper(BillMapper.class);

    Bill map(BillDto billDto);
    List<Bill> mapList(List<BillDto> billDto);

    /**
     * Inbound, from a client. {@code recurringSeriesId} is deliberately ignored: provenance is
     * something the server knows and a caller must not be able to assert. A hand-entered bill
     * claiming to be auto-posted would be excluded from its series' real occurrences, and the series
     * would then anchor its next prediction somewhere nobody intended.
     */
    @Mapping(target = "recurringSeriesId", ignore = true)
    BillDto map(Bill bill);
}
