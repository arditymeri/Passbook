package at.ymeri.my.finance.controller.income;

import at.ymeri.my.finance.application.controller.income.IncomeGetApi;
import at.ymeri.my.finance.application.data.IncomeListResponse;
import at.ymeri.my.finance.application.data.IncomeResponse;
import at.ymeri.my.finance.application.mapper.IncomeMapper;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class IncomeGetController implements IncomeGetApi {

    private final GetIncomeService getIncomeService;

    public IncomeGetController(GetIncomeService getIncomeService) {
        this.getIncomeService = getIncomeService;
    }

    @Override
    public ResponseEntity<IncomeResponse> getIncomeById(String id) {
        try {
            IncomeResponse response = IncomeMapper.INSTANCE.map(
                    getIncomeService.getIncomeById(UUID.fromString(id))
            );
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<IncomeListResponse> listIncomes(LocalDate date) {
        List<IncomeResponse> incomes = IncomeMapper.INSTANCE.mapList(getIncomeService.getAll());
        return ResponseEntity.ok(new IncomeListResponse().incomes(incomes));
    }
}
