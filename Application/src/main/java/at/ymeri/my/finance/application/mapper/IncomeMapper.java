package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.CreateIncomeRequest;
import at.ymeri.my.finance.application.data.IncomeResponse;
import at.ymeri.my.finance.application.data.IncomeSource;
import at.ymeri.my.finance.application.data.RecurringFrequency;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface IncomeMapper {

    IncomeMapper INSTANCE = Mappers.getMapper(IncomeMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "source", qualifiedByName = "apiSourceToDomain")
    @Mapping(target = "recurringFrequency", qualifiedByName = "apiFrequencyToDomain")
    IncomeDto map(CreateIncomeRequest request);

    @Mapping(target = "source", qualifiedByName = "domainSourceToApi")
    @Mapping(target = "recurringFrequency", qualifiedByName = "domainFrequencyToApi")
    IncomeResponse map(IncomeDto incomeDto);

    List<IncomeResponse> mapList(List<IncomeDto> incomeDtos);

    @Named("apiSourceToDomain")
    default at.ymeri.my.finance.domain.data.income.IncomeSource apiSourceToDomain(IncomeSource source) {
        return source != null
                ? at.ymeri.my.finance.domain.data.income.IncomeSource.valueOf(source.getValue())
                : null;
    }

    @Named("domainSourceToApi")
    default IncomeSource domainSourceToApi(at.ymeri.my.finance.domain.data.income.IncomeSource source) {
        return source != null ? IncomeSource.fromValue(source.name()) : null;
    }

    @Named("apiFrequencyToDomain")
    default at.ymeri.my.finance.domain.data.common.RecurringFrequency apiFrequencyToDomain(RecurringFrequency frequency) {
        return frequency != null
                ? at.ymeri.my.finance.domain.data.common.RecurringFrequency.valueOf(frequency.getValue())
                : null;
    }

    @Named("domainFrequencyToApi")
    default RecurringFrequency domainFrequencyToApi(at.ymeri.my.finance.domain.data.common.RecurringFrequency frequency) {
        return frequency != null ? RecurringFrequency.fromValue(frequency.name()) : null;
    }
}
