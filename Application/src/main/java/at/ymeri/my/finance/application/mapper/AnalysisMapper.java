package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.MonthlySummary;
import at.ymeri.my.finance.domain.data.analysis.MonthlySummaryDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface AnalysisMapper {

    AnalysisMapper INSTANCE = Mappers.getMapper(AnalysisMapper.class);

    MonthlySummary map(MonthlySummaryDto dto);

    List<MonthlySummary> mapList(List<MonthlySummaryDto> dtos);
}
