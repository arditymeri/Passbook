package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface BillMapper {

    BillMapper INSTANCE = Mappers.getMapper(BillMapper.class);

    Bill map(BillDto billDto);
    List<Bill> mapList(List<BillDto> billDto);

    BillDto map(Bill bill);
}
