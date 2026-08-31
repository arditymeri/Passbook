package at.ymeri.my.finance.controller.bill;

import at.ymeri.my.finance.application.controller.bill.BillNecessityTagApi;
import at.ymeri.my.finance.application.data.BillResponseModel;
import at.ymeri.my.finance.application.data.UpdateNecessityTagRequest;
import at.ymeri.my.finance.application.data.BillListResponseModelCommon;
import at.ymeri.my.finance.application.mapper.BillMapper;
import at.ymeri.my.finance.domain.api.UpdateBillNecessityTagService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.bill.NecessityTag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
public class BillNecessityTagController implements BillNecessityTagApi {

    private final UpdateBillNecessityTagService updateBillNecessityTagService;

    public BillNecessityTagController(UpdateBillNecessityTagService updateBillNecessityTagService) {
        this.updateBillNecessityTagService = updateBillNecessityTagService;
    }

    @Override
    public ResponseEntity<BillResponseModel> updateBillNecessityTag(String id, UpdateNecessityTagRequest request) {
        NecessityTag tag = request.getTag() != null ? NecessityTag.valueOf(request.getTag().name()) : null;
        BillDto updated = updateBillNecessityTagService.updateNecessityTag(id, tag);

        BillListResponseModelCommon common = new BillListResponseModelCommon()
                .message("OK")
                .success(true);
        return ResponseEntity.ok(new BillResponseModel()
                .bill(BillMapper.INSTANCE.map(updated))
                .common(common));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
