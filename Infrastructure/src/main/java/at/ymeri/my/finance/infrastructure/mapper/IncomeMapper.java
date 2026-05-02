package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import at.ymeri.my.finance.domain.data.common.RecurringFrequency;
import at.ymeri.my.finance.infrastructure.entity.IncomeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface IncomeMapper {

    IncomeMapper INSTANCE = Mappers.getMapper(IncomeMapper.class);

    @Mapping(target = "source", qualifiedByName = "sourceToString")
    @Mapping(target = "recurringFrequency", qualifiedByName = "frequencyToString")
    IncomeEntity map(IncomeDto incomeDto);

    @Mapping(target = "source", qualifiedByName = "stringToSource")
    @Mapping(target = "recurringFrequency", qualifiedByName = "stringToFrequency")
    IncomeDto map(IncomeEntity incomeEntity);

    List<IncomeDto> map(List<IncomeEntity> entities);

    @Named("sourceToString")
    default String sourceToString(IncomeSource source) {
        return source != null ? source.name() : null;
    }

    @Named("frequencyToString")
    default String frequencyToString(RecurringFrequency frequency) {
        return frequency != null ? frequency.name() : null;
    }

    @Named("stringToSource")
    default IncomeSource stringToSource(String source) {
        return source != null ? IncomeSource.valueOf(source) : null;
    }

    @Named("stringToFrequency")
    default RecurringFrequency stringToFrequency(String frequency) {
        return frequency != null ? RecurringFrequency.valueOf(frequency) : null;
    }
}
