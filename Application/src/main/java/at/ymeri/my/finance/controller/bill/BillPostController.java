package at.ymeri.my.finance.controller.bill;


import at.ymeri.my.finance.application.controller.bill.BillCreateApi;
import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.application.data.BillListResponseModelCommon;
import at.ymeri.my.finance.application.data.BillResponseModel;
import at.ymeri.my.finance.application.mapper.BillMapper;
import at.ymeri.my.finance.domain.api.AddBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillPostController implements BillCreateApi {

    private final AddBillService addBillService;

    public BillPostController(AddBillService addBillService) {
        this.addBillService = addBillService;
    }

    @Override
    public ResponseEntity<BillResponseModel> createBill(Bill bill) {
        BillDto billDto = BillMapper.INSTANCE.map(bill);
        BillDto stored = addBillService.addBill(billDto);

        BillListResponseModelCommon common = new BillListResponseModelCommon()
                .message("OK")
                .success(true);
        BillResponseModel response = new BillResponseModel()
                .bill(BillMapper.INSTANCE.map(stored))
                .common(common);
        return ResponseEntity.ok(response);

    }
}
