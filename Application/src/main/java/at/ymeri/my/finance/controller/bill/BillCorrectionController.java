package at.ymeri.my.finance.controller.bill;

import at.ymeri.my.finance.application.controller.bill.BillCorrectionApi;
import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.application.data.BillHistoryResponse;
import at.ymeri.my.finance.application.data.BillListResponseModelCommon;
import at.ymeri.my.finance.application.data.BillResponseModel;
import at.ymeri.my.finance.application.data.CorrectBillRequest;
import at.ymeri.my.finance.application.mapper.BillMapper;
import at.ymeri.my.finance.domain.api.CorrectBillService;
import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.api.RemoveBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class BillCorrectionController implements BillCorrectionApi {

    private final CorrectBillService correctBillService;
    private final RemoveBillService removeBillService;
    private final GetBillService getBillService;

    public BillCorrectionController(CorrectBillService correctBillService,
                                    RemoveBillService removeBillService,
                                    GetBillService getBillService) {
        this.correctBillService = correctBillService;
        this.removeBillService = removeBillService;
        this.getBillService = getBillService;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<BillResponseModel> correctBill(String id, CorrectBillRequest request) {
        BillDto corrected = correctBillService.correctBill(UUID.fromString(id), toDto(request));

        BillListResponseModelCommon common = new BillListResponseModelCommon()
                .message("OK")
                .success(true);
        return ResponseEntity.ok(new BillResponseModel()
                .bill(BillMapper.INSTANCE.map(corrected))
                .common(common));
    }

    @Override
    public ResponseEntity<Void> removeBill(String id) {
        removeBillService.removeBill(UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<BillHistoryResponse> getBillHistory(String id) {
        List<Bill> history = BillMapper.INSTANCE.mapList(getBillService.getHistory(UUID.fromString(id)));
        return ResponseEntity.ok(new BillHistoryResponse().history(history));
    }

    private static BillDto toDto(CorrectBillRequest request) {
        BillDto dto = new BillDto();
        dto.setAmount(request.getAmount() != null ? BigDecimal.valueOf(request.getAmount()) : null);
        dto.setDescription(request.getDescription());
        dto.setTime(request.getTime());
        dto.setCategoryId(request.getCategoryId());
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
