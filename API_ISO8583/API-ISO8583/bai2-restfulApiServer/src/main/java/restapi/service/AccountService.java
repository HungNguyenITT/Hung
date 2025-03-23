package restapi.service;


import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    private static final Map<String, Account> ACCOUNTS_SOURCE = new HashMap<>();
    private static final Map<String, String> CONFIRM_CODES = new HashMap<>();

    static {
        ACCOUNTS_SOURCE.put("111111", new Account("111111",1_000_000L,"ACTIVE"));
        ACCOUNTS_SOURCE.put("222222", new Account("222222",500_000L,"ACTIVE"));
        ACCOUNTS_SOURCE.put("333333", new Account("333333",2_000_000L,"LOCKED"));
    }

    public String checkSource(String pan) {
        Account a = ACCOUNTS_SOURCE.get(pan);
        if(a==null) return "SOURCE_NOT_FOUND";
        if(!"ACTIVE".equalsIgnoreCase(a.getStatus())) return "SOURCE_NOT_ACTIVE";
        return "OK";
    }

    public String generateConfirmCode(String sourcePan, String destPan) {
        String code = UUID.randomUUID().toString().substring(0,5).toUpperCase();
        String key = sourcePan + "_" + destPan;
        CONFIRM_CODES.put(key, code);
        return code;
    }

    public String checkConfirmCode(String sourcePan, String destPan, String userCode) {
        String key = sourcePan + "_" + destPan;
        String real = CONFIRM_CODES.get(key);
        if(real==null || !real.equals(userCode)) {
            return "INVALID_CONFIRM_CODE";
        }
        return "OK";
    }

    public String debit(String sourcePan, long amount) {
        Account a = ACCOUNTS_SOURCE.get(sourcePan);
        if(a==null) return "SOURCE_NOT_FOUND";
        if(!"ACTIVE".equalsIgnoreCase(a.getStatus())) return "SOURCE_NOT_ACTIVE";
        if(a.getBalance()<amount) return "SOURCE_INSUFFICIENT_FUNDS";
        a.setBalance(a.getBalance()-amount);
        return "OK";
    }

    public void removeConfirmCode(String sourcePan, String destPan) {
        String key = sourcePan + "_" + destPan;
        CONFIRM_CODES.remove(key);
    }
    public long getBalance(String pan) {
        Account a = ACCOUNTS_SOURCE.get(pan);
        if(a==null) return -1;
        return a.getBalance();
    }

    private static class Account {
        private String pan;
        private long balance;
        private String status;

        public Account(String p, long b, String s){
            this.pan=p; this.balance=b; this.status=s;
        }
        public String getPan(){return pan;}
        public long getBalance(){return balance;}
        public void setBalance(long b){this.balance=b;}
        public String getStatus(){return status;}
        public void setStatus(String st){this.status=st;}
    }
}

