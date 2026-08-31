package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.RecurringCostSummaryItem;
import at.ymeri.my.finance.domain.data.recurring.RecurringCostSummaryItemDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RecurringCostSummaryMapper {

    RecurringCostSummaryMapper INSTANCE = Mappers.getMapper(RecurringCostSummaryMapper.class);

    RecurringCostSummaryItem map(RecurringCostSummaryItemDto dto);

    List<RecurringCostSummaryItem> mapList(List<RecurringCostSummaryItemDto> dtos);
}
