package API2.controller;

import API2.service.Api2Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api2")
public class API2Controller {

    @Autowired
    private Api2Service api2Service;

    @PostMapping(value="/inquiry", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> handleInquiry(@RequestBody Map<String,String> body){
        System.out.println("[API2Controller] Received inquiry => "+body);
        try {
            Map<String,String> resp= api2Service.handleInquiry(body);
            if(resp==null || resp.isEmpty()){
                return error("No resp from Napas?");
            }
            return resp;
        }catch(Exception e){
            e.printStackTrace();
            return error("Error: "+ e.getMessage());
        }
    }

    @PostMapping(value="/payment", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> handlePayment(@RequestBody Map<String,String> body){
        System.out.println("[API2Controller] Received payment => "+body);
        try {
            Map<String,String> resp= api2Service.handlePayment(body);
            if(resp==null || resp.isEmpty()){
                return error("No resp from Napas?");
            }
            return resp;
        }catch(Exception e){
            e.printStackTrace();
            return error("Error: "+ e.getMessage());
        }
    }

    private Map<String,String> error(String msg){
        Map<String,String> m=new HashMap<>();
        m.put("F1","0210");
        m.put("F39","96");
        m.put("error", msg);
        return m;
    }
}
