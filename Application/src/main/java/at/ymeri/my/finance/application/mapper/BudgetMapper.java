package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.BudgetResponse;
import at.ymeri.my.finance.application.data.BudgetStatusEntry;
import at.ymeri.my.finance.application.data.RepeatAllocationsResponseAppliedInner;
import at.ymeri.my.finance.application.data.TransferAllocationResponse;
import at.ymeri.my.finance.domain.data.budget.AllocationTopUp;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import at.ymeri.my.finance.domain.data.budget.BudgetStatus;
import at.ymeri.my.finance.domain.data.budget.BudgetStatusDto;
import at.ymeri.my.finance.domain.data.budget.MoveAllocationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface BudgetMapper {

    BudgetMapper INSTANCE = Mappers.getMapper(BudgetMapper.class);

    BudgetResponse map(BudgetDto dto);

    List<BudgetResponse> mapList(List<BudgetDto> dtos);

    @Mapping(target = "status", expression = "java(mapStatus(dto.getStatus()))")
    BudgetStatusEntry mapStatus(BudgetStatusDto dto);

    List<BudgetStatusEntry> mapStatusList(List<BudgetStatusDto> dtos);

    @Mapping(source = "transfer.id", target = "id")
    @Mapping(source = "transfer.fromCategoryId", target = "fromCategoryId")
    @Mapping(source = "transfer.toCategoryId", target = "toCategoryId")
    @Mapping(source = "transfer.amount", target = "amount")
    TransferAllocationResponse map(MoveAllocationResult result);

    RepeatAllocationsResponseAppliedInner map(AllocationTopUp topUp);

    List<RepeatAllocationsResponseAppliedInner> mapTopUps(List<AllocationTopUp> topUps);

    default BudgetStatusEntry.StatusEnum mapStatus(BudgetStatus status) {
        if (status == null) return null;
        return switch (status) {
            case UNDER_BUDGET -> BudgetStatusEntry.StatusEnum.UNDER_BUDGET;
            case OVER_BUDGET -> BudgetStatusEntry.StatusEnum.OVER_BUDGET;
        };
    }
}
