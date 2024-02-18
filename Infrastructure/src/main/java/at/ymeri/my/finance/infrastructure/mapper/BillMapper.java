package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface BillMapper {

    BillMapper INSTANCE = Mappers.getMapper(BillMapper.class);

    BillEntity map(BillDto billDto);

    BillDto map(BillEntity billEntity);

    List<BillDto> map(List<BillEntity> entities);
}
