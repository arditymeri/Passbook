package at.ymeri.my.finance.domain.api;

import at.ymeri.my.finance.domain.data.goal.SavingsGoalStatusDto;

import java.util.List;

public interface GetSavingsGoalService {

    List<SavingsGoalStatusDto> getAll();

    SavingsGoalStatusDto getById(String id);
}
