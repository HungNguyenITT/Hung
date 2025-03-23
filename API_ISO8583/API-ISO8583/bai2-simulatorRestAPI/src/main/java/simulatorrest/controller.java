package simulatorrest;


import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/simulator")
public class controller {   

    private static final Map<String, Account> ACCOUNTS_DEST = new ConcurrentHashMap<String, Account>();

    static {
        ACCOUNTS_DEST.put("888888", new Account("888888",3_000_000L,"ACTIVE"));
        ACCOUNTS_DEST.put("999999", new Account("999999",5_000_000L,"ACTIVE"));
        ACCOUNTS_DEST.put("777777", new Account("777777",1_000_000L,"LOCKED"));
    }

    @PostMapping
    public Map<String,String> handle(@RequestBody Map<String,Object> req){
        String f0 = (String) req.get("field0");
        if(f0==null) f0="0200";
        String f3 = (String) req.get("field3");
        if(f3==null) f3="";
        String f4 = (String) req.get("field4");
        if(f4==null) f4="0";
        String f47= (String) req.get("field47");
        if(f47==null) f47="";

        long amt=0;
        try{ amt=Long.parseLong(f4);}catch(Exception e){}

        String rc;
        if("432020".equals(f3)) {
            rc = inquiry(f47);
        } else if("912020".equals(f3)) {
            rc = payment(f47, amt);
        } else {
            rc = "UNKNOWN_PC";
        }

        String iso39;
        if("OK".equals(rc)) iso39="00";
        else if("DEST_NOT_FOUND".equals(rc)) iso39="14";
        else if("DEST_NOT_ACTIVE".equals(rc)) iso39="62";
        else iso39="96";

        int mtiNum=200;
        try{ mtiNum=Integer.parseInt(f0);}catch(Exception e){}
        mtiNum += 10;
        String respMti = String.format("%04d", mtiNum);

        Map<String,String> resp = new HashMap<String,String>();
        resp.put("MTI", respMti);
        resp.put("field39", iso39);
        return resp;
    }

    private String inquiry(String pan){
        Account d = ACCOUNTS_DEST.get(pan);
        if(d==null) return "DEST_NOT_FOUND";
        if(!"ACTIVE".equalsIgnoreCase(d.getStatus())) return "DEST_NOT_ACTIVE";
        return "OK";
    }

    private String payment(String pan, long amt){
        Account d = ACCOUNTS_DEST.get(pan);
        if(d==null) return "DEST_NOT_FOUND";
        if(!"ACTIVE".equalsIgnoreCase(d.getStatus())) return "DEST_NOT_ACTIVE";
        d.setBalance(d.getBalance()+amt);
        return "OK";
    }

    private static class Account {
        private String pan;
        private long balance;
        private String status;

        public Account(String p,long b,String s){
            this.pan=p; this.balance=b; this.status=s;
        }
        public String getPan(){return pan;}
        public long getBalance(){return balance;}
        public void setBalance(long bal){this.balance=bal;}
        public String getStatus(){return status;}
        public void setStatus(String st){this.status=st;}
    }
}
