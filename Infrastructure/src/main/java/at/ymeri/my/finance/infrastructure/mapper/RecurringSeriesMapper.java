package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.infrastructure.entity.RecurringSeriesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RecurringSeriesMapper {

    RecurringSeriesMapper INSTANCE = Mappers.getMapper(RecurringSeriesMapper.class);

    RecurringSeriesEntity map(RecurringSeriesDto dto);

    RecurringSeriesDto map(RecurringSeriesEntity entity);

    List<RecurringSeriesDto> map(List<RecurringSeriesEntity> entities);
}
