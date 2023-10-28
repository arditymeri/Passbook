package at.ymeri.controller.bill;

import at.ymeri.finance.controller.bill.BillsApi;
import at.ymeri.finance.data.BillResponseModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
public class BillGetController implements BillsApi {

    @Override
    public ResponseEntity<BillResponseModel> listBills(LocalDate date){
        BillResponseModel responseModel = new BillResponseModel();
        responseModel.id("");
        responseModel.setDate(LocalDate.now());
        responseModel.setAmount(BigDecimal.TEN.doubleValue());
        return ResponseEntity.ok(responseModel);
    }
}
