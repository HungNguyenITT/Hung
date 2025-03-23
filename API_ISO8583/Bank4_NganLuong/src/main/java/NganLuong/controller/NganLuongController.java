package NganLuong.controller;

import NganLuong.service.NganLuongService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/ngl")
public class NganLuongController {

    @Autowired
    private NganLuongService nganLuongService;

    @PostMapping(value="/inquiry", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> inquiry(@RequestBody Map<String,String> req){
        return nganLuongService.processInquiry(req);
    }

    @PostMapping(value="/payment", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> payment(@RequestBody Map<String,String> req){
        return nganLuongService.processPayment(req);
    }
}
