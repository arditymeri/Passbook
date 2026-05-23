package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.infrastructure.entity.BudgetEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface BudgetMapper {

    BudgetMapper INSTANCE = Mappers.getMapper(BudgetMapper.class);

    BudgetEntity map(BudgetDto budgetDto);

    BudgetDto map(BudgetEntity budgetEntity);

    List<BudgetDto> map(List<BudgetEntity> entities);
}
