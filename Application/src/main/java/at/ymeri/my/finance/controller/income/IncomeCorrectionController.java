package at.ymeri.my.finance.controller.income;

import at.ymeri.my.finance.application.controller.income.IncomeCorrectionApi;
import at.ymeri.my.finance.application.data.CorrectIncomeRequest;
import at.ymeri.my.finance.application.data.IncomeHistoryResponse;
import at.ymeri.my.finance.application.data.IncomeResponse;
import at.ymeri.my.finance.application.mapper.IncomeMapper;
import at.ymeri.my.finance.domain.api.CorrectIncomeService;
import at.ymeri.my.finance.domain.api.GetIncomeService;
import at.ymeri.my.finance.domain.api.RemoveIncomeService;
import at.ymeri.my.finance.domain.data.income.IncomeDto;
import at.ymeri.my.finance.domain.data.income.IncomeSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class IncomeCorrectionController implements IncomeCorrectionApi {

    private final CorrectIncomeService correctIncomeService;
    private final RemoveIncomeService removeIncomeService;
    private final GetIncomeService getIncomeService;

    public IncomeCorrectionController(CorrectIncomeService correctIncomeService,
                                      RemoveIncomeService removeIncomeService,
                                      GetIncomeService getIncomeService) {
        this.correctIncomeService = correctIncomeService;
        this.removeIncomeService = removeIncomeService;
        this.getIncomeService = getIncomeService;
    }

    @Override
    public ResponseEntity<IncomeResponse> correctIncome(String id, CorrectIncomeRequest request) {
        IncomeDto corrected = correctIncomeService.correctIncome(UUID.fromString(id), toDto(request));
        return ResponseEntity.ok(IncomeMapper.INSTANCE.map(corrected));
    }

    @Override
    public ResponseEntity<Void> removeIncome(String id) {
        removeIncomeService.removeIncome(UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<IncomeHistoryResponse> getIncomeHistory(String id) {
        List<IncomeResponse> history =
                IncomeMapper.INSTANCE.mapList(getIncomeService.getHistory(UUID.fromString(id)));
        return ResponseEntity.ok(new IncomeHistoryResponse().history(history));
    }

    private static IncomeDto toDto(CorrectIncomeRequest request) {
        IncomeDto dto = new IncomeDto();
        dto.setAmount(request.getAmount() != null ? BigDecimal.valueOf(request.getAmount()) : null);
        dto.setDescription(request.getDescription());
        dto.setTime(request.getTime());
        dto.setSource(request.getSource() != null
                ? IncomeSource.valueOf(request.getSource().getValue())
                : null);
        dto.setAccountId(request.getAccountId());
        return dto;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
