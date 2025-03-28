package ZaloPay.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import ZaloPay.service.ZaloPayService;
import java.util.Map;

@RestController
@RequestMapping("/zalo")
public class ZaloPayController {

    @Autowired
    private ZaloPayService zaloPayService;

    // ZaloPay inbound (CLI hoặc bên thứ 3) sẽ gọi 2 endpoint này
    @PostMapping(value="/inquiry", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> inquiry(@RequestBody Map<String,String> req){
        return zaloPayService.processInquiry(req);
    }

    @PostMapping(value="/payment", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> payment(@RequestBody Map<String,String> req){
        return zaloPayService.processPayment(req);
    }
}
