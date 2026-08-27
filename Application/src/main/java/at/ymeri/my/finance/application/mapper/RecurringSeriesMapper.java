package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.PriceChangeAlert;
import at.ymeri.my.finance.application.data.RecurringDashboardResponse;
import at.ymeri.my.finance.application.data.RecurringSeriesResponse;
import at.ymeri.my.finance.application.data.UpcomingRecurringItem;
import at.ymeri.my.finance.domain.data.recurring.PriceChangeAlertDto;
import at.ymeri.my.finance.domain.data.recurring.RecurringDashboardResult;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.data.recurring.UpcomingRecurringItemDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RecurringSeriesMapper {

    RecurringSeriesMapper INSTANCE = Mappers.getMapper(RecurringSeriesMapper.class);

    RecurringSeriesResponse map(RecurringSeriesDto dto);

    List<RecurringSeriesResponse> mapList(List<RecurringSeriesDto> dtos);

    UpcomingRecurringItem map(UpcomingRecurringItemDto dto);

    PriceChangeAlert map(PriceChangeAlertDto dto);

    RecurringDashboardResponse map(RecurringDashboardResult result);
}
