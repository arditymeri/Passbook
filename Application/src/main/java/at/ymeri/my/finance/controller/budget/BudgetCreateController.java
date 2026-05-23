package at.ymeri.my.finance.controller.budget;

import at.ymeri.my.finance.application.controller.budget.BudgetCreateApi;
import at.ymeri.my.finance.application.data.BudgetResponse;
import at.ymeri.my.finance.application.data.CreateBudgetRequest;
import at.ymeri.my.finance.application.mapper.BudgetMapper;
import at.ymeri.my.finance.domain.api.SetBudgetService;
import at.ymeri.my.finance.domain.data.budget.BudgetDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class BudgetCreateController implements BudgetCreateApi {

    private final SetBudgetService setBudgetService;

    public BudgetCreateController(SetBudgetService setBudgetService) {
        this.setBudgetService = setBudgetService;
    }

    @Override
    public ResponseEntity<BudgetResponse> createOrUpdateBudget(CreateBudgetRequest request) {
        try {
            BudgetDto dto = new BudgetDto();
            dto.setCategoryId(request.getCategoryId() != null ? request.getCategoryId().toString() : null);
            dto.setYear(request.getYear());
            dto.setMonth(request.getMonth());
            dto.setLimitAmount(request.getLimitAmount());

            BudgetResponse response = BudgetMapper.INSTANCE.map(setBudgetService.setBudget(dto));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
