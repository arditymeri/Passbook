package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalDto;
import at.ymeri.my.finance.infrastructure.entity.SavingsGoalEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface SavingsGoalMapper {

    SavingsGoalMapper INSTANCE = Mappers.getMapper(SavingsGoalMapper.class);

    SavingsGoalEntity map(SavingsGoalDto dto);

    SavingsGoalDto map(SavingsGoalEntity entity);

    List<SavingsGoalDto> map(List<SavingsGoalEntity> entities);
}
