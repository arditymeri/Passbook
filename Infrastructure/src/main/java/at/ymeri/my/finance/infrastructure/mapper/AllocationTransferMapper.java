package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.budget.AllocationTransferDto;
import at.ymeri.my.finance.infrastructure.entity.AllocationTransferEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface AllocationTransferMapper {

    AllocationTransferMapper INSTANCE = Mappers.getMapper(AllocationTransferMapper.class);

    AllocationTransferEntity map(AllocationTransferDto allocationTransferDto);

    AllocationTransferDto map(AllocationTransferEntity allocationTransferEntity);

    List<AllocationTransferDto> map(List<AllocationTransferEntity> entities);
}
