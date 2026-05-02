package at.ymeri.my.finance.controller.income;

import at.ymeri.my.finance.application.controller.income.IncomeCreateApi;
import at.ymeri.my.finance.application.data.CreateIncomeRequest;
import at.ymeri.my.finance.application.data.IncomeResponse;
import at.ymeri.my.finance.application.mapper.IncomeMapper;
import at.ymeri.my.finance.domain.api.AddIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncomeCreateController implements IncomeCreateApi {

    private final AddIncomeService addIncomeService;

    public IncomeCreateController(AddIncomeService addIncomeService) {
        this.addIncomeService = addIncomeService;
    }

    @Override
    public ResponseEntity<IncomeResponse> createIncome(CreateIncomeRequest createIncomeRequest) {
        IncomeDto incomeDto = IncomeMapper.INSTANCE.map(createIncomeRequest);
        IncomeDto saved = addIncomeService.addIncome(incomeDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(IncomeMapper.INSTANCE.map(saved));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
