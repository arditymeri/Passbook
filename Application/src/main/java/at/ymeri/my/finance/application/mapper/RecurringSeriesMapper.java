package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.RecurringSeriesResponse;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RecurringSeriesMapper {

    RecurringSeriesMapper INSTANCE = Mappers.getMapper(RecurringSeriesMapper.class);

    RecurringSeriesResponse map(RecurringSeriesDto dto);

    List<RecurringSeriesResponse> mapList(List<RecurringSeriesDto> dtos);
}
