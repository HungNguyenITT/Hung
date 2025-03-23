package API1.controller;

import API1.service.Api1Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api1")
public class API1Controller {

    @Autowired
    private Api1Service api1Service;

    @PostMapping(value="/inquiry", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> handleInquiry(@RequestBody Map<String,String> body){
        System.out.println("[API1Controller] Received inquiry => "+body);
        try {
            Map<String,String> resp= api1Service.handleInquiry(body);
            if(resp==null || resp.isEmpty()){
                return createError("No response from Napas?");
            }
            return resp;
        }catch(Exception e){
            e.printStackTrace();
            return createError("Error: "+ e.getMessage());
        }
    }

    @PostMapping(value="/payment", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,String> handlePayment(@RequestBody Map<String,String> body){
        System.out.println("[API1Controller] Received payment => "+body);
        try {
            Map<String,String> resp= api1Service.handlePayment(body);
            if(resp==null || resp.isEmpty()){
                return createError("No response from Napas?");
            }
            return resp;
        } catch(Exception e){
            e.printStackTrace();
            return createError("Error: "+ e.getMessage());
        }
    }

    private Map<String,String> createError(String msg){
        Map<String,String> m=new HashMap<>();
        m.put("F1","0210");
        m.put("F39","96");
        m.put("error", msg);
        return m;
    }
}
