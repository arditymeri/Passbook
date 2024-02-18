package at.ymeri.my.finance.controller.bill;


import at.ymeri.my.finance.application.controller.bill.BillGetApi;
import at.ymeri.my.finance.application.data.Bill;
import at.ymeri.my.finance.application.data.BillListResponseModel;
import at.ymeri.my.finance.application.data.BillListResponseModelCommon;
import at.ymeri.my.finance.application.data.BillResponseModel;
import at.ymeri.my.finance.application.mapper.BillMapper;
import at.ymeri.my.finance.domain.api.GetBillService;
import at.ymeri.my.finance.domain.data.bill.BillDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class BillGetController implements BillGetApi {

    private final GetBillService getBillService;

    public BillGetController(GetBillService getBillService) {
        this.getBillService = getBillService;
    }

    @Override
    public ResponseEntity<BillListResponseModel> listBills(LocalDate date){
        List<BillDto> bills = getBillService.getAll();
        List<Bill> responseModel = BillMapper.INSTANCE.mapList(bills);

        BillListResponseModelCommon responseModelCommon = new BillListResponseModelCommon()
                .message("OK")
                .success(true);
        return ResponseEntity.ok(new BillListResponseModel()
                .bills(responseModel)
                .common(responseModelCommon));
    }

    @Override
    public ResponseEntity<BillResponseModel> getById(String id){
        BillDto billDto = this.getBillService.getBillById(UUID.fromString(id));
        Bill responseModel = BillMapper.INSTANCE.map(billDto);
        BillListResponseModelCommon responseModelCommon = new BillListResponseModelCommon()
                .message("OK")
                .success(true);
        return ResponseEntity
                .ok(new BillResponseModel()
                        .bill(responseModel)
                        .common(responseModelCommon));
    }
}
