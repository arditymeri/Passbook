package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.SavingsGoalResponse;
import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SavingsGoalMapper {

    SavingsGoalMapper INSTANCE = Mappers.getMapper(SavingsGoalMapper.class);

    SavingsGoalResponse map(SavingsGoalStatusDto dto);
}
