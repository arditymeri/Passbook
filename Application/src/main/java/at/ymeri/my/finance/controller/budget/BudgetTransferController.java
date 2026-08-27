package at.ymeri.my.finance.controller.budget;

import at.ymeri.my.finance.application.controller.budget.BudgetTransferApi;
import at.ymeri.my.finance.application.data.TransferAllocationRequest;
import at.ymeri.my.finance.application.data.TransferAllocationResponse;
import at.ymeri.my.finance.application.mapper.BudgetMapper;
import at.ymeri.my.finance.domain.api.MoveAllocationService;
import at.ymeri.my.finance.domain.data.budget.MoveAllocationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class BudgetTransferController implements BudgetTransferApi {

    private final MoveAllocationService moveAllocationService;

    public BudgetTransferController(MoveAllocationService moveAllocationService) {
        this.moveAllocationService = moveAllocationService;
    }

    @Override
    public ResponseEntity<TransferAllocationResponse> transferAllocation(TransferAllocationRequest request) {
        try {
            MoveAllocationResult result = moveAllocationService.moveAllocation(
                    request.getFromCategoryId() != null ? request.getFromCategoryId().toString() : null,
                    request.getToCategoryId() != null ? request.getToCategoryId().toString() : null,
                    request.getYear(),
                    request.getMonth(),
                    request.getAmount());
            return ResponseEntity.ok(BudgetMapper.INSTANCE.map(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
